package com.example.game.World;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

/** Axis-Aligned Bounding Box implementation. Immutable min/max references but mutable contents. */
public final class AABB implements BoundingBox {
  public final Vector3 min = new Vector3();
  public final Vector3 max = new Vector3();

  public AABB() {}
  public AABB(Vector3 min, Vector3 max) { this.min.set(min); this.max.set(max); }
  public AABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    this.min.set(minX, minY, minZ); this.max.set(maxX, maxY, maxZ);
  }

  public AABB set(Vector3 min, Vector3 max) { this.min.set(min); this.max.set(max); return this; }
  public AABB set(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    this.min.set(minX, minY, minZ); this.max.set(maxX, maxY, maxZ); return this;
  }

  public static AABB fromCenterHalfExtents(Vector3 c, float hx, float hy, float hz) {
    return new AABB(c.x - hx, c.y - hy, c.z - hz, c.x + hx, c.y + hy, c.z + hz);
  }

  @Override
  public boolean contains(float x, float y, float z) {
    return x >= min.x && x <= max.x && y >= min.y && y <= max.y && z >= min.z && z <= max.z;
  }

  @Override
  public float intersectRayT(Ray r) {
    // Standard slab intersection. Returns entry t if hit, else -1.
    float tmin = (min.x - r.origin.x) / r.direction.x;
    float tmax = (max.x - r.origin.x) / r.direction.x;
    if (tmin > tmax) { float tmp = tmin; tmin = tmax; tmax = tmp; }

    float tymin = (min.y - r.origin.y) / r.direction.y;
    float tymax = (max.y - r.origin.y) / r.direction.y;
    if (tymin > tymax) { float tmp = tymin; tymin = tymax; tymax = tmp; }

    if (tmin > tymax || tymin > tmax) return -1f;
    if (tymin > tmin) tmin = tymin;
    if (tymax < tmax) tmax = tymax;

    float tzmin = (min.z - r.origin.z) / r.direction.z;
    float tzmax = (max.z - r.origin.z) / r.direction.z;
    if (tzmin > tzmax) { float tmp = tzmin; tzmin = tzmax; tzmax = tmp; }

    if (tmin > tzmax || tzmin > tmax) return -1f;
    if (tzmin > tmin) tmin = tzmin;
    if (tzmax < tmax) tmax = tzmax;

    return tmax < 0f ? -1f : (tmin >= 0f ? tmin : tmax);
  }
}
