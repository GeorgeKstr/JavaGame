package com.example.game.World;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.example.game.Render.SimpleShader;

import java.util.Arrays;

/**
 * TerrainChunk
 * - Stores heights (short) and per-tile texture IDs (byte)
 * - Knows its world-space origin
 * - Can build mesh vertex/index arrays on CPU and upload Mesh on render thread
 */
public class TerrainChunk {

  // --- immutable chunk params ---
  private final int S;            // samples per side (e.g., 64)
  private final float tile;       // meters per cell
  private final float yScale;     // meters per height unit
  private final float originX;    // world x of sample (0,0)
  private final float originZ;    // world z of sample (0,0)

  // --- data ---
  private short[] heights;        // length = S*S
  private byte[] texIds;          // length = S*S (0=default)
  private Mesh mesh;              // GL resource (created on render thread)
  private boolean dirty;          // mark when CPU data changed and needs rebuild

  // ---- ctor ----
  public TerrainChunk(int S, float tile, float yScale, float originX, float originZ, byte[] texIds) {
    this.S = S;
    this.tile = tile;
    this.yScale = yScale;
    this.originX = originX;
    this.originZ = originZ;
    this.heights = new short[S * S];
    this.texIds = (texIds != null) ? Arrays.copyOf(texIds, S * S) : new byte[S * S];
  }

  // If you want to init with heights immediately
  public TerrainChunk(int S, float tile, float yScale, float originX, float originZ,
                      short[] heights, byte[] texIds) {
    this(S, tile, yScale, originX, originZ, texIds);
    setHeights(heights);
  }

  public TerrainChunk(int s2, float tILE2, float ySCALE2) {
    this.S = s2;
    this.tile = tILE2;
    this.yScale = ySCALE2;
    this.originX = 0;
    this.originZ = 0;
    this.heights = new short[S * S];
    this.texIds = new byte[S * S];
}

  // ---- CPU-side data setters ----
  public void setHeights(short[] h) {
    if (h.length != S * S) throw new IllegalArgumentException("heights length mismatch");
    this.heights = Arrays.copyOf(h, h.length);
    this.dirty = true;
  }

  public void setTexIds(byte[] ids) {
    if (ids.length != S * S) throw new IllegalArgumentException("texIds length mismatch");
    this.texIds = Arrays.copyOf(ids, ids.length);
    this.dirty = true;
  }

  // ---- Mesh building (render thread safe wrappers) ----

  /** Full build: generates verts/indices on CPU and uploads a Mesh (call on render thread). */
  public void buildMesh() {
    MeshArrays md = buildMeshArrays();
    createMeshFromArrays(md.verts, md.indices);
  }

  /**
   * Upload precomputed arrays to GL. Call this ONLY on the render thread.
   * (Used by async streaming: mesh arrays built off-thread, then uploaded here.)
   */
  public void createMeshFromArrays(float[] verts, short[] indices) {
    if (mesh != null) mesh.dispose();
    mesh = new Mesh(true, verts.length / 8, indices.length,
        new VertexAttribute(Usage.Position, 3, "a_position"),
        new VertexAttribute(Usage.Normal,   3, "a_normal"),
        new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"));
    mesh.setVertices(verts);
    mesh.setIndices(indices);
    dirty = false;
  }

  /** Build interleaved vertex/index arrays (CPU only; no GL). */
  public MeshArrays buildMeshArrays() {
    final int V = S * S;
    final int Q = (S - 1) * (S - 1);

    float[] verts = new float[V * 8]; // pos(3), nrm(3), uv(2)
    short[] idx   = new short[Q * 6];

    // positions/UVs (normals temp as 0,1,0; will accumulate)
    int v = 0;
    for (int z = 0; z < S; z++) {
      for (int x = 0; x < S; x++) {
        float px = originX + x * tile;
        float py = heights[z * S + x] * yScale;
        float pz = originZ + z * tile;

        verts[v++] = px;
        verts[v++] = py;
        verts[v++] = pz;
        verts[v++] = 0f; // nx
        verts[v++] = 1f; // ny
        verts[v++] = 0f; // nz
        verts[v++] = x;  // u (grid UV; your shader can tile from this)
        verts[v++] = z;  // v
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
      float nx = verts[base], ny = verts[base + 1], nz = verts[base + 2];
      float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
      if (len > 1e-6f) {
        verts[base]     = nx / len;
        verts[base + 1] = ny / len;
        verts[base + 2] = nz / len;
      } else {
        verts[base] = 0f; verts[base + 1] = 1f; verts[base + 2] = 0f;
      }
    }

    return new MeshArrays(verts, idx);
  }

  private static int putTri(short[] idx, int i, int a, int b, int c, float[] v) {
    idx[i++] = (short) a; idx[i++] = (short) b; idx[i++] = (short) c;

    int sa = a * 8, sb = b * 8, sc = c * 8;
    float ax = v[sa],   ay = v[sa + 1], az = v[sa + 2];
    float bx = v[sb],   by = v[sb + 1], bz = v[sb + 2];
    float cx = v[sc],   cy = v[sc + 1], cz = v[sc + 2];
    float ux = bx - ax, uy = by - ay, uz = bz - az;
    float vx = cx - ax, vy = cy - ay, vz = cz - az;
    float nx = uy * vz - uz * vy;
    float ny = uz * vx - ux * vz;
    float nz = ux * vy - uy * vx;
    v[sa + 3] += nx; v[sa + 4] += ny; v[sa + 5] += nz;
    v[sb + 3] += nx; v[sb + 4] += ny; v[sb + 5] += nz;
    v[sc + 3] += nx; v[sc + 4] += ny; v[sc + 5] += nz;
    return i;
  }

  // ---- Sampling ----

  /** Bilinear height at world (x,z); returns NaN if (x,z) is outside this chunk. */
  public float heightAtWorld(float x, float z) {
    float lx = (x - originX) / tile;
    float lz = (z - originZ) / tile;
    if (lx < 0f || lz < 0f || lx > S - 1.001f || lz > S - 1.001f) return Float.NaN;

    int x0 = (int) Math.floor(lx), z0 = (int) Math.floor(lz);
    int x1 = x0 + 1, z1 = z0 + 1;
    float tx = lx - x0, tz = lz - z0;

    float h00 = heights[z0 * S + x0] * yScale;
    float h10 = heights[z0 * S + x1] * yScale;
    float h01 = heights[z1 * S + x0] * yScale;
    float h11 = heights[z1 * S + x1] * yScale;

    float hx0 = h00 + (h10 - h00) * tx;
    float hx1 = h01 + (h11 - h01) * tx;
    return hx0 + (hx1 - hx0) * tz;
  }

  // ---- Render ----

  public void render(SimpleShader shader) {
    if (mesh == null) return;
    mesh.bind(shader.getProgram());
    shader.applyUniforms();
    mesh.render(shader.getProgram(), com.badlogic.gdx.graphics.GL20.GL_TRIANGLES);
    mesh.unbind(shader.getProgram());
  }

  public void dispose() {
    if (mesh != null) { mesh.dispose(); mesh = null; }
  }

  // ---- Accessors (used by TerrainSystem & editor) ----

  public int getSize() { return S; }
  public float getTile() { return tile; }
  public float getYScale() { return yScale; }
  public float getOriginX() { return originX; }
  public float getOriginZ() { return originZ; }

  public short[] getHeightsArray() { return heights; }
  public byte[]  getTexIdsArray()  { return texIds; }

  public boolean isDirty() { return dirty; }
  public void setDirty(boolean dirty) { this.dirty = dirty; }

  /** CPU-side container for mesh arrays (pos/nrm/uv + indices). */
  public static class MeshArrays {
    public final float[] verts;
    public final short[] indices;
    public MeshArrays(float[] v, short[] i) { this.verts = v; this.indices = i; }
  }
}
