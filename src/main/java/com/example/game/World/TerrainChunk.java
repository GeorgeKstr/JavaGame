package com.example.game.World;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.example.game.Render.SimpleShader;

import java.util.Arrays;

/**
 * TerrainChunk with multi-LOD support + skirts.
 * Vertex layout (updated for blending):
 *   a_position(3), a_normal(3), a_texCoord0(2), a_texIds(3), a_texW(3) => 14 floats/vertex
 */
public class TerrainChunk {
  // Optional global non-destructive height modifier applied during mesh builds and sampling
  private static volatile HeightModifier MOD = null;
  public static void setGlobalModifier(HeightModifier m) { MOD = m; }

  // --- immutable chunk params ---
  private final int S;            // samples per side (e.g., 65)
  private final float tile;       // meters per cell
  private final float yScale;     // meters per height unit
  private final float originX;    // world x of sample (0,0)
  private final float originZ;    // world z of sample (0,0)

  // --- data ---
  private short[] heights;        // length = S*S
  private byte[] texIds;          // length = S*S (top layer id)
  // Additional layers + weights (0..255). Sum of weights per sample will be kept ~255.
  private byte[] texId1;          // second layer id per sample
  private byte[] texId2;          // third layer id per sample
  private byte[] texW0;           // weight of top layer
  private byte[] texW1;           // weight of second layer
  private byte[] texW2;           // weight of third layer

  // Full-res mesh (kept for tools); LOD meshes for runtime
  private Mesh mesh;              // full-res (optional)
  private Mesh[] lodMeshes;       // per LOD (step=1<<lod)
  private Mesh[] lodSkirts;       // skirt meshes per LOD
  private int builtMaxLod = -1;

  private boolean dirty;          // CPU data changed → rebuild LODs

  // Baseline copy of heights for baking reset (null until first bake uses it)
  private short[] baselineHeights;

  // ---- ctors ----

  /** Legacy: construct with base ids only (single layer). */
  public TerrainChunk(int S, float tile, float yScale, float originX, float originZ, byte[] texIds) {
    this.S = S; this.tile = tile; this.yScale = yScale; this.originX = originX; this.originZ = originZ;
    int n = S * S;
    this.heights = new short[n];
    this.texIds = (texIds != null) ? Arrays.copyOf(texIds, n) : new byte[n];
    // Initialize blending arrays: default single-layer (w0=255, others 0)
    this.texId1 = new byte[n];
    this.texId2 = new byte[n];
    this.texW0  = new byte[n];
    this.texW1  = new byte[n];
    this.texW2  = new byte[n];
    Arrays.fill(this.texW0, (byte)255);
  }

  /** Legacy: construct with heights + base ids (single layer). */
  public TerrainChunk(int S, float tile, float yScale, float originX, float originZ, short[] heights, byte[] texIds) {
    this(S, tile, yScale, originX, originZ, texIds);
    setHeights(heights);
  }

  /** Convenience minimal ctor. */
  public TerrainChunk(int s2, float tILE2, float ySCALE2) {
    this.S = s2; this.tile = tILE2; this.yScale = ySCALE2; this.originX = 0; this.originZ = 0;
    int n = S * S;
    this.heights = new short[n]; this.texIds = new byte[n];
    this.texId1 = new byte[n]; this.texId2 = new byte[n];
    this.texW0 = new byte[n]; this.texW1 = new byte[n]; this.texW2 = new byte[n];
    Arrays.fill(this.texW0, (byte)255);
  }

  /** New: construct with heights + full 3 id + 3 weight arrays. */
  public TerrainChunk(int S, float tile, float yScale, float originX, float originZ,
                      short[] heights,
                      byte[] id0, byte[] id1, byte[] id2,
                      byte[] w0,  byte[] w1,  byte[] w2) {
    this.S = S; this.tile = tile; this.yScale = yScale; this.originX = originX; this.originZ = originZ;
    final int n = S * S;

    if (heights == null || heights.length != n)
      throw new IllegalArgumentException("heights length != S*S");

    // Accept null arrays: allocate sane defaults
    this.texIds = (id0 != null && id0.length == n) ? id0 : new byte[n];
    this.texId1 = (id1 != null && id1.length == n) ? id1 : new byte[n];
    this.texId2 = (id2 != null && id2.length == n) ? id2 : new byte[n];
    this.texW0  = (w0  != null && w0.length  == n) ? w0  : new byte[n];
    this.texW1  = (w1  != null && w1.length  == n) ? w1  : new byte[n];
    this.texW2  = (w2  != null && w2.length  == n) ? w2  : new byte[n];

    this.heights = Arrays.copyOf(heights, n);

    // If all three weights are zero for any sample, fall back to w0=255.
    for (int i = 0; i < n; i++) ensureWeightsValid(i);

    this.dirty = true;
  }

  // ---- CPU-side setters ----
  public void setHeights(short[] h) {
    if (h == null || h.length != S * S) throw new IllegalArgumentException("heights length mismatch");
    this.heights = Arrays.copyOf(h, h.length);
    this.dirty = true;
  }

  /** Ensure a baseline copy exists; if not, capture current heights as baseline. */
  public void ensureBaseline() {
    if (baselineHeights == null) baselineHeights = Arrays.copyOf(heights, heights.length);
  }

  /** Replace the entire heights array with the baseline copy if present. */
  public void restoreAllFromBaseline() {
    if (baselineHeights != null) {
      System.arraycopy(baselineHeights, 0, heights, 0, heights.length);
      dirty = true;
    }
  }

  /** Access raw arrays for editor reset operations. */
  public short[] getBaselineArray() { return baselineHeights; }
  /** Set or replace the baseline array (must be S*S); stores a copy. */
  public void setBaselineHeights(short[] base) {
    if (base == null || base.length != S * S) return;
    this.baselineHeights = java.util.Arrays.copyOf(base, base.length);
  }

  public void setTexIds(byte[] ids) {
    if (ids == null || ids.length != S * S) throw new IllegalArgumentException("texIds length mismatch");
    this.texIds = Arrays.copyOf(ids, ids.length);
    this.dirty = true;
  }

  // New setters for v2 data (used by streamer/editor when not using the new ctor)
  public void setTexId1Array(byte[] a) { requireLen(a, "texId1"); this.texId1 = a; this.dirty = true; }
  public void setTexId2Array(byte[] a) { requireLen(a, "texId2"); this.texId2 = a; this.dirty = true; }
  public void setTexW0Array(byte[] a)  { requireLen(a, "texW0");  this.texW0  = a; this.dirty = true; }
  public void setTexW1Array(byte[] a)  { requireLen(a, "texW1");  this.texW1  = a; this.dirty = true; }
  public void setTexW2Array(byte[] a)  { requireLen(a, "texW2");  this.texW2  = a; this.dirty = true; }

  private void requireLen(byte[] a, String name) {
    if (a == null || a.length != S * S) throw new IllegalArgumentException(name + " length mismatch");
  }

  /** If all three weights are zero, set w0=255 so the shader has a valid layer. */
  private void ensureWeightsValid(int i) {
    int a = texW0[i] & 0xFF, b = texW1[i] & 0xFF, c = texW2[i] & 0xFF;
    if ((a | b | c) == 0) texW0[i] = (byte)255;
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
    final int STRIDE = 14;

    float[] verts = new float[V * STRIDE];
    short[] idx   = new short[Q * 6];

    int v = 0;
    for (int z = 0; z < S; z++) {
      for (int x = 0; x < S; x++) {
        float px = originX + x * tile;
        float pz = originZ + z * tile;
        float py = heights[z * S + x] * yScale;
        if (MOD != null) py += MOD.deltaAt(px, pz);

        verts[v++] = px; verts[v++] = py; verts[v++] = pz;
        verts[v++] = 0f; verts[v++] = 1f; verts[v++] = 0f;
        verts[v++] = x;  verts[v++] = z;
        int idxS = z * S + x;
        // ids (as floats)
        verts[v++] = (float)(texIds[idxS] & 0xFF);
        verts[v++] = (float)(texId1[idxS] & 0xFF);
        verts[v++] = (float)(texId2[idxS] & 0xFF);
        // weights normalized 0..1
        verts[v++] = (float)(texW0[idxS] & 0xFF) / 255f;
        verts[v++] = (float)(texW1[idxS] & 0xFF) / 255f;
        verts[v++] = (float)(texW2[idxS] & 0xFF) / 255f;
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
    Mesh m = new Mesh(true, verts.length / 14, indices.length,
        new VertexAttribute(Usage.Position, 3, "a_position"),
        new VertexAttribute(Usage.Normal,   3, "a_normal"),
        new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
        new VertexAttribute(Usage.Generic, 3, "a_texIds"),
        new VertexAttribute(Usage.Generic, 3, "a_texW"));
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
    final int STRIDE = 14;
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
        int idxS = z * S + x;
        verts[v++] = (float)(texIds[idxS] & 0xFF);
        verts[v++] = (float)(texId1[idxS] & 0xFF);
        verts[v++] = (float)(texId2[idxS] & 0xFF);
        verts[v++] = (float)(texW0[idxS] & 0xFF) / 255f;
        verts[v++] = (float)(texW1[idxS] & 0xFF) / 255f;
        verts[v++] = (float)(texW2[idxS] & 0xFF) / 255f;
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
    final int STRIDE = 14;
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
    final int STRIDE = 14;
    float px = originX + x * tile;
    float py = heights[z * S + x] * yScale + yOffset;
    float pz = originZ + z * tile;

    verts[v++] = px; verts[v++] = py; verts[v++] = pz;
    // simple outward-ish normal; 0,1,0 is fine for most lighting since skirt is thin
    verts[v++] = 0f; verts[v++] = 1f; verts[v++] = 0f;
    verts[v++] = x;  verts[v++] = z;
    int idxS = z * S + x;
    verts[v++] = (float)(texIds[idxS] & 0xFF);
    verts[v++] = (float)(texId1[idxS] & 0xFF);
    verts[v++] = (float)(texId2[idxS] & 0xFF);
    verts[v++] = (float)(texW0[idxS] & 0xFF) / 255f;
    verts[v++] = (float)(texW1[idxS] & 0xFF) / 255f;
    verts[v++] = (float)(texW2[idxS] & 0xFF) / 255f;
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
    float h = hx0 + (hx1 - hx0) * tz;
    if (MOD != null) h += MOD.deltaAt(x, z);
    return h;
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
  public byte[]  getTexId0Array()  { return texIds; }
  public byte[]  getTexId1Array()  { return texId1; }
  public byte[]  getTexId2Array()  { return texId2; }
  public byte[]  getTexW0Array()   { return texW0; }
  public byte[]  getTexW1Array()   { return texW1; }
  public byte[]  getTexW2Array()   { return texW2; }

  public boolean isDirty() { return dirty; }
  public void setDirty(boolean dirty) { this.dirty = dirty; }

  /** CPU-side container for mesh arrays (pos/nrm/uv + indices). */
  public static class MeshArrays {
    public final float[] verts; public final short[] indices;
    public MeshArrays(float[] v, short[] i) { this.verts = v; this.indices = i; }
  }
}
