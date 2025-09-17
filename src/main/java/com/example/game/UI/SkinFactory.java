package com.example.game.UI;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

public class SkinFactory {

  public static Skin create() {
    Skin skin = new Skin();

    // 1x1 white texture
    Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pm.setColor(Color.WHITE);
    pm.fill();
    Texture white = new Texture(pm);
    pm.dispose();
    skin.add("white", white);

    // Font
    BitmapFont font = new BitmapFont();
    skin.add("default-font", font, BitmapFont.class);

    // Colors
    Color up   = new Color(0.18f, 0.20f, 0.25f, 1f);
    Color over = new Color(0.24f, 0.27f, 0.33f, 1f);
    Color down = new Color(0.14f, 0.16f, 0.20f, 1f);
    Color bg   = new Color(0.10f, 0.11f, 0.14f, 1f);

    // Register drawables **as Drawable.class** so getDrawable() finds them
    skin.add("btn-up",   tinted(white, up),   Drawable.class);
    skin.add("btn-over", tinted(white, over), Drawable.class);
    skin.add("btn-down", tinted(white, down), Drawable.class);
    skin.add("bg",       tinted(white, bg),   Drawable.class);

    // Label styles
    Label.LabelStyle label = new Label.LabelStyle(font, Color.WHITE);
    skin.add("default", label);
    Label.LabelStyle title = new Label.LabelStyle(font, Color.valueOf("C8F7FF"));
    title.font.getData().setScale(1.2f);
    skin.add("title", title);

    // Button style
    TextButton.TextButtonStyle bs = new TextButton.TextButtonStyle();
    bs.up   = skin.getDrawable("btn-up");
    bs.over = skin.getDrawable("btn-over");
    bs.down = skin.getDrawable("btn-down");
    bs.font = font;
    bs.fontColor = Color.WHITE;
    skin.add("default", bs);

    return skin;
  }

  private static NinePatchDrawable tinted(Texture tex, Color color) {
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
