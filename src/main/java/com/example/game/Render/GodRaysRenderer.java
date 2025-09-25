package com.example.game.Render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/** Screen-space god rays (crepuscular rays) via radial blur from sun position. */
public class GodRaysRenderer {
    // Low-res occlusion (render sky bright, occluders black)
    private FrameBuffer occlusionFbo;
    private Texture occlusionTex;
    private int occW, occH;

    // Fullscreen quad
    private Mesh fsQuad;

    // Radial blur shader
    private ShaderProgram radial;

    // Params
    public int samples = 64;
    public float exposure = 0.3f;
    public float decay = 0.97f;
    public float density = 0.9f;
    public float weight = 0.22f;
    public Color tint = new Color(1f, 0.96f, 0.85f, 1f); // warm-ish

    private final Vector2 lightNdc = new Vector2();   // [-1..1]
    private final Vector2 lightUv  = new Vector2();   // [0..1]
    private final Vector3 tmp = new Vector3();

    public void init(int screenW, int screenH) {
        ShaderProgram.pedantic = false;
        String v = Gdx.files.classpath("shaders/godrays_radial.vert.glsl").readString();
        String f = Gdx.files.classpath("shaders/godrays_radial.frag.glsl").readString();
        radial = new ShaderProgram(v, f);
        if (!radial.isCompiled()) throw new IllegalStateException(radial.getLog());

        // quarter-res works well
        resize(screenW, screenH);

        fsQuad = buildFullScreenQuad();
    }

    public void resize(int screenW, int screenH) {
        if (occlusionFbo != null) occlusionFbo.dispose();
        occW = Math.max(1, screenW / 2);
        occH = Math.max(1, screenH / 2);
        occlusionFbo = new FrameBuffer(Pixmap.Format.RGBA8888, occW, occH, false);
        occlusionTex = occlusionFbo.getColorBufferTexture();
        occlusionTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        occlusionTex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
    }

    /** Project the sun dir to screen UV [0..1]. camPos + sunDir * far is good enough. */
    public void updateLightScreenPos(Camera cam, Vector3 sunDirWorld) {
        tmp.set(cam.position).mulAdd(sunDirWorld, cam.far * 0.9f);
        // to NDC
        Vector3 ndc = tmp.prj(cam.combined);
        lightNdc.set(ndc.x, ndc.y);
        // to UV
        lightUv.set( lightNdc.x * 0.5f + 0.5f, lightNdc.y * 0.5f + 0.5f );
    }

    /** Begin occlusion rendering. Clear to bright (sky), then render occluders in black. */
    public void beginOcclusion() {
        occlusionFbo.begin();
        Gdx.gl.glViewport(0, 0, occW, occH);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f); // bright = unoccluded light
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    /** End occlusion rendering. */
    public void endOcclusion(int backbufferW, int backbufferH) {
        occlusionFbo.end();
        Gdx.gl.glViewport(0, 0, backbufferW, backbufferH);
    }

    /** Composite god rays additively over the backbuffer. */
    public void compositeAdd(int backbufferW, int backbufferH) {
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE); // additive

        radial.bind();
        radial.setUniformi("u_occlusion", 0);
        occlusionTex.bind(0);

        radial.setUniformf("u_resolution", backbufferW, backbufferH);
        radial.setUniformf("u_lightPos", lightUv);
        radial.setUniformf("u_exposure", exposure);
        radial.setUniformf("u_decay", decay);
        radial.setUniformf("u_density", density);
        radial.setUniformf("u_weight", weight);
        radial.setUniformf("u_tint", tint);

        // hard-code samples in shader; or pass as uniform if you add loop-unroll variants

        fsQuad.render(radial, GL20.GL_TRIANGLES);

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public void dispose() {
        if (occlusionFbo != null) occlusionFbo.dispose();
        if (fsQuad != null) fsQuad.dispose();
        if (radial != null) radial.dispose();
    }

    private static Mesh buildFullScreenQuad() {
        // x,y, u,v
        float[] v = {
            -1,-1, 0,0,
             1,-1, 1,0,
             1, 1, 1,1,
            -1, 1, 0,1
        };
        short[] i = {0,1,2, 0,2,3};
        Mesh m = new Mesh(true, 4, 6,
            new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"));
        m.setVertices(v);
        m.setIndices(i);
        return m;
    }
}
