package com.example.game.Render.Passes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.example.game.Render.Pipeline.RenderContext;
import com.example.game.Render.Pipeline.RenderPass;

/**
 * HDR pre-pass: apply filmic tonemapping to the scene color before subsequent post (e.g., god rays).
 * Renders sceneColorTex -> temp FBO with filmic curve, then copies temp -> scene FBO color.
 */
public final class HDRPrePass implements RenderPass {
  private FrameBuffer temp;
  private ShaderProgram tonemap;
  private ShaderProgram blit;
  private Mesh fsQuad;
  // Luminance measurement
  private FrameBuffer lumFbo; // 1x1
  private ShaderProgram lumProg; // center-weighted log-average luminance
  private java.nio.ByteBuffer lumBuf;
  // Histogram metering resources (downsampled luminance grid)
  private FrameBuffer lumGridFbo; // e.g., 64x64 grid
  private ShaderProgram lumGridProg; // outputs per-pixel luminance
  private java.nio.ByteBuffer lumGridBuf;

  // Metering throttle/cache
  private float sampleIntervalSec = 0.10f; // sample metering at ~10 Hz
  private float sampleTimer = 0f;
  private boolean haveSample = false;
  private float cacheLum = 0f, cacheLref = 0f, cacheL98 = 0f, cacheBright = 0f;

  // Base exposure for the scene prior to eye adaptation
  private float exposure = 0.9f;
  private float saturation = 1.08f; // mild color boost to avoid greyscale sky
  private float contrast = 1.06f;   // subtle contrast around midtones

  public void setExposure(float e) { exposure = e; }
  public void setSaturation(float s) { saturation = s; }
  public void setContrast(float c) { contrast = c; }
  public float getExposure() { return exposure; }
  public float getSaturation() { return saturation; }
  public float getContrast() { return contrast; }

  public void init(int w, int h) {
    ShaderProgram.pedantic = false;
    String vsrc = "attribute vec2 a_position;\n"+
                  "attribute vec2 a_texCoord0;\n"+
                  "varying vec2 v_uv;\n"+
                  "void main(){ v_uv=a_texCoord0; gl_Position=vec4(a_position,0.0,1.0); }";
    // Uncharted2 filmic tonemap (Hable) with white scale + mild saturation and contrast adjustments
    String fsrc = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"+
                  "varying vec2 v_uv;\n"+
                  "uniform sampler2D u_tex;\n"+
                  "uniform float u_exposure;\n"+
                  "uniform float u_saturation;\n"+
                  "uniform float u_contrast;\n"+
                  "vec3 uncharted2Tonemap(vec3 x){\n"+
                  "  const float A=0.22; const float B=0.30; const float C=0.10; const float D=0.20; const float E=0.01; const float F=0.30;\n"+
                  "  return ((x*(A*x+C*B)+D*E)/(x*(A*x+B)+D*F)) - E/F;\n"+
                  "}\n"+
                  "void main(){\n"+
                  "  vec3 c = texture2D(u_tex, v_uv).rgb;\n"+
                  "  c *= u_exposure;\n"+
                  "  float W = 11.2;\n"+
                  "  vec3 curr = uncharted2Tonemap(c);\n"+
                  "  vec3 white = vec3(uncharted2Tonemap(vec3(W)));\n"+
                  "  c = curr / white;\n"+
                  "  // mild saturation boost to counter flat look\n"+
                  "  float luma = dot(c, vec3(0.2126,0.7152,0.0722));\n"+
                  "  c = mix(vec3(luma), c, u_saturation);\n"+
                  "  // subtle contrast about 0.5 (scene-referred after tonemap)\n"+
                  "  c = (c - 0.5) * u_contrast + 0.5;\n"+
                  "  c = clamp(c, 0.0, 1.0);\n"+
                  "  gl_FragColor = vec4(c,1.0);\n"+
                  "}";
    tonemap = new ShaderProgram(vsrc, fsrc);
    if (!tonemap.isCompiled()) throw new IllegalStateException(tonemap.getLog());

    String fsrcBlit = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"+
                      "varying vec2 v_uv;\n"+
                      "uniform sampler2D u_tex;\n"+
                      "void main(){ vec3 c = texture2D(u_tex, v_uv).rgb; gl_FragColor = vec4(c,1.0); }";
    blit = new ShaderProgram(vsrc, fsrcBlit);
    if (!blit.isCompiled()) throw new IllegalStateException(blit.getLog());

    fsQuad = buildFullScreenQuad();
    resize(w, h);

    // Luminance resources
    lumFbo = new FrameBuffer(Pixmap.Format.RGBA8888, 1, 1, false);
    ShaderProgram.pedantic = false;
    String vLum = "attribute vec2 a_position;\n"+
                  "attribute vec2 a_texCoord0;\n"+
                  "varying vec2 v_uv;\n"+
                  "void main(){ v_uv=a_texCoord0; gl_Position=vec4(a_position,0.0,1.0); }";
    String fLum = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"+
                  "varying vec2 v_uv;\n"+
                  "uniform sampler2D u_tex;\n"+
                  "float lum(vec3 c){ return dot(c, vec3(0.2126,0.7152,0.0722)); }\n"+
                  "void main(){\n"+
                  "  const int N = 4;\n"+
                  "  float sumLogW = 0.0;\n"+
                  "  float sumW = 0.0;\n"+
                  "  const float eps = 1e-4;\n"+
                  "  const float sigma = 0.18;\n"+
                  "  // center-weighted grid over full scene\n"+
                  "  for (int j=0;j<N;j++){\n"+
                  "    for (int i=0;i<N;i++){\n"+
                  "      vec2 uv = (vec2(float(i)+0.5, float(j)+0.5) / float(N));\n"+
                  "      // restrict to central window ~60% of screen\n"+
                  "      vec2 minC = vec2(0.2); vec2 maxC = vec2(0.8);\n"+
                  "      uv = mix(minC, maxC, uv);\n"+
                  "      vec2 d = uv - vec2(0.5);\n"+
                  "      float w = exp(-dot(d,d)/(2.0*sigma*sigma));\n"+
                  "      vec3 c = texture2D(u_tex, uv).rgb;\n"+
                  "      float l = lum(c);\n"+
                  "      sumLogW += w * log(max(l, eps));\n"+
                  "      sumW += w;\n"+
                  "    }\n"+
                  "  }\n"+
                  "  float avgLog = exp(sumLogW / max(sumW, eps));\n"+
                  "  gl_FragColor = vec4(avgLog, avgLog, avgLog, 1.0);\n"+
                  "}";
    lumProg = new ShaderProgram(vLum, fLum);
    if (!lumProg.isCompiled()) throw new IllegalStateException(lumProg.getLog());
    lumBuf = java.nio.ByteBuffer.allocateDirect(4);
    lumBuf.order(java.nio.ByteOrder.nativeOrder());

    // Histogram grid resources (reduced to 32x32 to avoid heavy readback stalls)
    lumGridFbo = new FrameBuffer(Pixmap.Format.RGBA8888, 32, 32, false);
    String fLumGrid = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"+
                     "varying vec2 v_uv;\n"+
                     "uniform sampler2D u_tex;\n"+
                     "float lum(vec3 c){ return dot(c, vec3(0.2126,0.7152,0.0722)); }\n"+
                     "void main(){ vec3 c = texture2D(u_tex, v_uv).rgb; float l = lum(c); gl_FragColor = vec4(l,l,l,1.0); }";
    lumGridProg = new ShaderProgram(vLum, fLumGrid);
    if (!lumGridProg.isCompiled()) throw new IllegalStateException(lumGridProg.getLog());
    lumGridBuf = java.nio.ByteBuffer.allocateDirect(lumGridFbo.getWidth() * lumGridFbo.getHeight() * 4);
    lumGridBuf.order(java.nio.ByteOrder.nativeOrder());
  }

  @Override public void resize(int w, int h) {
    if (temp != null) temp.dispose();
    int tw = Math.max(1, w);
    int th = Math.max(1, h);
    temp = new FrameBuffer(Pixmap.Format.RGBA8888, tw, th, false);
    try {
      Texture t = temp.getColorBufferTexture();
      t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
      t.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
    } catch (Throwable ignored) {}
  }

  @Override public void update(float dt, RenderContext ctx) {
    sampleTimer += Math.max(0f, dt);
  }

  @Override public void render(RenderContext ctx) {
    if (ctx.sceneColorTex == null || ctx.sceneFboHandle == 0) return;
    boolean doSample = (sampleTimer >= sampleIntervalSec) || !haveSample;
    if (doSample) {
      // Measure pre-tonemap luminance into 1x1 and store in context
      lumFbo.begin();
      Gdx.gl.glViewport(0, 0, 1, 1);
      try { Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); } catch (Throwable ignored) {}
      try { Gdx.gl.glDisable(GL20.GL_BLEND); } catch (Throwable ignored) {}
      try { Gdx.gl.glDisable(GL20.GL_CULL_FACE); } catch (Throwable ignored) {}
      lumProg.bind();
      lumProg.setUniformi("u_tex", 0);
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      ctx.sceneColorTex.bind();
      fsQuad.render(lumProg, GL20.GL_TRIANGLES);
      lumBuf.clear();
      Gdx.gl.glReadPixels(0, 0, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, lumBuf);
      lumFbo.end();
      lumBuf.rewind();
      int r = lumBuf.get() & 0xFF;
      // skip G,B,A
      lumBuf.get(); lumBuf.get(); lumBuf.get();
      cacheLum = r / 255f;

      // Build a small luminance grid and compute robust percentiles
      lumGridFbo.begin();
      Gdx.gl.glViewport(0, 0, lumGridFbo.getWidth(), lumGridFbo.getHeight());
      try { Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); } catch (Throwable ignored) {}
      try { Gdx.gl.glDisable(GL20.GL_BLEND); } catch (Throwable ignored) {}
      try { Gdx.gl.glDisable(GL20.GL_CULL_FACE); } catch (Throwable ignored) {}
      lumGridProg.bind();
      lumGridProg.setUniformi("u_tex", 0);
      Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
      ctx.sceneColorTex.bind();
      fsQuad.render(lumGridProg, GL20.GL_TRIANGLES);
      lumGridBuf.clear();
      Gdx.gl.glReadPixels(0, 0, lumGridFbo.getWidth(), lumGridFbo.getHeight(), GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, lumGridBuf);
      lumGridFbo.end();

      // Histogram over 256 bins with center weighting; ignore bottom/top tails
      final int W = lumGridFbo.getWidth();
      final int H = lumGridFbo.getHeight();
      float[] hist = new float[256];
      float totalW = 0f;
      lumGridBuf.rewind();
      for (int y = 0; y < H; y++) {
        for (int x = 0; x < W; x++) {
          int R8 = lumGridBuf.get() & 0xFF; // R
          lumGridBuf.get(); lumGridBuf.get(); lumGridBuf.get(); // G,B,A
          float l = R8 / 255f;
          // center weight (vignette): Gaussian in NDC
          float nx = (x + 0.5f) / W * 2f - 1f;
          float ny = (y + 0.5f) / H * 2f - 1f;
          float d2 = nx*nx + ny*ny;
          float wv = (float)Math.exp(-d2 / (2f * 0.5f * 0.5f)); // sigma ~0.5
          int bin = Math.min(255, Math.max(0, (int)(l * 255f + 0.5f)));
          hist[bin] += wv;
          totalW += wv;
        }
      }
      // Build CDF and trim tails
      float bottomCut = totalW * 0.05f; // 5%
      float topCut = totalW * 0.001f;   // 0.1%
      float[] cdf = new float[256];
      float acc = 0f;
      for (int i = 0; i < 256; i++) { acc += hist[i]; cdf[i] = acc; }
      float lo = bottomCut;
      float hi = totalW - topCut;
      // Helper to find percentile in [lo,hi]
      java.util.function.Function<Float, Float> perc = (p) -> {
        float t = lo + (hi - lo) * p;
        for (int i = 0; i < 256; i++) { if (cdf[i] >= t) return i / 255f; }
        return 1f;
      };
      cacheLref = perc.apply(0.88f);
      cacheL98  = perc.apply(0.98f);
      // Bright fraction above high threshold (e.g., 0.9)
      float brightCount = 0f;
      for (int i = (int)(0.9f * 255f); i < 256; i++) brightCount += hist[i];
      cacheBright = totalW > 1e-6f ? brightCount / totalW : 0f;

      haveSample = true;
      sampleTimer = 0f;
    }

    // Publish cached values to context every frame
    ctx.hdrLuminance = cacheLum;
    ctx.hdrLuminanceRef = cacheLref;
    ctx.hdrLuminanceP98 = cacheL98;
    ctx.hdrBrightFrac = cacheBright;

    // Stats-only mode: leave scene color as-is; tonemapping is done in final blit now.
    // No draws here other than metering above.
  }

  @Override public void dispose() {
    if (temp != null) temp.dispose();
    if (fsQuad != null) fsQuad.dispose();
    if (tonemap != null) tonemap.dispose();
    if (blit != null) blit.dispose();
    if (lumFbo != null) lumFbo.dispose();
    if (lumProg != null) lumProg.dispose();
    if (lumGridFbo != null) lumGridFbo.dispose();
    if (lumGridProg != null) lumGridProg.dispose();
  }

  private static Mesh buildFullScreenQuad() {
    float[] v = {
      -1,-1, 0,0,
       1,-1, 1,0,
       1, 1, 1,1,
      -1, 1, 0,1
    };
    short[] i = {0,1,2, 0,2,3};
    Mesh m = new Mesh(true, 4, 6,
      new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
      new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"));
    m.setVertices(v);
    m.setIndices(i);
    return m;
  }
}
