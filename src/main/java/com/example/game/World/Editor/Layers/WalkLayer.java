package com.example.game.World.Editor.Layers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.example.game.UI.SkinFactory;
import com.example.game.World.HeightSampler;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.*;

/**
 * WalkLayer: simple FPS-style walkthrough inside the editor.
 * - Mouse look + WASD move over terrain, camera locked to heightmap + eye height.
 * - Captures mouse on activate, releases on deactivate.
 * - Minimal left-panel UI to exit and tweak speed/eye height.
 */
public class WalkLayer implements EditorLayer {

  private final TerrainSystem terrain;
  private final Skin skin;

  // camera state (uses the shared EditorContext camera)
  private final Vector3 pos = new Vector3();
  private float yawDeg = 0f;
  private float pitchDeg = 0f;

  // tunables
  private float eyeHeight = 1.7f;      // meters
  private float moveSpeed = 6.0f;      // m/s
  private float mouseSensitivity = 0.2f; // deg per pixel
  private boolean captureMouse = true;

  // helpers
  private final ShapeRenderer sr = new ShapeRenderer();
  private boolean wasCursorCatched = false;

  // UI bindings (optional live tweak)
  private Label speedVal, eyeVal;

  public WalkLayer(TerrainSystem terrain, Skin skin) {
    this.terrain = terrain;
    this.skin = skin;
  }

  @Override public String id() { return "walk"; }
  @Override public String displayName() { return "Walk"; }

  @Override
  public void onActivate() {
    // Initialize from current editor camera
    PerspectiveCamera cam = EditorContext.cam();
    pos.set(cam.position);
    // derive yaw/pitch from camera direction
    Vector3 dir = new Vector3(cam.direction).nor();
    yawDeg = MathUtils.atan2(dir.x, -dir.z) * MathUtils.radiansToDegrees;
    float horiz = (float)Math.sqrt(dir.x*dir.x + dir.z*dir.z);
    pitchDeg = MathUtils.atan2(dir.y, horiz) * MathUtils.radiansToDegrees;
    pitchDeg = MathUtils.clamp(pitchDeg, -85f, 85f);

    // snap to terrain height
    float ground = terrain.heightAt(pos.x, pos.z, pos.y);
    if (!Float.isNaN(ground)) pos.y = ground + eyeHeight;

    // capture mouse (remember previous)
    wasCursorCatched = Gdx.input.isCursorCatched();
    if (captureMouse) Gdx.input.setCursorCatched(true);
  }

  @Override
  public void onDeactivate() {
    // release/cascade previous capture state
    Gdx.input.setCursorCatched(wasCursorCatched);
  }

  @Override
  public void update(float dt) {
    PerspectiveCamera cam = EditorContext.cam();

    // Mouse look (kept as-is) ...
    if (Gdx.input.isCursorCatched()) {
    float dx = Gdx.input.getDeltaX();
    float dy = Gdx.input.getDeltaY();
    yawDeg   += dx * mouseSensitivity;
    pitchDeg = MathUtils.clamp(pitchDeg - dy * mouseSensitivity, -85f, 85f);
    }

    // Apply yaw/pitch to camera direction
    float cp = MathUtils.cosDeg(pitchDeg), sp = MathUtils.sinDeg(pitchDeg);
    float sy = MathUtils.sinDeg(yawDeg),   cy = MathUtils.cosDeg(yawDeg);
    Vector3 dir = new Vector3(cp * sy, sp, cp * -cy).nor();
    cam.direction.set(dir);
    cam.up.set(Vector3.Y);

    // ---- FIXED movement basis: derive from camera.direction ----
    Vector3 fwd = new Vector3(cam.direction.x, 0f, cam.direction.z);
    if (fwd.isZero()) fwd.set(0, 0, -1);
    fwd.nor();

    // right = forward × up  (NOT up × forward)
    Vector3 right = new Vector3(fwd).crs(Vector3.Y).nor();  // correct +X when facing -Z

    // Movement input
    float speed = moveSpeed * (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT) ? 1.8f : 1f);
    Vector3 delta = new Vector3();
    if (Gdx.input.isKeyPressed(Input.Keys.W)) delta.add(fwd);
    if (Gdx.input.isKeyPressed(Input.Keys.S)) delta.sub(fwd);
    if (Gdx.input.isKeyPressed(Input.Keys.D)) delta.add(right);
    if (Gdx.input.isKeyPressed(Input.Keys.A)) delta.sub(right);
    if (!delta.isZero()) {
    delta.nor().scl(speed * dt);
    pos.add(delta);
    }

    // Stick to ground
    float ground = terrain.heightAt(pos.x, pos.z, pos.y);
    if (!Float.isNaN(ground)) pos.y = ground + eyeHeight;

    // Apply position
    cam.position.set(pos);
    cam.update();

  }

  @Override
  public void render3D(Camera cam) {
    // simple RGB 3 axis lines in screen space
    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    sr.setProjectionMatrix(((PerspectiveCamera)cam).projection.cpy().idt()); // we’ll draw in NDC-ish with manual lines below
    // Instead of fiddling with matrices, draw a tiny 3D axis at camera forward a bit:
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    sr.setProjectionMatrix(cam.combined);
    sr.begin(ShapeRenderer.ShapeType.Line);
    Vector3 p0 = new Vector3(cam.position).mulAdd(cam.direction, 1.0f);
    float s = 0.05f;
    sr.setColor(Color.RED);
    sr.line(p0.x - s, p0.y, p0.z, p0.x + s, p0.y, p0.z); // X axis
    sr.setColor(Color.GREEN);
    sr.line(p0.x, p0.y - s, p0.z, p0.x, p0.y + s, p0.z); // Y axis
    sr.setColor(Color.BLUE);
    sr.line(p0.x, p0.y, p0.z - s, p0.x, p0.y, p0.z + s); // Z axis
    sr.end();

  }

  @Override
  public void buildUI(Table left, Skin skin) {
    left.defaults().pad(6).left().top();

    left.add(new Label("Walk Mode", skin, "title")).left().row();

    // Speed control
    left.add(new Label("Move Speed (m/s)", skin)).left().row();
    Table speedRow = new Table(); speedRow.defaults().padRight(6);
    TextButton spMinus = new TextButton("-", skin);
    speedVal = new Label(String.format("%.1f", moveSpeed), skin);
    TextButton spPlus  = new TextButton("+", skin);
    spMinus.addListener(SkinFactory.onClick(() -> { moveSpeed = Math.max(0.5f, moveSpeed - 0.5f); speedVal.setText(String.format("%.1f", moveSpeed)); }));
    spPlus.addListener(SkinFactory.onClick(() -> { moveSpeed = Math.min(50f, moveSpeed + 0.5f); speedVal.setText(String.format("%.1f", moveSpeed)); }));
    speedRow.add(spMinus); speedRow.add(speedVal).width(52).center(); speedRow.add(spPlus);
    left.add(speedRow).left().row();

    // Eye height
    left.add(new Label("Eye Height (m)", skin)).left().row();
    Table eyeRow = new Table(); eyeRow.defaults().padRight(6);
    TextButton ehMinus = new TextButton("-", skin);
    eyeVal = new Label(String.format("%.2f", eyeHeight), skin);
    TextButton ehPlus  = new TextButton("+", skin);
    ehMinus.addListener(SkinFactory.onClick(() -> { eyeHeight = MathUtils.clamp(eyeHeight - 0.1f, 0.5f, 3.0f); eyeVal.setText(String.format("%.2f", eyeHeight)); }));
    ehPlus.addListener(SkinFactory.onClick(() -> { eyeHeight = MathUtils.clamp(eyeHeight + 0.1f, 0.5f, 3.0f); eyeVal.setText(String.format("%.2f", eyeHeight)); }));
    eyeRow.add(ehMinus); eyeRow.add(eyeVal).width(52).center(); eyeRow.add(ehPlus);
    left.add(eyeRow).left().row();

    // Help
    Label help = new Label("WASD: move • Shift: sprint\nMouse: look • ESC: release mouse", skin);
    help.setWrap(true);
    help.setAlignment(Align.left);
    left.add(help).width(320).left().row();

    // Exit button (switch layer via tabs typically; this provides an extra)
    TextButton exit = new TextButton("Exit Walk Mode (release mouse)", skin);
    exit.addListener(SkinFactory.onClick(() -> Gdx.input.setCursorCatched(false)));
    left.add(exit).left().row();
  }

  @Override
  public boolean handleInput(EditorInputRouter.InputEvent e) {
    // We mostly poll input in update(). Consume scroll to avoid terrain zoom during walk.
    if (e.type == EditorInputRouter.InputEvent.Type.Scroll) return true;
    // Prevent editor brush usage: consume left mouse down.
    if (e.type == EditorInputRouter.InputEvent.Type.MouseDown && e.buttonLeft) {
        Gdx.input.setCursorCatched(true);   
        return true;
    } else if (e.type == EditorInputRouter.InputEvent.Type.MouseUp && e.buttonLeft) {
        Gdx.input.setCursorCatched(false);   
        return true;
    }

    return false;
  }

  // Optionally expose to other systems
  public Vector3 position() { return pos; }
  public float yawDegrees() { return yawDeg; }
  public float pitchDegrees() { return pitchDeg; }

  public void dispose() { sr.dispose(); }
}
