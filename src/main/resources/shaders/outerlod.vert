#version 120
// Attributes (LibGDX compatible)
attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec4 a_color;
attribute vec2 a_texCoord0;

// Uniforms
uniform mat4 u_projView;

// Varyings
varying vec4 v_color;
varying vec3 v_norm;
varying vec2 v_uv;

void main() {
  v_color = a_color;
  v_norm = a_normal;
  v_uv = a_texCoord0;
  gl_Position = u_projView * vec4(a_position, 1.0);
}
