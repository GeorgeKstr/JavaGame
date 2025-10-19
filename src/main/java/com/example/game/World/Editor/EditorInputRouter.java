package com.example.game.World.Editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.example.game.World.Editor.Tools.Tool;

/**
 * Unified editor input:
 * - LMB: send to active tool (via BaseToolLayer)
 * - MMB drag: orbit; Shift+MMB drag: pan
 * - Wheel: zoom (positive = zoom out; invert in Access if desired)
 * - RMB drag or WASD: move
 *
 * Use with:
 *   new EditorInputRouter(new EditorInputRouter.Access() { ... })
 * and (optionally) call router.update(dt) each frame for WASD.
 */
public final class EditorInputRouter extends InputAdapter {

  /** Host-provided hooks (camera ops, picking, active layer). */
  public interface Access {
    Camera cam();
    EditorLayer activeLayer();

    void cameraOrbit(float dx, float dy);
    void cameraZoom(float amount);

    /** WASD-style movement; dt provided by caller (update). */
    void moveWASD(boolean w, boolean a, boolean s, boolean d, float dt);

  }

  private final Access access;

  // mouse button state
  private boolean lmbDown = false;
  private boolean mmbDown = false;
  private boolean rmbDown = false;


  public EditorInputRouter(Access access) {
    this.access = access;
  }

  // ------------------------------------------------------------
  // Mouse
  // ------------------------------------------------------------

  @Override
  public boolean touchDown(int screenX, int screenY, int pointer, int button) {
    switch (button) {
      case Input.Buttons.LEFT:
        lmbDown = true;
        sendTool(screenX, screenY, true);
        return true;
      case Input.Buttons.MIDDLE:
        mmbDown = true;
        return true;
      case Input.Buttons.RIGHT:
        rmbDown = true;
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean touchDragged(int screenX, int screenY, int pointer) {
    //System.out.println("drag @ "+screenX+","+screenY+",  cursor at "+Gdx.input.getX()+","+Gdx.input.getY());
    if (mmbDown) {
      float dx = Gdx.input.getDeltaX();
      float dy = -Gdx.input.getDeltaY();
      access.cameraOrbit(dx, dy);
    
      return true;
    }

    if (rmbDown) {
      // RMB drag → move like WASD using deltas
      float dx = -Gdx.input.getDeltaX();
      float dy = Gdx.input.getDeltaY();
      // Map drag to movement intent
      boolean w = dy > 0f, s = dy < 0f, a = dx < 0f, d = dx > 0f;
      access.moveWASD(w, a, s, d, Gdx.graphics.getDeltaTime());
      return true;
    }

    if (lmbDown) {
      sendTool(screenX, screenY, false);
      return true;
    }

    return false;
  }

  @Override
  public boolean touchUp(int screenX, int screenY, int pointer, int button) {
    switch (button) {
      case Input.Buttons.LEFT:
        lmbDown = false;
        sendToolUp(screenX, screenY);
        return true;
      case Input.Buttons.MIDDLE:
        mmbDown = false;
        return true;
      case Input.Buttons.RIGHT:
        rmbDown = false;
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean scrolled(float amountX, float amountY) {
    EditorLayer layer = access.activeLayer();
    if (layer != null) {
      if (layer instanceof BaseToolLayer && ((BaseToolLayer) layer).getActiveTool() != null) {
        if (((BaseToolLayer) layer).getActiveTool().scrolled(amountX, amountY)) {
          return true;
        }
      }
      if (layer.scrolled(amountX, amountY)) return true;
    }
    access.cameraZoom(amountY); // positive usually means scroll down
    return true;
  }

  // ------------------------------------------------------------
  // Keyboard
  // ------------------------------------------------------------

  /** Call this each frame to keep keyboard movement responsive. */
  public void update(float dt) {
    // Arrow keys always control movement (RTS and FPS)
    boolean upArrow = Gdx.input.isKeyPressed(Input.Keys.UP);
    boolean lfArrow = Gdx.input.isKeyPressed(Input.Keys.LEFT);
    boolean dnArrow = Gdx.input.isKeyPressed(Input.Keys.DOWN);
    boolean rtArrow = Gdx.input.isKeyPressed(Input.Keys.RIGHT);

    // WASD only when Walk+Roam and cursor is captured
    EditorLayer layer = access.activeLayer();
    boolean roamActive = false;
    if (layer != null && "walk".equals(layer.id()) && layer instanceof BaseToolLayer) {
      Tool t = ((BaseToolLayer) layer).getActiveTool();
      roamActive = (t != null && "Roam".equals(t.name()));
    }
    boolean allowWASD = roamActive && Gdx.input.isCursorCatched();
    boolean upW = allowWASD && Gdx.input.isKeyPressed(Input.Keys.W);
    boolean lfA = allowWASD && Gdx.input.isKeyPressed(Input.Keys.A);
    boolean dnS = allowWASD && Gdx.input.isKeyPressed(Input.Keys.S);
    boolean rtD = allowWASD && Gdx.input.isKeyPressed(Input.Keys.D);

    boolean up = upArrow || upW;
    boolean lf = lfArrow || lfA;
    boolean dn = dnArrow || dnS;
    boolean rt = rtArrow || rtD;

    if (up || lf || dn || rt) {
      access.moveWASD(up, lf, dn, rt, dt);
    }

    // Continuous mouse-look for Walk layer when cursor is captured
    EditorLayer layer2 = access.activeLayer();
    boolean walk = (layer2 != null && "walk".equals(layer2.id()));
    if (walk && Gdx.input.isCursorCatched()) {
      float dx = Gdx.input.getDeltaX();
      float dy = -Gdx.input.getDeltaY();
      if (dx != 0f || dy != 0f) access.cameraOrbit(dx, dy);
    }
  }

  // ------------------------------------------------------------
  // Tool dispatch (to active layer if it's a BaseToolLayer)
  // ------------------------------------------------------------

  private void sendTool(int screenX, int screenY, boolean down) {
    EditorLayer layer = access.activeLayer();
    if (layer == null) return;
    

    boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    boolean ctrl  = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);

    if (layer instanceof BaseToolLayer) {
      BaseToolLayer btl = (BaseToolLayer) layer;
      if (down) btl.onPointerDown(access.cam(), Input.Buttons.LEFT, shift, ctrl);
      else      btl.onPointerDrag(access.cam(), Input.Buttons.LEFT, shift, ctrl);
    }
  }

  private void sendToolUp(int screenX, int screenY) {
    EditorLayer layer = access.activeLayer();
    if (layer == null) return;

    boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    boolean ctrl  = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);

    if (layer instanceof BaseToolLayer) {
      ((BaseToolLayer) layer).onPointerUp(access.cam(), Input.Buttons.LEFT, shift, ctrl);
    }
  }
}
