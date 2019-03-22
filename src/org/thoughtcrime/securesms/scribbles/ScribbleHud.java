package org.thoughtcrime.securesms.scribbles;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.components.ComposeText;
import org.thoughtcrime.securesms.components.InputAwareLayout;
import org.thoughtcrime.securesms.components.SendButton;
import org.thoughtcrime.securesms.components.emoji.EmojiDrawer;
import org.thoughtcrime.securesms.components.emoji.EmojiToggle;
import org.thoughtcrime.securesms.scribbles.widget.ColorPaletteAdapter;
import org.thoughtcrime.securesms.scribbles.widget.VerticalSlideColorPicker;
import org.thoughtcrime.securesms.util.CharacterCalculator.CharacterState;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.views.Stub;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Locale;
import java.util.Set;

/**
 * The HUD (heads-up display) that contains all of the tools for interacting with
 * {@link org.thoughtcrime.securesms.scribbles.widget.ScribbleView}
 */
public class ScribbleHud extends LinearLayout {

  private View                     drawButton;
  private View                     highlightButton;
  private View                     textButton;
  private View                     stickerButton;
  private View                     undoButton;
  private View                     deleteButton;
  private View                     confirmButton;
  private VerticalSlideColorPicker colorPicker;
  private RecyclerView             colorPalette;

  private EventListener       eventListener;
  private ColorPaletteAdapter colorPaletteAdapter;

  public ScribbleHud(@NonNull Context context) {
    super(context);
    initialize();
  }

  public ScribbleHud(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public ScribbleHud(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  private void initialize() {
    inflate(getContext(), R.layout.scribble_hud, this);
    setOrientation(VERTICAL);

    drawButton      = findViewById(R.id.scribble_draw_button);
    highlightButton = findViewById(R.id.scribble_highlight_button);
    textButton      = findViewById(R.id.scribble_text_button);
    stickerButton   = findViewById(R.id.scribble_sticker_button);
    undoButton      = findViewById(R.id.scribble_undo_button);
    deleteButton    = findViewById(R.id.scribble_delete_button);
    confirmButton   = findViewById(R.id.scribble_confirm_button);
    colorPicker     = findViewById(R.id.scribble_color_picker);
    colorPalette    = findViewById(R.id.scribble_color_palette);

    initializeViews();
    setMode(Mode.NONE);
  }

  private void initializeViews() {
    undoButton.setOnClickListener(v -> {
      if (eventListener != null) {
        eventListener.onUndo();
      }
    });

    deleteButton.setOnClickListener(v -> {
      if (eventListener != null) {
        eventListener.onDelete();
      }
      setMode(Mode.NONE);
    });

    confirmButton.setOnClickListener(v -> setMode(Mode.NONE));

    colorPaletteAdapter = new ColorPaletteAdapter();
    colorPaletteAdapter.setEventListener(colorPicker::setActiveColor);

    colorPalette.setLayoutManager(new LinearLayoutManager(getContext()));
    colorPalette.setAdapter(colorPaletteAdapter);
  }

  public void setColorPalette(@NonNull Set<Integer> colors) {
    colorPaletteAdapter.setColors(colors);
  }

  public int getActiveColor() {
    return colorPicker.getActiveColor();
  }

  public void setActiveColor(int color) {
    colorPicker.setActiveColor(color);
  }

  public void setEventListener(@Nullable EventListener eventListener) {
    this.eventListener = eventListener;
  }

  public void enterMode(@NonNull Mode mode) {
    setMode(mode, false);
  }

  private void setMode(@NonNull Mode mode) {
    setMode(mode, true);
  }

  private void setMode(@NonNull Mode mode, boolean notify) {
    switch (mode) {
      case NONE:      presentModeNone();      break;
      case DRAW:      presentModeDraw();      break;
      case HIGHLIGHT: presentModeHighlight(); break;
      case TEXT:      presentModeText();      break;
      case STICKER:   presentModeSticker();   break;
    }

    if (notify && eventListener != null) {
      eventListener.onModeStarted(mode);
    }
  }

  private void presentModeNone() {
    drawButton.setVisibility(VISIBLE);
    highlightButton.setVisibility(VISIBLE);
    textButton.setVisibility(VISIBLE);
    stickerButton.setVisibility(VISIBLE);

    undoButton.setVisibility(GONE);
    deleteButton.setVisibility(GONE);
    confirmButton.setVisibility(GONE);
    colorPicker.setVisibility(GONE);
    colorPalette.setVisibility(GONE);

    drawButton.setOnClickListener(v -> setMode(Mode.DRAW));
    highlightButton.setOnClickListener(v -> setMode(Mode.HIGHLIGHT));
    textButton.setOnClickListener(v -> setMode(Mode.TEXT));
    stickerButton.setOnClickListener(v -> setMode(Mode.STICKER));
  }

  private void presentModeDraw() {
    confirmButton.setVisibility(VISIBLE);
    undoButton.setVisibility(VISIBLE);
    colorPicker.setVisibility(VISIBLE);
    colorPalette.setVisibility(VISIBLE);

    drawButton.setVisibility(GONE);
    highlightButton.setVisibility(GONE);
    textButton.setVisibility(GONE);
    stickerButton.setVisibility(GONE);
    deleteButton.setVisibility(GONE);


    colorPicker.setOnColorChangeListener(standardOnColorChangeListener);
    colorPicker.setActiveColor(Color.RED);
  }

  private void presentModeHighlight() {
    confirmButton.setVisibility(VISIBLE);
    undoButton.setVisibility(VISIBLE);
    colorPicker.setVisibility(VISIBLE);
    colorPalette.setVisibility(VISIBLE);

    drawButton.setVisibility(GONE);
    highlightButton.setVisibility(GONE);
    textButton.setVisibility(GONE);
    deleteButton.setVisibility(GONE);
    stickerButton.setVisibility(GONE);

    colorPicker.setOnColorChangeListener(highlightOnColorChangeListener);
    colorPicker.setActiveColor(Color.YELLOW);
  }

  private void presentModeText() {
    confirmButton.setVisibility(VISIBLE);
    deleteButton.setVisibility(VISIBLE);
    colorPicker.setVisibility(VISIBLE);
    colorPalette.setVisibility(VISIBLE);

    textButton.setVisibility(GONE);
    drawButton.setVisibility(GONE);
    highlightButton.setVisibility(GONE);
    stickerButton.setVisibility(GONE);
    undoButton.setVisibility(GONE);

    colorPicker.setOnColorChangeListener(standardOnColorChangeListener);
    colorPicker.setActiveColor(Color.WHITE);
  }

  private void presentModeSticker() {
    deleteButton.setVisibility(VISIBLE);
    confirmButton.setVisibility(VISIBLE);

    drawButton.setVisibility(GONE);
    highlightButton.setVisibility(GONE);
    textButton.setVisibility(GONE);
    stickerButton.setVisibility(GONE);
    undoButton.setVisibility(GONE);
    colorPicker.setVisibility(GONE);
    colorPalette.setVisibility(GONE);
  }

  private final VerticalSlideColorPicker.OnColorChangeListener standardOnColorChangeListener = new VerticalSlideColorPicker.OnColorChangeListener() {
    @Override
    public void onColorChange(int selectedColor) {
      if (eventListener != null) {
        eventListener.onColorChange(selectedColor);
      }
    }
  };

  private final VerticalSlideColorPicker.OnColorChangeListener highlightOnColorChangeListener = new VerticalSlideColorPicker.OnColorChangeListener() {
    @Override
    public void onColorChange(int selectedColor) {
      if (eventListener != null) {
        int r = Color.red(selectedColor);
        int g = Color.green(selectedColor);
        int b = Color.blue(selectedColor);
        int a = 128;

        eventListener.onColorChange(Color.argb(a, r, g, b));
      }
    }
  };

  public enum Mode {
    NONE, DRAW, HIGHLIGHT, TEXT, STICKER
  }

  public interface EventListener {
    void onModeStarted(@NonNull Mode mode);
    void onColorChange(int color);
    void onUndo();
    void onDelete();
  }
}
