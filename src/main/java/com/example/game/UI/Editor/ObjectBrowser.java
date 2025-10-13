package com.example.game.UI.Editor;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.math.Vector3;
import com.example.game.World.Placeable;
import com.example.game.World.PlaceableRegistry;

import java.util.ArrayList;
import java.util.function.Consumer;

/** Simple modal dialog that lists Placeable objects and returns the selected one via callback. */
public final class ObjectBrowser extends Dialog {
  private final java.util.List<Placeable> items;
  private final java.util.List<Placeable> filtered;
  private final List<String> listUI;
  private TextField search;
  private Consumer<Placeable> onChosen;

  public ObjectBrowser(String title, Skin skin, java.util.List<Placeable> items, Consumer<Placeable> onChosen) {
    super(title, skin);
    this.items = new ArrayList<>(items != null ? items : java.util.Collections.emptyList());
    this.filtered = new ArrayList<>(this.items);
    this.onChosen = onChosen;
    this.listUI = new List<>(skin);

    rebuildFromRegistry();

    Table content = getContentTable();
    content.defaults().left().pad(6);
    // Search bar
    Table searchRow = new Table();
    search = new TextField("", skin);
    search.setMessageText("Search objects (fuzzy)...");
    search.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      applyFilter(search.getText());
    }});
    search.setTextFieldListener((tf, c) -> {
      if (c == '\n' || c == '\r') chooseTopIfAny();
    });
    searchRow.add(search).width(400);
    content.add(searchRow).growX().row();

    ScrollPane sp = new ScrollPane(listUI, skin);
    sp.setFadeScrollBars(false);
    content.add(sp).width(420).height(260).row();

    Table buttons = getButtonTable();
    TextButton jump = new TextButton("Jump", skin);
    TextButton refresh = new TextButton("Refresh", skin);
    TextButton close = new TextButton("Close", skin);
    jump.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ chooseAndClose(); }});
    refresh.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ rebuildFromRegistry(); }});
    close.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ hide(); }});
    buttons.add(jump).pad(6);
    buttons.add(refresh).pad(6);
    buttons.add(close).pad(6);

    // Selection change does not auto-close; use Jump button to confirm

    setMovable(true);
    setResizable(false);
    getTitleLabel().setAlignment(Align.left);

    // Global Enter key inside dialog should jump to top result
    addListener(new InputListener(){
      @Override public boolean keyDown(InputEvent event, int keycode) {
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
          chooseTopIfAny();
          return true;
        }
        return false;
      }
    });
  }

  private void chooseAndClose() {
    int idx = listUI.getSelectedIndex();
    if (idx >= 0 && idx < filtered.size() && onChosen != null) {
      onChosen.accept(filtered.get(idx));
    }
    hide();
  }

  private void rebuildFromRegistry() {
    this.items.clear();
    this.items.addAll(PlaceableRegistry.snapshot());
    applyFilter(search == null ? "" : search.getText());
  }

  private static String safeName(String n) { return (n == null || n.isEmpty()) ? "(unnamed)" : n; }

  public static void show(Stage stage, Skin skin, java.util.List<Placeable> items, Consumer<Placeable> onChosen) {
    ObjectBrowser ob = new ObjectBrowser("Placed Objects", skin, items, onChosen);
    ob.show(stage);
    // Focus search when opened so typing starts filtering immediately
    stage.setKeyboardFocus(ob.search);
    stage.setScrollFocus(ob.search);
  }

  // ----- Filtering (fuzzy) -----
  private void applyFilter(String query) {
    String q = (query == null) ? "" : query.trim();
    // Recompute filtered list with fuzzy score; keep stable order for equal scores
    java.util.ArrayList<Placeable> base = new java.util.ArrayList<>(this.items);
    if (q.isEmpty()) {
      this.filtered.clear();
      this.filtered.addAll(base);
    } else {
      String qlc = q.toLowerCase();
      // Score each item
      java.util.ArrayList<ItemScore> scored = new java.util.ArrayList<>(base.size());
      for (Placeable p : base) {
        String name = safeName(p.getName());
        int score = fuzzyScore(name.toLowerCase(), qlc);
        if (score > 0) scored.add(new ItemScore(p, score));
      }
      // Sort: higher score first
      scored.sort((a,b) -> Integer.compare(b.score, a.score));
      this.filtered.clear();
      for (ItemScore is : scored) this.filtered.add(is.p);
    }
    // Build labels for UI
    java.util.ArrayList<String> labels = new java.util.ArrayList<>();
    for (Placeable p : this.filtered) {
      Vector3 v = p.getPosition();
      labels.add(String.format("[%d] %s  (%.1f, %.1f, %.1f)", p.getId(), safeName(p.getName()), v.x, v.y, v.z));
    }
    listUI.setItems(labels.toArray(new String[0]));
    if (labels.size() > 0) listUI.setSelectedIndex(0);
  }

  private static final class ItemScore { final Placeable p; final int score; ItemScore(Placeable p,int s){this.p=p;this.score=s;} }

  // Simple fuzzy scoring: subsequence match with bonuses for adjacency and start-match.
  // Returns 0 when no subsequence match; higher is better.
  private static int fuzzyScore(String text, String query) {
    if (query.isEmpty()) return 1;
    int ti = 0, qi = 0, score = 0, streak = 0;
    final int TL = text.length(), QL = query.length();
    while (ti < TL && qi < QL) {
      char tc = text.charAt(ti);
      char qc = query.charAt(qi);
      if (tc == qc) {
        // base hit
        score += 5;
        // adjacency bonus
        if (streak > 0) score += 10;
        // start or word boundary bonus
        if (ti == 0 || text.charAt(Math.max(0, ti-1)) == ' ') score += 8;
        streak++;
        qi++;
      } else {
        streak = 0;
      }
      ti++;
    }
    return (qi == QL) ? score : 0;
  }

  private void chooseTopIfAny() {
    if (filtered == null || filtered.isEmpty()) return;
    listUI.setSelectedIndex(0);
    chooseAndClose();
  }
}
