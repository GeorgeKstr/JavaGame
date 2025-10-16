package com.example.game.UI.Editor;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import java.util.function.BooleanSupplier;

/**
 * Routes input between the Stage (UI) and a secondary InputProcessor (e.g., camera/tools)
 * based on cursor hit-testing and UI focus. If the cursor is over the UI or the UI
 * owns keyboard/scroll focus, events go to the Stage first; otherwise they go to the
 * secondary processor first. If the first target doesn't handle the event, it is
 * forwarded to the second.
 */
public final class StageAwareInputRouter implements InputProcessor {
  private final Stage stage;
  private final InputProcessor secondary;
  private final BooleanSupplier uiEnabled;
  private final Vector2 tmp = new Vector2();

  // Track which processor captured the pointer on touchDown so drag/up go to the same
  private boolean pointerCapturedByStage = false;
  private int lastMouseX = 0, lastMouseY = 0;

  public StageAwareInputRouter(Stage stage, InputProcessor secondary) {
    this(stage, secondary, () -> true);
  }

  public StageAwareInputRouter(Stage stage, InputProcessor secondary, BooleanSupplier uiEnabled) {
    this.stage = stage;
    this.secondary = secondary;
    this.uiEnabled = (uiEnabled != null ? uiEnabled : () -> true);
  }

  private boolean isOverUI(int screenX, int screenY) {
    if (!uiEnabled.getAsBoolean()) return false;
    tmp.set(screenX, screenY);
    stage.screenToStageCoordinates(tmp);
    Actor hit = stage.hit(tmp.x, tmp.y, true);
    return hit != null;
  }

  private boolean uiHasFocus() {
    return uiEnabled.getAsBoolean() && (stage.getKeyboardFocus() != null || stage.getScrollFocus() != null);
  }

  @Override public boolean keyDown(int keycode) {
    if (!uiEnabled.getAsBoolean()) {
      return secondary != null && secondary.keyDown(keycode);
    }
    boolean uiFirst = uiHasFocus();
    if (uiFirst) {
      if (stage.keyDown(keycode)) return true;
      return secondary != null && secondary.keyDown(keycode);
    } else {
      if (secondary != null && secondary.keyDown(keycode)) return true;
      return stage.keyDown(keycode);
    }
  }

  @Override public boolean keyUp(int keycode) {
    boolean uiFirst = uiHasFocus();
    if (uiFirst) {
      if (stage.keyUp(keycode)) return true;
      return secondary != null && secondary.keyUp(keycode);
    } else {
      if (secondary != null && secondary.keyUp(keycode)) return true;
      return stage.keyUp(keycode);
    }
  }

  @Override public boolean keyTyped(char character) {
    boolean uiFirst = uiHasFocus();
    if (uiFirst) {
      if (stage.keyTyped(character)) return true;
      return secondary != null && secondary.keyTyped(character);
    } else {
      if (secondary != null && secondary.keyTyped(character)) return true;
      return stage.keyTyped(character);
    }
  }

  @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
    boolean overUI = isOverUI(screenX, screenY);
    pointerCapturedByStage = overUI; // capture route for drag/up
    if (overUI) {
      if (stage.touchDown(screenX, screenY, pointer, button)) return true;
      return secondary != null && secondary.touchDown(screenX, screenY, pointer, button);
    } else {
      // Clicked outside UI: explicitly clear UI focus so TextFields de-focus
      if (uiEnabled.getAsBoolean()) { stage.setKeyboardFocus(null); stage.setScrollFocus(null); }
      if (secondary != null && secondary.touchDown(screenX, screenY, pointer, button)) return true;
      return uiEnabled.getAsBoolean() && stage.touchDown(screenX, screenY, pointer, button);
    }
  }

  @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) {
    if (pointerCapturedByStage && uiEnabled.getAsBoolean()) {
      if (stage.touchUp(screenX, screenY, pointer, button)) { pointerCapturedByStage = false; return true; }
      boolean handled = secondary != null && secondary.touchUp(screenX, screenY, pointer, button);
      pointerCapturedByStage = false;
      return handled;
    } else {
      if (secondary != null && secondary.touchUp(screenX, screenY, pointer, button)) return true;
      return uiEnabled.getAsBoolean() && stage.touchUp(screenX, screenY, pointer, button);
    }
  }

  @Override public boolean touchDragged(int screenX, int screenY, int pointer) {
    if (pointerCapturedByStage && uiEnabled.getAsBoolean()) {
      if (stage.touchDragged(screenX, screenY, pointer)) return true;
      return secondary != null && secondary.touchDragged(screenX, screenY, pointer);
    } else {
      if (secondary != null && secondary.touchDragged(screenX, screenY, pointer)) return true;
      return uiEnabled.getAsBoolean() && stage.touchDragged(screenX, screenY, pointer);
    }
  }

  @Override public boolean mouseMoved(int screenX, int screenY) {
    // Mouse move to both, UI first if over UI to keep hover effects snappy
    lastMouseX = screenX; lastMouseY = screenY;
    if (!uiEnabled.getAsBoolean()) {
      return secondary != null && secondary.mouseMoved(screenX, screenY);
    }
    boolean overUI = isOverUI(screenX, screenY);
    if (overUI) {
      if (stage.mouseMoved(screenX, screenY)) return true;
      return secondary != null && secondary.mouseMoved(screenX, screenY);
    } else {
      if (secondary != null && secondary.mouseMoved(screenX, screenY)) return true;
      return stage.mouseMoved(screenX, screenY);
    }
  }

  @Override public boolean scrolled(float amountX, float amountY) {
    // Prefer Stage if cursor is over UI; otherwise route to secondary (camera/tools)
    if (!uiEnabled.getAsBoolean()) {
      return secondary != null && secondary.scrolled(amountX, amountY);
    }
    boolean overUI = isOverUI(lastMouseX, lastMouseY);
    if (overUI) {
      if (stage.scrolled(amountX, amountY)) return true;
      return secondary != null && secondary.scrolled(amountX, amountY);
    } else {
      if (secondary != null && secondary.scrolled(amountX, amountY)) return true;
      return stage.scrolled(amountX, amountY);
    }
  }

  @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
    // Mirror touchUp routing
    if (pointerCapturedByStage && uiEnabled.getAsBoolean()) {
      if (stage.touchCancelled(screenX, screenY, pointer, button)) { pointerCapturedByStage = false; return true; }
      boolean handled = secondary != null && secondary.touchCancelled(screenX, screenY, pointer, button);
      pointerCapturedByStage = false;
      return handled;
    } else {
      if (secondary != null && secondary.touchCancelled(screenX, screenY, pointer, button)) return true;
      return uiEnabled.getAsBoolean() && stage.touchCancelled(screenX, screenY, pointer, button);
    }
  }
}
