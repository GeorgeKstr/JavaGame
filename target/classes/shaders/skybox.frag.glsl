#ifdef GL_ES
precision mediump float;
precision mediump int;
#endif

varying vec3 v_dir;           // view-space
uniform vec3  u_sunDir;       // view-space, normalized
uniform float u_time;

// === New tweakable params ===
uniform float u_sunSizeDeg;    // degrees, e.g. 0.53..2.0
uniform float u_sunCoreBoost;  // 0..50 (disc brightness)
uniform float u_warmth;        // 0=cool, 1=warm (tints sun & sky a bit)
uniform float u_mieG;          // 0..0.95 (forward anisotropy)
uniform float u_mieAmount;     // 0..1
uniform float u_rayleighAmount;// 0..1
uniform float u_haze;          // 0..1 (horizon haze)
uniform vec3  u_horizonBase;   // base horizon color
uniform vec3  u_zenithBase;    // base zenith color

vec3 tonemap(vec3 x){ return x / (1.0 + x); }

void main() {
    vec3 d = normalize(v_dir);
    vec3 up = vec3(0.0, 1.0, 0.0);

    // Warm/cool tint helpers
    vec3 warmTint = vec3(1.00, 0.90, 0.75);
    vec3 coolTint = vec3(0.90, 0.97, 1.00);

    // Base gradient with slight warmth lerp
    float h = clamp(dot(d, up), 0.0, 1.0);
    vec3 horizon = mix(u_horizonBase * coolTint, u_horizonBase * warmTint, u_warmth);
    vec3 zenith  = mix(u_zenithBase  * coolTint, u_zenithBase  * warmTint, u_warmth);
    vec3 sky     = mix(horizon, zenith, pow(h, 0.65));

    // Sun geometry / phase
    float mu = dot(d, normalize(u_sunDir));
    float sunAngular = acos(clamp(mu, -1.0, 1.0));

    float sunSize = radians(max(0.05, u_sunSizeDeg)); // safety clamp
    // Visible disc via smoothstep
    float sunCore = 1.0 - smoothstep(sunSize*0.85, sunSize, sunAngular);
    // Glow/halo
    float sunGlow = exp(-pow(sunAngular / sunSize, 2.0)) * 6.0;
    float sunHalo = exp(-pow(sunAngular / (sunSize * 6.0), 2.0)) * 0.35;

    // Rayleigh (broad, blue-ish) & Mie (forward)
    float rayleigh = 0.5 + 0.5 * pow(1.0 - clamp(mu, -1.0, 1.0), 2.0);
    float g = clamp(u_mieG, 0.0, 0.95);
    float denom = 1.0 + g*g - 2.0*g*clamp(mu, -1.0, 1.0);
    float mie = (1.0 - g*g) / (denom * sqrt(denom + 1e-4));

    // Sun color warms with u_warmth
    vec3 sunColor = mix(vec3(1.0), vec3(1.0, 0.93, 0.82), u_warmth);

    vec3 color = sky * (0.6 + 0.4 * rayleigh * u_rayleighAmount);
    // Sun disc (boosted) + scattering around sun
    color += sunColor * (sunCore * u_sunCoreBoost);
    color += sunColor * (0.02 * mie * u_mieAmount + sunGlow + sunHalo);

    // Horizon haze
    float horizonAmt = pow(1.0 - h, 1.5) * u_haze;
    color = mix(color, mix(vec3(0.8,0.85,0.9), warmTint, u_warmth*0.5), horizonAmt);

    gl_FragColor = vec4(tonemap(color), 1.0);
}
