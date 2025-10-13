package com.example.game.UI.Editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.example.game.UI.SkinFactory;
import com.example.game.World.Editor.LayerManager;
import com.example.game.World.Editor.EditorLayer;
import com.example.game.Config.cvar.CVars;
import com.example.game.UI.CVar.CVarUI;

public final class EditorHUD implements Disposable {
  public interface Callbacks {
    void onBack();
    void onSave();
    void onHourChanged(float hour);
    void onSunSizeChanged(float deg);
    void onWarmthChanged(float warmth);
    void onGodRaysChanged(float exposure);
    void onViewDistanceChanged(int radius);
    void onRefresh();
    void onOpenObjects();
    void onToggleOuterLod(boolean enabled);
    void onRenameSelectedPlaceable(String newName);
  }

  private final Stage stage = new Stage(new ScreenViewport());
  private final Skin skin = SkinFactory.create();
  private final Table root = new Table();
  private final Table tabsBar = new Table();
  private final Table leftPane = new Table();
  private final Table rightPane = new Table();

  private Label hourLabel, raysLabel, distanceLabel, fpsLabel;
  private Slider hourSlider, raysSlider, distanceSlider;
  private TextField selectedPlaceableField;
  private long lastSelectedId = Long.MIN_VALUE;
  private String lastSelectedName = null;

  public EditorHUD() {
    root.setFillParent(true);
    stage.addActor(root);
  }

  public Stage stage() { return stage; }
  public Skin skin() { return skin; }

  public void build(LayerManager layers, Callbacks cb, float initialHour, float initialWarmth, float initialSunSize,
                    float initialRays, int initialDist) {
    // --- top bar
    tabsBar.clear();
    tabsBar.defaults().pad(6);
    HorizontalGroup tabs = new HorizontalGroup(); tabs.space(6f);
    tabsBar.add(tabs).left().expandX();

    for (EditorLayer l : layers.all()) {
      TextButton b = new TextButton(l.displayName(), skin);
      tabs.addActor(b);
      b.addListener(SkinFactory.onClick(() -> {
        layers.setActive(l.id());
        rebuildLeftPane(layers);
      }));
    }

    fpsLabel = new Label("FPS: --", skin);
    tabsBar.add(fpsLabel).padRight(12).right();

    TextButton save = new TextButton("Save (Ctrl+S)", skin);
    save.addListener(SkinFactory.onClick(cb::onSave));
    tabsBar.add(save).right().padRight(6);

    TextButton back = new TextButton("Back", skin);
    back.addListener(SkinFactory.onClick(cb::onBack));
    tabsBar.add(back).right();

    // --- left tools (scroll)
    leftPane.clear(); leftPane.top().left().pad(8);
    ScrollPane leftScroll = new ScrollPane(leftPane, skin);
    leftScroll.setFadeScrollBars(false);
    leftScroll.setScrollingDisabled(true, false);
    leftScroll.setFlickScroll(false); // disable kinetic scroll/drag feel
    // Capture drag to prevent drag-based scrolling; wheel scroll still works
    leftScroll.addCaptureListener(new InputListener(){
      @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
        event.stop(); // consume drag so ScrollPane doesn't pan on drag
      }
    });
    // Only capture scroll when hovered: manage scroll focus explicitly
    leftScroll.addListener(new InputListener() {
      @Override public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
        stage.setScrollFocus(leftScroll);
      }
      @Override public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
        // Clear scroll focus when mouse leaves the scroll pane area
        if (stage.getScrollFocus() == leftScroll) stage.setScrollFocus(null);
      }
    });

    // --- right controls
    rightPane.clear(); rightPane.top().right().pad(8);
    buildRightControls(rightPane, cb, initialHour, initialWarmth, initialSunSize, initialRays, initialDist);

    // --- layout
    root.clear();
    root.add(tabsBar).growX().left(); root.row();
    root.add(leftScroll).width(360).growY().top().left();
    root.add().grow();
    root.add(rightPane).width(260).growY().top().right();

    rebuildLeftPane(layers);
  }

  public void rebuildLeftPane(LayerManager layers) {
    leftPane.clear();
    layers.buildUI(leftPane, skin);
  }

  private void buildRightControls(Table parent, Callbacks cb,
                                  float initialHour, float initialWarmth, float initialSunSize,float initialRays, int initialDist) {
    parent.add(new Label("Sky / Time", skin, "title")).left().row();

    hourLabel = new Label(formatHour(initialHour), skin);
    parent.add(hourLabel).left().padBottom(6).row();

    hourSlider = new Slider(0f, 24f, 0.05f, false, skin);
    hourSlider.setValue(initialHour);
    hourSlider.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        float h = hourSlider.getValue();
        hourLabel.setText(formatHour(h));
        cb.onHourChanged(h);
      }
    });
    parent.add(hourSlider).width(240).height(32).padBottom(8).row();

    final Label sizeLbl = new Label(String.format("Sun Size: %.2f°", initialSunSize), skin);
    parent.add(sizeLbl).left().padTop(8).row();
    final Slider sizeSlider = new Slider(0.3f, 3.0f, 0.01f, false, skin);
    sizeSlider.setValue(initialSunSize);
    sizeSlider.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        float v = sizeSlider.getValue();
        sizeLbl.setText(String.format("Sun Size: %.2f°", v));
        cb.onSunSizeChanged(v);
      }
    });
    parent.add(sizeSlider).width(240).height(32).row();

    final Label warmLbl = new Label(String.format("Warmth: %.2f", initialWarmth), skin);
    parent.add(warmLbl).left().padTop(8).row();
    final Slider warmSlider = new Slider(0f, 1f, 0.01f, false, skin);
    warmSlider.setValue(initialWarmth);
    warmSlider.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        float v = warmSlider.getValue();
        warmLbl.setText(String.format("Warmth: %.2f", v));
        cb.onWarmthChanged(v);
      }
    });
    parent.add(warmSlider).width(240).height(32).row();

    raysLabel = new Label(String.format("God Rays: %.2f", initialRays), skin);
    parent.add(raysLabel).left().padTop(8).row();
    raysSlider = new Slider(0f, 1.5f, 0.01f, false, skin);
    raysSlider.setValue(initialRays);
    raysSlider.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        float v = raysSlider.getValue();
        raysLabel.setText(String.format("God Rays: %.2f", v));
        cb.onGodRaysChanged(v);
      }
    });
    parent.add(raysSlider).width(240).height(32).row();

    distanceLabel = new Label("Render Distance: " + initialDist, skin);
    parent.add(distanceLabel).left().padTop(8).row();
    distanceSlider = new Slider(0f, 100f, 1f, false, skin);
    distanceSlider.setValue(initialDist);
    distanceSlider.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        int v = (int)distanceSlider.getValue();
        distanceLabel.setText("Render Distance: " + v);
        cb.onViewDistanceChanged(v);
      }
    });
    parent.add(distanceSlider).width(240).height(32).row();

    // --- Debug/Overlays ---
    parent.add(new Label("Debug / Overlays", skin, "title")).left().padTop(12).row();
    // Wireframe toggle
    CheckBox wire = CVarUI.checkbox("Wireframe terrain", skin, CVars.RENDER_TERRAIN_WIREFRAME);
    parent.add(wire).left().padTop(4).row();
    // Chunk grid toggle
    CheckBox grid = CVarUI.checkbox("Show chunk grid", skin, CVars.RENDER_TERRAIN_CHUNK_GRID);
    parent.add(grid).left().padTop(4).row();
    // Low-LOD outer terrain toggle (CVar-bound)
    final CheckBox lodOuter = CVarUI.checkbox("Show low-LOD outer terrain", skin, CVars.RENDER_SHOW_OUTER_LOD);
    lodOuter.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ cb.onToggleOuterLod(lodOuter.isChecked()); }});
    parent.add(lodOuter).left().padTop(4).row();
    // Refresh button
    TextButton refresh = new TextButton("Refresh", skin);
    refresh.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ cb.onRefresh(); }});
    parent.add(refresh).left().padTop(6).row();

    // Objects browser button
    TextButton objects = new TextButton("Objects...", skin);
    objects.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ cb.onOpenObjects(); }});
    // Selected placeable rename field next to objects
    Table objRow = new Table();
    objRow.add(objects).left().padRight(6);
    selectedPlaceableField = new TextField("", skin);
    selectedPlaceableField.setMessageText("Selected name...");
    selectedPlaceableField.setTextFieldListener((textField, c) -> {
      if (c == '\n' || c == '\r') {
        cb.onRenameSelectedPlaceable(selectedPlaceableField.getText());
      }
    });
    selectedPlaceableField.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){
      // Commit on change as well; EditorScreen will no-op if unchanged/invalid
      cb.onRenameSelectedPlaceable(selectedPlaceableField.getText());
    }});
    objRow.add(selectedPlaceableField).width(180);
    parent.add(objRow).left().padTop(4).row();
  }

  public void setFps(int fps) { if (fpsLabel != null) fpsLabel.setText("FPS: " + fps); }

  public void actAndDraw(float dt) {
    stage.act(dt);
    stage.draw();
    // Sync selected placeable name field with current selection
    com.example.game.World.Placeable sel = com.example.game.World.Editor.EditorContext.getSelectedPlaceable();
    long id = (sel == null ? Long.MIN_VALUE : sel.getId());
    String name = (sel == null ? "" : (sel.getName()==null?"":sel.getName()));
    if (id != lastSelectedId || (name != null && !name.equals(lastSelectedName))) {
      if (selectedPlaceableField != null) selectedPlaceableField.setText(name);
      lastSelectedId = id; lastSelectedName = name;
    }
    if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
      // convenience: pretend the "Save" button was pressed
      for (Actor a : tabsBar.getChildren()) {
        // noop; leave actual save to EditorScreen which handles F5 separately if you want
      }
    }
  }

  @Override public void dispose() { stage.dispose(); skin.dispose(); }

  private static String formatHour(float h) {
    int hh = ((int)h) % 24;
    int mm = (int)((h - (int)h) * 60f + 0.5f);
    if (mm >= 60) { mm -= 60; hh = (hh + 1) % 24; }
    return String.format("Hour: %02d:%02d", hh, mm);
  }
}
