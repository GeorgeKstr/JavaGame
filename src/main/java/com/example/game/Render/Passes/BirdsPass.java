package com.example.game.Render.Passes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.example.game.Render.Pipeline.RenderContext;
import com.example.game.Render.Pipeline.RenderPass;

/**
 * Birds rendered as camera-facing textured quads on a dome centered on the camera (“headspace”).
 * - Uniform-on-hemisphere distribution with adjustable horizon bias (fewer near screen center).
 * - PNG/atlas texture with alpha. Mipmapped by default to avoid shimmer.
 * - Restores GL state after drawing to keep later passes correct.
 */
public final class BirdsPass implements RenderPass {

  // ===== Shaders =====
  private static final String VERT =
      "attribute vec3 aAziElvSiz;" +     // x=azimuth(rad), y=elevation(rad), z=sizeScale
      "attribute vec2 aSpdSeed;" +       // x=speed(rad/s), y=seed
      "attribute vec2 aCorner;" +        // -1..1 quad corner
      "uniform mat4  uProjView;" +
      "uniform vec3  uCamPos;" +
      "uniform float uTime;" +
      "uniform float uRadius;" +
      "uniform float uMinPx;" +
      "uniform float uMaxPx;" +
      "uniform float uPxToWorld;" +      // pixels -> world units at distance uRadius
      "varying float vFade;" +
      "varying float vSeed;" +
      "varying vec2  vUV;" +
      "void main(){\n" +
      "  float az = aAziElvSiz.x + aSpdSeed.x * uTime;\n" +
      "  float el = aAziElvSiz.y;\n" +
      "  float cE = cos(el), sE = sin(el);\n" +
      "  vec3 dir = vec3(sin(az)*cE, sE, -cos(az)*cE);   // cam->bird on dome\n" +
      "  vec3 center = uCamPos + dir * uRadius;\n" +
      "  // per-bird camera-facing basis (normal toward camera)\n" +
      "  vec3 n = -normalize(dir);\n" +
      "  vec3 upRef = abs(n.y) > 0.99 ? vec3(1.0,0.0,0.0) : vec3(0.0,1.0,0.0);\n" +
      "  vec3 r = normalize(cross(upRef, n));\n" +
      "  vec3 u = cross(n, r);\n" +
      "  // size: lessen growth near zenith to avoid visual “center clustering”\n" +
      "  float t = clamp(dir.y*0.5+0.5, 0.0, 1.0);\n" +
      "  float px = mix(uMinPx, uMaxPx, pow(t, 0.6)) * aAziElvSiz.z;\n" +
      "  vec3 billboard = (r * aCorner.x + u * aCorner.y) * (px * uPxToWorld);\n" +
      "  gl_Position = uProjView * vec4(center + billboard, 1.0);\n" +
      "  vFade = smoothstep(-0.1, 0.4, dir.y);\n" +
      "  vSeed = aSpdSeed.y;\n" +
      "  vUV = aCorner * 0.5 + 0.5; // -1..1 -> 0..1\n" +
      "}\n";

  private static final String FRAG =
      "#ifdef GL_ES\nprecision mediump float;\n#endif\n" +
      "uniform sampler2D uTex;" +
      "uniform vec4  uUVRect;" +          // (u1,v1,u2,v2) if using atlas; else (0,0,1,1)\n" +
      "uniform vec3  uTint;" +            // multiply color (for silhouettes use near-white)\n" +
      "uniform float uTime;" +
      "varying float vFade;" +
      "varying float vSeed;" +
      "varying vec2  vUV;" +
      "void main(){\n" +
      "  vec2 uv = mix(uUVRect.xy, uUVRect.zw, vUV);\n" +
      "  vec4 tex = texture2D(uTex, uv);\n" +
      "  float flap = 0.12 * sin(uTime*6.0 + vSeed*17.0);\n" +
      "  float a = tex.a * (0.65 + flap) * vFade;\n" +
      "  vec3 col = tex.rgb * uTint;\n" +
      "  gl_FragColor = vec4(col, a);\n" +
      "}\n";

  // ===== Data & resources =====
  private Mesh mesh;
  private ShaderProgram shader;
  private int birdCount;

  private Texture tex;
  private boolean ownTexture = false;
  private float u1 = 0f, v1 = 0f, u2 = 1f, v2 = 1f;

  // Tunables
  private float domeRadius = 250f;
  private float minPx = 2.0f, maxPx = 9.0f;
  private float globalSpeedMul = 1.0f;
  private float tintR = 1.0f, tintG = 1.0f, tintB = 1.0f; // default white (use PNG’s color)

  // Distribution controls
  private float minElevDeg = 3f;    // avoid exact horizon seam
  private float maxElevDeg = 85f;   // avoid pole singularities
  private float horizonBias = 0.35f; // [-1..1] >0 pushes toward horizon (less center)

  public BirdsPass(int count) { this.birdCount = Math.max(0, count); }

  // ===== Public API =====
  public void setTexture(Texture texture, boolean takeOwnership) {
    if (ownTexture && tex != null) tex.dispose();
    this.tex = texture; this.ownTexture = takeOwnership;
    this.u1 = 0f; this.v1 = 0f; this.u2 = 1f; this.v2 = 1f;
  }
  public void setTextureRegion(TextureRegion rgn, boolean takeOwnershipOfUnderlying) {
    setTexture(rgn.getTexture(), takeOwnershipOfUnderlying);
    this.u1 = rgn.getU(); this.v1 = rgn.getV(); this.u2 = rgn.getU2(); this.v2 = rgn.getV2();
  }
  public void setTint(float r, float g, float b) { tintR=r; tintG=g; tintB=b; }
  public void setRadius(float r) { domeRadius = Math.max(10f, r); }
  public void setPixelSizeRange(float min, float max) { minPx = min; maxPx = Math.max(min, max); }
  public void setGlobalSpeed(float mul) { globalSpeedMul = Math.max(0f, mul); }
  public void setCount(int count) { if (count != birdCount) { disposeMesh(); birdCount = Math.max(0, count); buildMesh(); } }
  public void setElevationBand(float minDeg, float maxDeg){ minElevDeg=minDeg; maxElevDeg=maxDeg; }
  public void setHorizonBias(float b){ horizonBias = MathUtils.clamp(b, -1f, 1f); }

  // ===== Lifecycle =====
  public void init() {
    ShaderProgram.pedantic = false;
    shader = new ShaderProgram(VERT, FRAG);
    if (!shader.isCompiled()) throw new IllegalStateException("Birds shader: " + shader.getLog());
    buildMesh();
    if (tex == null) {
      // Provide your own: textures/bird_silhouette.png (white silhouette + alpha)
      tex = new Texture(Gdx.files.internal("textures/bird_silhouette.png"), true);
      tex.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
      tex.setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);
      ownTexture = true;
    }
  }

  private void buildMesh() {
    if (birdCount == 0) return;

    final int VERTS = birdCount * 4;
    final int INDS  = birdCount * 6;

    mesh = new Mesh(true, VERTS, INDS,
        new VertexAttribute(VertexAttributes.Usage.Position, 3, "aAziElvSiz"),
        new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "aSpdSeed"),
        new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "aCorner"));

    float[] verts = new float[VERTS * 7]; // 3 + 2 + 2
    short[] idx   = new short[INDS];

    int vi = 0, ii = 0, base = 0;

    // Precompute sin(elev) band for uniform distribution
    float sMin = MathUtils.sinDeg(minElevDeg);
    float sMax = MathUtils.sinDeg(maxElevDeg);

    for (int b = 0; b < birdCount; b++) {
      float az    = MathUtils.random(MathUtils.PI2);

      // Uniform in sin(elev), with optional bias toward horizon (u->1)
      float u     = bias01(MathUtils.random(), horizonBias);
      float s     = MathUtils.lerp(sMin, sMax, u);
      float el    = (float)Math.asin(s);

      float size  = MathUtils.lerp(0.8f, 1.3f, MathUtils.random());
      float speed = MathUtils.lerp(0.15f, 0.6f, MathUtils.random());
      float seed  = MathUtils.random();

      // 4 corners: (-1,-1), (1,-1), (1,1), (-1,1)
      float[][] corners = { {-1f,-1f}, {1f,-1f}, {1f,1f}, {-1f,1f} };
      for (int c = 0; c < 4; c++) {
        verts[vi++] = az;    // aAziElvSiz.x
        verts[vi++] = el;    // aAziElvSiz.y
        verts[vi++] = size;  // aAziElvSiz.z
        verts[vi++] = speed; // aSpdSeed.x
        verts[vi++] = seed;  // aSpdSeed.y
        verts[vi++] = corners[c][0]; // aCorner.x
        verts[vi++] = corners[c][1]; // aCorner.y
      }

      // indices (two triangles)
      idx[ii++] = (short)(base+0); idx[ii++] = (short)(base+1); idx[ii++] = (short)(base+2);
      idx[ii++] = (short)(base+0); idx[ii++] = (short)(base+2); idx[ii++] = (short)(base+3);
      base += 4;
    }

    mesh.setVertices(verts);
    mesh.setIndices(idx);
  }

  @Override public void render(RenderContext ctx) {
    if (mesh == null || shader == null || birdCount == 0 || tex == null) return;

    // pixels -> world at dome radius: 2 * r * tan(fovY/2) / viewportHeight
    float pxToWorld = (2f * domeRadius * MathUtils.tanDeg(ctx.cam.fieldOfView * 0.5f))
                    / Gdx.graphics.getHeight();

    // --- State for over-sky transparent quads (no depth writes) ---
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDepthMask(false);
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

    shader.bind();
    shader.setUniformMatrix("uProjView", ctx.cam.combined);
    shader.setUniformf("uCamPos", ctx.cam.position);
    shader.setUniformf("uTime", ctx.elapsedSeconds * globalSpeedMul);
    shader.setUniformf("uRadius", domeRadius);
    shader.setUniformf("uMinPx", minPx);
    shader.setUniformf("uMaxPx", maxPx);
    shader.setUniformf("uPxToWorld", pxToWorld);
    shader.setUniformf("uUVRect", u1, v1, u2, v2);
    shader.setUniformf("uTint", tintR, tintG, tintB);

    tex.bind(0);
    shader.setUniformi("uTex", 0);

    mesh.render(shader, GL20.GL_TRIANGLES);

    // --- Restore (terrain expects opaque, depth-tested, culled) ---
    Gdx.gl.glDisable(GL20.GL_BLEND);
    Gdx.gl.glDepthMask(true);
    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
  }

  @Override public void resize(int w, int h) { /* no buffers */ }

  @Override public void dispose() {
    disposeMesh();
    if (shader != null) { shader.dispose(); shader = null; }
    if (ownTexture && tex != null) { tex.dispose(); tex = null; }
  }

  private void disposeMesh(){ if (mesh != null) { mesh.dispose(); mesh = null; } }

  // Bias helper: push random u in [0..1] toward 1 (horizon) or 0 (zenith)
  private static float bias01(float u, float bias){
    if (bias == 0f) return u;
    float k = 1f + 4f * Math.abs(bias);       // strength
    return (bias > 0f) ? (1f - (float)Math.pow(1f - u, k))
                       : (float)Math.pow(u, k);
  }
}
