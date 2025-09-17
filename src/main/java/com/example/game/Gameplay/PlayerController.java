package com.example.game.Gameplay;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.example.game.World.TerrainChunk;

public class PlayerController {
  public final PerspectiveCamera cam;
  public final TerrainChunk terrain;

  public final Vector3 pos = new Vector3(32, 0, 32);
  private float yawDeg = 0f;     // 0° faces -Z in LibGDX
  private float pitchDeg = 0f;

  private final Vector3 vel = new Vector3();
  public float eyeHeight = 1.6f;

  public float maxSpeed = 6.0f;
  public float accel = 30.0f;
  public float friction = 12.0f;
  public float stopSpeed = 2.0f;
  public float mouseSens = 0.1f;
  public float pitchMin = -85f, pitchMax = 85f;

  public PlayerController(PerspectiveCamera cam, TerrainChunk terrain) {
    this.cam = cam; this.terrain = terrain;
    Gdx.input.setCursorCatched(true);
  }

  public void toggleCursor() {
    Gdx.input.setCursorCatched(!Gdx.input.isCursorCatched());
  }

  public void update(float dt) {
    // --- mouse look ---
    if (Gdx.input.isCursorCatched()) {
      float dx = Gdx.input.getDeltaX();
      float dy = Gdx.input.getDeltaY();
      yawDeg   += dx * mouseSens;       // right is +yaw
      pitchDeg -= dy * mouseSens;       // up is +pitch (FIXED)
      pitchDeg = MathUtils.clamp(pitchDeg, pitchMin, pitchMax);
    }

    // --- input ---
    float fwd = (key(Input.Keys.W) ? 1f : 0f) - (key(Input.Keys.S) ? 1f : 0f);
    float str = (key(Input.Keys.D) ? 1f : 0f) - (key(Input.Keys.A) ? 1f : 0f);
    float runMul = key(Input.Keys.SHIFT_LEFT) ? 1.6f : 1.0f;

    // --- wish direction from yaw (FIXED 90°) ---
    float sin = MathUtils.sinDeg(yawDeg);
    float cos = MathUtils.cosDeg(yawDeg);
    // forward = ( sin(yaw), 0, -cos(yaw) ), right = ( cos(yaw), 0, sin(yaw) )
    float fx = fwd * sin + str * cos;
    float fz = -fwd * cos + str * sin;
    Vector3 wishDir = new Vector3(fx, 0f, fz);
    if (wishDir.len2() > 0f) wishDir.nor();

    float wishSpeed = maxSpeed * runMul;

    // --- DOOM-ish accel/friction ---
    if (!wishDir.isZero()) {
      float currentSpeed = vel.x * wishDir.x + vel.z * wishDir.z;
      float addSpeed = wishSpeed - currentSpeed;
      if (addSpeed > 0f) {
        float accelAmount = accel * dt * wishSpeed;
        if (accelAmount > addSpeed) accelAmount = addSpeed;
        vel.x += accelAmount * wishDir.x;
        vel.z += accelAmount * wishDir.z;
      }
    } else applyFriction(dt);

    // clamp planar speed a bit
    float planar = (float)Math.sqrt(vel.x*vel.x + vel.z*vel.z);
    if (planar > wishSpeed * 1.1f) {
      float s = (wishSpeed * 1.1f) / planar;
      vel.x *= s; vel.z *= s;
    }

    // integrate XZ
    pos.x += vel.x * dt;
    pos.z += vel.z * dt;

    // bounds clamp (replace with your world bounds)
    float maxX = (terrainSize() - 1f) * terrainTile();
    float maxZ = (terrainSize() - 1f) * terrainTile();
    pos.x = MathUtils.clamp(pos.x, 0f, maxX);
    pos.z = MathUtils.clamp(pos.z, 0f, maxZ);

    // ground snap
    float groundY = terrain.heightAt(pos.x, pos.z);
    pos.y = groundY + eyeHeight;

    // camera direction from yaw/pitch (robust)
    float cp = MathUtils.cosDeg(pitchDeg);
    cam.position.set(pos);
    cam.direction.set(
        MathUtils.sinDeg(yawDeg) * cp,
        MathUtils.sinDeg(pitchDeg),
        -MathUtils.cosDeg(yawDeg) * cp
    ).nor();
    cam.up.set(Vector3.Y);
    cam.update();
  }

  private boolean key(int k) { return Gdx.input.isKeyPressed(k); }

  private void applyFriction(float dt) {
    float speed = (float)Math.sqrt(vel.x*vel.x + vel.z*vel.z);
    if (speed < 1e-4f) { vel.x = vel.z = 0f; return; }
    float control = Math.max(speed, stopSpeed);
    float drop = control * friction * dt;
    float newSpeed = Math.max(speed - drop, 0f);
    if (newSpeed != speed) {
      float s = newSpeed / speed;
      vel.x *= s; vel.z *= s;
    }
  }

  // tiny helpers (swap for real getters if you add them to TerrainChunk)
  private int terrainSize() {
    try { var f = TerrainChunk.class.getDeclaredField("S"); f.setAccessible(true); return f.getInt(terrain); }
    catch (Exception e) { return 64; }
  }
  private float terrainTile() {
    try { var f = TerrainChunk.class.getDeclaredField("tile"); f.setAccessible(true); return f.getFloat(terrain); }
    catch (Exception e) { return 1f; }
  }
}
