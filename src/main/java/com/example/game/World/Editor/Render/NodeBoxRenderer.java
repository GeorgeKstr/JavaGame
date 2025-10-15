package com.example.game.World.Editor.Render;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import com.badlogic.gdx.math.Vector3;
import com.example.game.World.Editor.Layers.SplinesLayer;
import com.example.game.World.Splines.TerrainSpline;
import com.example.game.World.Splines.TerrainSplineNode;
import com.example.game.World.TerrainSystem;

import java.util.List;

public final class NodeBoxRenderer implements Renderable {
  private final SplinesLayer layer;
  private final TerrainSystem terrain;
  private final ImmediateModeRenderer20 imLines = new ImmediateModeRenderer20(16384, false, true, 0);

  public NodeBoxRenderer(SplinesLayer layer, TerrainSystem terrain) {
    this.layer = layer;
    this.terrain = terrain;
  }

  @Override
  public void render(Camera cam, RenderMode mode) {
    // Draw all spline nodes as wire AABBs on top
    com.badlogic.gdx.Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
    com.badlogic.gdx.Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    com.badlogic.gdx.Gdx.gl.glDisable(GL20.GL_CULL_FACE);
    com.badlogic.gdx.Gdx.gl.glLineWidth(2f);

    List<TerrainSpline> all = layer.getSplines();
    imLines.begin(cam.combined, GL20.GL_LINES);
    for (TerrainSpline s : all) {
      // Mode-based filtering
      if (mode == RenderMode.TOOL_RIVER && s.getType() != TerrainSpline.Type.RIVER) continue;
      if (mode == RenderMode.TOOL_ROAD && s.getType() != TerrainSpline.Type.ROAD) continue;
      List<TerrainSplineNode> nodes = s.getNodes();
      boolean selSpline = (s == layer.getSelectedSpline());

      // Box half extents vary slightly by spline type to match existing tools
      float halfXZ, halfYAbove, halfYBelow;
      if (s.getType() == TerrainSpline.Type.RIVER) {
        halfXZ = 3.0f; halfYAbove = 2.5f; halfYBelow = 0.5f;
      } else { // ROAD
        halfXZ = 1.0f; halfYAbove = 0.6f; halfYBelow = 0.2f;
      }

      for (int k = 0; k < nodes.size(); k++) {
        Vector3 p = nodes.get(k).pos;
        float baseY = terrain.getBaseHeightSampler().heightAt(p.x, p.z, p.y);
        float y0 = baseY - halfYBelow;
        float y1 = baseY + halfYAbove;
        float x0 = p.x - halfXZ, x1 = p.x + halfXZ;
        float z0 = p.z - halfXZ, z1 = p.z + halfXZ;

        int selIdx = layer.getSelectedNode();
        float cr, cg, cb, ca;
        if (selSpline && k == selIdx) {
          cr = 1.0f; cg = 0.25f; cb = 0.25f; ca = 1.0f; // selected node
        } else if (selSpline) {
          cr = 0.20f; cg = 0.60f; cb = 1.00f; ca = 1.0f;
        } else {
          cr = 0.08f; cg = 0.35f; cb = 0.60f; ca = 0.9f;
        }
        // bottom
        imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y0, z0); imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y0, z0);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y0, z0); imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y0, z1);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y0, z1); imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y0, z1);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y0, z1); imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y0, z0);
        // top
        imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y1, z0); imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y1, z0);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y1, z0); imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y1, z1);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y1, z1); imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y1, z1);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y1, z1); imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y1, z0);
        // verticals
        imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y0, z0); imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y1, z0);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y0, z0); imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y1, z0);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y0, z1); imLines.color(cr,cg,cb,ca); imLines.vertex(x1, y1, z1);
        imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y0, z1); imLines.color(cr,cg,cb,ca); imLines.vertex(x0, y1, z1);
      }
    }
    imLines.end();

    com.badlogic.gdx.Gdx.gl.glLineWidth(1f);
    com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
  }
}
