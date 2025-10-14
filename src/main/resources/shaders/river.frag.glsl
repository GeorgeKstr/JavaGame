#ifdef GL_ES
precision mediump float;
#endif

// ===== Optional extension for cheaper normals =====
#ifdef GL_ES
#extension GL_OES_standard_derivatives : enable
#endif

// ================= Build-time switches =================
// Toggle these to scale quality/cost without touching the code below.
#ifndef USE_POM
  #define USE_POM 1        // 1 = enable parallax offset, 0 = disable
#endif
#ifndef PARALLAX_STEPS
  #define PARALLAX_STEPS 2 // extra refinement steps after first offset (0–4)
#endif
#ifndef USE_DERIV_NORMALS
  // If derivatives are available, 1 uses them for a single-sample normal (cheap)
  // If your device lacks OES_standard_derivatives, leave at 0.
  #define USE_DERIV_NORMALS 1
#endif

// =================== Varyings from vertex ===================
varying vec4 v_color;
varying vec2 v_uv;
varying vec3 v_worldPos;

// =================== Uniforms ===================
uniform float u_time;
uniform vec3  u_camPos;
uniform vec3  u_sunDir;     // points from sun to world (so -u_sunDir = light dir)
uniform vec3  u_skyColor;   // sky reflection tint
uniform vec3  u_sunColor;   // sun highlight color

// --- Optional color/looks controls (provide from engine; see defaults below) ---
uniform vec3  u_envGroundColor; // avg terrain/veg color near water
uniform float u_waterAbsorb;    // 0..1, absorb blue toward ground tint
uniform float u_reflectBoost;   // 0..1, more shine at grazing

// ===================== Tunables (balanced) =====================
const float FLOW_SPEED     = 0.040;  // tiny downstream drift (0.0 to freeze)
const float WARP_STRENGTH  = 0.10;   // static domain warp strength (world-anchored)
const float WARP_FREQ      = 0.55;   // warp scale (world-anchored)
const float HEIGHT_SCALE   = 0.045;  // parallax scale (view-only)
const float RIPPLE_GAIN    = 0.85;   // normal intensity
const float SPEC_POWER     = 110.0;  // specular exponent

// Band-limited height composition (distinct ripples)
const float MIDFREQ1       = 12.0;
const float MIDFREQ2       = 23.0;
const float FBM_COARSE     = 1.2;
const float FBM_FINE       = 2.2;

// Foam controls
const float FOAM_EDGE_GAIN = 0.90;
const float FOAM_STREAKS   = 0.70;
const float FOAM_TURB_GAIN = 0.55;

// ===================== Noise =====================
float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
float noise(vec2 p){
  vec2 i=floor(p), f=fract(p);
  float a=hash(i);
  float b=hash(i+vec2(1.0,0.0));
  float c=hash(i+vec2(0.0,1.0));
  float d=hash(i+vec2(1.0,1.0));
  vec2 u=f*f*(3.0-2.0*f);
  return mix(a,b,u.x)+ (c-a)*u.y*(1.0-u.x) + (d-b)*u.x*u.y;
}

// Lightweight fbm (3 octaves)
float fbm(vec2 p){
  const mat2 R = mat2(0.80, -0.60, 0.60, 0.80);
  float a=0.0, w=0.5;
  for(int i=0;i<3;i++){
    a += w * noise(p);
    p = R * p * 2.02;
    w *= 0.5;
  }
  return a;
}

// ===================== Perf helpers =====================
float pow5(float x){ float x2=x*x; return x2*x2*x; }  // x^5

float specPow(float ndh, float power){
  ndh = max(ndh, 1e-4);
  // Often cheaper than pow() on many ES drivers
  return exp2(log2(ndh) * power);
}

float fresnelSchlick(float ndv, float F0){
  return F0 + (1.0 - F0) * pow5(1.0 - ndv);
}

// ===================== Static world-anchored warp =====================
// Fewer noise evaluations (2) + curl-ish twist
vec2 staticWarp(vec2 uvWS){
  vec2 w = v_worldPos.xz * WARP_FREQ;
  vec2 n = vec2(noise(w + 1.73), noise(w*1.19 - 2.41)) - 0.5;
  vec2 curlish = vec2(-n.y, n.x);
  return uvWS + WARP_STRENGTH * (n*0.8 + 0.6*curlish);
}

// ===================== Height composition =====================
float heightBands(vec2 uv){
  float s1 = sin(uv.x*MIDFREQ1 + uv.y*7.0);
  float s2 = sin(uv.x*MIDFREQ2 - uv.y*15.0);
  float r1 = 1.0 - abs(s1);
  float r2 = 1.0 - abs(s2);
  return 0.32*s1 + 0.26*s2 + 0.20*(r1*r2);
}
float heightField(vec2 uv){
  return heightBands(uv)
       + 0.18*fbm(uv*FBM_COARSE)
       + 0.09*fbm(uv*FBM_FINE);
}

// ===================== Normals =====================
vec3 normalFromHeightFast(vec2 uv){
  // Analytic derivative for sine bands + one fbm skew
  float a = uv.x*MIDFREQ1 + uv.y*7.0;
  float b = uv.x*MIDFREQ2 - uv.y*15.0;
  float s1 = sin(a),  c1 = cos(a);
  float s2 = sin(b),  c2 = cos(b);
  float r1 = 1.0 - abs(s1);
  float r2 = 1.0 - abs(s2);
  float sign1 = (s1 >= 0.0) ? 1.0 : -1.0;
  float sign2 = (s2 >= 0.0) ? 1.0 : -1.0;

  float dhdx = 0.32*c1*MIDFREQ1 + 0.26*c2*MIDFREQ2
             + 0.20*((-sign1*c1*MIDFREQ1)*r2 + r1*(-sign2*c2*MIDFREQ2));
  float dhdy = 0.32*c1*7.0      + 0.26*c2*(-15.0)
             + 0.20*((-sign1*c1*7.0)*r2 + r1*(-sign2*c2*(-15.0)));

  float f = fbm(uv*FBM_COARSE); // single call
  dhdx += (f - 0.5) * 0.6;
  dhdy += (f - 0.5) * 0.6;

  return normalize(vec3(-dhdx*RIPPLE_GAIN, 1.0, -dhdy*RIPPLE_GAIN));
}

#ifdef GL_OES_standard_derivatives
vec3 normalFromHeightDeriv(vec2 uv){
  float h = heightBands(uv) + 0.18*fbm(uv*FBM_COARSE);
  vec3 p = vec3(uv, h);
  vec3 dx = dFdx(p);
  vec3 dy = dFdy(p);
  vec3 N = normalize(cross(dx, dy));
  return normalize(vec3(N.x*RIPPLE_GAIN, abs(N.y), N.z*RIPPLE_GAIN));
}
#endif

// ===================== Parallax (view-only; stable) =====================
vec2 parallaxOffset(vec2 uv, vec3 V, vec2 flowDir2D, float h){
  vec2 T = normalize(flowDir2D);
  vec2 B = normalize(vec2(-T.y, T.x));
  vec3 Vn = normalize(V);
  float vz = max(0.15, dot(Vn, vec3(0.0,1.0,0.0)));
  float scale = HEIGHT_SCALE * (1.0 / vz);
  float vTx = dot(Vn.xz, T);
  float vBx = dot(Vn.xz, B);
  return uv + (T*vTx + B*vBx) * (h * scale);
}

// ===================== Main =====================
void main(){
  // Across/along in river uv space
  float u = clamp(v_uv.x, 0.0, 1.0);
  float v = v_uv.y;

  // Flow direction (uv space)
  vec2 flowDir = normalize(vec2(0.15, 1.0));

  // Base river-space coords
  vec2 baseUV = vec2(u*2.0, v*1.1);

  // Static world-anchored warp (no time)
  vec2 uvWSWarped = staticWarp(baseUV);

  // Tiny single-direction drift (set FLOW_SPEED=0 to freeze)
  vec2 uvFlow = uvWSWarped - flowDir * (u_time * FLOW_SPEED);

  // ------ Parallax (optional) ------
  vec3 V = normalize(u_camPos - v_worldPos);
  vec2 uvP = uvFlow;

#if USE_POM
  float h0 = heightField(uvFlow);
  vec2 firstOff = parallaxOffset(uvFlow, V, flowDir, h0);
  uvP = mix(uvFlow, firstOff, 0.85);

  // micro refinement
  #if PARALLAX_STEPS > 0
    vec2 stepDir = (firstOff - uvFlow) / float(PARALLAX_STEPS);
    for (int i=0; i<PARALLAX_STEPS; ++i){
      uvP += stepDir;
      float hi = heightField(uvP);
      vec2 offi = parallaxOffset(uvP, V, flowDir, hi);
      uvP = mix(uvP, offi, 0.6);
    }
  #endif
#endif

  // ------ Normal (choose derivative path if available) ------
  vec3 N;
#if USE_DERIV_NORMALS && defined(GL_OES_standard_derivatives)
  N = normalFromHeightDeriv(uvP);
#else
  N = normalFromHeightFast(uvP);
#endif

  // ------ Lighting basis ------
  vec3 L = normalize(-u_sunDir);
  vec3 H = normalize(L + V);
  float ndv = clamp(dot(N,V), 0.0, 1.0);
  float ndl = max(dot(N,L), 0.0);
  float ndh = max(dot(N,H), 0.0);
  float spec = specPow(ndh, SPEC_POWER) * ndl;
  float fres = fresnelSchlick(ndv, 0.02);

  // ------ Depth/edge tint across width ------
  float edge = min(u, 1.0-u);
  float curveProxy = 0.5 + 0.5*sin(v_worldPos.z * 0.1 + sin(v_worldPos.z*0.023));
  float shallow = smoothstep(0.20, 0.02, edge) * (0.85 + 0.3*(curveProxy-0.5));
  // Natural base colors (less blue by default)
  vec3 deepColor    = vec3(0.02, 0.12, 0.14); // slightly green/teal deep
  vec3 shallowColor = vec3(0.12, 0.65, 0.95);
  vec3 waterColor   = mix(deepColor, shallowColor, shallow);

  // ------ Foam: stable (no flicker), streaked and edge-weighted ------
  float edgeMask = smoothstep(0.18, 0.05, edge) * FOAM_EDGE_GAIN;
  vec2 worldUV = v_worldPos.xz * 0.22;
  vec2 T = normalize(flowDir);
  vec2 B = normalize(vec2(-T.y, T.x));
  vec2 foamUV = vec2(dot(worldUV, T)*2.4, dot(worldUV, B)*0.55);
  float streaks    = fbm(foamUV*1.15);
  float foamStreak = smoothstep(0.66, 0.93, streaks) * FOAM_STREAKS;

  // Turbulence proxy from normal delta along flow
  vec3 Nf = normalFromHeightFast(uvP + T*0.05);
  float turb = clamp(length(Nf - N), 0.0, 1.0);
  float foamTurb = smoothstep(0.20, 0.44, turb) * FOAM_TURB_GAIN;

  float foam = edgeMask * (foamStreak + foamTurb);

  // ------ Bottom tint (subtle, static) ------
  float viewFacing = 1.0 - ndv;
  float shallowReveal = shallow * smoothstep(0.0, 0.55, viewFacing);
  float n1 = noise(worldUV);
  vec3 bottomTint = mix(vec3(0.08, 0.06, 0.03), vec3(0.14, 0.12, 0.08), n1);
  waterColor = mix(waterColor, bottomTint, 0.22 * shallowReveal);

  // ------ Environment-aware absorption & sunset weighting ------
  // Safe defaults if you don't set the uniforms from CPU side:
  vec3  envGround = (u_envGroundColor.x+u_envGroundColor.y+u_envGroundColor.z > 0.0) ? u_envGroundColor
                     : vec3(0.20, 0.25, 0.18);
  float absorbAmt = (u_waterAbsorb > 0.0) ? u_waterAbsorb : 0.45;
  float reflBoost = (u_reflectBoost > 0.0) ? u_reflectBoost : 0.3;

  float depthProxy = 1.0 - shallow; // mid-river = deeper
  vec3 absorb = mix(vec3(1.0), envGround, absorbAmt * depthProxy);
  waterColor *= absorb;

  float sunAltitude = clamp(0.5 + 0.5*u_sunDir.y, 0.0, 1.0);
  float sunset = 1.0 - sunAltitude;
  float facingSun = smoothstep(0.0, 0.5, ndl);
  vec3 skyRef = mix(u_skyColor, u_sunColor, clamp(sunset * facingSun, 0.0, 1.0));

  float fresBoost = mix(1.0, 1.0 + reflBoost, pow5(1.0 - ndv));
  vec3 reflected = skyRef * fresBoost;

  // ------ Compose ------
  vec3 color = mix(waterColor, reflected, clamp(fres, 0.0, 1.0));

  // Shinier highlight near the hot spot
  float specBoost = mix(1.0, 1.3, clamp(spec, 0.0, 1.0));
  color += u_sunColor * spec * 0.34 * specBoost;

  // Foam composite (no flicker)
  color = mix(color, vec3(1.0), clamp(foam, 0.0, 1.0));
  color += 0.015 * foam;

  gl_FragColor = vec4(color * v_color.rgb, v_color.a);
}
