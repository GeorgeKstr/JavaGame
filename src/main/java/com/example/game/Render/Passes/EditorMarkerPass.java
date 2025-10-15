package com.example.game.Render.Passes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.example.game.Render.Pipeline.RenderContext;
import com.example.game.Render.Pipeline.RenderPass;
import com.example.game.World.HeightSampler;
import com.example.game.World.Editor.EditorContext;

/**
 * Draws a small ring at the terrain point under the camera. Always on top (no depth test).
 */
public final class EditorMarkerPass implements RenderPass {

  private final HeightSampler sampler;
  private Mesh ring;
  private ShaderProgram shader;

  private float radius = 1.0f;          // meters
  private float thickness = 0.08f;      // meters
  private final Color color = new Color(0.4f, 0.8f, 1.0f, 0.9f); // light blue

  // temp
  private final Vector3 center = new Vector3();

  public EditorMarkerPass(HeightSampler sampler) {
    this.sampler = sampler;
  }

  public void setRadius(float r){ radius = Math.max(0.05f, r); }
  public void setThickness(float t){ thickness = Math.max(0.01f, t); }
  public void setColor(float r, float g, float b, float a){ color.set(r,g,b,a); }

  public void init(){
    ShaderProgram.pedantic = false;
    String vert =
        "uniform mat4 uProjView;"+
        "uniform vec3 uCenter;"+
        "uniform float uRadius;"+
        "uniform float uInner;"+
        "attribute vec2 aPos;"+ // unit circle coords, xz plane; radius=1 for outer, inner via uInner
        "attribute float aInner;"+ // 0=outer,1=inner
        "varying float vAlpha;"+
        "void main(){\n"+
        "  float r = mix(uRadius, uInner, aInner);\n"+
        "  vec3 wp = vec3(uCenter.x + aPos.x * r, uCenter.y, uCenter.z + aPos.y * r);\n"+
        "  gl_Position = uProjView * vec4(wp, 1.0);\n"+
        "  vAlpha = 1.0;\n"+
        "}";
    String frag =
        "#ifdef GL_ES\nprecision mediump float;\n#endif\n"+
        "uniform vec4 uColor;"+
        "varying float vAlpha;"+
        "void main(){\n"+
        "  gl_FragColor = vec4(uColor.rgb, uColor.a * vAlpha);\n"+
        "}";
    shader = new ShaderProgram(vert, frag);
    if (!shader.isCompiled()) throw new IllegalStateException("EditorMarker shader: "+shader.getLog());
    buildRingMesh(64);
  }

  private void buildRingMesh(int segments){
    if (ring != null) ring.dispose();
    // triangle strip with (segments+1)*2 vertices (outer/inner)
    int vcount = (segments + 1) * 2;
    float[] verts = new float[vcount * 3]; // aPos.x, aPos.y, aInner
    short[] inds = new short[vcount]; // triangle strip order is just sequential

    int vi=0, ii=0;
    for (int i=0;i<=segments;i++){
      double ang = (Math.PI*2.0) * (i/(double)segments);
      float x = (float)Math.cos(ang);
      float z = (float)Math.sin(ang);
      // outer
      verts[vi++] = x; verts[vi++] = z; verts[vi++] = 0f;
      inds[ii++] = (short)(i*2 + 0);
      // inner
      verts[vi++] = x; verts[vi++] = z; verts[vi++] = 1f;
      inds[ii++] = (short)(i*2 + 1);
    }
    ring = new Mesh(true, vcount, vcount,
        new VertexAttribute(VertexAttributes.Usage.Position, 2, "aPos"),
        new VertexAttribute(VertexAttributes.Usage.Generic, 1, "aInner"));
    ring.setVertices(verts);
    ring.setIndices(inds);
  }

  @Override public void render(RenderContext ctx) {
    if (ring == null || shader == null) return;

    // Use camera pivot (rotation axis) published by EditorCamera, project to ground via sampler
    EditorContext.getFocusPosition(center);
    if (sampler != null) {
      float h = sampler.heightAt(center.x, center.z, center.y);
      if (h != Float.NEGATIVE_INFINITY && !Float.isNaN(h)) center.y = h; 
    }

    // Topmost: disable depth test, no cull, enable blend
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDepthMask(false);
    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

    shader.bind();
    shader.setUniformMatrix("uProjView", ctx.cam.combined);
    shader.setUniformf("uCenter", center);
    shader.setUniformf("uRadius", radius);
    shader.setUniformf("uInner", Math.max(0.0f, radius - thickness));
    shader.setUniformf("uColor", color);

    ring.render(shader, GL20.GL_TRIANGLE_STRIP);

    // Restore for rest of frame (terrain etc already rendered earlier)
    Gdx.gl.glDisable(GL20.GL_BLEND);
    Gdx.gl.glDepthMask(true);
    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
  }

  @Override public void resize(int w, int h) {}

  @Override public void dispose() {
    if (ring != null) { ring.dispose(); ring = null; }
    if (shader != null) { shader.dispose(); shader = null; }
  }
}
