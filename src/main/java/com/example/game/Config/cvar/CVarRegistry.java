package com.example.game.Config.cvar;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Global-ish registry for CVars. You can have multiple registries if desired. */
public final class CVarRegistry {
  private final Map<String, CVar<?>> map = new HashMap<>();

  @SuppressWarnings("unchecked")
  public <T> CVar<T> register(CVar<T> v) {
    if (v == null) return null;
    CVar<?> prev = map.putIfAbsent(v.name(), v);
    return prev == null ? v : (CVar<T>) prev; // return existing if duplicate
  }

  @SuppressWarnings("unchecked")
  public <T> CVar<T> get(String name) { return (CVar<T>) map.get(name); }
  public Map<String, CVar<?>> all() { return Collections.unmodifiableMap(map); }

  // Convenience creators that also register
  public CVar<Boolean> defineBool(String name, boolean def) {
    return register(new CVar<>(name, CVarType.BOOL, CVarCodec.bool(), def));
  }
  public CVar<Integer> defineInt(String name, int def) {
    return register(new CVar<>(name, CVarType.INT, CVarCodec.int32(), def));
  }
  public CVar<Float> defineFloat(String name, float def) {
    return register(new CVar<>(name, CVarType.FLOAT, CVarCodec.f32(), def));
  }
  public CVar<String> defineString(String name, String def) {
    return register(new CVar<>(name, CVarType.STRING, CVarCodec.str(), def));
  }

  // Enum support via custom codec from/to string names
  public <E extends Enum<E>> CVar<E> defineEnum(String name, E def, Class<E> enumClass) {
    CVarCodec<E> codec = new CVarCodec<E>() {
      @Override public E parse(String s) {
        if (s == null) return def;
        try { return Enum.valueOf(enumClass, s); } catch (Exception e) { return def; }
      }
      @Override public String format(E v) { return v == null ? def.name() : v.name(); }
    };
    return register(new CVar<>(name, CVarType.ENUM, codec, def));
  }
}
