in vec3 a_position;
in vec3 a_normal;
in vec2 a_texCoord0;
in float a_texId;

uniform mat4 u_projViewTrans;

out vec2 v_uv;
out float v_texId;
out vec3 v_normal;

void main() {
    v_uv = a_texCoord0;   // or scale/derive from world coords
    v_texId = a_texId;
    v_normal = a_normal;
    gl_Position = u_projViewTrans * vec4(a_position, 1.0);
}
