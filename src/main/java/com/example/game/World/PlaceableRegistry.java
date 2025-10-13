package com.example.game.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Global registry of placeable objects keyed by a stable ID. */
public final class PlaceableRegistry {
  private static final ConcurrentHashMap<Long, Placeable> map = new ConcurrentHashMap<>();
  private static final AtomicLong idGen = new AtomicLong(System.currentTimeMillis());

  private PlaceableRegistry() {}

  /** Generate a new unique ID. Use when creating new placeables. */
  public static long nextId() { return idGen.getAndIncrement(); }

  /** Ensure the generator is at least a value (e.g., after loading persisted ids). */
  public static void ensureAtLeast(long minExclusive) {
    long cur;
    do { cur = idGen.get(); if (cur >= minExclusive) return; } while (!idGen.compareAndSet(cur, minExclusive));
  }

  /** Register or update an entry. */
  public static void upsert(Placeable p) { if (p != null) map.put(p.getId(), p); }

  /** Remove an entry by id. */
  public static void remove(long id) { map.remove(id); }

  /** Remove an entry by instance. */
  public static void remove(Placeable p) { if (p != null) map.remove(p.getId()); }

  /** Snapshot list of all known placeables. */
  public static List<Placeable> snapshot() { return new ArrayList<>(map.values()); }
}
