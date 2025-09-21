package com.example.game.World;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.example.game.Render.SimpleShader;

import java.util.Arrays;

/**
 * TerrainChunk with multi-LOD support + skirts.
 * Vertex layout: a_position(3), a_normal(3), a_texCoord0(2), a_texId(1) => 9 floats/vertex
 */
public class TerrainChunk {

  // --- immutable chunk params ---
  private final int S;            // samples per side (e.g., 65)
  private final float tile;       // meters per cell
  private final float yScale;     // meters per height unit
  private final float originX;    // world x of sample (0,0)
  private final float originZ;    // world z of sample (0,0)

  // --- data ---
  private short[] heights;        // length = S*S
  private byte[] texIds;          // length = S*S (0=default)

  // Full-res mesh (kept for tools); LOD meshes for runtime
  private Mesh mesh;              // full-res (optional)
  private Mesh[] lodMeshes;       // per LOD (step=1<<lod)
  private Mesh[] lodSkirts;       // skirt meshes per LOD
  private int builtMaxLod = -1;

  private boolean dirty;          // CPU data changed → rebuild LODs

  // ---- ctors ----
  public TerrainChunk(int S, float tile, float yScale, float originX, float originZ, byte[] texIds) {
    this.S = S; this.tile = tile; this.yScale = yScale; this.originX = originX; this.originZ = originZ;
    this.heights = new short[S * S];
    this.texIds = (texIds != null) ? Arrays.copyOf(texIds, S * S) : new byte[S * S];
  }

  public TerrainChunk(int S, float tile, float yScale, float originX, float originZ, short[] heights, byte[] texIds) {
    this(S, tile, yScale, originX, originZ, texIds);
    setHeights(heights);
  }

  public TerrainChunk(int s2, float tILE2, float ySCALE2) {
    this.S = s2; this.tile = tILE2; this.yScale = ySCALE2; this.originX = 0; this.originZ = 0;
    this.heights = new short[S * S]; this.texIds = new byte[S * S];
  }

  // ---- CPU-side setters ----
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

  // ---- Mesh building (full-res) ----
  /** Tools: build single full-res mesh (render thread). */
  public void buildMesh() {
    MeshArrays md = buildMeshArrays();
    if (mesh != null) mesh.dispose();
    mesh = createMesh(md.verts, md.indices);
    dirty = false;
  }

  /** CPU-only arrays for full-res. */
  public MeshArrays buildMeshArrays() {
    final int V = S * S;
    final int Q = (S - 1) * (S - 1);
    final int STRIDE = 9;

    float[] verts = new float[V * STRIDE];
    short[] idx   = new short[Q * 6];

    int v = 0;
    for (int z = 0; z < S; z++) {
      for (int x = 0; x < S; x++) {
        float px = originX + x * tile;
        float py = heights[z * S + x] * yScale;
        float pz = originZ + z * tile;

        verts[v++] = px; verts[v++] = py; verts[v++] = pz;
        verts[v++] = 0f; verts[v++] = 1f; verts[v++] = 0f;
        verts[v++] = x;  verts[v++] = z;
        verts[v++] = (float)(texIds[z * S + x] & 0xFF);
      }
    }

    int i = 0;
    for (int z = 0; z < S - 1; z++) {
      for (int x = 0; x < S - 1; x++) {
        int v0 = z * S + x; int v1 = v0 + 1; int v2 = v0 + S; int v3 = v2 + 1;
        i = putTri(idx, i, v0, v2, v1, verts, STRIDE);
        i = putTri(idx, i, v1, v2, v3, verts, STRIDE);
      }
    }

    // normalize normals
    for (int n = 0; n < V; n++) {
      int base = n * STRIDE + 3;
      float nx = verts[base], ny = verts[base + 1], nz = verts[base + 2];
      float len = (float)Math.sqrt(nx*nx + ny*ny + nz*nz);
      if (len > 1e-6f) { verts[base]/=len; verts[base+1]/=len; verts[base+2]/=len; }
      else { verts[base]=0f; verts[base+1]=1f; verts[base+2]=0f; }
    }
    return new MeshArrays(verts, idx);
  }

  private static int putTri(short[] idx, int i, int a, int b, int c, float[] v, int STRIDE) {
    idx[i++] = (short)a; idx[i++] = (short)b; idx[i++] = (short)c;
    int sa = a * STRIDE, sb = b * STRIDE, sc = c * STRIDE;
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

  private Mesh createMesh(float[] verts, short[] indices) {
    Mesh m = new Mesh(true, verts.length / 9, indices.length,
        new VertexAttribute(Usage.Position, 3, "a_position"),
        new VertexAttribute(Usage.Normal,   3, "a_normal"),
        new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
        new VertexAttribute(Usage.Generic, 1, "a_texId"));
    m.setVertices(verts);
    m.setIndices(indices);
    return m;
  }

  // ---- LOD building ----

  /** Build LOD meshes (LOD0..maxLod) + skirts; call on render thread. */
  public void buildLods(int maxLod) {
    disposeLods();
    builtMaxLod = maxLod;
    lodMeshes = new Mesh[maxLod + 1];
    lodSkirts = new Mesh[maxLod + 1];

    for (int lod = 0; lod <= maxLod; lod++) {
      int step = 1 << lod;
      MeshArrays grid = buildMeshArraysForStep(step);
      lodMeshes[lod] = createMesh(grid.verts, grid.indices);

      MeshArrays skirt = buildSkirtArraysForStep(step, 0.5f * tile);
      lodSkirts[lod] = createMesh(skirt.verts, skirt.indices);
    }
    dirty = false;
  }

  /** True if we already have LODs built for at least wantMax. */
  public boolean hasLods(int wantMax) {
    return lodMeshes != null && lodMeshes.length >= wantMax + 1 && builtMaxLod >= wantMax;
  }

  /** CPU-only downsampled mesh arrays for step (1,2,4,...) */
  public MeshArrays buildMeshArraysForStep(int step) {
    final int S2 = ((S - 1) / step) + 1;   // reduced samples per side
    final int STRIDE = 9;
    final int V = S2 * S2;
    final int Q = (S2 - 1) * (S2 - 1);

    float[] verts = new float[V * STRIDE];
    short[]  idx  = new short[Q * 6];

    int v = 0;
    for (int z2 = 0; z2 < S2; z2++) {
      int z = z2 * step;
      for (int x2 = 0; x2 < S2; x2++) {
        int x = x2 * step;
        float px = originX + x * tile;
        float py = heights[z * S + x] * yScale;
        float pz = originZ + z * tile;

        verts[v++] = px; verts[v++] = py; verts[v++] = pz;
        verts[v++] = 0f; verts[v++] = 1f; verts[v++] = 0f;
        verts[v++] = x;  verts[v++] = z;
        verts[v++] = (float)(texIds[z * S + x] & 0xFF);
      }
    }

    int i = 0;
    for (int z2 = 0; z2 < S2 - 1; z2++) {
      for (int x2 = 0; x2 < S2 - 1; x2++) {
        int v0 = z2 * S2 + x2; int v1 = v0 + 1; int v2 = v0 + S2; int v3 = v2 + 1;
        i = putTri(idx, i, v0, v2, v1, verts, STRIDE);
        i = putTri(idx, i, v1, v2, v3, verts, STRIDE);
      }
    }

    // normalize normals
    for (int n = 0; n < V; n++) {
      int base = n * STRIDE + 3;
      float nx = verts[base], ny = verts[base + 1], nz = verts[base + 2];
      float len = (float)Math.sqrt(nx*nx + ny*ny + nz*nz);
      if (len > 1e-6f) { verts[base]/=len; verts[base+1]/=len; verts[base+2]/=len; }
      else { verts[base]=0f; verts[base+1]=1f; verts[base+2]=0f; }
    }
    return new MeshArrays(verts, idx);
  }

  /** Build a perimeter skirt (top + lowered ring), hiding cracks between LODs. */
  private MeshArrays buildSkirtArraysForStep(int step, float skirtDepth) {
    final int S2 = ((S - 1) / step) + 1;
    final int STRIDE = 9;
    final int RING = S2 * 4;         // top ring vertices
    final int V = RING * 2;          // top + bottom
    final int SEG_PER_EDGE = S2 - 1;
    final int SEGMENTS = SEG_PER_EDGE * 4;
    final int ICOUNT = SEGMENTS * 6;

    float[] verts = new float[V * STRIDE];
    short[]  idx  = new short[ICOUNT];

    int v = 0;
    // Emit top ring in order: +X edge, -X edge, +Z edge, -Z edge
    // +X edge (x = S2-1, z=0..S2-1)
    for (int z2 = 0; z2 < S2; z2++) v = emitSkirtVertex(verts, v, (S2 - 1) * step, z2 * step, 0f);
    // -X edge (x = 0, z=0..S2-1)
    for (int z2 = 0; z2 < S2; z2++) v = emitSkirtVertex(verts, v, 0, z2 * step, 0f);
    // +Z edge (z = S2-1, x=0..S2-1)
    for (int x2 = 0; x2 < S2; x2++) v = emitSkirtVertex(verts, v, x2 * step, (S2 - 1) * step, 0f);
    // -Z edge (z = 0, x=0..S2-1)
    for (int x2 = 0; x2 < S2; x2++) v = emitSkirtVertex(verts, v, x2 * step, 0, 0f);

    // Bottom ring (duplicated, lowered)
    for (int z2 = 0; z2 < S2; z2++) v = emitSkirtVertex(verts, v, (S2 - 1) * step, z2 * step, -skirtDepth);
    for (int z2 = 0; z2 < S2; z2++) v = emitSkirtVertex(verts, v, 0, z2 * step, -skirtDepth);
    for (int x2 = 0; x2 < S2; x2++) v = emitSkirtVertex(verts, v, x2 * step, (S2 - 1) * step, -skirtDepth);
    for (int x2 = 0; x2 < S2; x2++) v = emitSkirtVertex(verts, v, x2 * step, 0, -skirtDepth);

    int i = 0;
    // Create quads per edge between corresponding top/bottom vertices
    int topStart = 0, botStart = RING;
    for (int edge = 0; edge < 4; edge++) {
      int edgeTop = topStart + edge * S2;
      int edgeBot = botStart + edge * S2;
      for (int s = 0; s < S2 - 1; s++) {
        short t0 = (short)(edgeTop + s);
        short t1 = (short)(edgeTop + s + 1);
        short b0 = (short)(edgeBot + s);
        short b1 = (short)(edgeBot + s + 1);
        idx[i++] = t0; idx[i++] = b0; idx[i++] = t1;
        idx[i++] = t1; idx[i++] = b0; idx[i++] = b1;
      }
    }
    return new MeshArrays(verts, idx);
  }

  private int emitSkirtVertex(float[] verts, int v, int x, int z, float yOffset) {
    final int STRIDE = 9;
    float px = originX + x * tile;
    float py = heights[z * S + x] * yScale + yOffset;
    float pz = originZ + z * tile;
    byte tid = texIds[z * S + x];

    verts[v++] = px; verts[v++] = py; verts[v++] = pz;
    // simple outward-ish normal; 0,1,0 is fine for most lighting since skirt is thin
    verts[v++] = 0f; verts[v++] = 1f; verts[v++] = 0f;
    verts[v++] = x;  verts[v++] = z;
    verts[v++] = (float)(tid & 0xFF);
    return v;
  }

  // ---- Sampling ----

  /** Bilinear height at world (x,z); returns NaN if (x,z) is outside this chunk. */
  public float heightAtWorld(float x, float z) {
    float lx = (x - originX) / tile;
    float lz = (z - originZ) / tile;
    if (lx < 0f || lz < 0f || lx > S - 1.001f || lz > S - 1.001f) return Float.NaN;

    int x0 = (int)Math.floor(lx), z0 = (int)Math.floor(lz);
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

  /** Legacy render (full-res). Prefer renderLod() from TerrainSystem. */
  public void render(SimpleShader shader) {
    if (mesh == null) return;
    mesh.bind(shader.getProgram());
    shader.applyUniforms();
    mesh.render(shader.getProgram(), com.badlogic.gdx.graphics.GL20.GL_TRIANGLES);
    mesh.unbind(shader.getProgram());
  }

  public void renderLod(SimpleShader shader, int lod) {
    if (lodMeshes == null || lod < 0 || lod >= lodMeshes.length) return;
    Mesh m = lodMeshes[lod]; if (m == null) return;
    m.bind(shader.getProgram());
    shader.applyUniforms();
    m.render(shader.getProgram(), com.badlogic.gdx.graphics.GL20.GL_TRIANGLES);
    m.unbind(shader.getProgram());
  }

  public void renderLodSkirt(SimpleShader shader, int lod) {
    if (lodSkirts == null || lod < 0 || lod >= lodSkirts.length) return;
    Mesh m = lodSkirts[lod]; if (m == null) return;
    m.bind(shader.getProgram());
    shader.applyUniforms();
    m.render(shader.getProgram(), com.badlogic.gdx.graphics.GL20.GL_TRIANGLES);
    m.unbind(shader.getProgram());
  }

  public void dispose() {
    if (mesh != null) { mesh.dispose(); mesh = null; }
    disposeLods();
  }

  private void disposeLods() {
    if (lodMeshes != null)  for (Mesh m : lodMeshes) if (m != null) m.dispose();
    if (lodSkirts != null)  for (Mesh m : lodSkirts) if (m != null) m.dispose();
    lodMeshes = null; lodSkirts = null; builtMaxLod = -1;
  }

  // ---- Accessors ----

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
    public final float[] verts; public final short[] indices;
    public MeshArrays(float[] v, short[] i) { this.verts = v; this.indices = i; }
  }
}
