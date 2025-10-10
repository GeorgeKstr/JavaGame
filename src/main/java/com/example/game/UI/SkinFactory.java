package com.example.game.UI;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

/** Minimal programmatic skin with button/toggle/slider styles (horizontal & vertical). */
public class SkinFactory {

  public static Skin create() {
    Skin skin = new Skin();

    // ---- 1×1 white texture & region ----
    Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pm.setColor(Color.WHITE);
    pm.fill();
    Texture whiteTex = new Texture(pm);
    pm.dispose();
    skin.add("white", whiteTex);
    skin.add("white", new TextureRegion(whiteTex)); // so skin.getRegion("white") works

    // ---- font ----
    BitmapFont font = new BitmapFont();
    skin.add("default-font", font, BitmapFont.class);

    // ---- colors ----
    Color btnUp   = new Color(0.18f, 0.20f, 0.25f, 1f);
    Color btnOver = new Color(0.24f, 0.27f, 0.33f, 1f);
    Color btnDown = new Color(0.14f, 0.16f, 0.20f, 1f);
    Color bgCol   = new Color(0.10f, 0.11f, 0.14f, 1f);

    Color cbOff   = new Color(0.20f, 0.22f, 0.28f, 1f);
    Color cbOn    = new Color(0.35f, 0.75f, 0.45f, 1f);

    Color scrollTrack = new Color(1f,1f,1f,0.08f);
    Color scrollKnob  = new Color(1f,1f,1f,0.25f);

    // ---- drawables ----
    skin.add("btn-up",   tinted(whiteTex, btnUp),   Drawable.class);
    skin.add("btn-over", tinted(whiteTex, btnOver), Drawable.class);
    skin.add("btn-down", tinted(whiteTex, btnDown), Drawable.class);
    skin.add("bg",       tinted(whiteTex, bgCol),   Drawable.class);

    NinePatchDrawable cbOffDr = rounded(whiteTex, cbOff);
    NinePatchDrawable cbOnDr  = rounded(whiteTex, cbOn);
    cbOffDr.setMinWidth(18f); cbOffDr.setMinHeight(18f);
    cbOnDr.setMinWidth(18f);  cbOnDr.setMinHeight(18f);
    skin.add("cb-off", cbOffDr, Drawable.class);
    skin.add("cb-on",  cbOnDr,  Drawable.class);

    // scrollbars
    skin.add("scroll",       tinted(whiteTex, scrollTrack), Drawable.class);
    skin.add("scroll-knob",  tinted(whiteTex, scrollKnob),  Drawable.class);

    // ---- Label styles ----
    Label.LabelStyle label = new Label.LabelStyle(font, Color.WHITE);
    skin.add("default", label);

    Label.LabelStyle title = new Label.LabelStyle(font, Color.valueOf("C8F7FF"));
    title.font.getData().setScale(1.2f);
    skin.add("title", title);

    // ---- TextButton style ----
    TextButton.TextButtonStyle tbs = new TextButton.TextButtonStyle();
    tbs.up   = skin.getDrawable("btn-up");
    tbs.over = skin.getDrawable("btn-over");
    tbs.down = skin.getDrawable("btn-down");
    tbs.font = font;
    tbs.fontColor = Color.WHITE;
    skin.add("default", tbs);

    // ---- Button style (plain) ----
    Button.ButtonStyle bs = new Button.ButtonStyle();
    bs.up   = skin.getDrawable("btn-up");
    bs.over = skin.getDrawable("btn-over");
    bs.down = skin.getDrawable("btn-down");
    skin.add("default", bs);

    // ---- Toggle Button style ----
    Button.ButtonStyle toggle = new Button.ButtonStyle();
    toggle.up      = skin.getDrawable("btn-up");
    toggle.over    = skin.getDrawable("btn-over");
    toggle.down    = skin.getDrawable("btn-down");
    toggle.checked = tinted(whiteTex, new Color(0.16f, 0.35f, 0.22f, 1f)); // green-ish when checked
    skin.add("toggle", toggle);

    // ---- Toggle TextButton style ----
    TextButton.TextButtonStyle toggleTBS = new TextButton.TextButtonStyle();
    toggleTBS.up       = skin.getDrawable("btn-up");
    toggleTBS.over     = skin.getDrawable("btn-over");
    toggleTBS.down     = skin.getDrawable("btn-down");
    toggleTBS.checked  = tinted(whiteTex, new Color(0.16f, 0.35f, 0.22f, 1f)); // green-ish when checked
    toggleTBS.font     = font;
    toggleTBS.fontColor = Color.WHITE;
    skin.add("toggle", toggleTBS);

    // ---- CheckBox style ----
    CheckBox.CheckBoxStyle cbs = new CheckBox.CheckBoxStyle();
    cbs.checkboxOff = skin.getDrawable("cb-off");
    cbs.checkboxOn  = skin.getDrawable("cb-on");
    cbs.font = font;
    cbs.fontColor = Color.WHITE;
    // Slight background when hovered (optional)
    cbs.over = skin.getDrawable("btn-over");
    skin.add("default", cbs);

    // ---- ScrollPane style ----
    ScrollPane.ScrollPaneStyle sps = new ScrollPane.ScrollPaneStyle();
    sps.background   = skin.getDrawable("bg");
    sps.hScroll      = skin.getDrawable("scroll");
    sps.hScrollKnob  = skin.getDrawable("scroll-knob");
    sps.vScroll      = skin.getDrawable("scroll");
    sps.vScrollKnob  = skin.getDrawable("scroll-knob");
    skin.add("default", sps);

    // ---- TextField style ----
    TextField.TextFieldStyle tfs = new TextField.TextFieldStyle();
    tfs.background = skin.getDrawable("btn-up");
    tfs.font = font;
    tfs.fontColor = Color.WHITE;
    tfs.cursor = tinted(whiteTex, Color.WHITE);
    tfs.selection = tinted(whiteTex, new Color(1f,1f,1f,0.25f));
    skin.add("default", tfs);

    // ---- List style (used by SelectBox's dropdown) ----
    List.ListStyle listStyle = new List.ListStyle();
    listStyle.font = font;
    listStyle.fontColorSelected = Color.WHITE;
    listStyle.fontColorUnselected = Color.LIGHT_GRAY;
    listStyle.selection = tinted(whiteTex, new Color(1f, 1f, 1f, 0.22f)); // highlight row
    listStyle.background = skin.getDrawable("bg"); // subtle bg behind rows
    skin.add("default", listStyle);

    // ---- SelectBox style ----
    SelectBox.SelectBoxStyle sb = new SelectBox.SelectBoxStyle();
    sb.font = font;
    sb.fontColor = Color.WHITE;
    sb.background = skin.getDrawable("btn-up");   // closed state
    sb.scrollStyle = skin.get(ScrollPane.ScrollPaneStyle.class); // reuse your default scroll
    sb.listStyle = listStyle;
    sb.backgroundOver = skin.getDrawable("btn-over");
    sb.backgroundOpen = skin.getDrawable("btn-over"); // when dropdown is open
    skin.add("default", sb);

    Slider.SliderStyle sliderH = new Slider.SliderStyle();
    NinePatchDrawable track = tinted(whiteTex, scrollTrack);
    track.setMinHeight(8f);      // thicker line
    track.setLeftWidth(6f);      // some padding so knob can sit inside track
    track.setRightWidth(6f);
    sliderH.background = track;

    NinePatchDrawable knob = tinted(whiteTex, scrollKnob);
    knob.setMinWidth(18f);       // bigger knob
    knob.setMinHeight(18f);
    sliderH.knob = knob;

    // (Optional) pre/post track fill
    sliderH.knobBefore = tinted(whiteTex, new Color(1f,1f,1f,0.18f));
    sliderH.knobAfter  = tinted(whiteTex, new Color(1f,1f,1f,0.10f));
    sliderH.knobBefore.setMinHeight(8f);
    sliderH.knobAfter.setMinHeight(8f);

    // Register styles
    skin.add("default-horizontal", sliderH);
    skin.add("default-vertical", sliderH);   // reuse; fine for our UI
    skin.add("default", sliderH);

    // ---- Window / Dialog style ----
    Window.WindowStyle ws = new Window.WindowStyle();
    ws.titleFont = font;
    ws.titleFontColor = Color.WHITE;
    ws.background = skin.getDrawable("bg");
    skin.add("default", ws);

    return skin;
  }

  private static NinePatchDrawable tinted(Texture tex, Color color) {
    NinePatch patch = new NinePatch(tex, 0, 0, 0, 0);
    patch.setColor(color);
    return new NinePatchDrawable(patch);
  }

  private static NinePatchDrawable rounded(Texture tex, Color color) {
    NinePatch patch = new NinePatch(tex, 0, 0, 0, 0);
    patch.setColor(color);
    return new NinePatchDrawable(patch);
  }

  public static ClickListener onClick(Runnable r) {
    return new ClickListener() {
      @Override public void clicked(InputEvent event, float x, float y) { r.run(); }
    };
  }
}
