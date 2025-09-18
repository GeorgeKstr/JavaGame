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

    // ---- label styles ----
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

    // ---- Button style ----
    Button.ButtonStyle bs = new Button.ButtonStyle();
    bs.up   = skin.getDrawable("btn-up");
    bs.over = skin.getDrawable("btn-over");
    bs.down = skin.getDrawable("btn-down");
    skin.add("default", bs);

    // ---- CheckBox style ----
    CheckBox.CheckBoxStyle cbs = new CheckBox.CheckBoxStyle();
    cbs.checkboxOff = skin.getDrawable("cb-off");
    cbs.checkboxOn  = skin.getDrawable("cb-on");
    cbs.font = font;
    cbs.fontColor = Color.WHITE;
    skin.add("default", cbs);

    // ---- ScrollPane style (FIX for your error) ----
    ScrollPane.ScrollPaneStyle sps = new ScrollPane.ScrollPaneStyle();
    sps.background   = skin.getDrawable("bg");           // panel bg
    sps.hScroll      = skin.getDrawable("scroll");       // track
    sps.hScrollKnob  = skin.getDrawable("scroll-knob");  // knob
    sps.vScroll      = skin.getDrawable("scroll");
    sps.vScrollKnob  = skin.getDrawable("scroll-knob");
    skin.add("default", sps);

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
