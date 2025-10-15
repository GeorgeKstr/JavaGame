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

  // ======== Shaders (true 3D sky dome around camera) ========
  private static final String VERT =
      // per-bird
      "attribute vec3 aAziElvSiz;" +     // x=azimuth(rad), y=elevation(rad), z=sizeScale
      "attribute vec2 aSpdSeed;" +       // x=azimuthSpeed(rad/s), y=seed
      "attribute vec2 aCorner;" +        // -1..1 quad corner
      // camera & projection
      "uniform mat4  uProjView;" +
      "uniform vec3  uCamPos;" +
      "uniform vec3  uCamRight;" +
      "uniform vec3  uCamUp;" +
      "uniform vec3  uCamDir;" +
      "uniform float uTanHalfFovX;" +
      "uniform float uTanHalfFovY;" +
      // params
      "uniform float uTime;" +
      "uniform float uRadius;" +
      "uniform float uMinPx;" +
      "uniform float uMaxPx;" +
      "uniform float uPxToWorld;" +
      // varyings
      "varying float vFade;" +
      "varying float vSeed;" +
      "varying vec2  vUV;" +
      // helpers
      "vec2 toNDC(vec3 d){\n" +
      "  float denom = max(1e-4, dot(d, uCamDir));\n" +
      "  float sx = dot(d, uCamRight) / (denom * uTanHalfFovX);\n" +
      "  float sy = dot(d, uCamUp)    / (denom * uTanHalfFovY);\n" +
      "  return vec2(sx, sy);\n" +
      "}\n" +
      "// hash helpers from seed\n" +
      "float hash11(float x){ return fract(sin(x*43758.5453)*12543.123); }\n" +
      "vec3 hash31(float x){ return normalize(vec3(hash11(x+1.23), hash11(x+4.56), hash11(x+7.89))*2.0-1.0); }\n" +
      "vec3 rotateAxisAngle(vec3 v, vec3 axis, float ang){\n" +
      "  float c = cos(ang), s = sin(ang);\n" +
      "  return v*c + cross(axis, v)*s + axis*dot(axis, v)*(1.0-c);\n" +
      "}\n" +
      "void main(){\n" +
      "  // 1) base world-space direction on a unit sphere (Y-up) with slight elevation wander\n" +
      "  float baseAz = aAziElvSiz.x;\n" +
      "  float baseEl = aAziElvSiz.y;\n" +
      "  float wander = 0.02 * sin(uTime*0.5 + aSpdSeed.y*7.0);\n" +
      "  float spdVar = aSpdSeed.x * (0.20 + 0.20 * hash11(aSpdSeed.y*13.7));\n" +
      "  float az = baseAz + spdVar * uTime;\n" +
      "  float el = clamp(baseEl + wander, -1.2, 1.2);\n" +
      "  float cE = cos(el), sE = sin(el);\n" +
      "  vec3 dir0 = vec3(sin(az)*cE, sE, -cos(az)*cE);\n" +
      "  // 1b) rotate around a per-bird random axis to avoid pure circles\n" +
      "  // prefer rotation around world-up (horizontal motion), with slight per-bird deviation\n" +
      "  vec3 axisRand = hash31(aSpdSeed.y*97.13);\n" +
      "  vec3 axis = normalize(mix(vec3(0.0,1.0,0.0), axisRand, 0.15));\n" +
      "  // strictly forward angle (monotonic); avoid reversals\n" +
      "  float omega = mix(0.05, 0.12, hash11(aSpdSeed.y*31.7));\n" +
      "  float ang   = omega * uTime;\n" +
      "  vec3 dirW = normalize( rotateAxisAngle(dir0, axis, ang) );\n" +
      "  // gently bias toward horizontal flight\n" +
      "  vec3 flat = normalize(vec3(dirW.x, 0.0, dirW.z) + 1e-5);\n" +
      "  dirW = normalize(mix(dirW, flat, 0.35));\n" +
      "  // 2) place on dome around camera and billboard in world\n" +
      "  float rJit = 1.0 + 0.01*(hash11(aSpdSeed.y*57.1)*2.0 - 1.0);\n" +
      "  vec3 center = uCamPos + dirW * (uRadius * rJit);\n" +
      "  vec3 n = -dirW;\n" +
      "  // heading tangent along great-circle motion\n" +
      "  vec3 h = normalize(cross(axis, dirW));\n" +
      "  // ensure heading lies in billboard plane (orthogonal to n)\n" +
      "  h = normalize(h - n * dot(h, n));\n" +
      "  vec3 r = h;\n" +
      "  vec3 u = cross(n, r);\n" +
      "  // side-angle thinning: compute NDC of view direction and thin vertical near sides\n" +
      "  vec2 s = toNDC(dirW);\n" +
      "  float side = clamp(abs(s.x), 0.0, 1.2);\n" +
      "  float vScale = mix(1.0, 0.6, smoothstep(0.35, 0.95, side));\n" +
      "  // far-horizon thinning: thinner vertically near horizon (small |dirW.y|)\n" +
      "  float horiz = 1.0 - clamp(abs(dirW.y), 0.0, 1.0);\n" +
      "  vScale *= mix(1.0, 0.7, smoothstep(0.10, 0.55, horiz));\n" +
      "  float px = mix(uMinPx, uMaxPx, 0.5) * aAziElvSiz.z;\n" +
      "  vec3 billboard = (r * aCorner.x + u * (aCorner.y * vScale)) * (px * uPxToWorld);\n" +
      "  gl_Position = uProjView * vec4(center + billboard, 1.0);\n" +
      "  vFade = smoothstep(-0.2, 0.35, dirW.y);\n" +
      "  vSeed = aSpdSeed.y;\n" +
      "  vUV = aCorner * 0.5 + 0.5;\n" +
      "}\n";

  private static final String FRAG =
      "#ifdef GL_ES\nprecision mediump float;\n#endif\n" +
      "uniform sampler2D uTex;" +
      "uniform vec4  uUVRect;" +
      "uniform vec3  uTint;" +
      "uniform float uTime;" +
      "uniform float uOcc;" +
      "varying float vFade;" +
      "varying float vSeed;" +
      "varying vec2  vUV;" +
      "void main(){\n" +
      "  vec2 uv = mix(uUVRect.xy, uUVRect.zw, vUV);\n" +
      "  vec4 tex = texture2D(uTex, uv);\n" +
      "  float flap = 0.12 * sin(uTime*6.0 + vSeed*17.0);\n" +
      "  float a = tex.a * (0.65 + flap) * vFade;\n" +
      "  if (uOcc > 0.5) {\n" +
      "    gl_FragColor = vec4(0.0, 0.0, 0.0, a);\n" +
      "  } else {\n" +
      "    vec3 col = tex.rgb * uTint;\n" +
      "    gl_FragColor = vec4(col, a);\n" +
      "  }\n" +
      "}\n";

  // ======== Data/resources ========
  private Mesh mesh;
  private ShaderProgram shader;
  private int birdCount;

  private Texture tex;
  private boolean ownTexture = false;
  private float u1=0f, v1=0f, u2=1f, v2=1f;

  // Tunables
  private float domeRadius = 800f;
  private float minPx = 2.0f, maxPx = 9.0f;
  private float globalSpeedMul = 0.5f;
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

    // state: let terrain occlude birds (this pass should run AFTER terrain)
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDepthMask(false);
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

    shader.bind();
    shader.setUniformMatrix("uProjView", ctx.cam.combined);
    shader.setUniformf("uCamPos", ctx.cam.position);
    shader.setUniformf("uCamRight", tmpRight);
    shader.setUniformf("uCamUp",    tmpUp);
    shader.setUniformf("uCamDir",   tmpDir);
    shader.setUniformf("uTanHalfFovX", tanHalfX);
    shader.setUniformf("uTanHalfFovY", tanHalfY);

    shader.setUniformf("uTime", ctx.elapsedSeconds);
    shader.setUniformf("uRadius", domeRadius);
    shader.setUniformf("uMinPx", minPx);
    shader.setUniformf("uMaxPx", maxPx);
    shader.setUniformf("uPxToWorld", pxToWorld);
    shader.setUniformf("uOcc", 0f);

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

  /** Render birds as occluders into the current framebuffer (e.g., GodRays occlusion FBO).
   * Caller must set GL state (depth/blend) and viewport. */
  public void renderOcclusion(RenderContext ctx) {
    if (mesh == null || shader == null || birdCount == 0 || tex == null) return;

    // camera basis & FOV (match main render)
    tmpRight.set(ctx.cam.direction).crs(ctx.cam.up).nor();
    tmpUp.set(tmpRight).crs(ctx.cam.direction).nor();
    tmpDir.set(ctx.cam.direction).nor();

    float tanHalfY = MathUtils.tanDeg(ctx.cam.fieldOfView * 0.5f);
    float aspect   = (float)Gdx.graphics.getWidth() / (float)Gdx.graphics.getHeight();
    float tanHalfX = tanHalfY * aspect;
    float pxToWorld = (2f * domeRadius * tanHalfY) / Gdx.graphics.getHeight();

    shader.bind();
    shader.setUniformMatrix("uProjView", ctx.cam.combined);
    shader.setUniformf("uCamPos", ctx.cam.position);
    shader.setUniformf("uCamRight", tmpRight);
    shader.setUniformf("uCamUp",    tmpUp);
    shader.setUniformf("uCamDir",   tmpDir);
    shader.setUniformf("uTanHalfFovX", tanHalfX);
    shader.setUniformf("uTanHalfFovY", tanHalfY);

    shader.setUniformf("uTime", ctx.elapsedSeconds);
    shader.setUniformf("uRadius", domeRadius);
    shader.setUniformf("uMinPx", minPx);
    shader.setUniformf("uMaxPx", maxPx);
    shader.setUniformf("uPxToWorld", pxToWorld);
    shader.setUniformf("uOcc", 1f);

    shader.setUniformf("uUVRect", u1, v1, u2, v2);
    // tint ignored in occlusion path

    tex.bind(0);
    shader.setUniformi("uTex", 0);

    mesh.render(shader, GL20.GL_TRIANGLES);
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
