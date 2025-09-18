package com.example.game.World.Editor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/**
 * Scrollable grid of texture swatches.
 * - Swatches are Images tinted with setColor(...) (no Drawable#tint).
 * - Selection is shown by swapping the Button style to a highlighted variant.
 */
public class TexturePalette {

  private final Skin skin;
  private final Table grid = new Table();
  private final ScrollPane scroll;

  private int[] ids = new int[]{0,1,2,3,4,5,6,7};
  private int selected = 1;
  private Runnable onChange;

  private Button lastSelectedBtn;

  // cached drawables from skin
  private final Drawable up, over, down;

  public TexturePalette(Skin skin, int[] ids) {
    this.skin = skin;
    if (ids != null && ids.length > 0) this.ids = ids.clone();

    // pull once
    up   = skin.getDrawable("btn-up");
    over = skin.getDrawable("btn-over");
    down = skin.getDrawable("btn-down");

    grid.defaults().pad(4);
    scroll = new ScrollPane(grid, skin); // needs ScrollPaneStyle "default"
    scroll.setFadeScrollBars(false);
    scroll.setScrollingDisabled(true, false);

    rebuild();
  }

  /** Replace the palette IDs and rebuild. */
  public void setIds(int[] newIds) {
    if (newIds == null || newIds.length == 0) return;
    this.ids = newIds.clone();
    boolean stillSelected = false;
    for (int id : ids) if (id == selected) { stillSelected = true; break; }
    if (!stillSelected) selected = ids[0];
    rebuild();
  }

  /** Widget to add to your UI layout. */
  public Actor widget() { return scroll; }

  public int selectedId() { return selected; }

  public void onChange(Runnable r) { this.onChange = r; }

  // ------------------------------------------------------------------

  private void rebuild() {
    grid.clear();
    int cols = 4, col = 0;
    lastSelectedBtn = null;

    for (int id : ids) {
      Button b = makeSwatchButton(id);
      grid.add(b).size(72, 72);

      if (id == selected) {
        markSelected(b, true);
        lastSelectedBtn = b;
      }
      if (++col >= cols) { grid.row(); col = 0; }
    }
  }

  private Button makeSwatchButton(int id) {
    Button b = new Button(skin); // requires ButtonStyle "default"

    // base tile image (we tint the Image, not the Drawable)
    Image tile = new Image(up);
    tile.setColor(makeSwatchColor(id)); // <- no Drawable#tint

    // id label overlay
    Label label = new Label(String.valueOf(id), skin);

    // stack them
    Stack stack = new Stack();
    stack.add(tile);
    stack.add(label);
    b.add(stack).grow();

    final int capture = id;
    b.addListener(com.example.game.UI.SkinFactory.onClick(() -> {
      if (lastSelectedBtn != null) markSelected(lastSelectedBtn, false);
      selected = capture;
      markSelected(b, true);
      lastSelectedBtn = b;
      if (onChange != null) onChange.run();
    }));

    return b;
  }

  private void markSelected(Button b, boolean sel) {
    // Clone style and swap drawables to show a highlight when selected
    Button.ButtonStyle st = new Button.ButtonStyle(b.getStyle());
    if (sel) {
      st.up = over;   // brighter background when selected
      st.over = down; // pressed-ish when hovered
      st.down = down;
    } else {
      st.up = up;
      st.over = over;
      st.down = down;
    }
    b.setStyle(st);
  }

  private static Color makeSwatchColor(int id) {
    if (id == 0) return new Color(0.15f, 0.18f, 0.22f, 1f); // default texture swatch
    // generate a stable-ish color per id
    float r = 0.30f + 0.05f * ((id * 3) % 7);
    float g = 0.22f + 0.04f * ((id * 5) % 7);
    float b = 0.26f + 0.06f * ((id * 7) % 7);
    return new Color(r, g, b, 1f);
  }
}
