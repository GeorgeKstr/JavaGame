package com.example.game.World.Editor.Layers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.example.game.UI.SkinFactory;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.*;

public class TerrainLayer implements EditorLayer {
    private final TerrainSystem terrain;
    private final BrushOverlay overlay = new BrushOverlay(64);
    private final EditorState state = new EditorState();
    private final Skin skin;

    // NEW: keep a handle to the brush radius value label so we can update it on scroll
    private Label brushRadiusValueLabel;

    public TerrainLayer(TerrainSystem terrain, Skin skin) {
        this.terrain = terrain;
        this.skin = skin;
    }

    @Override public String id() { return "terrain"; }
    @Override public String displayName() { return "Terrain"; }

    @Override public void onActivate() {}
    @Override public void onDeactivate() {}

    @Override public void update(float dt) {
        overlay.update(EditorContext.cam(), terrain, state.radius(), terrain.getTileSize());
    }

    @Override public void render3D(Camera cam) { overlay.draw(cam); }

    @Override
    public void buildUI(Table left, Skin skin) {
        left.defaults().pad(6).left().top();

        // Title
        left.add(new Label("Terrain Tools", skin, "title")).left().row();

        // Tool buttons
        Table toolRow = new Table(); toolRow.defaults().padRight(6);
        TextButton bRaise = new TextButton("1  Raise", skin);
        TextButton bLower = new TextButton("2  Lower", skin);
        TextButton bPaint = new TextButton("3  Paint", skin);
        toolRow.add(bRaise); toolRow.add(bLower); toolRow.add(bPaint);
        left.add(toolRow).left().row();

        bRaise.addListener(SkinFactory.onClick(() -> state.setTool(EditorState.Tool.RAISE)));
        bLower.addListener(SkinFactory.onClick(() -> state.setTool(EditorState.Tool.LOWER)));
        bPaint.addListener(SkinFactory.onClick(() -> state.setTool(EditorState.Tool.PAINT)));

        // Brush radius (uses a special stepper so we keep a reference to the value label)
        left.add(new Label("Brush Radius", skin)).left().row();
        left.add(buildBrushRadiusStepper()).left().row();

        // Height delta
        left.add(new Label("Height Δ", skin)).left().row();
        left.add(stepper(() -> (int)state.delta(), v -> state.setDelta((short)Math.max(1, v)))).left().row();

        // Texture palette
        left.add(new Label("Texture", skin)).left().row();
        TexturePalette palette = new TexturePalette(skin, new int[]{0,1,2,3,4,5,6,7});
        palette.onChange(() -> state.setTexId(palette.selectedId()));
        left.add(palette.widget()).width(320).height(180).left().row();

        // Help label (wrapped)
        Label help = new Label(
            "LMB: apply • RMB: pan • MMB: orbit\nCtrl+Wheel: brush size • F5: Save",
            skin
        );
        help.setWrap(true);
        help.setAlignment(Align.left);
        left.add(help).width(320).left().row();
    }

    /** Stepper that controls the brush radius and stores its value label. */
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

    // NOTE: if this method is called by your input system separately, leave it as-is (no @Override needed unless you implement InputProcessor)
    public boolean scrolled(float amountX, float amountY) {
        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) {
            int r = state.radius() + (amountY > 0 ? -1 : 1); // wheel down = smaller
            r = Math.max(1, r);
            if (r != state.radius()) {
                state.setRadius(r);
                if (brushRadiusValueLabel != null) {
                    brushRadiusValueLabel.setText(String.valueOf(r));
                }
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
