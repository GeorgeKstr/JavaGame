// terrain.vert.glsl
#version 130
#ifdef GL_ES
precision highp float;
precision highp int;
#endif

in vec3 a_position;
in vec3 a_normal;
in vec2 a_texCoord0;
in vec3 a_texIds;
in vec3 a_texW;

uniform mat4 u_projViewTrans;  // proj * (view with translation zeroed)
uniform vec3 u_cameraWorld;    // camera.position in world space

out vec2 v_uv;
flat out vec3 v_texIds;
out vec3 v_texW;
out vec3 v_normal;

void main() {
    v_uv = a_texCoord0;
    v_texIds = a_texIds;
    v_texW = a_texW;
    v_normal = a_normal;

    vec3 rel = a_position - u_cameraWorld;          // camera-relative
    gl_Position = u_projViewTrans * vec4(rel, 1.0);
}
