#ifdef GL_ES
precision mediump float;
#endif

// Attributes (rename if your engine uses different names)
attribute vec3 a_position;
attribute vec2 a_uv;
attribute vec4 a_color;

// Uniforms
uniform mat4 u_model;
uniform mat4 u_viewProj;

// Varyings to fragment
varying vec4 v_color;
varying vec2 v_uv;
varying vec3 v_worldPos;

void main() {
  vec4 world = u_model * vec4(a_position, 1.0);
  v_worldPos = world.xyz;
  v_uv = a_uv;
  v_color = a_color;
  gl_Position = u_viewProj * world;
}
