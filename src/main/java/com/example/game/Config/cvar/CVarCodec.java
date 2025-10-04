package com.example.game.Config.cvar;

/** Parse/format strategy per CVar. */
public interface CVarCodec<T> {
  T parse(String s);
  String format(T v);

  static CVarCodec<Boolean> bool() {
    return new CVarCodec<Boolean>() {
      @Override public Boolean parse(String s) { return s == null ? Boolean.FALSE : Boolean.parseBoolean(s); }
      @Override public String format(Boolean v) { return String.valueOf(v != null && v); }
    };
  }
  static CVarCodec<Integer> int32() {
    return new CVarCodec<Integer>() {
      @Override public Integer parse(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }
      @Override public String format(Integer v) { return String.valueOf(v == null ? 0 : v); }
    };
  }
  static CVarCodec<Float> f32() {
    return new CVarCodec<Float>() {
      @Override public Float parse(String s) { try { return Float.parseFloat(s); } catch (Exception e) { return 0f; } }
      @Override public String format(Float v) { return String.valueOf(v == null ? 0f : v); }
    };
  }
  static CVarCodec<String> str() {
    return new CVarCodec<String>() {
      @Override public String parse(String s) { return s == null ? "" : s; }
      @Override public String format(String v) { return v == null ? "" : v; }
    };
  }
}
