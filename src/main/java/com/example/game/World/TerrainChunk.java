package com.example.game.World;

import java.util.Arrays;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.example.game.Render.SimpleShader;

public class TerrainChunk {
  private final int S;           // samples per side (e.g., 64)
  private final float tile;      // world units per grid cell (e.g., 1.0)
  private final float yScale;    // meters per height unit (e.g., 0.5)
  private short[] h;             // S*S heights
  private Mesh mesh;

  public TerrainChunk(int samplesPerSide, float tileSize, float yScale) {
    this.S = samplesPerSide;
    this.tile = tileSize;
    this.yScale = yScale;
    this.h = new short[S * S];
  }

  public void setHeights(short[] heights) {
    if (heights.length != S * S) throw new IllegalArgumentException("heights length mismatch");
    this.h = Arrays.copyOf(heights, heights.length);
  }

  public void buildMesh() {
    int V = S * S;
    int Q = (S - 1) * (S - 1);

    // pos(3) + normal(3) + uv(2) = 8 floats per vertex
    float[] verts = new float[V * 8];
    short[] idx = new short[Q * 6];

    // positions + uv, init normals to zero
    int v = 0;
    for (int z = 0; z < S; z++) {
      for (int x = 0; x < S; x++) {
        float px = x * tile;
        float py = (h[z * S + x]) * yScale;
        float pz = z * tile;
        verts[v++] = px; verts[v++] = py; verts[v++] = pz;
        verts[v++] = 0;  verts[v++] = 1;  verts[v++] = 0; // temp normal
        verts[v++] = x;  verts[v++] = z;                  // uv (tile in shader)
      }
    }

    // indices + accumulate face normals
    int i = 0;
    for (int z = 0; z < S - 1; z++) {
      for (int x = 0; x < S - 1; x++) {
        int v0 = z * S + x;
        int v1 = v0 + 1;
        int v2 = v0 + S;
        int v3 = v2 + 1;

        i = putTri(idx, i, v0, v2, v1, verts);
        i = putTri(idx, i, v1, v2, v3, verts);
      }
    }

    // normalize vertex normals
    for (int n = 0; n < V; n++) {
      int base = n * 8 + 3;
      float nx = verts[base], ny = verts[base+1], nz = verts[base+2];
      float len = (float)Math.sqrt(nx*nx + ny*ny + nz*nz);
      if (len > 1e-6f) { verts[base] /= len; verts[base+1] /= len; verts[base+2] /= len; }
      else { verts[base] = 0; verts[base+1] = 1; verts[base+2] = 0; }
    }

    // (Re)create Mesh
    if (mesh != null) mesh.dispose();
    mesh = new Mesh(true, V, idx.length,
        new VertexAttribute(Usage.Position, 3, "a_position"),
        new VertexAttribute(Usage.Normal,   3, "a_normal"),
        new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"));
    mesh.setVertices(verts);
    mesh.setIndices(idx);
  }

  private static int putTri(short[] idx, int i, int a, int b, int c, float[] v) {
    idx[i++] = (short)a; idx[i++] = (short)b; idx[i++] = (short)c;

    int sa = a*8, sb = b*8, sc = c*8;
    float ax=v[sa], ay=v[sa+1], az=v[sa+2];
    float bx=v[sb], by=v[sb+1], bz=v[sb+2];
    float cx=v[sc], cy=v[sc+1], cz=v[sc+2];
    float ux=bx-ax, uy=by-ay, uz=bz-az;
    float vx=cx-ax, vy=cy-ay, vz=cz-az;
    float nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
    v[sa+3]+=nx; v[sa+4]+=ny; v[sa+5]+=nz;
    v[sb+3]+=nx; v[sb+4]+=ny; v[sb+5]+=nz;
    v[sc+3]+=nx; v[sc+4]+=ny; v[sc+5]+=nz;
    return i;
  }

  public void render(SimpleShader shader) {
    if (mesh == null) return;
    mesh.bind(shader.getProgram());
    shader.applyUniforms();
    mesh.render(shader.getProgram(), com.badlogic.gdx.graphics.GL20.GL_TRIANGLES);
    mesh.unbind(shader.getProgram());
  }

  public void dispose() { if (mesh != null) mesh.dispose(); }

  // World height at arbitrary (x,z) in world units. Bilinear filtered.
  public float heightAt(float x, float z) {
    // convert to grid space
    float gx = x / tile;
    float gz = z / tile;

    // clamp to valid cell range (S-1 because we sample (x0,x1) and (z0,z1))
    if (gx < 0) gx = 0; if (gz < 0) gz = 0;
    if (gx > S - 1.001f) gx = S - 1.001f;
    if (gz > S - 1.001f) gz = S - 1.001f;

    int x0 = (int)Math.floor(gx), z0 = (int)Math.floor(gz);
    int x1 = x0 + 1,             z1 = z0 + 1;
    float tx = gx - x0,          tz = gz - z0;

    float h00 = h[z0 * S + x0] * yScale;
    float h10 = h[z0 * S + x1] * yScale;
    float h01 = h[z1 * S + x0] * yScale;
    float h11 = h[z1 * S + x1] * yScale;

    float hx0 = h00 + (h10 - h00) * tx;
    float hx1 = h01 + (h11 - h01) * tx;
    return hx0 + (hx1 - hx0) * tz;
  }
}