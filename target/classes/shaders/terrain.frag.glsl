#ifdef GL_ES
precision mediump float;
#endif

in vec2 v_uv;
in float v_texId;
in vec3 v_normal;

out vec4 o_color;

uniform sampler2DArray u_groundTex;
uniform float u_uvScales[256];

uniform vec3 u_sunDir;
uniform vec4 u_tint;

void main() {
    int id = int(round(v_texId));
    float scale = (id >= 0 && id < 256) ? u_uvScales[id] : 1.0;

    vec3 texCol = texture(u_groundTex, vec3(v_uv * scale, float(id))).rgb;

    // simple diffuse light
    float NdotL = max(dot(normalize(v_normal), normalize(-u_sunDir)), 0.0);
    vec3 lit = texCol * (0.3 + 0.7 * NdotL);

    o_color = vec4(lit, 1.0) * u_tint;
}
