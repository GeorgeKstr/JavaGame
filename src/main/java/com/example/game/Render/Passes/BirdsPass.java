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
import com.badlogic.gdx.math.Vector3;
import com.example.game.Render.Pipeline.RenderContext;
import com.example.game.Render.Pipeline.RenderPass;

public final class BirdsPass implements RenderPass {

  // ======== Shaders (world-anchored + sky-anchored equalization) ========
  private static final String VERT =
      // per-bird
      "attribute vec3 aAziElvSiz;" +     // x=azimuth(rad), y=elevation(rad), z=sizeScale
      "attribute vec2 aSpdSeed;" +       // x=azimuthSpeed(rad/s), y=seed
      "attribute vec2 aCorner;" +        // -1..1 quad corner
      // camera basis & optics
      "uniform mat4  uProjView;" +
      "uniform vec3  uCamPos;" +
      "uniform vec3  uCamDir;" +
      "uniform vec3  uCamRight;" +
      "uniform vec3  uCamUp;" +
      "uniform float uTanHalfFovX;" +
      "uniform float uTanHalfFovY;" +
      // params
      "uniform float uTime;" +
      "uniform float uRadius;" +
      "uniform float uMinPx;" +
      "uniform float uMaxPx;" +
      "uniform float uPxToWorld;" +
      "uniform float uCenterRepel;" +    // 0..1 outward push amount
      // varyings
      "varying float vFade;" +
      "varying float vSeed;" +
      "varying vec2  vUV;" +
      // helpers
      "vec2 toNDC(vec3 d){\n" +
      "  float denom = max(1e-4, dot(d, uCamDir));\n" +                 // avoid div-by-0 / behind camera
      "  float sx = dot(d, uCamRight) / (denom * uTanHalfFovX);\n" +
      "  float sy = dot(d, uCamUp)    / (denom * uTanHalfFovY);\n" +
      "  return vec2(sx, sy);\n" +
      "}\n" +
      "vec3 fromNDC(vec2 s){\n" +
      "  return normalize(uCamDir + s.x * uTanHalfFovX * uCamRight + s.y * uTanHalfFovY * uCamUp);\n" +
      "}\n" +
      "vec2 skyAnchorNDC(){\n" +                                           // project world-up
      "  vec3 upW = vec3(0.0, 1.0, 0.0);\n" +
      "  float denom = dot(upW, uCamDir);\n" +
      "  if (denom <= 0.01) return vec2(0.0, 1.2); // if behind camera, place anchor just above top\n" +
      "  float sx = dot(upW, uCamRight) / (denom * uTanHalfFovX);\n" +
      "  float sy = dot(upW, uCamUp)    / (denom * uTanHalfFovY);\n" +
      "  return vec2(sx, sy);\n" +
      "}\n" +
      "vec2 equalizeAbout(vec2 s, vec2 anchor, float k, float anisoY){\n" +
      "  if (k <= 0.0) return s;\n" +
      "  vec2 v = s - anchor;\n" +
      "  v.y *= anisoY;                              // more push vertically when looking down\n" +
      "  float r = length(v);\n" +
      "  if (r < 1e-5) return s;\n" +
      "  float exp = mix(1.0, 0.55, clamp(k, 0.0, 1.0)); // 1=no change, 0.55=strong push\n" +
      "  float r2 = pow(clamp(r, 0.0, 2.0), exp);\n" +
      "  v *= (r2 / r);\n" +
      "  v.y /= anisoY;\n" +
      "  return anchor + v;\n" +
      "}\n" +
      "void main(){\n" +
      "  // 1) base world direction on the dome (Y-up)\n" +
      "  float az = aAziElvSiz.x + aSpdSeed.x * uTime; // spin around world Y\n" +
      "  float el = aAziElvSiz.y;\n" +
      "  float cE = cos(el), sE = sin(el);\n" +
      "  vec3 dir = vec3(sin(az)*cE, sE, -cos(az)*cE);\n" +
      "  // 2) equalize in screen space around the sky anchor (projected world-up)\n" +
      "  vec2 anchor = skyAnchorNDC();\n" +
      "  float pitch = dot(uCamDir, vec3(0.0,1.0,0.0));     // +1=looking up, -1=down\n" +
      "  float anisoY = mix(1.0, 1.8, clamp(-pitch, 0.0, 1.0)); // more vertical push when looking down\n" +
      "  vec2 s0 = toNDC(dir);\n" +
      "  vec2 s1 = equalizeAbout(s0, anchor, uCenterRepel, anisoY);\n" +
      "  vec3 d  = fromNDC(s1);\n" +
      "  // 3) place on dome & billboard toward camera\n" +
      "  vec3 center = uCamPos + d * uRadius;\n" +
      "  vec3 n = -d;\n" +
      "  vec3 upRef = abs(n.y) > 0.99 ? vec3(1.0,0.0,0.0) : vec3(0.0,1.0,0.0);\n" +
      "  vec3 r = normalize(cross(upRef, n));\n" +
      "  vec3 u = cross(n, r);\n" +
      "  float px = mix(uMinPx, uMaxPx, 0.5) * aAziElvSiz.z; // keep size neutral (no zenith bias)\n" +
      "  vec3 billboard = (r * aCorner.x + u * aCorner.y) * (px * uPxToWorld);\n" +
      "  gl_Position = uProjView * vec4(center + billboard, 1.0);\n" +
      "  vFade = smoothstep(-0.2, 0.35, d.y);\n" +
      "  vSeed = aSpdSeed.y;\n" +
      "  vUV = aCorner * 0.5 + 0.5;\n" +
      "}\n";

  private static final String FRAG =
      "#ifdef GL_ES\nprecision mediump float;\n#endif\n" +
      "uniform sampler2D uTex;" +
      "uniform vec4  uUVRect;" +
      "uniform vec3  uTint;" +
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

  // ======== Data/resources ========
  private Mesh mesh;
  private ShaderProgram shader;
  private int birdCount;

  private Texture tex;
  private boolean ownTexture = false;
  private float u1=0f, v1=0f, u2=1f, v2=1f;

  // Tunables
  private float domeRadius = 250f;
  private float minPx = 2.0f, maxPx = 9.0f;
  private float globalSpeedMul = 1.0f;
  private float tintR = 1f, tintG = 1f, tintB = 1f;
  private float centerRepel = 0.6f; // 0..1

  // Distribution (world hemisphere)
  private float minElevDeg = 3f, maxElevDeg = 85f;
  private float horizonBias = 0.6f;       // >0 pushes toward horizon

  // temp
  private final Vector3 tmpRight = new Vector3();
  private final Vector3 tmpUp    = new Vector3();
  private final Vector3 tmpDir   = new Vector3();

  public BirdsPass(int count) { this.birdCount = Math.max(0, count); }

  // ======== Public API ========
  public void setTexture(Texture texture, boolean takeOwnership) {
    if (ownTexture && tex != null) tex.dispose();
    this.tex = texture; this.ownTexture = takeOwnership;
    this.u1=0f; this.v1=0f; this.u2=1f; this.v2=1f;
  }
  public void setTextureRegion(TextureRegion rgn, boolean takeOwnershipOfUnderlying) {
    setTexture(rgn.getTexture(), takeOwnershipOfUnderlying);
    this.u1 = rgn.getU(); this.v1 = rgn.getV(); this.u2 = rgn.getU2(); this.v2 = rgn.getV2();
  }
  public void setTint(float r, float g, float b){ tintR=r; tintG=g; tintB=b; }
  public void setRadius(float r){ domeRadius = Math.max(10f, r); }
  public void setPixelSizeRange(float min, float max){ minPx=min; maxPx=Math.max(min, max); }
  public void setGlobalSpeed(float mul){ globalSpeedMul = Math.max(0f, mul); }
  public void setCount(int count){ if (count!=birdCount){ disposeMesh(); birdCount=Math.max(0,count); buildMesh(); } }
  public void setElevationBand(float minDeg, float maxDeg){ minElevDeg=minDeg; maxElevDeg=maxDeg; }
  public void setHorizonBias(float b){ horizonBias = MathUtils.clamp(b, -1f, 1f); }
  public void setCenterRepel(float k){ centerRepel = MathUtils.clamp(k, 0f, 1f); }

  // ======== Lifecycle ========
  public void init() {
    ShaderProgram.pedantic = false;
    shader = new ShaderProgram(VERT, FRAG);
    if (!shader.isCompiled()) throw new IllegalStateException("Birds shader: " + shader.getLog());
    buildMesh();
    if (tex == null) {
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
        new VertexAttribute(VertexAttributes.Usage.Position,           3, "aAziElvSiz"),
        new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "aSpdSeed"),
        new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "aCorner"));

    float[] verts = new float[VERTS * 7];
    short[] idx   = new short[INDS];

    int vi = 0, ii = 0, base = 0;

    final float sMin = MathUtils.sinDeg(minElevDeg);
    final float sMax = MathUtils.sinDeg(maxElevDeg);
    final float golden = 0.61803398875f;

    for (int i = 0; i < birdCount; i++) {
      float u = (i + 0.5f) / birdCount;
      u = bias01(u, horizonBias);                 // push population toward horizon if >0
      float s = MathUtils.lerp(sMin, sMax, u);
      float el = (float)Math.asin(s);
      float az = MathUtils.PI2 * frac(i * golden + MathUtils.random(0.0f, 0.02f));

      float size  = MathUtils.lerp(0.8f, 1.3f, MathUtils.random());
      float speed = MathUtils.lerp(0.10f, 0.40f, MathUtils.random()) * globalSpeedMul;
      float seed  = MathUtils.random();

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

      idx[ii++] = (short)(base+0); idx[ii++] = (short)(base+1); idx[ii++] = (short)(base+2);
      idx[ii++] = (short)(base+0); idx[ii++] = (short)(base+2); idx[ii++] = (short)(base+3);
      base += 4;
    }

    mesh.setVertices(verts);
    mesh.setIndices(idx);
  }

  @Override public void render(RenderContext ctx) {
    if (mesh == null || shader == null || birdCount == 0 || tex == null) return;

    // camera basis & FOV
    tmpRight.set(ctx.cam.direction).crs(ctx.cam.up).nor();
    tmpUp.set(tmpRight).crs(ctx.cam.direction).nor();
    tmpDir.set(ctx.cam.direction).nor();

    float tanHalfY = MathUtils.tanDeg(ctx.cam.fieldOfView * 0.5f);
    float aspect   = (float)Gdx.graphics.getWidth() / (float)Gdx.graphics.getHeight();
    float tanHalfX = tanHalfY * aspect;

    // pixels -> world at dome radius
    float pxToWorld = (2f * domeRadius * tanHalfY) / Gdx.graphics.getHeight();

    // state
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDepthMask(false);
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

    shader.bind();
    shader.setUniformMatrix("uProjView", ctx.cam.combined);
    shader.setUniformf("uCamPos", ctx.cam.position);
    shader.setUniformf("uCamDir",   tmpDir);
    shader.setUniformf("uCamRight", tmpRight);
    shader.setUniformf("uCamUp",    tmpUp);
    shader.setUniformf("uTanHalfFovX", tanHalfX);
    shader.setUniformf("uTanHalfFovY", tanHalfY);

    shader.setUniformf("uTime", ctx.elapsedSeconds);
    shader.setUniformf("uRadius", domeRadius);
    shader.setUniformf("uMinPx", minPx);
    shader.setUniformf("uMaxPx", maxPx);
    shader.setUniformf("uPxToWorld", pxToWorld);
    shader.setUniformf("uCenterRepel", centerRepel);

    shader.setUniformf("uUVRect", u1, v1, u2, v2);
    shader.setUniformf("uTint", tintR, tintG, tintB);

    tex.bind(0);
    shader.setUniformi("uTex", 0);

    mesh.render(shader, GL20.GL_TRIANGLES);

    // restore state for terrain
    Gdx.gl.glDisable(GL20.GL_BLEND);
    Gdx.gl.glDepthMask(true);
    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
  }

  @Override public void resize(int w, int h) {}

  @Override public void dispose() {
    disposeMesh();
    if (shader != null) { shader.dispose(); shader = null; }
    if (ownTexture && tex != null) { tex.dispose(); tex = null; }
  }

  private void disposeMesh(){ if (mesh != null) { mesh.dispose(); mesh = null; } }

  private static float frac(double x){ return (float)(x - Math.floor(x)); }
  private static float bias01(float u, float bias){
    if (bias == 0f) return u;
    float k = 1f + 4f * Math.abs(bias);
    return (bias > 0f) ? (1f - (float)Math.pow(1f - u, k))
                       : (float)Math.pow(u, k);
  }
}
