package com.example.game.Config.cvar;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** A typed console/config variable with change listeners. */
public final class CVar<T> {
  private final String name;           // unique key, e.g., "editor.invertY"
  private final CVarType type;         // primitive type (for persistence/UI)
  private final CVarCodec<T> codec;    // parse/format
  private final T defaultValue;

  private T value;
  private final List<Consumer<T>> listeners = new ArrayList<>();

  CVar(String name, CVarType type, CVarCodec<T> codec, T defaultValue) {
    this.name = Objects.requireNonNull(name, "name");
    this.type = Objects.requireNonNull(type, "type");
    this.codec = Objects.requireNonNull(codec, "codec");
    this.defaultValue = defaultValue;
    this.value = defaultValue;
  }

  public String name() { return name; }
  public CVarType type() { return type; }
  public T get() { return value; }
  public T def() { return defaultValue; }

  public void set(T newValue) {
    if (!Objects.equals(this.value, newValue)) {
      this.value = newValue;
      // notify
      for (int i = 0; i < listeners.size(); i++) listeners.get(i).accept(newValue);
    }
  }

  public void onChanged(Consumer<T> listener) {
    if (listener != null) listeners.add(listener);
  }

  public String format() { return codec.format(value); }
  public T parse(String s) { return codec.parse(s); }
}
