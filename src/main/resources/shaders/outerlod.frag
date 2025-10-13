#version 120
#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec3 v_norm;
varying vec2 v_uv;

uniform vec3 u_sunDir;        // world-space light direction (same convention as main terrain)
uniform float u_gridThickness; // e.g. 0.03
uniform vec4 u_gridColor;      // rgb + alpha for overlay

void main() {
  // Base lambert
  vec3 n = normalize(v_norm);
  float lambert = max(0.0, dot(n, u_sunDir));
  vec3 base = v_color.rgb * (0.35 + 0.65 * lambert);

  // Procedural border grid (1 cell per chunk)
  float edge = min(min(v_uv.x, 1.0 - v_uv.x), min(v_uv.y, 1.0 - v_uv.y));
  float grid = step(0.0, u_gridThickness - edge); // 1 near border within thickness
  vec3 outRgb = mix(base, u_gridColor.rgb, u_gridColor.a * grid);
  gl_FragColor = vec4(outRgb, 1.0);
}
