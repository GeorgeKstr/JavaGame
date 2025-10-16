package com.example.game.World.Editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.example.game.Config.Settings;
import com.example.game.World.HeightSampler;

/**
 * Simple orbital editor camera controller.
 * - orbit(dx,dy): rotates camera around a pivot/focus
 * - pan(dx,dy):    shifts the pivot in view space
 * - zoom(amount):  changes camera distance to pivot
 * - move(WASD):    strafes the pivot on XZ plane (keeps height)
 * - update(cam):   applies yaw/pitch/distance to the libGDX Camera
 */
public final class EditorCamera {

  private final Settings settings;
  private HeightSampler heightSampler; // optional, for ground clamping
  private boolean clampToGround = false; // when true, pivot.y snaps to terrain
  private boolean lockPivotYZero = true; // when true, force pivot.y = 0 (RTS plane)

  // Spherical state around pivot
  private final Vector3 pivot = new Vector3(0, 0, 0);
  private float yawDeg = 0f;            // yaw around +Y
  private float pitchDeg = -20f;        // up/down (clamped)
  private float distance = 30f;         // radius from pivot

  // Tunables
  private float orbitSens = 0.2f;       // pixels → degrees
  private float panFactor = 0.0030f;    // fallback scaling when FOV unknown
  private float zoomFactor = 0.1f;      // scroll multiplier (expo)

  // Scratch
  private final Vector3 dir = new Vector3();
  private final Vector3 right = new Vector3();
  private final Vector3 up = new Vector3();
  // Optional: terrain system for precise raycast-based zoom clamping
  private com.example.game.World.TerrainSystem terrainRef;

  public EditorCamera(Settings settings) {
    this.settings = settings;
  }

  /** Provide terrain sampler to enable ground clamping logic. */
  public void setHeightSampler(HeightSampler sampler) { this.heightSampler = sampler; }

  /** Provide TerrainSystem to enable raycast clamping during zoom. */
  public void setTerrainForRaycast(com.example.game.World.TerrainSystem terrain) { this.terrainRef = terrain; }

  /** Enable/disable snapping pivot.y to sampled ground. */
  public void setClampToGround(boolean on) { this.clampToGround = on; }

  /** Lock pivot to y=0 plane (RTS style). */
  public void setLockPivotYZero(boolean on) { this.lockPivotYZero = on; }

  /** Serializable camera state for persistence between tool switches. */
  public static final class State {
    public final Vector3 pivot = new Vector3();
    public float yawDeg;
    public float pitchDeg;
    public float distance;
  }

  public State getState() {
    State s = new State();
    s.pivot.set(pivot);
    s.yawDeg = yawDeg; s.pitchDeg = pitchDeg; s.distance = distance;
    return s;
  }

  public void setState(State s) {
    if (s == null) return;
    pivot.set(s.pivot);
    yawDeg = s.yawDeg; pitchDeg = s.pitchDeg; distance = s.distance;
  }

  /** Current world-space focus/pivot. */
  public Vector3 focus() { return pivot; }

  /** Set focus (e.g., on selection). */
  public void setFocus(float x, float y, float z) { pivot.set(x, y, z); }

  /** Rotate around the pivot. dx,dy in pixels. */
  public void orbit(float dx, float dy) {
    yawDeg   -= dx * orbitSens;
    pitchDeg -= dy * orbitSens;
    pitchDeg = MathUtils.clamp(pitchDeg, -89f, 89f);
  }

  /** Pan the pivot in camera space (Shift+MMB by default). dx,dy in pixels. */
  public void pan(Camera cam, float dx, float dy) {
    // Build a stable right/up basis from the current camera
    right.set(cam.direction).crs(cam.up).nor();
    up.set(right).crs(cam.direction).nor();

    // Map screen pixels to world units at current depth using FOV for more accurate dragging
    if (cam instanceof PerspectiveCamera pc) {
      float tanHalfY = MathUtils.tanDeg(pc.fieldOfView * 0.5f);
      float aspect   = (float)Gdx.graphics.getWidth() / (float)Gdx.graphics.getHeight();
      float tanHalfX = tanHalfY * aspect;
      // World height of one screen at current depth ~ 2 * depth * tan(fov/2)
      float worldPerPixelY = (2f * distance * tanHalfY) / Math.max(1, Gdx.graphics.getHeight());
      float worldPerPixelX = (2f * distance * tanHalfX) / Math.max(1, Gdx.graphics.getWidth());
      pivot.mulAdd(right, -dx * worldPerPixelX).mulAdd(up, dy * worldPerPixelY);
    } else {
      float s = Math.max(0.001f, distance) * panFactor;
      pivot.mulAdd(right, -dx * s).mulAdd(up, dy * s);
    }
  }

  /** Scroll (positive = zoom out by default). */
  public void zoom(float amount) {
    // Exponential zoom feels better across scales
    float mul = (float)Math.exp(amount * zoomFactor);
    float desired = MathUtils.clamp(distance * mul, 1.0f, 2000f);

    // If zooming OUT and terrain is available, clamp by raycast from pivot toward camera
    if (terrainRef != null && desired > distance) {
      // Recompute forward dir from yaw/pitch (same math as update)
      float yawRad = yawDeg * MathUtils.degreesToRadians;
      float pitchRad = pitchDeg * MathUtils.degreesToRadians;
      float cp = MathUtils.cos(pitchRad);
      Vector3 fwd = new Vector3(
        MathUtils.sin(yawRad) * cp,
        MathUtils.sin(pitchRad),
        -MathUtils.cos(yawRad) * cp
      ).nor();
      // Ray points from pivot to camera direction (backwards along forward)
      Vector3 dirToCam = new Vector3(fwd).scl(-1f).nor();
      com.badlogic.gdx.math.collision.Ray ray = new com.badlogic.gdx.math.collision.Ray(new Vector3(pivot), dirToCam);
      Vector3 hit = terrainRef.getPicker().rayIntersectTerrain(ray, terrainRef);
      if (hit != null) {
        float clearance = 1.0f; // meters to keep from terrain
        float maxDist = hit.dst(pivot) - clearance;
        if (maxDist < desired) desired = Math.max(1.0f, maxDist);
      }
    }

    distance = desired;
  }

  /**
   * Move the pivot on the XZ plane using WASD intent.
   * Signature matches your router: move(cam, w, a, s, d, dt)
   */
  public void move(Camera cam, boolean w, boolean a, boolean s, boolean d, float dt) {
    float base = (settings != null ? settings.editorMoveSpeed : 10f);

    // Planar forward/right (ignore pitch)
    Vector3 fwd = new Vector3(cam.direction.x, 0f, cam.direction.z).nor();
    if (fwd.isZero()) fwd.set(0f, 0f, -1f);
    Vector3 rgt = new Vector3(fwd).crs(Vector3.Y).nor();

    Vector3 delta = new Vector3();
    if (w) delta.add(fwd);
    if (s) delta.sub(fwd);
    if (a) delta.sub(rgt);
    if (d) delta.add(rgt);

    if (!delta.isZero()) {
      // Ensure reasonable speed at close range; behave a bit faster than walking when fully zoomed in
      float minEffDist = 12f; // meters
      float effDist = Math.max(distance, minEffDist);
      float speed = dt * base * 1.25f * zoomFactor * effDist * 0.1f;
      delta.nor().scl(-speed);
      pivot.add(delta);
    }
  }

  /** Apply yaw/pitch/distance to the libGDX Camera. Call once per frame. */
  public void update(Camera cam) {
    // Dir from yaw/pitch
    float yawRad = yawDeg * MathUtils.degreesToRadians;
    float pitchRad = pitchDeg * MathUtils.degreesToRadians;

    float cp = MathUtils.cos(pitchRad);
    dir.set(
      MathUtils.sin(yawRad) * cp,
      MathUtils.sin(pitchRad),
      -MathUtils.cos(yawRad) * cp
    ).nor();

    // Vertical pivot policy
    float ground = pivot.y;
    if (lockPivotYZero) {
      pivot.y = 0f; ground = 0f;
    } else if (clampToGround && heightSampler != null) {
      float h = heightSampler.heightAt(pivot.x, pivot.z, pivot.y);
      if (!Float.isNaN(h) && h != Float.NEGATIVE_INFINITY) { ground = h; pivot.y = ground; }
    }

    // Position the camera on the sphere, looking at pivot
    cam.position.set(pivot).sub(new Vector3(dir).scl(distance));
    // Enforce minimum clearance by clamping camera Y, not by changing distance.
    // This prevents "un-zooming" when the user looks up at close range.
    float minClear = 2f;
    float minY = ground + minClear;
    if (cam.position.y < minY) cam.position.y = minY;
    cam.direction.set(dir);
    cam.up.set(Vector3.Y); // keep a stable up for editor
    cam.update(true);

    // Publish the rotation axis/pivot to the editor context for other systems (e.g., marker)
    EditorContext.setFocusPosition(pivot);
  }
}
