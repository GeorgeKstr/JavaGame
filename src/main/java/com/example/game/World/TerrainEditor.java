package com.example.game.World;

import com.badlogic.gdx.utils.IntMap;

final class TerrainEditor {
  private final TerrainWorld w;

  TerrainEditor(TerrainWorld w){ this.w = w; }

  private static class Index { int cx, cz, lx, lz; void set(int cx,int cz,int lx,int lz){this.cx=cx;this.cz=cz;this.lx=lx;this.lz=lz;} }
  private int cells() { return w.S - 1; }

  private boolean primaryIndex(int gx, int gz, Index out) {
    int cpc = cells();
    int cx = (int)Math.floor((float)gx / cpc), cz = (int)Math.floor((float)gz / cpc);
    int lx = gx - cx * cpc, lz = gz - cz * cpc;
    if (lx < 0 || lz < 0 || lx >= w.S || lz >= w.S) return false;
    out.set(cx, cz, lx, lz); return true;
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
    boolean changed=false; Index p=new Index(); Index[] ns={new Index(),new Index(),new Index(),new Index(),new Index(),new Index(),new Index(),new Index()};
    for (int dz=-radius; dz<=radius; dz++) for (int dx=-radius; dx<=radius; dx++) {
      if (dx*dx+dz*dz>radius*radius) continue;
      int gxi=gx+dx, gzi=gz+dz;
      if (!primaryIndex(gxi,gzi,p)) continue;
      TerrainChunk tc=get(p.cx,p.cz); if (tc==null) continue;
      byte[] tex=tc.getTexIdsArray(); int idx=p.lz*w.S+p.lx; byte b=tex[idx];
      if (b!=texId){ tex[idx]=texId; tc.setDirty(true); changed=true; }
      int ncount=neighborsForSample(p,ns);
      for(int i=0;i<ncount;i++){ TerrainChunk nb=get(ns[i].cx,ns[i].cz); if(nb==null)continue;
        byte[] tarr=nb.getTexIdsArray(); int nidx=ns[i].lz*w.S+ns[i].lx; if(tarr[nidx]!=texId){ tarr[nidx]=texId; nb.setDirty(true); }
      }
    }
    return changed;
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
}
