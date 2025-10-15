package com.example.game.World.Editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;

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
    void cameraPan(float dx, float dy);
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
      // MMB drag: orbit; Shift+MMB: pan
      boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
      float dx = Gdx.input.getDeltaX();
      float dy = -Gdx.input.getDeltaY();
      if (!shift) access.cameraOrbit(dx, dy); else access.cameraPan(dx, dy);
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
    boolean w = Gdx.input.isKeyPressed(Input.Keys.UP);
    boolean a = Gdx.input.isKeyPressed(Input.Keys.LEFT);
    boolean s = Gdx.input.isKeyPressed(Input.Keys.DOWN);
    boolean d = Gdx.input.isKeyPressed(Input.Keys.RIGHT);
    if (w || a || s || d) {
      access.moveWASD(w, a, s, d, dt);
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
