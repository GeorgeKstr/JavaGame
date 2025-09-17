package com.example.game.Render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

public class SimpleShader {
  private ShaderProgram program;
  private final Matrix4 projView = new Matrix4();
  private final Vector3 sunDir = new Vector3(-0.4f, -1f, -0.3f).nor();
  private final Color tint = new Color(0.6f, 0.8f, 0.6f, 1f);

  public void init() {
    ShaderProgram.pedantic = false;
    String vert = Gdx.files.classpath("shaders/terrain.vert.glsl").readString();
    String frag = Gdx.files.classpath("shaders/terrain.frag.glsl").readString();
    program = new ShaderProgram(vert, frag);
    if (!program.isCompiled()) {
      throw new IllegalStateException("Shader compile error:\n" + program.getLog());
    }

  }

  public void bind() { program.bind(); }
  public void unbind() {}
  public ShaderProgram getProgram() { return program; }

  public void setProjectionView(Matrix4 pv) { projView.set(pv); }
  public void setSunDirection(float x, float y, float z) { sunDir.set(x,y,z).nor(); }
  public void setColor(Color c) { tint.set(c); }

  // push uniforms; call after bind()
  public void applyUniforms() {
    program.setUniformMatrix("u_projViewTrans", projView);
    program.setUniformf("u_sunDir", sunDir);
    program.setUniformf("u_tint", tint);
  }

  public void dispose() { if (program != null) program.dispose(); }
}