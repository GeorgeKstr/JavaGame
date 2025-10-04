package com.example.game.Config.cvar;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/** Generic persistence of all CVars in a registry using LibGDX Preferences. */
public final class PrefsCVarStore {
  private static final String PREFS = "game_cvars";

  public static void loadAll(CVarRegistry reg) {
    if (reg == null || Gdx.app == null) return;
    Preferences p = Gdx.app.getPreferences(PREFS);
    for (var e : reg.all().entrySet()) {
      CVar<?> v = e.getValue();
      String stored = p.getString(v.name(), null);
      if (stored != null && !stored.isEmpty()) {
        applyParsed(v, stored);
      } else {
        // keep default
      }
    }
  }

  public static void saveAll(CVarRegistry reg) {
    if (reg == null || Gdx.app == null) return;
    Preferences p = Gdx.app.getPreferences(PREFS);
    for (var e : reg.all().entrySet()) {
      CVar<?> v = e.getValue();
      p.putString(v.name(), v.format());
    }
    p.flush();
  }

  @SuppressWarnings({"rawtypes","unchecked"})
  private static <T> void applyParsed(CVar v, String s) {
    try {
      Object parsed = v.parse(s);
      v.set(parsed);
    } catch (Exception ex) {
      // ignore parse error; keep current
    }
  }
}
