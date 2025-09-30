package com.example.game.World;

import com.badlogic.gdx.utils.IntMap;

/**
 * TerrainEditor — smooth brush + soft blending (drop-in replacement)
 *
 * Fixes / Changes:
 *  - Border/bias fix: precise primaryIndex mapping so shared edges map to the current chunk's S-1 vertex.
 *  - Texture brush is now a "minimum threshold" raise-only mixer:
 *      * ensures target layer >= threshold (falloff-shaped * strength)
 *      * never reduces any layer that's already higher than the target threshold
 *      * trims only the non-target layers (proportionally) if total would exceed 1
 *      * gentle "dust" cut still applies, but never below the target threshold
 *  - Supersampling and falloff logic from your version kept as-is.
 */
final class TerrainEditor {
  private final TerrainWorld w;

  TerrainEditor(TerrainWorld w){ this.w = w; }

  private static class Index { int cx, cz, lx, lz; void set(int cx,int cz,int lx,int lz){this.cx=cx;this.cz=cz;this.lx=lx;this.lz=lz;} }
  private int cells() { return w.S - 1; }

  /**
   * Primary index mapping with exact gridline handling.
   * Maps shared edges to the "previous" chunk's S-1 local vertex so neighbor propagation works
   * and brush location is not biased at chunk borders. Works for negative world coords, too.
   */
  private boolean primaryIndex(int gx, int gz, Index out) {
    final int S = w.S;
    final int cpc = S - 1; // cells per chunk (local indices 0..S-1)

    int cx = Math.floorDiv(gx, cpc);
    int cz = Math.floorDiv(gz, cpc);
    int lx = gx - cx * cpc; // remainder in [0..cpc]
    int lz = gz - cz * cpc;

    // If we're exactly on an internal grid line, assign to the previous chunk's S-1.
    // Avoids shifting the effective brush center at borders.
    if (lx == 0 && gx != 0) { cx -= 1; lx = cpc; }
    if (lz == 0 && gz != 0) { cz -= 1; lz = cpc; }

    if (lx < 0 || lz < 0 || lx >= S || lz >= S) return false;
    out.set(cx, cz, lx, lz);
    return true;
  }

  private int neighborsForSample(Index p, Index[] o) {
    int S = w.S; int c=0;
    if (p.lx==S-1) o[c++].set(p.cx+1,p.cz,0,p.lz);
    if (p.lx==0)   o[c++].set(p.cx-1,p.cz,S-1,p.lz);
    if (p.lz==S-1) o[c++].set(p.cx,p.cz+1,p.lx,0);
    if (p.lz==0)   o[c++].set(p.cx,p.cz-1,p.lx,S-1);
    if (p.lx==S-1&&p.lz==S-1) o[c++].set(p.cx+1,p.cz+1,0,0);
    if (p.lx==0  &&p.lz==S-1) o[c++].set(p.cx-1,p.cz+1,S-1,0);
    if (p.lx==S-1&&p.lz==0)   o[c++].set(p.cx+1,p.cz-1,0,S-1);
    if (p.lx==0  &&p.lz==0)   o[c++].set(p.cx-1,p.cz-1,S-1,S-1);
    return c;
  }

  boolean editHeightsCircle(int gx, int gz, int radius, short delta) {
    boolean changed=false; Index p=new Index(); Index[] ns={new Index(),new Index(),new Index(),new Index(),new Index(),new Index(),new Index(),new Index()};
    for (int dz=-radius; dz<=radius; dz++) for (int dx=-radius; dx<=radius; dx++) {
      if (dx*dx+dz*dz>radius*radius) continue;
      int gxi=gx+dx, gzi=gz+dz;
      if (!primaryIndex(gxi,gzi,p)) continue;
      TerrainChunk tc=get(p.cx,p.cz); if (tc==null) continue;
      short[] arr=tc.getHeightsArray(); int idx=p.lz*w.S+p.lx; short b=arr[idx], a=(short)(b+delta);
      if (a!=b){ arr[idx]=a; tc.setDirty(true); changed=true; }
      int ncount=neighborsForSample(p,ns);
      for(int i=0;i<ncount;i++){ TerrainChunk nb=get(ns[i].cx,ns[i].cz); if(nb==null)continue;
        short[] harr=nb.getHeightsArray(); int nidx=ns[i].lz*w.S+ns[i].lx; short bb=harr[nidx], aa=(short)(bb+delta);
        if(aa!=bb){ harr[nidx]=aa; nb.setDirty(true); }
      }
    }
    return changed;
  }

  boolean paintTexCircle(int gx, int gz, int radius, byte texId) {
    // Back-compat default strength/falloff
    return paintTexCircle(gx, gz, radius, texId, 1.0f, 0.8f);
  }

  // New API: includes brush strength (0..1) and falloff (0..1, 1=soft)
  boolean paintTexCircle(int gx, int gz, int radius, byte texId, float strength, float falloff) {
    boolean changed=false; Index p=new Index(); Index[] ns={new Index(),new Index(),new Index(),new Index(),new Index(),new Index(),new Index(),new Index()};

    // Clamp inputs once
    strength = clamp01(strength);
    falloff  = clamp01(falloff);

    // precompute rotation (fixed) for rotated-grid supersampling to avoid axis-aligned facets
    final float ROT = 0.463647609f; // ~26.565°, tan=0.5
    final float CS = (float)Math.cos(ROT);
    final float SN = (float)Math.sin(ROT);

    for (int dz=-radius; dz<=radius; dz++) for (int dx=-radius; dx<=radius; dx++) {
      // 4x4 rotated-grid supersampling with per-cell deterministic jitter
      int gxi = gx + dx, gzi = gz + dz;
      int h = hash32(gxi, gzi);
      float fSum = 0f;
      for (int sIdx=0; sIdx<16; ++sIdx) {
        int sxi = sIdx & 3; int szi = sIdx >> 2; // 0..3
        // center samples in [-0.5,0.5)
        float ox = ((sxi + rand01(h + sIdx*73)) - 1.5f) * 0.5f; // 1/2 cell spacing
        float oz = ((szi + rand01(h + sIdx*91)) - 1.5f) * 0.5f;
        // rotate the sample pattern to avoid grid alignment artifacts
        float rx = ox * CS - oz * SN;
        float rz = ox * SN + oz * CS;

        float px = dx + rx;
        float pz = dz + rz;
        float rr = (float)Math.sqrt(px*px + pz*pz) / (float)radius; // normalized distance

        // Two falloffs: quintic (finite support) and Gaussian (infinite, smooth). Blend by falloff.
        float s  = clamp01(1f - rr);
        float q  = smootherstep5(s);
        float sigma = 0.32f + 0.30f * falloff; // normalized sigma ~0.32..0.62
        float g = (float)Math.exp(-(rr*rr)/(2f*sigma*sigma));
        float shaped = lerp(q, g, falloff); // 0 → crisp quintic, 1 → soft Gaussian
        fSum += shaped;
      }
      float f = (fSum * (1f/16f)) * strength; // average & scale
      if (f <= 0f) continue;

      if (!primaryIndex(gxi,gzi,p)) continue;
      TerrainChunk tc=get(p.cx,p.cz); if (tc==null) continue;
      int idx=p.lz*w.S+p.lx;
      if (applyPaintToCell(tc, idx, texId, f)) { tc.setDirty(true); changed=true; }

      int ncount=neighborsForSample(p,ns);
      for(int i=0;i<ncount;i++){
        TerrainChunk nb=get(ns[i].cx,ns[i].cz); if(nb==null)continue;
        int nidx=ns[i].lz*w.S+ns[i].lx;
        if (applyPaintToCell(nb, nidx, texId, f)) { nb.setDirty(true); }
      }
    }
    return changed;
  }

  /**
   * Raise-Only (Minimum Threshold) blending for a single cell.
   * Ensures the target layer's weight >= f (threshold).
   * Never pulls down any layer that's already > its previous value; trims only non-target layers if sum>1.
   */
  private boolean applyPaintToCell(TerrainChunk c, int idx, byte newId, float f) {
    f = clamp01(f);
    if (f <= 0f) return false;

    // read current
    byte[] id0Arr = c.getTexId0Array();
    byte[] id1Arr = c.getTexId1Array();
    byte[] id2Arr = c.getTexId2Array();
    byte[] w0Arr  = c.getTexW0Array();
    byte[] w1Arr  = c.getTexW1Array();
    byte[] w2Arr  = c.getTexW2Array();

    int id0 = id0Arr[idx] & 0xFF;
    int id1 = id1Arr[idx] & 0xFF;
    int id2 = id2Arr[idx] & 0xFF;
    float w0 = (w0Arr[idx] & 0xFF) / 255f;
    float w1 = (w1Arr[idx] & 0xFF) / 255f;
    float w2 = (w2Arr[idx] & 0xFF) / 255f;

    final float EPS_EMPTY = 0.02f; // small so new layers can appear smoothly
    final float CUT = 0.01f;
    final int newI = (newId & 0xFF);

    // Choose / prepare target slot
    int slot = -1;
    if (id0 == newI) slot = 0;
    else if (id1 == newI) slot = 1;
    else if (id2 == newI) slot = 2;
    else if (w0 < EPS_EMPTY) { id0 = newI; w0 = 0f; slot = 0; }
    else if (w1 < EPS_EMPTY) { id1 = newI; w1 = 0f; slot = 1; }
    else if (w2 < EPS_EMPTY) { id2 = newI; w2 = 0f; slot = 2; }
    else {
      // replace smallest weight (stable & soft)
      if (w0 <= w1 && w0 <= w2) { id0 = newI; w0 = 0f; slot = 0; }
      else if (w1 <= w0 && w1 <= w2) { id1 = newI; w1 = 0f; slot = 1; }
      else { id2 = newI; w2 = 0f; slot = 2; }
    }
    if (slot < 0) return false;

    float oldW = (slot==0? w0 : slot==1? w1 : w2);
    float target = Math.max(oldW, f);
    float delta  = target - oldW;
    if (delta <= 1e-6f) {
      // Nothing to raise; still allow bubble/sorting & gentle cut to keep arrays tidy, but skip trims.
      // We'll just rewrite bytes (may early-return false to skip dirty if you prefer).
      return false;
    }

    // Sum before the raise
    float sum = w0 + w1 + w2;
    float newSum = sum + delta;

    // If we overflow 1, trim only non-target layers proportionally.
    if (newSum > 1f + 1e-6f) {
      float over = newSum - 1f;
      float o0 = (slot==0)? 0f : w0;
      float o1 = (slot==1)? 0f : w1;
      float o2 = (slot==2)? 0f : w2;
      float otherSum = o0 + o1 + o2;

      if (otherSum > 1e-8f) {
        float scale = Math.max(0f, 1f - (over / otherSum));
        if (slot != 0) w0 *= scale;
        if (slot != 1) w1 *= scale;
        if (slot != 2) w2 *= scale;
      } else {
        // No room elsewhere; the target simply clamps to 1 and others to 0
        if (slot != 0) w0 = 0f;
        if (slot != 1) w1 = 0f;
        if (slot != 2) w2 = 0f;
        target = 1f;
      }
    }

    // Apply the raise to the target slot
    if (slot == 0) w0 = target;
    else if (slot == 1) w1 = target;
    else w2 = target;

    // Normalize to sum==1 after trimming stage (keeps shader expectations),
    // but do NOT push the target above 'target' nor pull anything else extra.
    float sNorm = w0 + w1 + w2;
    if (sNorm > 1e-6f) {
      w0 /= sNorm; w1 /= sNorm; w2 /= sNorm;
      // Ensure target is still >= f after normalization due to numeric error
      float wT = (slot==0? w0 : slot==1? w1 : w2);
      if (wT < f) {
        float addBack = f - wT;
        if (slot == 0) w0 += addBack;
        else if (slot == 1) w1 += addBack;
        else w2 += addBack;
        // Re-normalize if we overflew 1 (very rare)
        float s2 = w0 + w1 + w2;
        if (s2 > 1f + 1e-6f) {
          float over = s2 - 1f;
          float o0 = (slot==0)? 0f : w0;
          float o1 = (slot==1)? 0f : w1;
          float o2 = (slot==2)? 0f : w2;
          float oSum = o0 + o1 + o2;
          if (oSum > 1e-8f) {
            float scale = Math.max(0f, 1f - (over / oSum));
            if (slot != 0) w0 *= scale;
            if (slot != 1) w1 *= scale;
            if (slot != 2) w2 *= scale;
          }
        }
      }
    } else {
      // Degenerate case
      if (slot == 0) { w0 = 1f; w1 = 0f; w2 = 0f; }
      else if (slot == 1) { w0 = 0f; w1 = 1f; w2 = 0f; }
      else { w0 = 0f; w1 = 0f; w2 = 1f; }
    }

    // Bubble-up by weight so that layer 0 is the dominant/top layer
    if (w1 > w0) { float tw=w0; w0=w1; w1=tw; int ti=id0; id0=id1; id1=ti; if (slot==0) slot=1; else if (slot==1) slot=0; }
    if (w2 > w1) { float tw=w1; w1=w2; w2=tw; int ti=id1; id1=id2; id2=ti; if (slot==1) slot=2; else if (slot==2) slot=1; }
    if (w1 > w0) { float tw=w0; w0=w1; w1=tw; int ti=id0; id0=id1; id1=ti; if (slot==0) slot=1; else if (slot==1) slot=0; }

    // Gentle dust cut for non-target layers; never let target fall below f
    float tWeight = (slot==0? w0 : slot==1? w1 : w2);
    if (slot != 0 && w0 < CUT) w0 = 0f;
    if (slot != 1 && w1 < CUT) w1 = 0f;
    if (slot != 2 && w2 < CUT) w2 = 0f;
    // Re-assert target minimum after cuts
    if (slot == 0) w0 = Math.max(w0, f);
    else if (slot == 1) w1 = Math.max(w1, f);
    else w2 = Math.max(w2, f);

    // If we now exceed 1 due to re-asserting f, trim non-targets again (rare, tiny deltas)
    float sFinal = w0 + w1 + w2;
    if (sFinal > 1f + 1e-6f) {
      float over = sFinal - 1f;
      float o0 = (slot==0)? 0f : w0;
      float o1 = (slot==1)? 0f : w1;
      float o2 = (slot==2)? 0f : w2;
      float oSum = o0 + o1 + o2;
      if (oSum > 1e-8f) {
        float scale = Math.max(0f, 1f - (over / oSum));
        if (slot != 0) w0 *= scale;
        if (slot != 1) w1 *= scale;
        if (slot != 2) w2 *= scale;
      } else {
        if (slot != 0) w0 = 0f;
        if (slot != 1) w1 = 0f;
        if (slot != 2) w2 = 0f;
        if (slot == 0) w0 = 1f;
        else if (slot == 1) w1 = 1f;
        else w2 = 1f;
      }
    }

    // write back (apply byte-level deadzone to non-targets; target is clamped normally)
    id0Arr[idx] = (byte)id0; id1Arr[idx] = (byte)id1; id2Arr[idx] = (byte)id2;
    int cutByte = (int)Math.ceil(CUT * 255f);

    int b0 = Math.round(clamp01(w0) * 255f);
    int b1 = Math.round(clamp01(w1) * 255f);
    int b2 = Math.round(clamp01(w2) * 255f);

    // Only zero-out non-targets with dust cut at byte level
    if (slot != 0 && b0 <= cutByte) b0 = 0;
    if (slot != 1 && b1 <= cutByte) b1 = 0;
    if (slot != 2 && b2 <= cutByte) b2 = 0;

    w0Arr[idx] = (byte)b0;
    w1Arr[idx] = (byte)b1;
    w2Arr[idx] = (byte)b2;

    return true;
  }

  void rebuildDirtyChunks() {
    for (IntMap.Entry<TerrainChunk> e : w.chunks) {
      TerrainChunk c = e.value;
      if (c.isDirty()) {
        c.buildLods(w.MAX_LOD);
        c.setDirty(false);
        w.defaultFlatChunks.remove(e.key);
      }
    }
  }

  private TerrainChunk get(int cx, int cz) { return w.chunks.get(TerrainWorld.key(cx, cz)); }

  // -------- utilities --------
  private static float clamp01(float x){ return x < 0f ? 0f : (x > 1f ? 1f : x); }
  private static float smootherstep5(float x){ // 6x^5 - 15x^4 + 10x^3
    if (x <= 0f) return 0f; if (x >= 1f) return 1f; return x*x*x*(x*(x*6f - 15f) + 10f);
  }
  private static float lerp(float a, float b, float t){ return a + (b - a) * t; }
  private static int hash32(int x, int y){
    int h = x * 0x9E3779B9 ^ y * 0x85EBCA6B;
    h ^= (h >>> 16); h *= 0x7FEB352D; h ^= (h >>> 15); h *= 0x846CA68B; h ^= (h >>> 16);
    return h;
  }
  private static float rand01(int h){ // 0..1 deterministic
    h ^= 0x68E31DA4; h *= 0xBC164501; h ^= (h >>> 13); h *= 0x96D7C17B; h ^= (h >>> 15);
    return ((h >>> 8) & 0xFFFFFF) / 16777215f;
  }
}
