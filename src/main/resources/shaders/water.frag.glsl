#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_uv;
varying vec3 v_worldPos;

uniform float u_time;
uniform vec3 u_camPos;
uniform vec3 u_sunDir;
uniform vec3 u_skyColor;
uniform vec3 u_sunColor;
uniform float u_reverseFlow; // 0 = normal, 1 = reversed

/* ===================== Ultra-calm tunables ===================== */
const float FLOW_SPEED        = 0.060;  // << tiny downstream drift (set 0.0 to freeze)
const float WARP_STRENGTH     = 0.1;    // static domain warp strength (world-anchored)
const float WARP_FREQ         = 0.55;   // warp scale (world-anchored)
const float HEIGHT_SCALE      = 0.045;  // parallax scale (view-only, no time)
const int   PARALLAX_STEPS    = 1;

const float MIDFREQ1          = 11.0;    // primary band (broad)
const float MIDFREQ2          = 19.0;   // secondary band (mid)
const float MIDFREQ3          = 3.0;   // NEW: tertiary band (fine)
const float FBM_COARSE        = 1.2;    // low-octave chaos (static)
const float FBM_FINE          = 2.2;    // very light fine detail (static)

const float RIPPLE_GAIN       = 0.85;   // normal intensity
const float SPEC_POWER        = 110.0;

const float FOAM_EDGE_GAIN    = 0.9;
const float FOAM_STREAKS      = 0.80;   // along-flow elongation amount
const float FOAM_TURB_GAIN    = 0.55;

/* ===================== Noise ===================== */
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

/* ============ STATIC world-anchored warp (no time) ============ */
vec2 staticWarp(vec2 uvWS){
  vec2 w = v_worldPos.xz * WARP_FREQ;
  vec2 f0 = vec2(noise(w + 1.73), noise(w + 4.11)) - 0.5;
  vec2 f1 = vec2(noise(w*1.19 - 2.41), noise(w*1.31 + 3.33)) - 0.5;
  vec2 curlish = vec2(-(f0.y+f1.y), (f0.x+f1.x)); // 90° twist for variation
  return uvWS + WARP_STRENGTH * (f0*0.6 + f1*0.4 + 0.35*curlish);
}

/* ===================== Height / Normals (with third band) ===================== */
float heightField(vec2 uv){
  // three band-limited ripples + light fbm flavor
  float s1 = sin(uv.x*MIDFREQ1 + uv.y*7.0);
  float s2 = sin(uv.x*MIDFREQ2 - uv.y*15.0);
  float s3 = sin(uv.x*MIDFREQ3 + uv.y*11.0); // slight different tilt

  // ridge helpers for sharper crests
  float r1 = 1.0 - abs(s1);
  float r2 = 1.0 - abs(s2);
  float r3 = 1.0 - abs(s3);

  // weights tuned to keep energy similar to your 2-band version
  float bands = 0.30*s1 + 0.24*s2 + 0.18*s3
              + 0.12*(r1*r2) + 0.06*(r2*r3); // tiny ridge mix for sparkle

  float coarse = 0.16*fbm(uv*FBM_COARSE); // slightly reduced to offset s3 cost
  float fine   = 0.08*fbm(uv*FBM_FINE);
  return bands + coarse + fine;
}

vec3 normalFromHeight(vec2 uv){
  // finite-difference normal as in your version
  float e = 0.02;
  float hC = heightField(uv);
  float dx = heightField(uv + vec2(e,0.0)) - hC;
  float dy = heightField(uv + vec2(0.0,e)) - hC;
  return normalize(vec3(-dx*RIPPLE_GAIN, 1.0, -dy*RIPPLE_GAIN));
}

/* ===================== Parallax (view-only; no time) ===================== */
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

void main(){
  // Across/along in river uv space
  float u = clamp(v_uv.x, 0.0, 1.0);
  float v = v_uv.y;

  // Flow direction (uv space); advection is tiny
  vec2 flowDir = normalize(vec2(0.15, 1.0));

  // Base river-space coords
  vec2 baseUV = vec2(u*2.0, v*1.1);

  // World-anchored static warp (no time)
  vec2 uvWSWarped = staticWarp(baseUV);

  // Tiny single-direction drift (set FLOW_SPEED=0 to freeze). Reverse with u_reverseFlow.
  float dirSign = (u_reverseFlow > 0.5) ? -1.0 : 1.0;
  vec2 uvFlow = uvWSWarped - flowDir * (u_time * FLOW_SPEED * dirSign);

  // Parallax (view-only, stable)
  vec3 V = normalize(u_camPos - v_worldPos);
  float h0 = heightField(uvFlow);
  vec2 uvP = parallaxOffset(uvFlow, V, flowDir, h0);

  // Clear, bump-like normals from parallaxed coords
  vec3 N  = normalFromHeight(uvP);

  // ==== Lighting ====
  vec3 L = normalize(-u_sunDir);
  vec3 H = normalize(L + V);
  float ndv = clamp(dot(N,V), 0.0, 1.0);
  float ndl = max(dot(N,L), 0.0);
  float ndh = max(dot(N,H), 0.0);
  float spec = pow(ndh, SPEC_POWER) * ndl;

  float F0 = 0.02;
  float fres = F0 + (1.0 - F0) * pow(1.0 - ndv, 5.0);

  // Depth/edge tint across width (unchanged)
  float edge = min(u, 1.0-u);
  float curveProxy = 0.5 + 0.5*sin(v_worldPos.z * 0.1 + sin(v_worldPos.z*0.023));
  float shallow = smoothstep(0.20, 0.02, edge) * (0.85 + 0.3*(curveProxy-0.5));
  vec3 deepColor = vec3(0.02, 0.20, 0.25);
  vec3 shallowColor = vec3(0.45, 0.85, 0.95);
  vec3 waterColor = mix(deepColor, shallowColor, shallow);

  // ==== Foam: stable (no flicker), streaked and edge-weighted ====
  float edgeMask = smoothstep(0.18, 0.05, edge) * FOAM_EDGE_GAIN;

  // World-anchored foam field (no time); slight along-flow stretch
  vec2 worldUV = v_worldPos.xz * 0.22;
  vec2 T = normalize(flowDir);
  vec2 B = normalize(vec2(-T.y, T.x));
  vec2 foamUV = vec2(dot(worldUV, T)*2.4, dot(worldUV, B)*0.35);

  float streaks   = fbm(foamUV*1.15);
  float foamStreak= smoothstep(0.66, 0.93, streaks) * FOAM_STREAKS;

  // Turbulence proxy from normal change along flow (stable; no time)
  vec3 Nf = normalFromHeight(uvP + T*0.05);
  float turb = clamp(length(Nf - N), 0.0, 1.0);
  float foamTurb = smoothstep(0.20, 0.44, turb) * FOAM_TURB_GAIN;

  float foam = edgeMask * (foamStreak + foamTurb);

  // Bottom tint (subtle, static)
  float viewFacing = 1.0 - ndv;
  float shallowReveal = shallow * smoothstep(0.0, 0.55, viewFacing);
  float n1 = noise(worldUV);
  vec3 bottomTint = mix(vec3(0.08, 0.06, 0.03), vec3(0.14, 0.12, 0.08), n1);
  waterColor = mix(waterColor, bottomTint, 0.22 * shallowReveal);

  // Fresnel + spec
  vec3 reflected = u_skyColor;
  vec3 color = mix(waterColor, reflected, clamp(fres, 0.0, 1.0));
  color += u_sunColor * spec * 0.44;

  // Foam composite (no temporal jitter)
  color = mix(color, vec3(1.0), clamp(foam, 0.0, 1.0));
  color += 0.015 * foam;

  gl_FragColor = vec4(color * v_color.rgb, v_color.a);
}
