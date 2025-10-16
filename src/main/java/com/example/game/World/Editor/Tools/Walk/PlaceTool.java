package com.example.game.World.Editor.Tools.Walk;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.example.game.World.TerrainSystem;
import com.example.game.World.Editor.EditorContext;
import com.example.game.World.Editor.FpsCameraController;
import com.example.game.World.Editor.Tools.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Placement tool for player nodes, used with RTS camera. */
public final class PlaceTool implements Tool {
  private final TerrainSystem terrain;
  private final Consumer<FpsCameraController.State> onTeleport;
  private final Runnable onSwitchToRoam;

  private final List<Node> nodes = new ArrayList<>();
  private com.badlogic.gdx.scenes.scene2d.ui.List<String> listUI;
  private TextField nameField;
  private Slider eyeSlider;
  private Label eyeLabel;
  private int selectedIndex = -1;

  public PlaceTool(TerrainSystem t,
                   Consumer<FpsCameraController.State> onTeleport,
                   Runnable onSwitchToRoam) {
    this.terrain = t; this.onTeleport = onTeleport; this.onSwitchToRoam = onSwitchToRoam;
  }

  @Override public String name() { return "Place"; }

  @Override public void onSelected() {
    // Ensure RTS-like interaction: free the cursor
    com.badlogic.gdx.Gdx.input.setCursorCatched(false);
    if (nodes.isEmpty()) { try { loadPlayersFromMap(); rebuildUIList(); } catch (Throwable ignored) {} }
  }

  @Override public void buildUI(Table parent, Skin skin) {
    parent.add(new Label("Player Nodes", skin, "title")).left().row();

    Table row = new Table(); parent.add(row).left().row();
    TextButton add = new TextButton("Inspect", skin);
    TextButton del = new TextButton("Delete", skin);
    TextButton use = new TextButton("Roam here", skin);
    row.add(add).padRight(6); row.add(del).padRight(6); row.add(use);

    listUI = new com.badlogic.gdx.scenes.scene2d.ui.List<>(skin);
    ScrollPane sp = new ScrollPane(listUI, skin);
    sp.setFadeScrollBars(false);
    parent.add(sp).width(260).height(140).left().row();

    nameField = new TextField("", skin);
    nameField.setMessageText("Name");
    parent.add(nameField).width(260).left().row();

    eyeLabel = new Label("Eye: -", skin); eyeSlider = new Slider(1.2f, 2.2f, 0.01f, false, skin);
    parent.add(eyeLabel).left().row(); parent.add(eyeSlider).width(260).row();

    // Wire actions
    add.addListener(com.example.game.UI.SkinFactory.onClick(() -> {
      if(selectedIndex < 0 || selectedIndex >= nodes.size()) return;
      System.out.println("Selected index: " + selectedIndex + " (" + nodes.size() + " nodes)");
      Node n = nodes.get(selectedIndex);
      Camera cam = EditorContext.getCamera(); if (cam == null) return;
      cam.position.set(n.x, 0f, n.z);
    }));

    del.addListener(com.example.game.UI.SkinFactory.onClick(() -> {
      int idx = listUI.getSelectedIndex(); if (idx >= 0 && idx < nodes.size()) { nodes.remove(idx); selectedIndex = Math.min(idx, nodes.size()-1); rebuildUIList(); savePlayersToMap(); }
    }));

    use.addListener(com.example.game.UI.SkinFactory.onClick(() -> {
      int idx = listUI.getSelectedIndex(); if (idx < 0 || idx >= nodes.size()) return;
      Node n = nodes.get(idx);
      FpsCameraController.State s = new FpsCameraController.State();
      s.pos.set(n.x, 0f, n.z); s.yawDeg = 0f; s.pitchDeg = 0f; s.eyeHeight = n.eye;
      if (onTeleport != null) onTeleport.accept(s);
      if (onSwitchToRoam != null) onSwitchToRoam.run();
    }));

    listUI.addListener(new ChangeListener(){ @Override public void changed(ChangeListener.ChangeEvent event, Actor actor){
      selectedIndex = listUI.getSelectedIndex();
      syncSelectionUI();
      // Do not auto-enter Roam here; allow user to click 'Roam here' explicitly
      if (selectedIndex >= 0 && selectedIndex < nodes.size()) {
        Camera cam = EditorContext.getCamera();
        if (cam != null) {
          Vector3 pos = nodes.get(selectedIndex).getPosition();
          cam.position.set(pos.x, pos.y, pos.z);
        }
      }
    }});
    nameField.setTextFieldListener((tf, c) -> { Node n = getSelected(); if (n != null) { n.name = nameField.getText(); rebuildUIList(); savePlayersToMap(); } });
    eyeSlider.addListener(new ChangeListener(){ @Override public void changed(ChangeListener.ChangeEvent event, Actor actor){ Node n = getSelected(); if (n != null) { n.eye = eyeSlider.getValue(); eyeLabel.setText(String.format("Eye: %.2f m", n.eye)); savePlayersToMap(); } }});

    rebuildUIList();
    syncSelectionUI();
  }

  private Node getSelected(){ return (selectedIndex>=0 && selectedIndex<nodes.size()? nodes.get(selectedIndex): null); }

  private void rebuildUIList(){
    if (listUI == null) return;
    String[] items = new String[nodes.size()];
    for (int i=0;i<nodes.size();i++) { Node n = nodes.get(i); items[i] = String.format("%s (%.1f, %.1f)", (n.name==null?"":n.name), n.x, n.z); }
    listUI.setItems(items);
    if (selectedIndex >= nodes.size()) selectedIndex = nodes.size()-1;
    if (selectedIndex >= 0) listUI.setSelectedIndex(selectedIndex);
  }

  private void syncSelectionUI(){
    Node n = getSelected();
    if (n != null) {
      nameField.setText(n.name==null?"":n.name);
      eyeSlider.setValue(n.eye);
      eyeLabel.setText(String.format("Eye: %.2f m", n.eye));
    }
  }

  private void savePlayersToMap() {
    StringBuilder sb = new StringBuilder(256);
    sb.append('{').append("\"players\":[");
    for (int i=0;i<nodes.size();i++) {
      Node n = nodes.get(i);
      if (i>0) sb.append(',');
      sb.append('{')
        .append("\"name\":\"").append(n.name==null?"":n.name.replace("\"","\\\""))
        .append('\"')
        .append(",\"x\":").append(n.x)
        .append(",\"z\":").append(n.z)
        .append(",\"w\":").append(n.w)
        .append(",\"d\":").append(n.d)
        .append(",\"eye\":").append(n.eye)
        .append('}');
    }
    sb.append("]}");
    byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    terrain.getMap().stageExtra("walk/players.json", bytes);
  }

  private void loadPlayersFromMap() {
    byte[] data = terrain.getMap().readEntry("walk/players.json");
    if (data == null || data.length == 0) return;
    String json = new String(data, java.nio.charset.StandardCharsets.UTF_8);
    com.badlogic.gdx.utils.JsonValue root = new com.badlogic.gdx.utils.JsonReader().parse(json);
    com.badlogic.gdx.utils.JsonValue arr = root.get("players");
    if (arr == null) return;
    nodes.clear();
    for (com.badlogic.gdx.utils.JsonValue e : arr) {
      Node n = new Node();
      n.name = e.getString("name", "");
      n.x = e.getFloat("x", 0f);
      n.z = e.getFloat("z", 0f);
      n.w = e.getFloat("w", 0.6f);
      n.d = e.getFloat("d", 0.6f);
      n.eye = e.getFloat("eye", 1.7f);
      nodes.add(n);
    }
    selectedIndex = nodes.isEmpty()? -1 : 0;
  }

  @Override public void onPointerDown(int button, boolean shift, boolean ctrl) {
    if (button != Input.Buttons.LEFT) return;
    Camera cam = EditorContext.getCamera(); if (cam == null) return;
    com.badlogic.gdx.math.collision.Ray ray = cam.getPickRay(Gdx.input.getX(), Gdx.input.getY());
    Vector3 hit = terrain.getPicker().rayIntersectTerrain(ray, terrain);
    if (hit == null) return;
    if (!ctrl) {
      Node n = new Node(); n.x = hit.x; n.z = hit.z; n.w = 0.6f; n.d = 0.6f; n.eye = 1.7f; n.name = "Spawn";
      nodes.add(n); selectedIndex = nodes.size()-1; rebuildUIList(); savePlayersToMap();
    } else {
      // remove nearest
      int best = -1; float bestD = Float.POSITIVE_INFINITY;
      for (int i=0;i<nodes.size();i++) { Node n = nodes.get(i); float dx = n.x-hit.x, dz=n.z-hit.z; float d=dx*dx+dz*dz; if (d<bestD){bestD=d; best=i;} }
      if (best>=0) { nodes.remove(best); selectedIndex = Math.min(best, nodes.size()-1); rebuildUIList(); savePlayersToMap(); }
    }
  }

  private static final class Node { String name; float x, z; float w, d; float eye; public Vector3 getPosition(){ return new Vector3(x, 0f, z); } }

  /** Snapshot of node AABBs: each entry = [x, z, w, d, eye]. */
  public java.util.List<float[]> getNodeAABBs() {
    java.util.ArrayList<float[]> out = new java.util.ArrayList<>(nodes.size());
    for (Node n : nodes) out.add(new float[]{ n.x, n.z, n.w, n.d, n.eye });
    return out;
  }
}
