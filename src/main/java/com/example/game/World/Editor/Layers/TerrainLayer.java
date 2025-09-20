package com.example.game.World.Editor.Layers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.example.game.UI.SkinFactory;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.*;
import com.example.game.World.MapResource;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class TerrainLayer implements EditorLayer {
    private final TerrainSystem terrain;
    private final BrushOverlay overlay = new BrushOverlay(64);
    private final EditorState state = new EditorState();
    private final Skin skin;

    private Label brushRadiusValueLabel;

    // Browser UI
    private VerticalGroup texturesColumn; // holds rows (Tables)
    private ScrollPane texturesScroll;
    private TextButton importButton;
    private Label textureInfoLabel;

    private final ArrayList<Texture> disposableThumbs = new ArrayList<>();
    private final ButtonGroup<Button> textureButtons = new ButtonGroup<>();

    // Grid sizing tuned for 320px scroll area
    private static final int COLS = 4;
    private static final float CELL_W = 72f;   // 4 * 72 + gaps ≈ 320
    private static final float CELL_H = 88f;
    private static final float CELL_PAD = 2f;
    private static final float ROW_SPACE = 6f;

    public TerrainLayer(TerrainSystem terrain, Skin skin) {
        this.terrain = terrain;
        this.skin = skin;

        textureButtons.setMinCheckCount(1);
        textureButtons.setMaxCheckCount(1);
        textureButtons.setUncheckLast(true);
    }

    @Override public String id() { return "terrain"; }
    @Override public String displayName() { return "Terrain"; }

    @Override public void onActivate() {}
    @Override public void onDeactivate() { disposeThumbs(); }

    @Override public void update(float dt) {
        overlay.update(EditorContext.cam(), terrain, state.radius(), terrain.getTileSize());
    }

    @Override public void render3D(Camera cam) { overlay.draw(cam); }

    @Override
    public void buildUI(Table left, Skin skin) {
        left.defaults().pad(6).left().top();

        left.add(new Label("Terrain Tools", skin, "title")).left().row();

        Table toolRow = new Table(); toolRow.defaults().padRight(6);
        TextButton bRaise = new TextButton("1  Raise", skin);
        TextButton bLower = new TextButton("2  Lower", skin);
        TextButton bPaint = new TextButton("3  Paint", skin);
        toolRow.add(bRaise); toolRow.add(bLower); toolRow.add(bPaint);
        left.add(toolRow).left().row();

        bRaise.addListener(SkinFactory.onClick(() -> state.setTool(EditorState.Tool.RAISE)));
        bLower.addListener(SkinFactory.onClick(() -> state.setTool(EditorState.Tool.LOWER)));
        bPaint.addListener(SkinFactory.onClick(() -> state.setTool(EditorState.Tool.PAINT)));

        left.add(new Label("Brush Radius", skin)).left().row();
        left.add(buildBrushRadiusStepper()).left().row();

        left.add(new Label("Height Δ", skin)).left().row();
        left.add(stepper(() -> (int)state.delta(), v -> state.setDelta((short)Math.max(1, v)))).left().row();

        left.add(new Label("Textures", skin)).left().row();
        buildTextureBrowserUI(left);
        left.row();

        Label help = new Label(
            "LMB: apply • RMB: pan • MMB: orbit\nCtrl+Wheel: brush size • F5: Save",
            skin
        );
        help.setWrap(true);
        help.setAlignment(Align.left);
        left.add(help).width(320).left().row();
    }

    private Actor buildBrushRadiusStepper() {
        Table t = new Table(); t.defaults().padRight(6);
        TextButton minus = new TextButton("-", skin);
        brushRadiusValueLabel = new Label(String.valueOf(state.radius()), skin);
        TextButton plus = new TextButton("+", skin);

        minus.addListener(SkinFactory.onClick(() -> {
            state.setRadius(Math.max(1, state.radius() - 1));
            brushRadiusValueLabel.setText(String.valueOf(state.radius()));
        }));
        plus.addListener(SkinFactory.onClick(() -> {
            state.setRadius(state.radius() + 1);
            brushRadiusValueLabel.setText(String.valueOf(state.radius()));
        }));

        t.add(minus);
        t.add(brushRadiusValueLabel).width(52).center();
        t.add(plus);
        return t;
    }

    private Actor stepper(IntGetter get, IntSetter set) {
        Table t = new Table(); t.defaults().padRight(6);
        TextButton minus = new TextButton("-", skin);
        Label val = new Label(String.valueOf(get.get()), skin);
        TextButton plus = new TextButton("+", skin);
        minus.addListener(SkinFactory.onClick(() -> {
            set.set(Math.max(1, get.get() - 1));
            val.setText(String.valueOf(get.get()));
        }));
        plus.addListener(SkinFactory.onClick(() -> {
            set.set(get.get() + 1);
            val.setText(String.valueOf(get.get()));
        }));
        t.add(minus);
        t.add(val).width(52).center();
        t.add(plus);
        return t;
    }

    private interface IntGetter { int get(); }
    private interface IntSetter { void set(int v); }

    // =========================
    // Texture browser / import
    // =========================

    private void buildTextureBrowserUI(Table left) {
        disposeThumbs();

        texturesColumn = new VerticalGroup();
        texturesColumn.top().left();
        texturesColumn.space(ROW_SPACE);
        texturesColumn.columnAlign(Align.left);
        texturesColumn.setTouchable(Touchable.childrenOnly);

        rebuildTexturesColumn();

        texturesScroll = new ScrollPane(texturesColumn, skin);
        texturesScroll.setScrollingDisabled(true, false); // vertical-only
        texturesScroll.setForceScroll(false, true);
        texturesScroll.setSmoothScrolling(true);
        texturesScroll.setFadeScrollBars(false);
        texturesScroll.setOverscroll(false, false);

        left.add(texturesScroll).width(320).height(220).left().row();

        Table actions = new Table();
        TextButton importBtn = new TextButton("Import…", skin);
        importBtn.addListener(SkinFactory.onClick(this::onImportTexture));
        textureInfoLabel = new Label("", skin);
        textureInfoLabel.setWrap(true);

        actions.add(importBtn).left().padRight(8);
        actions.add(textureInfoLabel).width(200).left();
        left.add(actions).left().row();
    }

    /** Rebuilds the column of rows; each row is a Table with up to COLS cells. */
    private void rebuildTexturesColumn() {
        disposeThumbs();
        texturesColumn.clear();
        textureButtons.clear();

        MapResource map = getMapResource();
        if (map == null) {
            texturesColumn.addActor(new Label("No map loaded.", skin));
            textureButtons.setMinCheckCount(0);
            relayoutScroll();
            return;
        } else {
            textureButtons.setMinCheckCount(1);
        }

        var list = map.getManifest().groundTextures;
        final int currentId = Math.max(0, state.texId());
        boolean anySelected = false;

        Table currentRow = new Table();
        currentRow.left().top();

        int col = 0;
        for (int i = 0; i < list.size(); i++) {
            MapResource.TextureRef tr = list.get(i);

            // Build the toggle cell
            Button cell = buildTextureCell(map, tr);

            // Add to selection group
            textureButtons.add(cell);

            // Preselect if this is the currently active id
            if (tr.id == currentId) {
                cell.setChecked(true);
                anySelected = true;
                setSelectionInfo(tr);
            }

            currentRow.add(cell).size(CELL_W, CELL_H).pad(CELL_PAD);

            if (++col >= COLS) {
                // finalize this row
                texturesColumn.addActor(currentRow);
                // new row
                currentRow = new Table();
                currentRow.left().top();
                col = 0;
            }
        }

        // Add any remaining partial row
        if (col != 0 || list.isEmpty()) {
            if (list.isEmpty()) {
                Label none = new Label("No ground textures.\nClick “Import…” to add one.", skin);
                currentRow.add(none).left().top().colspan(COLS);
                textureButtons.setMinCheckCount(0);
            }
            texturesColumn.addActor(currentRow);
        }

        // Default to first if nothing matched
        if (!list.isEmpty() && !anySelected) {
            com.badlogic.gdx.utils.Array<Button> buttons = textureButtons.getButtons();
            if (buttons.size > 0) {
                Button first = buttons.first();
                first.setChecked(true);
                MapResource.TextureRef firstTr = list.get(0);
                state.setTexId(firstTr.id);
                setSelectionInfo(firstTr);
            }
        }

        relayoutScroll();
    }

    private Button buildTextureCell(MapResource map, MapResource.TextureRef tr) {
        Texture thumb = makeThumbnailFor(map, tr);
        disposableThumbs.add(thumb);

        Image img = new Image(new TextureRegionDrawable(new TextureRegion(thumb)));
        img.setScaling(Scaling.fit);

        Label name = new Label(displayName(tr), skin);
        name.setAlignment(Align.center);
        name.setEllipsis(true);
        name.setWrap(false);

        Table inner = new Table();
        inner.defaults().pad(2);
        inner.add(img).size(48, 48).bottom().row();
        inner.add(name).width(CELL_W - 8).center();

        Button b = new Button(skin, "toggle");
        b.add(inner).size(CELL_W, CELL_H);

        final int texId = tr.id;
        b.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (b.isChecked()) {
                    state.setTexId(texId);
                    setSelectionInfo(tr);
                }
            }
        });
        return b;
    }

    private void setSelectionInfo(MapResource.TextureRef tr) {
        if (textureInfoLabel != null) {
            textureInfoLabel.setText("Selected: " + displayName(tr) + " (id " + tr.id + ")");
        }
    }

    private void relayoutScroll() {
        if (texturesColumn != null) texturesColumn.invalidateHierarchy();
        if (texturesScroll != null) {
            texturesScroll.layout();
            texturesScroll.updateVisualScroll();
        }
    }

    private String displayName(MapResource.TextureRef tr) {
        if (tr.name != null && !tr.name.isEmpty()) return tr.name;
        String f = tr.file;
        int slash = f.lastIndexOf('/');
        return slash >= 0 ? f.substring(slash + 1) : f;
    }

    private Texture makeThumbnailFor(MapResource map, MapResource.TextureRef tr) {
        byte[] bytes = map.getTextureBytes(tr.file);
        if (bytes == null) {
            Pixmap pm = new Pixmap(1,1, Pixmap.Format.RGBA8888);
            pm.setColor(1,0,1,1); pm.fill();
            Texture t = new Texture(pm); pm.dispose(); return t;
        }
        Pixmap src = new Pixmap(bytes, 0, bytes.length);
        int tw = 64, th = 64;
        Pixmap thumb = new Pixmap(tw, th, Pixmap.Format.RGBA8888);
        thumb.drawPixmap(src, 0, 0, src.getWidth(), src.getHeight(), 0, 0, tw, th);
        src.dispose();
        Texture t = new Texture(thumb);
        thumb.dispose();
        return t;
    }

    private void disposeThumbs() {
        for (Texture t : disposableThumbs) if (t != null) t.dispose();
        disposableThumbs.clear();
    }

    private void onImportTexture() {
        if (Gdx.app.getType() != Application.ApplicationType.Desktop) {
            if (textureInfoLabel != null) textureInfoLabel.setText("Import is desktop-only.");
            return;
        }
        final String[] chosenPath = { null };
        try {
            if (EventQueue.isDispatchThread()) chosenPath[0] = openChooser();
            else EventQueue.invokeAndWait(() -> chosenPath[0] = openChooser());
        } catch (Exception ex) {
            if (textureInfoLabel != null) textureInfoLabel.setText("Chooser failed: " + ex.getMessage());
            return;
        }
        if (chosenPath[0] == null) return;

        Path p = Path.of(chosenPath[0]);
        byte[] data;
        try { data = Files.readAllBytes(p); }
        catch (IOException e) { if (textureInfoLabel != null) textureInfoLabel.setText("Read failed: " + e.getMessage()); return; }

        MapResource map = getMapResource();
        if (map == null) { if (textureInfoLabel != null) textureInfoLabel.setText("No map loaded."); return; }

        MapResource.TextureRef tr = map.importGroundTexture(p.getFileName().toString(), data);
        setSelectionInfo(tr);
        state.setTexId(tr.id);

        rebuildTexturesColumn();
    }

    private String openChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Ground Texture");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        chooser.setAcceptAllFileFilterUsed(true);
        int res = chooser.showOpenDialog(null);
        if (res == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            return chooser.getSelectedFile().getAbsolutePath();
        }
        return null;
    }

    private MapResource getMapResource() {
        try { return terrain.getMap(); }
        catch (Throwable t) { return null; }
    }

    // =========================
    // Input & painting
    // =========================

    @Override
    public boolean handleInput(EditorInputRouter.InputEvent e) {
        switch (e.type) {
            case KeyDown -> {
                if (e.keycode == Input.Keys.NUM_1) { state.setTool(EditorState.Tool.RAISE); return true; }
                if (e.keycode == Input.Keys.NUM_2) { state.setTool(EditorState.Tool.LOWER); return true; }
                if (e.keycode == Input.Keys.NUM_3) { state.setTool(EditorState.Tool.PAINT); return true; }
            }
            case MouseDown, MouseDrag -> {
                if (e.buttonLeft || (Gdx.input.isButtonPressed(Input.Buttons.LEFT)
                        && e.type == EditorInputRouter.InputEvent.Type.MouseDrag)) {
                    applyAtMouse();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean scrolled(float amountX, float amountY) {
        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) {
            int r = state.radius() + (amountY > 0 ? -1 : 1);
            r = Math.max(1, r);
            if (r != state.radius()) {
                state.setRadius(r);
                if (brushRadiusValueLabel != null) brushRadiusValueLabel.setText(String.valueOf(r));
            }
            return true;
        }
        return false;
    }

    private void applyAtMouse() {
        float mx = Gdx.input.getX(), my = Gdx.input.getY();
        var ray = EditorContext.cam().getPickRay(mx, my);
        var hit = BrushOverlay.rayIntersectTerrain(ray, terrain, 0.75f, 8);
        if (hit == null) return;

        int gx = Math.round(hit.x / terrain.getTileSize());
        int gz = Math.round(hit.z / terrain.getTileSize());
        boolean changed = false;
        switch (state.tool()) {
            case RAISE -> changed = terrain.editHeightsCircle(gx, gz, state.radius(), state.delta());
            case LOWER -> changed = terrain.editHeightsCircle(gx, gz, state.radius(), (short)-state.delta());
            case PAINT -> changed = terrain.paintTexCircle(gx, gz, state.radius(), (byte)state.texId());
        }
        if (changed) terrain.rebuildDirtyChunks();
    }
}
