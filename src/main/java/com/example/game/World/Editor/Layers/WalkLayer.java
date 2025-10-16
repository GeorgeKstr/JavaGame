package com.example.game.World.Editor.Layers;

import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.BaseToolLayer;
import com.example.game.World.Editor.FpsCameraController;
import com.example.game.World.Editor.Tools.Walk.PlaceTool;
import com.example.game.World.Editor.Tools.Walk.RoamTool;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.math.Matrix4;

public final class WalkLayer extends BaseToolLayer {
  private final Skin skin;
  public WalkLayer(TerrainSystem terrain, Skin skin, FpsCameraController fpsCam) {
    super("walk", "Walk");
    this.skin = skin;

    // Roam tool: FPS movement (no placement)
    RoamTool roam = new RoamTool();

    // Placement tool: RTS placement and list; on "Use" it teleports FPS and switches to roam
    PlaceTool place = new PlaceTool(terrain,
      state -> { fpsCam.setState(state); },
      () -> { setActiveTool(roam, this.skin); com.badlogic.gdx.Gdx.input.setCursorCatched(true); }
    );

    addTool(place);
    addTool(roam);

    this.placeTool = place;
  }

  @Override
  public void onActivate() {
    // Implement activation logic if needed
  }

  @Override
  public void onDeactivate() {
    // Implement deactivation logic if needed
  }

  // --- 3D debug draw of player nodes ---
  private final Matrix4 tmpOrtho = new Matrix4();
  private ImmediateModeRenderer20 im = null;
  private PlaceTool placeTool;

  @Override
  public void render3D(Camera cam) {
    super.render3D(cam);
    if (placeTool == null) return;
    java.util.List<float[]> aabbs = placeTool.getNodeAABBs();
    if (aabbs == null || aabbs.isEmpty()) return;
    if (im == null) im = new ImmediateModeRenderer20(4096, false, true, 0);

    // Solid translucent boxes using triangles
    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    im.begin(cam.combined, GL20.GL_TRIANGLES);
    float r=0.1f,g=0.85f,b=1f,a=0.22f;
    for (float[] e : aabbs) {
      float x=e[0], z=e[1], hw=e[2]*0.5f, hd=e[3]*0.5f;
      float y0 = terrainHeight(cam, x, z); // approximate ground
      float h = Math.max(1.5f, e[4]);
      float y1 = y0 + h;
      float x0 = x - hw, x1 = x + hw, z0 = z - hd, z1 = z + hd;
      // 6 faces (two triangles each)
      // bottom (y0)
      quad(im, x0,y0,z0,  x1,y0,z0,  x1,y0,z1,  x0,y0,z1,  r,g,b,a);
      // top (y1)
      quad(im, x0,y1,z0,  x1,y1,z0,  x1,y1,z1,  x0,y1,z1,  r,g,b,a);
      // front (z0)
      quad(im, x0,y0,z0,  x1,y0,z0,  x1,y1,z0,  x0,y1,z0,  r,g,b,a);
      // back (z1)
      quad(im, x0,y0,z1,  x1,y0,z1,  x1,y1,z1,  x0,y1,z1,  r,g,b,a);
      // left (x0)
      quad(im, x0,y0,z0,  x0,y0,z1,  x0,y1,z1,  x0,y1,z0,  r,g,b,a);
      // right (x1)
      quad(im, x1,y0,z0,  x1,y0,z1,  x1,y1,z1,  x1,y1,z0,  r,g,b,a);
    }
    im.end();
    Gdx.gl.glDisable(GL20.GL_BLEND);
  }

  private void line(ImmediateModeRenderer20 im, float x0,float y0,float z0, float x1,float y1,float z1, float r,float g,float b,float a){
    im.color(r,g,b,a); im.vertex(x0,y0,z0);
    im.color(r,g,b,a); im.vertex(x1,y1,z1);
  }

  private void quad(ImmediateModeRenderer20 im,
                    float x0,float y0,float z0,
                    float x1,float y1,float z1,
                    float x2,float y2,float z2,
                    float x3,float y3,float z3,
                    float r,float g,float b,float a) {
    // Tri 1: 0-1-2
    im.color(r,g,b,a); im.vertex(x0,y0,z0);
    im.color(r,g,b,a); im.vertex(x1,y1,z1);
    im.color(r,g,b,a); im.vertex(x2,y2,z2);
    // Tri 2: 0-2-3
    im.color(r,g,b,a); im.vertex(x0,y0,z0);
    im.color(r,g,b,a); im.vertex(x2,y2,z2);
    im.color(r,g,b,a); im.vertex(x3,y3,z3);
  }

  private float terrainHeight(Camera cam, float x, float z){
    try {
      // Sample ground via EditorMarkerPass pattern: use terrain sampler if available later; fallback 0
      return 0f;
    } catch (Throwable ignored) { return 0f; }
  }
}
