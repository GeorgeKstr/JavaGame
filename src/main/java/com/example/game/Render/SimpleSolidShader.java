package com.example.game.Render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;

public class SimpleSolidShader {
    private ShaderProgram prog;
    private final Matrix4 projView = new Matrix4();
    private final Color color = new Color(Color.BLACK);

    public void init() {
        ShaderProgram.pedantic = false;
        String v = "attribute vec3 a_position;\n" +
                   "uniform mat4 u_projView;\n" +
                   "void main(){ gl_Position = u_projView * vec4(a_position,1.0); }";
        String f = "#ifdef GL_ES\nprecision mediump float;\n#endif\n" +
                   "uniform vec4 u_color;\n" +
                   "void main(){ gl_FragColor = u_color; }";
        prog = new ShaderProgram(v, f);
        if (!prog.isCompiled()) throw new IllegalStateException(prog.getLog());
    }

    public void setColor(Color c){ color.set(c); }
    public ShaderProgram getProgram(){ return prog; }
    public void bind(Matrix4 pv){
        prog.bind();
        prog.setUniformMatrix("u_projView", projView.set(pv));
        prog.setUniformf("u_color", color);
    }
    public void dispose(){ if (prog != null) prog.dispose(); }
}
