#ifdef GL_ES
precision mediump float;
#endif

varying vec3 v_nrm;
varying vec3 v_pos;
varying vec2 v_uv;

uniform vec3 u_sunDir;
uniform vec4 u_tint;

void main() {
  vec3 n = normalize(v_nrm);
  float ndl = clamp(dot(-u_sunDir, n), 0.0, 1.0);
  float ambient = 0.35;
  float light = max(ambient, ndl);

  // optional: subtle grid overlay
  float grid = (step(0.96, fract(v_uv.x)) + step(0.96, fract(v_uv.y))) * 0.1;
  vec3 color = u_tint.rgb * light + vec3(grid);

  gl_FragColor = vec4(color, 1.0);
}
