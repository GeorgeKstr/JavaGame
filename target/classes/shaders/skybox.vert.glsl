#ifdef GL_ES
precision mediump float;
precision mediump int;
#endif

attribute vec3 a_position;

uniform mat4 u_proj;
uniform mat4 u_viewNoTrans;

varying vec3 v_dir;

void main() {
    // Direction (rotation only)
    v_dir = (u_viewNoTrans * vec4(a_position, 0.0)).xyz;

    // Push geometry far enough to never hit near-plane
    vec3 pos = a_position * 1000.0;            // big radius cube
    gl_Position = u_proj * u_viewNoTrans * vec4(pos, 1.0);
}
