package com.example.game.Render.Passes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.example.game.Render.Pipeline.RenderContext;
import com.example.game.Render.Pipeline.RenderPass;
import com.example.game.World.Atmosphere.FogVolume;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/** Screen-space localized fog blended from up to 3 nearest spherical volumes. */
public final class FogPass implements RenderPass {
  private Supplier<List<FogVolume>> supplier;
  private ShaderProgram prog;
  private Mesh fsQuad;
  // Reuse tiny arrays to avoid per-frame allocations
  private final float[] spheresTmp = new float[3*4];
  private final float[] cdTmp = new float[3*4];
  private final float[] foTmp = new float[3];
  private final float[] nzTmp = new float[3];
  private final float[] ncTmp = new float[3];

  public FogPass setSupplier(Supplier<List<FogVolume>> s){ this.supplier = s; return this; }

  public void init() {
    ShaderProgram.pedantic = false;
    String v = "attribute vec2 a_position;\n"+
               "attribute vec2 a_texCoord0;\n"+
               "varying vec2 v_uv;\n"+
               "void main(){ v_uv=a_texCoord0; gl_Position=vec4(a_position,0.0,1.0); }";
    String f = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"+
               "varying vec2 v_uv;\n"+
               "uniform sampler2D u_scene;\n"+
               "uniform sampler2D u_depth;\n"+
               "uniform int   u_hasDepth;\n"+
               "uniform int   u_count;\n"+
               "uniform vec4  u_spheres[3]; // xyz=center (world), w=radius\n"+
               "uniform mat4  u_view;\n"+
               "uniform mat4  u_invView;\n"+
               "uniform mat4  u_invProj;\n"+
               "uniform float u_near;\n"+
               "uniform float u_far;\n"+
               "uniform float u_time;\n"+
               "uniform vec3  u_wind;\n"+
               "uniform float u_noiseScale;\n"+
               "uniform float u_noiseSpeed;\n"+
               "uniform float u_noiseGamma;\n"+
               "uniform vec3  u_lightDir;\n"+
               "uniform float u_ambient;\n"+
               "uniform float u_diffuseStrength;\n"+
               "uniform float u_shadowStrength;\n"+
               "uniform float u_gradEps;\n"+
               "uniform float u_parallaxDamp;\n"+
               "uniform float u_groundBlend;\n"+
               "uniform float u_sampleMax;\n"+
               "uniform float u_depthFade;\n"+
               "uniform float u_mfp;\n"+
               "uniform float u_pathCap;\n"+
               "uniform vec4  u_colorDen[3];\n"+
               "uniform float u_nearClip[3];\n"+
               "uniform float u_falloff[3];\n"+
               "uniform float u_noise[3];\n"+
               "float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }\n"+
               "float hash3(vec3 p){ return fract(sin(dot(p, vec3(127.1,311.7, 74.7))) * 43758.5453); }\n"+
               "float vnoise3(vec3 p){\n"+
               "  vec3 i = floor(p); vec3 f = fract(p);\n"+
               "  // Trilinear value noise\n"+
               "  vec3 u = f*f*(3.0-2.0*f);\n"+
               "  float n000 = hash3(i + vec3(0,0,0));\n"+
               "  float n100 = hash3(i + vec3(1,0,0));\n"+
               "  float n010 = hash3(i + vec3(0,1,0));\n"+
               "  float n110 = hash3(i + vec3(1,1,0));\n"+
               "  float n001 = hash3(i + vec3(0,0,1));\n"+
               "  float n101 = hash3(i + vec3(1,0,1));\n"+
               "  float n011 = hash3(i + vec3(0,1,1));\n"+
               "  float n111 = hash3(i + vec3(1,1,1));\n"+
               "  /* u already computed above */\n"+
               "  float nx00 = mix(n000, n100, u.x);\n"+
               "  float nx10 = mix(n010, n110, u.x);\n"+
               "  float nx01 = mix(n001, n101, u.x);\n"+
               "  float nx11 = mix(n011, n111, u.x);\n"+
               "  float nxy0 = mix(nx00, nx10, u.y);\n"+
               "  float nxy1 = mix(nx01, nx11, u.y);\n"+
               "  return mix(nxy0, nxy1, u.z);\n"+
               "}\n"+
               "mat3 rot3(){\n"+
               "  // Constant rotation to break axis alignment and repetition\n"+
               "  float c1=0.93,s1=0.36; float c2=0.85,s2=0.52; float c3=0.77,s3=0.64;\n"+
               "  mat3 R1 = mat3(1,0,0, 0,c1,-s1, 0,s1,c1);\n"+
               "  mat3 R2 = mat3(c2,0,s2, 0,1,0, -s2,0,c2);\n"+
               "  mat3 R3 = mat3(c3,-s3,0, s3,c3,0, 0,0,1);\n"+
               "  return R3*R2*R1;\n"+
               "}\n"+
               "float fbm(vec3 p){\n"+
               "  float a=0.5, f=1.0, sum=0.0; mat3 R = rot3();\n"+
               "  for(int i=0;i<4;i++){ sum += a*vnoise3(p*f); p = R*p + 0.17; f*=2.13; a*=0.5; }\n"+
               "  return sum;\n"+
               "}\n"+
               "float fbmCoarse(vec3 p){\n"+
               "  float a=0.6, f=1.0, sum=0.0; mat3 R = rot3();\n"+
               "  for(int i=0;i<3;i++){ sum += a*vnoise3(p*f); p = R*p + 0.21; f*=1.73; a*=0.55; }\n"+
               "  return sum;\n"+
               "}\n"+
               "float linearizeDepth(float d){ float z = d * 2.0 - 1.0; return (2.0 * u_near * u_far) / (u_far + u_near - z * (u_far - u_near)); }\n"+
               "vec3 computeFogAdd(){\n"+
               "  // Build view-ray from NDC\n"+
               "  vec2 ndc = v_uv * 2.0 - 1.0;\n"+
               "  vec4 pN = u_invProj * vec4(ndc, -1.0, 1.0); pN /= pN.w;\n"+
               "  vec3 ro = vec3(0.0);\n"+
               "  vec3 rd = normalize(pN.xyz);\n"+
               "  // Scene depth to clamp fog in front of geometry\n"+
               "  float depthTex = (u_hasDepth==1) ? texture2D(u_depth, v_uv).r : 1.0;\n"+
               "  float tMax = 1e6;\n"+
               "  if (depthTex > 0.0 && depthTex < 1.0){\n"+
               "    float zLin = linearizeDepth(depthTex);\n"+
               "    // Reconstruct view-space position at depth along rd\n"+
               "    vec4 pD = u_invProj * vec4(ndc, depthTex * 2.0 - 1.0, 1.0); pD /= pD.w;\n"+
               "    tMax = length(pD.xyz);\n"+
               "  }\n"+
               "  float acc = 0.0; vec3 addC = vec3(0.0);\n"+
               "  for (int i=0;i<3;i++){ if (i>=u_count) break;\n"+
               "    vec4 sph = u_spheres[i]; vec3 cW = sph.xyz; float r = sph.w;\n"+
               "    // sphere center to view space\n"+
               "    vec3 cV = (u_view * vec4(cW,1.0)).xyz;\n"+
               "    // Ray-sphere intersection in view space (ro=0)\n"+
               "    vec3 oc = ro - cV;\n"+
               "    float b = dot(oc, rd);\n"+
               "    float c = dot(oc, oc) - r*r;\n"+
               "    float h = b*b - c;\n"+
               "    if (h < 0.0) continue;\n"+
               "    h = sqrt(max(h, 0.0));\n"+
               "    float t0 = -b - h;\n"+
               "    float t1 = -b + h;\n"+
               "    if (t1 <= 0.0) continue;\n"+
               "    float entry = max(t0, 0.0);\n"+
               "    float exit  = max(min(t1, tMax), 0.0);\n"+
               "    float thick = max(0.0, exit - entry);\n"+
               "    if (thick <= 1e-5) continue;\n"+
               "    // Pseudo-volumetric: single-sample near the camera within the segment to avoid distant minification\n"+
               "    float segEntry = max(entry, 0.0);\n"+
               "    float segExit  = max(min(exit, tMax), 0.0);\n"+
               "    float segLen   = max(0.0, segExit - segEntry);\n"+
               "    // Per-volume near clip: shift start forward so fog begins at least nearClip meters from camera\n"+
               "    float segEntryNC = min(segExit, segEntry + max(0.0, u_nearClip[i]));\n"+
               "    float segLenNC   = max(0.0, segExit - segEntryNC);\n"+
               "    float span     = min(segLenNC, u_sampleMax);\n"+
               "    float tSample  = segEntryNC + 0.50 * span;\n"+
               "    float tSample2 = segEntryNC + 0.80 * span;\n"+
               "    vec3 pV = ro + rd * tSample;\n"+
               "    vec3 pV2= ro + rd * tSample2;\n"+
               "    vec3 pW = (u_invView * vec4(pV,1.0)).xyz;\n"+
               "    vec3 pW2= (u_invView * vec4(pV2,1.0)).xyz;\n"+
               "    // Parallax damping: bias sample position toward sphere center to slow apparent motion while walking\n"+
               "    vec3 pLock = mix(pW,  cW, clamp(u_parallaxDamp, 0.0, 0.95));\n"+
               "    vec3 pLock2= mix(pW2, cW, clamp(u_parallaxDamp, 0.0, 0.95));\n"+
               "    float sN = clamp(u_noise[i], 0.0, 1.0);\n"+
               "    float sN2 = pow(sN, u_noiseGamma);\n"+
               "    // Reduce motion when slider is low so fog feels uniform/static\n"+
               "    float anim = u_time * u_noiseSpeed * sN2;\n"+
               "    // Per-sphere phase offset to desync patterns\n"+
               "    float phase = dot(cW, vec3(0.73, 0.29, 0.11));\n"+
               "    // World-locked to sphere: sample in local space relative to center\n"+
               "    vec3 q  = (pLock  - cW) * u_noiseScale + u_wind * anim + vec3(phase);\n"+
               "    vec3 q2 = (pLock2 - cW) * u_noiseScale + u_wind * anim + vec3(phase);\n"+
               "    // Prefer coarser features deeper in the sphere to avoid tiny-cloud look on large radii\n"+
               "    float n  = mix(fbm(q),  fbmCoarse(q),  smoothstep(0.35, 0.85, clamp(thick / (2.0*r), 0.0, 1.0))); // 0..1\n"+
               "    float n2 = mix(fbm(q2), fbmCoarse(q2), smoothstep(0.35, 0.85, clamp(thick / (2.0*r), 0.0, 1.0))); // 0..1\n"+
               "    float mask = 0.5 * (smoothstep(0.40, 0.90, n) + smoothstep(0.40, 0.90, n2));\n"+
               "    mask = clamp((mask - 0.5) * 1.35 + 0.5, 0.0, 1.0);\n"+
               "    // Gradient-based normal in noise/world domain (central differences)\n"+
               "    float e = u_gradEps * u_noiseScale;\n"+
               "    float nx = fbm(q + vec3(e,0,0)) - fbm(q - vec3(e,0,0));\n"+
               "    float ny = fbm(q + vec3(0,e,0)) - fbm(q - vec3(0,e,0));\n"+
               "    float nz = fbm(q + vec3(0,0,e)) - fbm(q - vec3(0,0,e));\n"+
               "    vec3 N = normalize(vec3(nx, ny, nz));\n"+
               "    vec3 L = normalize(u_lightDir);\n"+
               "    float diff = max(0.0, dot(N, -L));\n"+
               "    float shade = clamp(u_ambient + u_diffuseStrength * diff, 0.0, 2.0);\n"+
               "    // Cheap self-shadow: sample ahead along light, attenuate if denser\n"+
               "    float nAhead = fbm(q - L * (u_gradEps * 2.0));\n"+
               "    float sh = mix(1.0, smoothstep(0.4, 0.9, nAhead), clamp(u_shadowStrength,0.0,1.0));\n"+
               "    // Radial falloff as percentage of radius (edge width = u_falloff[i] * r)\n"+
               "    float dN = length(pW - cW) / max(1e-4, r);\n"+
               "    // Use geometric chord length for normalized density so gradient is consistent across radii\n"+
               "    float cap = max(1e-3, u_pathCap);\n"+
               "    float chord = 2.0 * r * sqrt(max(0.0, 1.0 - dN*dN));\n"+
               "    float raw = 1.0 - exp(-min(chord, cap) / max(1e-3, u_mfp));\n"+
               "    float ref = 1.0 - exp(-min(2.0*r, cap) / max(1e-3, u_mfp));\n"+
               "    float normM = (ref > 1e-3) ? clamp(raw / ref, 0.0, 1.0) : 0.0;\n"+
               "    // Interior factor reduces 'magnet' feel for very large radii near edges\n"+
               "    float inner = smoothstep(0.15, 0.85, 1.0 - dN);\n"+
               "    vec3 col=u_colorDen[i].rgb; float den=u_colorDen[i].a;\n"+
               "    float edgeW = clamp(u_falloff[i], 0.0, 1.0);\n"+
               "    // Always hard-fade to zero at the boundary, independent of edgeW\n"+
               "    float radial = 1.0 - smoothstep(1.0, 1.0 + 1e-3, dN);\n"+
               "    // If edgeW>0, also apply a soft rim fade inside the boundary\n"+
               "    if (edgeW > 0.0) {\n"+
               "      // Use rim at the actual entry point to avoid popping when tSample sits deeper inside\n"+
               "      vec3 pEntryW = (u_invView * vec4(ro + rd * segEntry, 1.0)).xyz;\n"+
               "      float dNEntry = length(pEntryW - cW) / max(1e-4, r);\n"+
               "      float rimEntry = 1.0 - smoothstep(1.0 - edgeW, 1.0, dNEntry);\n"+
               "      float rimSample = 1.0 - smoothstep(1.0 - edgeW, 1.0, dN);\n"+
               "      radial *= max(rimEntry, rimSample);\n"+
               "    }\n"+
               "    // Segment traversal feathering: ramp alpha over ~edgeW*r meters from entry and toward exit\n"+
               "    float eMeters = max(2.0, edgeW * r);\n"+
               "    float fEntry = (segLen > 0.0) ? smoothstep(0.0, eMeters, (tSample - segEntry)) : 1.0;\n"+
               "    float fExit  = (segLen > 0.0) ? smoothstep(0.0, eMeters, (segExit - tSample)) : 1.0;\n"+
               "    float segFeather = (edgeW > 0.0) ? min(fEntry, fExit) : 1.0;\n"+
               "    // Ground clipping: fade out below sphere center toward -r*u_groundBlend\n"+
               "    float gb = clamp(u_groundBlend, 0.0, 1.0);\n"+
               "    float vfac = smoothstep(cW.y - r*gb, cW.y, pLock.y);\n"+
               "    float base = 0.20;\n"+
               "    // Screen-space depth fade to soften contact with geometry (0 at surface, 1 away)\n"+
               "    float depthFade = 1.0;\n"+
               "    if (u_hasDepth==1) { depthFade = smoothstep(0.0, max(1e-3, u_depthFade), max(0.0, tMax - tSample)); }\n"+
               "    // Pseudo-volumetric contribution (with lighting)\n"+
               "    float aPV = den * (base + (1.0-base) * mask) * shade * sh * (normM * inner) * radial * segFeather * vfac * depthFade;\n"+
               "    float aUF = den * normM * radial * segFeather * vfac * depthFade;\n"+
               "    // Blend by slider (gamma-shaped) and interior factor: 0 => uniform, 1 => pseudo-volumetric\n"+
               "    float a = mix(aUF, aPV, sN2);\n"+
               "    float w = (1.0 - acc) * a;\n"+
               "    addC += col * w;\n"+
               "    acc += w;\n"+
               "  }\n"+
               "  return addC;\n"+
               "}\n"+
               "void main(){ vec3 addC = computeFogAdd(); gl_FragColor = vec4(addC, 1.0); }";
    prog = new ShaderProgram(v, f);
    if (!prog.isCompiled()) throw new IllegalStateException(prog.getLog());

    // fullscreen quad
    float[] vtx = {
      -1,-1, 0,0,
       1,-1, 1,0,
       1, 1, 1,1,
      -1, 1, 0,1
    };
    short[] idx = {0,1,2, 0,2,3};
    fsQuad = new Mesh(true, 4, 6,
        new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
        new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"));
    fsQuad.setVertices(vtx); fsQuad.setIndices(idx);
  }

  @Override public void render(RenderContext ctx) {
    if (supplier == null || prog == null) return;

    // Choose up to 3 closest to camera
    List<FogVolume> src = supplier.get();
    Vector3 cam = ctx.cam.position;
    ArrayList<FogVolume> sorted = new ArrayList<>();
    if (src != null) sorted.addAll(src);
    sorted.sort(Comparator.comparingDouble(f -> cam.dst2(f.center)));
    int count = Math.min(3, sorted.size());

    // Bind scene FBO again to ensure we composite into it
    try { if (Gdx.gl30 != null && ctx.sceneFboHandle != 0) Gdx.gl30.glBindFramebuffer(com.badlogic.gdx.graphics.GL30.GL_FRAMEBUFFER, ctx.sceneFboHandle); } catch (Throwable ignored) {}

    // Setup GL state and uniforms
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    // Additive blend fog contribution over existing scene color
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
    try { Gdx.gl.glDisable(GL20.GL_CULL_FACE); } catch (Throwable ignored) {}
    try { Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST); } catch (Throwable ignored) {}
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

    prog.bind();
    // We do not sample the scene color in the shader anymore; keep binding slot 0 unused
    prog.setUniformi("u_scene", 0);
    // Depth (optional)
    if (ctx.sceneDepthTex != null) { Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1); ctx.sceneDepthTex.bind(); Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0); prog.setUniformi("u_depth", 1); prog.setUniformi("u_hasDepth", 1); }
    else { prog.setUniformi("u_depth", 0); prog.setUniformi("u_hasDepth", 0); }

    prog.setUniformi("u_count", count);
    float[] spheres = spheresTmp;
    float[] cd = cdTmp;
    float[] fo = foTmp;
    float[] nz = nzTmp;
    float[] nc = ncTmp;
    for (int i=0;i<count;i++){
      FogVolume fv = sorted.get(i);
      spheres[4*i+0] = fv.center.x;
      spheres[4*i+1] = fv.center.y;
      spheres[4*i+2] = fv.center.z;
      spheres[4*i+3] = fv.radius;
      cd[i*4+0] = fv.r; cd[i*4+1] = fv.g; cd[i*4+2] = fv.b; cd[i*4+3] = fv.density;
      fo[i] = fv.falloff;
      nz[i] = fv.noise;
      nc[i] = Math.max(0f, fv.nearClip);
    }
    prog.setUniform4fv("u_spheres", spheres, 0, 3*4);
    prog.setUniform4fv("u_colorDen", cd, 0, 3*4);
    prog.setUniform1fv("u_falloff", fo, 0, 3);
    prog.setUniform1fv("u_noise", nz, 0, 3);
    prog.setUniform1fv("u_nearClip", nc, 0, 3);

    // Matrices and camera params
    com.badlogic.gdx.math.Matrix4 invProj = new com.badlogic.gdx.math.Matrix4(ctx.cam.projection);
    invProj.inv();
    prog.setUniformMatrix("u_invProj", invProj);
    prog.setUniformMatrix("u_view", ctx.cam.view);
    com.badlogic.gdx.math.Matrix4 invView = new com.badlogic.gdx.math.Matrix4(ctx.cam.view);
    invView.inv();
    prog.setUniformMatrix("u_invView", invView);
    prog.setUniformf("u_near", ctx.cam.near);
    prog.setUniformf("u_far", ctx.cam.far);
    // Animated noise params
    prog.setUniformf("u_time", ctx.elapsedSeconds);
    // Default gentle breeze; can be exposed to CVars later
    prog.setUniformf("u_wind", 0.14f, 0.00f, 0.08f);
    prog.setUniformf("u_noiseScale", 0.14f);
    prog.setUniformf("u_noiseSpeed", 0.18f);
    prog.setUniformf("u_noiseGamma", 1.5f);
    // Lighting params (intense yet balanced)
    com.badlogic.gdx.math.Vector3 L = new com.badlogic.gdx.math.Vector3(ctx.sunDir).nor();
    prog.setUniformf("u_lightDir", L.x, L.y, L.z);
    prog.setUniformf("u_ambient", 0.45f);
    prog.setUniformf("u_diffuseStrength", 1.30f);
    prog.setUniformf("u_shadowStrength", 0.45f);
    prog.setUniformf("u_gradEps", 1.0f);
    // Sample span near the camera along the fog segment (meters)
    prog.setUniformf("u_sampleMax", 35.0f);
    // Parallax damping (0..1): higher => slower perceived motion when walking
    prog.setUniformf("u_parallaxDamp", 0.25f);
    // Ground clip blend (0=no clip, 1=clip fully up to center height)
    prog.setUniformf("u_groundBlend", 1.0f);
    // Depth fade distance in meters to soften contact with terrain/meshes
    prog.setUniformf("u_depthFade", 6.0f);
    // Mean free path (m) and path cap (m) to normalize density w.r.t radius
    prog.setUniformf("u_mfp", 35.0f);
    prog.setUniformf("u_pathCap", 80.0f);
    

    fsQuad.render(prog, GL20.GL_TRIANGLES);
  }

  @Override public void resize(int w, int h) { /* no-op */ }

  @Override public void dispose() {
    if (fsQuad != null) fsQuad.dispose();
    if (prog != null) prog.dispose();
  }
}
