package com.example.game.World.Editor.Tools;

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

import java.util.ArrayList;
import java.util.List;

public final class WalkTool implements Tool {
  private final TerrainSystem terrain;
  private final FpsCameraController fps;

  private final List<Node> nodes = new ArrayList<>();
  private com.badlogic.gdx.scenes.scene2d.ui.List<String> listUI;
  private TextField nameField;
  private Slider eyeSlider;
  private Label eyeLabel;
  private int selectedIndex = -1;

  public WalkTool(TerrainSystem t, FpsCameraController fpsCam){ this.terrain = t; this.fps = fpsCam; }
  @Override public String name() { return "Walk"; }

  @Override public void onSelected() {
    if (nodes.isEmpty()) { try { loadPlayersFromMap(); rebuildUIList(); } catch (Throwable ignored) {} }
  }

  @Override public void buildUI(Table parent, Skin skin) {
    parent.add(new Label("Walk/Players", skin, "title")).left().row();

    Table row = new Table(); parent.add(row).left().row();
    TextButton add = new TextButton("Add at cursor", skin);
    TextButton del = new TextButton("Delete", skin);
    TextButton use = new TextButton("Use node", skin);
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
      Camera cam = EditorContext.getCamera(); if (cam == null) return; 
      com.badlogic.gdx.math.collision.Ray ray = cam.getPickRay(Gdx.input.getX(), Gdx.input.getY());
      Vector3 hit = terrain.getPicker().rayIntersectTerrain(ray, terrain);
      if (hit == null) return;
      Node n = new Node(); n.x = hit.x; n.z = hit.z; n.w = 0.6f; n.d = 0.6f; n.eye = 1.7f; n.name = "Spawn";
      nodes.add(n); selectedIndex = nodes.size()-1; rebuildUIList(); savePlayersToMap();
    }));

    del.addListener(com.example.game.UI.SkinFactory.onClick(() -> {
      int idx = listUI.getSelectedIndex(); if (idx >= 0 && idx < nodes.size()) { nodes.remove(idx); selectedIndex = Math.min(idx, nodes.size()-1); rebuildUIList(); savePlayersToMap(); }
    }));

    use.addListener(com.example.game.UI.SkinFactory.onClick(() -> {
      int idx = listUI.getSelectedIndex(); if (idx < 0 || idx >= nodes.size()) return;
      Node n = nodes.get(idx);
      FpsCameraController.State s = new FpsCameraController.State();
      float hintY = EditorContext.getCamera() != null ? EditorContext.getCamera().position.y : 0f;
      float h = 0f; try { if (terrain != null) h = terrain.heightAt(n.x, n.z, hintY); } catch (Throwable ignored) {}
      s.pos.set(n.x, h, n.z); s.yawDeg = 0f; s.pitchDeg = 0f; s.eyeHeight = n.eye;
      fps.setState(s);
    }));

    listUI.addListener(new ChangeListener(){ @Override public void changed(ChangeListener.ChangeEvent event, Actor actor){ selectedIndex = listUI.getSelectedIndex(); syncSelectionUI(); }});
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

  // Persistence to map zip
  private void savePlayersToMap() {
    StringBuilder sb = new StringBuilder(256);
    sb.append('{').append("\"players\":[");
    for (int i=0;i<nodes.size();i++) {
      Node n = nodes.get(i);
      if (i>0) sb.append(',');
      sb.append('{')
        .append("\"name\":\"").append(n.name==null?"":n.name.replace("\"","\\\"")).append('\"')
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

  // Rendering of AABBs could be added in render3D via layer, but out-of-scope here

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

  private static final class Node {
    String name; float x, z; float w, d; float eye;
  }
}
