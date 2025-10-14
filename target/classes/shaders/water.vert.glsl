attribute vec3 a_position;
uniform mat4 u_projTrans;
attribute vec4 a_color;
attribute vec2 a_texCoord0;
varying vec4 v_color;
varying vec2 v_uv;
varying vec3 v_worldPos;
varying vec4 v_clipPos;
void main(){
  v_color = a_color;
  v_uv = a_texCoord0;
  v_worldPos = a_position;
  vec4 clip = u_projTrans * vec4(a_position, 1.0);
  v_clipPos = clip;
  gl_Position = clip;
}
