package com.example.game.World.Editor;

/** Shared state for the editor (current tool, brush settings, paint texture). */
public class EditorState {

  public enum Tool { RAISE, LOWER, PAINT }

  private Tool  tool        = Tool.RAISE;
  private int   brushRadius = 2;     // tiles
  private short brushDelta  = 1;     // height units per click
  private int   paintTexId  = 1;     // 0 = default texture

  // --- getters ---
  public Tool  tool()        { return tool; }
  public int   radius()      { return brushRadius; }
  public short delta()       { return brushDelta; }
  public int   texId()       { return paintTexId; }

  // --- setters (clamped where sensible) ---
  public void setTool(Tool t)        { this.tool = (t == null) ? Tool.RAISE : t; }
  public void setRadius(int r)       { this.brushRadius = Math.max(1, r); }
  public void setDelta(short d)      { this.brushDelta  = (short)Math.max(1, d); }
  public void setTexId(int id)       { this.paintTexId  = Math.max(0, Math.min(255, id)); }

  // --- convenience helpers ---
  public void incRadius()            { setRadius(brushRadius + 1); }
  public void decRadius()            { setRadius(brushRadius - 1); }
  public void incDelta()             { setDelta((short)(brushDelta + 1)); }
  public void decDelta()             { setDelta((short)Math.max(1, brushDelta - 1)); }
  public void nextTool() {
    switch (tool) {
      case RAISE -> tool = Tool.LOWER;
      case LOWER -> tool = Tool.PAINT;
      case PAINT -> tool = Tool.RAISE;
    }
  }
}
