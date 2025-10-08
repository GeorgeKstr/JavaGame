package com.example.game.World.Editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.example.game.World.MapResource;
import com.example.game.World.TerrainSystem;

import java.util.*;
import java.util.function.Consumer;

/**
 * Modal texture browser for textures under "textures/ground/" (supports subfolders).
 * - Lists folders in a tree (derived from manifest paths).
 * - Shows thumbnails for textures in the selected folder.
 * - Import into the current folder.
 * - Move a selected texture to another folder.
 * - Acts as a selector: invokes onSelect callback with the chosen TextureRef.
 */
public final class TextureBrowser extends Window {
  private static final int THUMB = 72;
  private static final int COLS = 4;

  private final TerrainSystem terrain;
  private final MapResource map;
  private final Skin skin;
  private final Consumer<MapResource.TextureRef> onSelect;

  private final com.badlogic.gdx.scenes.scene2d.ui.List<String> folderList;
  private final Table grid;
  private final ScrollPane gridScroll;
  private final Label pathLabel;
  private final Array<Texture> tempThumbs = new Array<>(false, 32);
  private final ButtonGroup<Button> buttonGroup = new ButtonGroup<>();

  private String currentFolder = ""; // subpath under textures/ground ("" = root)
  private MapResource.TextureRef selected;

  public TextureBrowser(Skin skin, TerrainSystem terrain, Consumer<MapResource.TextureRef> onSelect) {
    this(skin, terrain, null, onSelect);
  }

  public TextureBrowser(Skin skin, TerrainSystem terrain, String initialFolder, Consumer<MapResource.TextureRef> onSelect) {
    super("Texture Browser", skin);
    this.skin = skin;
    this.terrain = terrain;
    this.map = terrain.getMap();
    this.onSelect = onSelect;
    if (initialFolder != null) this.currentFolder = normalizeFolder(initialFolder);

    setModal(true);
    setResizable(true);
    defaults().pad(6);

    // Left: folder list
    folderList = new com.badlogic.gdx.scenes.scene2d.ui.List<>(skin);
    refreshFolderList();
    ScrollPane treeScroll = new ScrollPane(folderList, skin);
    treeScroll.setFadeScrollBars(false);
    treeScroll.setScrollingDisabled(true, false);

    // Right: toolbar + grid
    Table right = new Table();
    right.defaults().left().padBottom(6);

    Table bar = new Table();
    pathLabel = new Label(pathString(), skin);
    TextButton importBtn = new TextButton("Import...", skin);
    TextButton newFolderBtn = new TextButton("New Folder", skin);
    TextButton deleteBtn = new TextButton("Delete", skin);
    TextButton selectBtn = new TextButton("Select", skin);
    bar.add(pathLabel).expandX().fillX().padRight(8);
    bar.add(importBtn).padRight(6);
    bar.add(newFolderBtn).padRight(6);
    bar.add(deleteBtn).padRight(6);
    bar.add(selectBtn);
    right.add(bar).growX().row();

    grid = new Table();
    grid.defaults().pad(4);
    gridScroll = new ScrollPane(grid, skin);
    gridScroll.setFadeScrollBars(false);
    gridScroll.setScrollingDisabled(true, false);
    right.add(gridScroll).width(640).height(420).row();

    // Layout without SplitPane: two columns
    Table content = new Table();
    content.add(treeScroll).width(240).top().padRight(8);
    content.add(right).grow();
    add(content).width(900).height(520).row();

    // Close button
    TextButton close = new TextButton("Close", skin);
    add(close).right();

    // Listeners
    folderList.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        String f = folderList.getSelected();
        if (f != null) {
          currentFolder = f.equals("<root>") ? "" : f;
          pathLabel.setText(pathString());
          selected = null;
          rebuildGrid();
        }
      }
    });

    importBtn.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        new Thread(() -> {
          int insertedId = terrain.importGroundTextureDesktopInto(currentFolder, 1.0f);
          if (insertedId >= 0) {
            Gdx.app.postRunnable(() -> {
              terrain.reloadGroundTextures();
              map.saveInPlaceDeltaIncludingStaged(java.util.Collections.emptyMap());
              refreshFolderList();
              rebuildGrid();
            });
          }
        }, "ImportTextureThread").start();
      }
    });
    newFolderBtn.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        TextInputDialog.show(getStage(), skin, "Create Folder", "Subfolder under textures/ground:", currentFolder, input -> {
          String nf = normalizeFolder(input);
          // No-op if exists; folders are implicit from texture paths
          currentFolder = nf;
          pathLabel.setText(pathString());
          refreshFolderList();
          rebuildGrid();
        });
      }
    });
    deleteBtn.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        if (selected == null) return;
        int delId = selected.id;
        map.removeTextureById(delId);
        // Persist immediately and reload GL array
        map.saveInPlaceDeltaIncludingStaged(java.util.Collections.emptyMap());
        terrain.reloadGroundTextures();
        // Choose a sensible fallback: first texture in current folder
        java.util.List<MapResource.TextureRef> list = map.listTexturesInFolder(currentFolder);
        selected = list.isEmpty() ? null : list.get(0);
        if (onSelect != null && selected != null) onSelect.accept(selected);
        refreshFolderList();
        rebuildGrid();
      }
    });
    selectBtn.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, Actor actor) {
        if (onSelect != null && selected != null) onSelect.accept(selected);
        TextureBrowser.this.remove();
      }
    });
    close.addListener(new ChangeListener() { @Override public void changed(ChangeEvent event, Actor actor) { TextureBrowser.this.remove(); }});

    // Initial content
    rebuildGrid();
  }

  private String pathString() { return "textures/" + (currentFolder.isEmpty() ? "" : currentFolder + "/"); }

  private void refreshFolderList() {
    Set<String> folders = map.listTextureFolders();
    Array<String> items = new Array<>();
    items.add("<root>");
    for (String f : folders) if (!f.isEmpty()) items.add(f);
    folderList.setItems(items);
    folderList.setSelected(currentFolder.isEmpty() ? "<root>" : currentFolder);
  }

  private void rebuildGrid() {
    grid.clearChildren();
    for (Texture t : tempThumbs) t.dispose();
    tempThumbs.clear();

    java.util.List<MapResource.TextureRef> list = map.listTexturesInFolder(currentFolder);
    if (list.isEmpty()) { grid.add(new Label("(empty)", skin)).left(); return; }

    buttonGroup.clear();
    buttonGroup.setMinCheckCount(0);
    buttonGroup.setMaxCheckCount(1);

    int col = 0; Table row = new Table();
    for (MapResource.TextureRef tr : list) {
      Texture thumb = buildThumb(tr.file);
      tempThumbs.add(thumb);
      Image img = new Image(thumb); img.setScaling(Scaling.fit); img.setAlign(Align.center);
      Label name = new Label(shortName(tr), skin); name.setAlignment(Align.center);

      Table cell = new Table();
      cell.add(img).size(THUMB, THUMB).row();
      cell.add(name).width(THUMB);

      Button btn = new Button(skin, "toggle");
      btn.add(cell).pad(4);
      btn.setChecked(selected != null && selected.id == tr.id);
      buttonGroup.add(btn);
      btn.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (((Button)actor).isChecked()) selected = tr; }});
      btn.addListener(new ClickListener(){ @Override public void clicked(InputEvent event, float x, float y){
        ((Button)event.getListenerActor()).setChecked(true);
        selected = tr;
        if (onSelect != null && selected != null) onSelect.accept(selected);
        // Do not close on click; allow continued browsing
      } });

      row.add(btn);
      col++;
      if (col >= COLS) { grid.add(row).left().row(); row = new Table(); col = 0; }
    }
    if (col > 0) grid.add(row).left().row();
    gridScroll.layout();
  }

  private String shortName(MapResource.TextureRef tr) {
    String label = (tr.name != null && !tr.name.isEmpty()) ? tr.name : tr.file;
    int slash = Math.max(label.lastIndexOf('/'), label.lastIndexOf('\\'));
    return (slash >= 0 ? label.substring(slash + 1) : label);
  }

  private Texture buildThumb(String pathInZip) {
    byte[] bytes = map.getTextureBytes(pathInZip);
    if (bytes == null || bytes.length == 0) {
      Pixmap pm = new Pixmap(THUMB, THUMB, Pixmap.Format.RGBA8888);
      pm.setColor(0.15f,0.15f,0.15f,1f); pm.fill();
      Texture t = new Texture(pm); pm.dispose(); return t;
    }
    Pixmap src = new Pixmap(bytes, 0, bytes.length);
    Pixmap scaled = new Pixmap(THUMB, THUMB, Pixmap.Format.RGBA8888);
    scaled.drawPixmap(src, 0, 0, src.getWidth(), src.getHeight(), 0, 0, THUMB, THUMB);
    src.dispose();
    Texture t = new Texture(scaled);
    t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    scaled.dispose();
    return t;
  }

  private static String normalizeFolder(String input) {
    if (input == null) return "";
    String s = input.trim().replace('\\', '/');
    if (s.startsWith("/")) s = s.substring(1);
    if (s.endsWith("/")) s = s.substring(0, s.length()-1);
    return s;
  }

  public void show(Stage stage) {
    pack();
    setPosition(Math.round((stage.getWidth()-getWidth())*0.5f), Math.round((stage.getHeight()-getHeight())*0.5f));
    stage.addActor(this);
  }

  @Override public boolean remove() {
    boolean r = super.remove();
    for (Texture t : tempThumbs) t.dispose();
    tempThumbs.clear();
    return r;
  }

  // Simple text input popup
  private static final class TextInputDialog extends Window {
    public interface Listener { void onDone(String text); }
    public static void show(Stage stage, Skin skin, String title, String label, String initial, Listener cb) {
      TextInputDialog w = new TextInputDialog(skin, title, label, initial, cb);
      w.pack();
      w.setPosition(Math.round((stage.getWidth()-w.getWidth())*0.5f), Math.round((stage.getHeight()-w.getHeight())*0.5f));
      stage.addActor(w);
    }
    private TextInputDialog(Skin skin, String title, String label, String initial, Listener cb) {
      super(title, skin);
      defaults().pad(6);
      add(new Label(label, skin)).left().row();
      final TextField tf = new TextField(initial==null?"":initial, skin);
      add(tf).width(320).row();
      Table row = new Table();
      TextButton ok = new TextButton("OK", skin);
      TextButton cancel = new TextButton("Cancel", skin);
      row.add(ok).padRight(6); row.add(cancel);
      add(row).right().row();
      ok.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ if (cb!=null) cb.onDone(tf.getText()); remove(); }});
      cancel.addListener(new ChangeListener(){ @Override public void changed(ChangeEvent event, Actor actor){ remove(); }});
      setModal(true);
    }
  }
}
