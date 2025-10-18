package com.example.game.World.Editor;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.example.game.World.HeightSampler;

/** Simple FPS-style camera controller (yaw/pitch + WASD) with eye height. */
public final class FpsCameraController {
  private final Vector3 pos = new Vector3(0f, 1.7f, 0f);
  private float yawDeg = 0f;
  private float pitchDeg = 0f; // clamp +-89
  private float moveSpeed = 6.0f; // m/s
  private float mouseSens = 0.2f; // deg per pixel
  private float eyeHeight = 1.7f;
  private HeightSampler heightSampler; // optional ground sampler

  // Scratch
  private final Vector3 fwd = new Vector3();
  private final Vector3 right = new Vector3();

  public static final class State {
    public final Vector3 pos = new Vector3();
    public float yawDeg;
    public float pitchDeg;
    public float eyeHeight;
  }

  public State getState() {
    State s = new State();
    s.pos.set(pos);
    s.yawDeg = yawDeg; s.pitchDeg = pitchDeg; s.eyeHeight = eyeHeight;
    return s;
  }

  public void setState(State s) {
    if (s == null) return;
    pos.set(s.pos);
    yawDeg = s.yawDeg; pitchDeg = s.pitchDeg; eyeHeight = s.eyeHeight;
  }

  public void look(float dx, float dy) {
    yawDeg   += dx * mouseSens;
    pitchDeg -= dy * mouseSens;
    pitchDeg = MathUtils.clamp(pitchDeg, -89f, 89f);
  }

  public void move(boolean w, boolean a, boolean s, boolean d, float dt) {
    // Build planar basis from yaw only
    float yawRad = yawDeg * MathUtils.degreesToRadians;
    fwd.set(MathUtils.sin(yawRad), 0f, -MathUtils.cos(yawRad)).nor();
    right.set(fwd).crs(Vector3.Y).nor();
    Vector3 delta = new Vector3();
    if (w) delta.add(fwd);
    if (s) delta.sub(fwd);
    if (a) delta.sub(right);
    if (d) delta.add(right);
    if (!delta.isZero()) {
      delta.nor().scl(moveSpeed * dt);
      pos.add(delta);
    }
  }

  public void setMoveSpeed(float mps) { moveSpeed = Math.max(0.1f, mps); }
  public void setEyeHeight(float meters) { eyeHeight = Math.max(0.1f, meters); }
  /** Provide a ground sampler so the camera follows terrain (y = ground + eye). */
  public void setHeightSampler(HeightSampler sampler) { this.heightSampler = sampler; }

  public void update(Camera cam) {
    // Direction from yaw/pitch
    float yawRad = yawDeg * MathUtils.degreesToRadians;
    float pitchRad = pitchDeg * MathUtils.degreesToRadians;
    float cp = MathUtils.cos(pitchRad);
    Vector3 dir = new Vector3(
      MathUtils.sin(yawRad) * cp,
      MathUtils.sin(pitchRad),
      -MathUtils.cos(yawRad) * cp
    ).nor();
    float groundY = 0f;
    boolean haveGround = false;
    if (heightSampler != null) {
      float hint = cam != null ? cam.position.y : (pos.y + eyeHeight);
      float h = heightSampler.heightAt(pos.x, pos.z, hint);
      if (!Float.isNaN(h) && h != Float.NEGATIVE_INFINITY) { groundY = h; haveGround = true; }
    }
    cam.position.set(pos.x, (haveGround ? groundY + eyeHeight : eyeHeight), pos.z);
    cam.direction.set(dir);
    cam.up.set(Vector3.Y);
    cam.update(true);
    EditorContext.setFocusPosition(new Vector3(pos.x, 0f, pos.z));
  }
}
