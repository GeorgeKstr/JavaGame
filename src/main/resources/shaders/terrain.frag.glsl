#version 130
#ifdef GL_ES
precision mediump float;
#endif

in vec2 v_uv;
flat in vec3 v_texIds;
in vec3 v_texW;
in vec3 v_normal;

out vec4 o_color;

uniform sampler2DArray u_groundTex;
uniform float u_uvScales[256];

uniform vec3 u_sunDir;
uniform vec4 u_tint;

void main() {
    // Sanitize weights to avoid dark rings when sum(w) < 1 due to quantization
    vec3 wv = max(v_texW, vec3(0.0));
    const float CUT = 0.03; // must match CPU deadzone roughly
    wv.x = (wv.x < CUT) ? 0.0 : wv.x;
    wv.y = (wv.y < CUT) ? 0.0 : wv.y;
    wv.z = (wv.z < CUT) ? 0.0 : wv.z;
    float wsum = wv.x + wv.y + wv.z;
    if (wsum > 0.0) {
        wv /= wsum;
    } else {
        wv = vec3(1.0, 0.0, 0.0);
    }

    // Accumulate up to 3 layers with normalized weights
    vec3 texCol = vec3(0.0);
    for (int k = 0; k < 3; ++k) {
        int id = int(round(k == 0 ? v_texIds.x : (k == 1 ? v_texIds.y : v_texIds.z)));
        float w = (k == 0 ? wv.x : (k == 1 ? wv.y : wv.z));
        if (w <= 0.0) continue;
        float scale = (id >= 0 && id < 256) ? u_uvScales[id] : 1.0;
        vec3 sampleRGB = texture(u_groundTex, vec3(v_uv * scale, float(id))).rgb;
        texCol += sampleRGB * w;
    }
    

    // simple diffuse light
    float NdotL = max(dot(normalize(v_normal), normalize(-u_sunDir)), 0.0);
    vec3 lit = texCol * (0.3 + 0.7 * NdotL);

    o_color = vec4(lit, 1.0) * u_tint;
}
