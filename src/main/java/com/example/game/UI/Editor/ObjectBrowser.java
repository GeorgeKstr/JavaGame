package com.example.game.UI.Editor;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.math.Vector3;
import com.example.game.World.Placeable;
import com.example.game.World.PlaceableRegistry;

import java.util.ArrayList;
import java.util.function.Consumer;

/** Simple modal dialog that lists Placeable objects and returns the selected one via callback. */
public final class ObjectBrowser extends Dialog {
  private final java.util.List<Placeable> items;
  private final List<String> listUI;
  private Consumer<Placeable> onChosen;

  public ObjectBrowser(String title, Skin skin, java.util.List<Placeable> items, Consumer<Placeable> onChosen) {
    super(title, skin);
    this.items = new ArrayList<>(items != null ? items : java.util.Collections.emptyList());
    this.onChosen = onChosen;
    this.listUI = new List<>(skin);

    rebuildFromRegistry();

    Table content = getContentTable();
    content.defaults().left().pad(6);
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
  }

  private void chooseAndClose() {
    int idx = listUI.getSelectedIndex();
    if (idx >= 0 && idx < items.size() && onChosen != null) {
      onChosen.accept(items.get(idx));
    }
    hide();
  }

  private void rebuildFromRegistry() {
    this.items.clear();
    this.items.addAll(PlaceableRegistry.snapshot());
    java.util.ArrayList<String> labels = new java.util.ArrayList<>();
    for (Placeable p : this.items) {
      Vector3 v = p.getPosition();
      labels.add(String.format("[%d] %s  (%.1f, %.1f, %.1f)", p.getId(), safeName(p.getName()), v.x, v.y, v.z));
    }
    listUI.setItems(labels.toArray(new String[0]));
    if (labels.size() > 0) listUI.setSelectedIndex(0);
  }

  private static String safeName(String n) { return (n == null || n.isEmpty()) ? "(unnamed)" : n; }

  public static void show(Stage stage, Skin skin, java.util.List<Placeable> items, Consumer<Placeable> onChosen) {
    ObjectBrowser ob = new ObjectBrowser("Placed Objects", skin, items, onChosen);
    ob.show(stage);
  }
}
