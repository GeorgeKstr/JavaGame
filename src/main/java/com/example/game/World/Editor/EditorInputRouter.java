package com.example.game.World.Editor;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

public class EditorInputRouter extends InputAdapter {
  public static class InputEvent {
    public enum Type { MouseDown, MouseDrag, MouseUp, KeyDown, Scroll }
    public final Type type;
    public final int x, y, keycode;
    public final boolean buttonLeft, buttonRight, buttonMiddle;
    public final float scrollY;

    public InputEvent(Type type, int x, int y, int keycode,
                      boolean l, boolean r, boolean m, float scrollY) {
      this.type=type; this.x=x; this.y=y; this.keycode=keycode;
      this.buttonLeft=l; this.buttonRight=r; this.buttonMiddle=m; this.scrollY=scrollY;
    }
  }

  public interface CameraCallbacks {
    void orbit(float dx, float dy);
    void pan(float dx, float dy);
    void zoom(float amountY);
  }

  private final LayerManager lm;
  private final CameraCallbacks camCB;
  private int lastX, lastY;
  private boolean orbiting=false, panning=false;

  public EditorInputRouter(LayerManager lm, CameraCallbacks camCB) {
    this.lm = lm; this.camCB = camCB;
  }

  @Override public boolean scrolled(float amountX, float amountY) {
    if (lm.scrolled(amountX, amountY)) return true;
    camCB.zoom(amountY); return true;
  }

  @Override public boolean touchDown (int x, int y, int pointer, int button) {
    boolean l = button==Input.Buttons.LEFT, r = button==Input.Buttons.RIGHT, m = button==Input.Buttons.MIDDLE;
    if (lm.handleInput(new InputEvent(InputEvent.Type.MouseDown, x,y,0,l,r,m,0))) return true;

    if (m) { orbiting=true; panning=false; lastX=x; lastY=y; return true; }
    if (r) { panning=true;  orbiting=false; lastX=x; lastY=y; return true; }
    return l; // let LMB be handled by layer only
  }

  @Override public boolean touchDragged (int x, int y, int pointer) {
    if (lm.handleInput(new InputEvent(InputEvent.Type.MouseDrag, x,y,0,
        Input.Buttons.LEFT==1,Input.Buttons.RIGHT==1,Input.Buttons.MIDDLE==1,0))) return true;

    float dx=x-lastX, dy=y-lastY;
    if (orbiting) { camCB.orbit(dx, dy); lastX=x; lastY=y; return true; }
    if (panning)  { camCB.pan(dx, dy);   lastX=x; lastY=y; return true; }
    return false;
  }

  @Override public boolean touchUp (int x, int y, int pointer, int button) {
    boolean l = button==Input.Buttons.LEFT, r = button==Input.Buttons.RIGHT, m = button==Input.Buttons.MIDDLE;
    if (lm.handleInput(new InputEvent(InputEvent.Type.MouseUp, x,y,0,l,r,m,0))) return true;
    if (m) { orbiting=false; return true; }
    if (r) { panning=false;  return true; }
    return false;
  }

  @Override public boolean keyDown(int keycode) {
    return lm.handleInput(new InputEvent(InputEvent.Type.KeyDown, 0,0,keycode,false,false,false,0));
  }
}
