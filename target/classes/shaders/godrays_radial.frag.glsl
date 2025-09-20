#ifdef GL_ES
precision mediump float;
precision mediump int;
#endif

varying vec2 v_uv;
uniform sampler2D u_occlusion;
uniform vec2  u_lightPos;     // sun UV in [0..1]
uniform vec2  u_resolution;   // backbuffer size (for px step if needed)
uniform float u_exposure;
uniform float u_decay;
uniform float u_density;
uniform float u_weight;
uniform vec4  u_tint;

void main(){
    // start ray from current pixel toward the light
    vec2 delta = (u_lightPos - v_uv) * (u_density / 64.0); // NUM_SAMPLES = 64
    vec2 coord = v_uv;
    float illumDecay = 1.0;

    vec3 sum = vec3(0.0);
    for(int i=0; i<64; ++i){
        coord += delta;
        float sample = texture2D(u_occlusion, coord).r;
        sum += sample * illumDecay * u_weight;
        illumDecay *= u_decay;
    }

    vec3 color = sum * u_exposure * u_tint.rgb;
    gl_FragColor = vec4(color, 1.0);
}
