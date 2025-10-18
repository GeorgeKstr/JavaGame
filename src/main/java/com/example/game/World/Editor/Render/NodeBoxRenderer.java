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

      for (int k = 0; k < nodes.size(); k++) {
        TerrainSplineNode n = nodes.get(k);
        Vector3 p = n.pos;
        // Relative box from node; if zero, use type defaults (symmetric)
        float rminx = n.getBMinRel().x, rminy = n.getBMinRel().y, rminz = n.getBMinRel().z;
        float rmaxx = n.getBMaxRel().x, rmaxy = n.getBMaxRel().y, rmaxz = n.getBMaxRel().z;
        boolean unset = (rminx == 0f && rminy == 0f && rminz == 0f && rmaxx == 0f && rmaxy == 0f && rmaxz == 0f);
        if (unset) {
          float hxz, hya, hyb;
          if (s.getType() == TerrainSpline.Type.RIVER) { hxz = 3.0f; hya = 2.5f; hyb = 0.5f; }
          else { hxz = 1.0f; hya = 0.6f; hyb = 0.2f; }
          rminx = -hxz; rminy = -hyb; rminz = -hxz;
          rmaxx = hxz;  rmaxy = hya;  rmaxz = hxz;
        }
        float x0 = p.x + Math.min(rminx, rmaxx);
        float y0 = p.y + Math.min(rminy, rmaxy);
        float z0 = p.z + Math.min(rminz, rmaxz);
        float x1 = p.x + Math.max(rminx, rmaxx);
        float y1 = p.y + Math.max(rminy, rmaxy);
        float z1 = p.z + Math.max(rminz, rmaxz);

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
