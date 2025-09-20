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

    skin.add("cb-off",   rounded(whiteTex, cbOff),  Drawable.class);
    skin.add("cb-on",    rounded(whiteTex, cbOn),   Drawable.class);

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

    // ---- CheckBox style ----
    CheckBox.CheckBoxStyle cbs = new CheckBox.CheckBoxStyle();
    cbs.checkboxOff = skin.getDrawable("cb-off");
    cbs.checkboxOn  = skin.getDrawable("cb-on");
    cbs.font = font;
    cbs.fontColor = Color.WHITE;
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
