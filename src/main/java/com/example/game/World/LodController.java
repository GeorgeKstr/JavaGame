package com.example.game.World;

final class LodController {
  private final TerrainWorld w;
  LodController(TerrainWorld w){ this.w = w; }

  int selectLod(float camX, float camZ, TerrainChunk c) {
    float size = (c.getSize()-1) * c.getTile();
    float cx = c.getOriginX() + size * 0.5f;
    float cz = c.getOriginZ() + size * 0.5f;
    float dx = camX - cx, dz = camZ - cz;
    float dist = (float)Math.sqrt(dx*dx + dz*dz);

    int desired = 0;
    int maxCheck = Math.min(w.MAX_LOD, w.lodDistances.length);
    for (int i = 0; i < maxCheck; i++) {
      if (dist > w.lodDistances[i]) desired = i + 1; else break;
    }

    int k = TerrainWorld.key(Math.round(c.getOriginX() / ((w.S-1)*w.tile)), Math.round(c.getOriginZ() / ((w.S-1)*w.tile)));
    Integer prev = w.lastLod.get(k);
    if (prev != null) {
      if (desired > prev) {
        float back = (desired-1 < w.lodDistances.length ? w.lodDistances[desired-1] : w.lodDistances[w.lodDistances.length-1]);
        if (dist < back + w.hysteresis) desired = prev;
      } else if (desired < prev) {
        float next = (desired < w.lodDistances.length ? w.lodDistances[desired] : Float.MAX_VALUE);
        if (dist > next - w.hysteresis) desired = prev;
      }
    }
    w.lastLod.put(k, desired);
    return Math.min(desired, w.MAX_LOD);
  }
}
