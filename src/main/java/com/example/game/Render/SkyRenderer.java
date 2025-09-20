package com.example.game.Render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/** Renders a procedural scattering sky using an inside-out cube. */
public class SkyRenderer {
    private ShaderProgram prog;
    private Mesh cube; // inside-viewed cube
    private final Matrix4 viewNoTrans = new Matrix4();
    private final Vector3 tmpSunView = new Vector3();
    private float sunSizeDeg = 0.9f;
    private float sunCoreBoost = 25f;
    private float warmth = 0.35f;
    private float mieG = 0.8f;
    private float mieAmount = 1.0f;
    private float rayleighAmount = 1.0f;
    private float haze = 0.2f;
    private final Vector3 horizonBase = new Vector3(0.85f, 0.75f, 0.65f);
    private final Vector3 zenithBase  = new Vector3(0.20f, 0.35f, 0.70f);

    public SkyRenderer setSunSizeDeg(float deg){ this.sunSizeDeg = deg; return this; }
    public SkyRenderer setSunCoreBoost(float b){ this.sunCoreBoost = b; return this; }
    public SkyRenderer setWarmth(float w){ this.warmth = MathUtils.clamp(w,0f,1f); return this; }
    public SkyRenderer setMieG(float g){ this.mieG = MathUtils.clamp(g,0f,0.95f); return this; }
    public SkyRenderer setMieAmount(float m){ this.mieAmount = MathUtils.clamp(m,0f,2f); return this; }
    public SkyRenderer setRayleighAmount(float r){ this.rayleighAmount = MathUtils.clamp(r,0f,2f); return this; }
    public SkyRenderer setHaze(float h){ this.haze = MathUtils.clamp(h,0f,1f); return this; }
    public SkyRenderer setBaseColors(Vector3 horizon, Vector3 zenith){
        if (horizon != null) this.horizonBase.set(horizon);
        if (zenith  != null) this.zenithBase.set(zenith);
        return this;
    }

    public void init() {
        ShaderProgram.pedantic = false;
        String vert = Gdx.files.classpath("shaders/skybox.vert.glsl").readString();
        String frag = Gdx.files.classpath("shaders/skybox.frag.glsl").readString();
        prog = new ShaderProgram(vert, frag);
        if (!prog.isCompiled()) {
            throw new IllegalStateException("Sky shader compile error:\n" + prog.getLog());
        }
        cube = buildInsideCube();
    }

    public void render(Camera cam, Vector3 sunDirWorld, float timeSeconds) {
        // Build view without translation (no parallax)
        viewNoTrans.set(cam.view);
        viewNoTrans.setTranslation(0, 0, 0);

        // Sun direction in view space (rotate only, no translation!)
        tmpSunView.set(sunDirWorld).nor();
        tmpSunView.rot(cam.view).nor();

        // GL state for sky
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL); // draw where depth is 1.0
        Gdx.gl.glDepthMask(false);          // don't write depth
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_FRONT);   // view inside of cube

        prog.bind();
        prog.setUniformMatrix("u_proj", cam.projection);
        prog.setUniformMatrix("u_viewNoTrans", viewNoTrans);
        prog.setUniformf("u_sunDir", tmpSunView);
        prog.setUniformf("u_time", timeSeconds);

        // push params
        prog.setUniformf("u_sunSizeDeg", sunSizeDeg);
        prog.setUniformf("u_sunCoreBoost", sunCoreBoost);
        prog.setUniformf("u_warmth", warmth);
        prog.setUniformf("u_mieG", mieG);
        prog.setUniformf("u_mieAmount", mieAmount);
        prog.setUniformf("u_rayleighAmount", rayleighAmount);
        prog.setUniformf("u_haze", haze);
        prog.setUniformf("u_horizonBase", horizonBase);
        prog.setUniformf("u_zenithBase", zenithBase);

        cube.render(prog, GL20.GL_TRIANGLES);

        // restore
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDepthFunc(GL20.GL_LESS);
    }

    public void dispose() {
        if (cube != null) cube.dispose();
        if (prog != null) prog.dispose();
    }

    private static Mesh buildInsideCube() {
        float[] v = {
            -1,-1,-1,  1,-1,-1,  1, 1,-1, -1, 1,-1, // back
            -1,-1, 1,  1,-1, 1,  1, 1, 1, -1, 1, 1  // front
        };
        short[] idx = {
            0,2,1, 0,3,2,       // back
            4,5,6, 4,6,7,       // front
            0,7,3, 0,4,7,       // left
            1,2,6, 1,6,5,       // right
            0,1,5, 0,5,4,       // bottom
            3,7,6, 3,6,2        // top
        };
        Mesh m = new Mesh(true, v.length/3, idx.length,
                new VertexAttribute(Usage.Position, 3, "a_position"));
        m.setVertices(v);
        m.setIndices(idx);
        return m;
    }
}
