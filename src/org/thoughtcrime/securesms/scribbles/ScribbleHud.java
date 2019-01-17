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
public class ScribbleHud extends InputAwareLayout implements ViewTreeObserver.OnGlobalLayoutListener {

  private View                     drawButton;
  private View                     highlightButton;
  private View                     textButton;
  private View                     stickerButton;
  private View                     undoButton;
  private View                     deleteButton;
  private View                     saveButton;
  private VerticalSlideColorPicker colorPicker;
  private RecyclerView             colorPalette;
  private ViewGroup                inputContainer;
  private ComposeText              composeText;
  private SendButton               sendButton;
  private ViewGroup                sendButtonBkg;
  private EmojiToggle              emojiToggle;
  private Stub<EmojiDrawer>        emojiDrawer;
  private TextView                 charactersLeft;

  private EventListener       eventListener;
  private ColorPaletteAdapter colorPaletteAdapter;
  private int                 visibleHeight;
  private Locale              locale;

  private final Rect visibleBounds = new Rect();

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

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    getRootView().getViewTreeObserver().addOnGlobalLayoutListener(this);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    getRootView().getViewTreeObserver().removeGlobalOnLayoutListener(this);
  }

  @Override
  public void onGlobalLayout() {
    getRootView().getWindowVisibleDisplayFrame(visibleBounds);

    int currentVisibleHeight = visibleBounds.height();

    if (currentVisibleHeight != visibleHeight) {
      getLayoutParams().height = currentVisibleHeight;
      layout(visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom);
      requestLayout();

      visibleHeight = currentVisibleHeight;
    }
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
    saveButton      = findViewById(R.id.scribble_save_button);
    colorPicker     = findViewById(R.id.scribble_color_picker);
    colorPalette    = findViewById(R.id.scribble_color_palette);
    inputContainer  = findViewById(R.id.scribble_compose_container);
    composeText     = findViewById(R.id.scribble_compose_text);
    sendButton      = findViewById(R.id.scribble_send_button);
    sendButtonBkg   = findViewById(R.id.scribble_send_button_bkg);
    emojiToggle     = findViewById(R.id.scribble_emoji_toggle);
    emojiDrawer     = new Stub<>(findViewById(R.id.scribble_emoji_drawer_stub));
    charactersLeft  = findViewById(R.id.scribble_characters_left);

    initializeViews();
    setMode(Mode.NONE);
    setTransport(Optional.absent());
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

    saveButton.setOnClickListener(v -> {
      if (eventListener != null) {
        eventListener.onEditComplete(Optional.absent(), Optional.absent());
      }
      setMode(Mode.NONE);
    });

    sendButton.setOnClickListener(v -> {
      if (eventListener != null) {
        if (isKeyboardOpen()) {
          hideSoftkey(composeText, null);
        }
        eventListener.onEditComplete(Optional.of(composeText.getTextTrimmed()), Optional.of(sendButton.getSelectedTransport()));
      }
      setMode(Mode.NONE);
    });

    sendButton.addOnTransportChangedListener((newTransport, manuallySelected) -> {
      presentCharactersRemaining();
      composeText.setTransport(newTransport);
      sendButtonBkg.getBackground().setColorFilter(newTransport.getBackgroundColor(), PorterDuff.Mode.MULTIPLY);
      sendButtonBkg.getBackground().invalidateSelf();
    });

    ComposeKeyPressedListener composeKeyPressedListener = new ComposeKeyPressedListener();

    composeText.setOnKeyListener(composeKeyPressedListener);
    composeText.addTextChangedListener(composeKeyPressedListener);
    composeText.setOnClickListener(composeKeyPressedListener);
    composeText.setOnFocusChangeListener(composeKeyPressedListener);

    emojiToggle.setOnClickListener(this::onEmojiToggleClicked);

    colorPaletteAdapter = new ColorPaletteAdapter();
    colorPaletteAdapter.setEventListener(colorPicker::setActiveColor);

    colorPalette.setLayoutManager(new LinearLayoutManager(getContext()));
    colorPalette.setAdapter(colorPaletteAdapter);
  }

  public void setLocale(@NonNull Locale locale) {
    this.locale = locale;
  }

  public void setTransport(@NonNull Optional<TransportOption> transport) {
    if (transport.isPresent()) {
      saveButton.setVisibility(GONE);
      inputContainer.setVisibility(VISIBLE);
      sendButton.setTransport(transport.get());
    } else {
      saveButton.setVisibility(VISIBLE);
      inputContainer.setVisibility(GONE);
    }
  }

  public void dismissEmojiKeyboard() {
    hideCurrentInput(composeText);
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
    colorPicker.setVisibility(GONE);
    colorPalette.setVisibility(GONE);

    drawButton.setOnClickListener(v -> setMode(Mode.DRAW));
    highlightButton.setOnClickListener(v -> setMode(Mode.HIGHLIGHT));
    textButton.setOnClickListener(v -> setMode(Mode.TEXT));
    stickerButton.setOnClickListener(v -> setMode(Mode.STICKER));
  }

  private void presentModeDraw() {
    drawButton.setVisibility(VISIBLE);
    undoButton.setVisibility(VISIBLE);
    colorPicker.setVisibility(VISIBLE);
    colorPalette.setVisibility(VISIBLE);

    highlightButton.setVisibility(GONE);
    textButton.setVisibility(GONE);
    stickerButton.setVisibility(GONE);
    deleteButton.setVisibility(GONE);

    drawButton.setOnClickListener(v -> setMode(Mode.NONE));

    colorPicker.setOnColorChangeListener(standardOnColorChangeListener);
    colorPicker.setActiveColor(Color.RED);
  }

  private void presentModeHighlight() {
    highlightButton.setVisibility(VISIBLE);
    undoButton.setVisibility(VISIBLE);
    colorPicker.setVisibility(VISIBLE);
    colorPalette.setVisibility(VISIBLE);

    drawButton.setVisibility(GONE);
    textButton.setVisibility(GONE);
    stickerButton.setVisibility(GONE);
    deleteButton.setVisibility(GONE);

    highlightButton.setOnClickListener(v -> setMode(Mode.NONE));

    colorPicker.setOnColorChangeListener(highlightOnColorChangeListener);
    colorPicker.setActiveColor(Color.YELLOW);
  }

  private void presentModeText() {
    textButton.setVisibility(VISIBLE);
    deleteButton.setVisibility(VISIBLE);
    colorPicker.setVisibility(VISIBLE);
    colorPalette.setVisibility(VISIBLE);

    drawButton.setVisibility(GONE);
    highlightButton.setVisibility(GONE);
    stickerButton.setVisibility(GONE);
    undoButton.setVisibility(GONE);

    textButton.setOnClickListener(v -> setMode(Mode.NONE));

    colorPicker.setOnColorChangeListener(standardOnColorChangeListener);
    colorPicker.setActiveColor(Color.WHITE);
  }

  private void presentModeSticker() {
    stickerButton.setVisibility(VISIBLE);
    deleteButton.setVisibility(VISIBLE);

    drawButton.setVisibility(GONE);
    highlightButton.setVisibility(GONE);
    textButton.setVisibility(GONE);
    undoButton.setVisibility(GONE);
    colorPicker.setVisibility(GONE);
    colorPalette.setVisibility(GONE);

    stickerButton.setOnClickListener(v -> setMode(Mode.NONE));
  }

  private void presentCharactersRemaining() {
    String          messageBody     = composeText.getTextTrimmed();
    TransportOption transportOption = sendButton.getSelectedTransport();
    CharacterState  characterState  = transportOption.calculateCharacters(messageBody);

    if (characterState.charactersRemaining <= 15 || characterState.messagesSpent > 1) {
      charactersLeft.setText(String.format(locale,
                                           "%d/%d (%d)",
                                           characterState.charactersRemaining,
                                           characterState.maxMessageSize,
                                           characterState.messagesSpent));
      charactersLeft.setVisibility(View.VISIBLE);
    } else {
      charactersLeft.setVisibility(View.GONE);
    }
  }

  private void onEmojiToggleClicked(View v) {
    if (!emojiDrawer.resolved()) {
      emojiToggle.attach(emojiDrawer.get());
      emojiDrawer.get().setEmojiEventListener(new EmojiDrawer.EmojiEventListener() {
        @Override
        public void onKeyEvent(KeyEvent keyEvent) {
          composeText.dispatchKeyEvent(keyEvent);
        }

        @Override
        public void onEmojiSelected(String emoji) {
          composeText.insertEmoji(emoji);
        }
      });
    }

    if (getCurrentInput() == emojiDrawer.get()) {
      showSoftkey(composeText);
    } else {
      hideSoftkey(composeText, () -> post(() -> show(composeText, emojiDrawer.get())));
    }
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

  private class ComposeKeyPressedListener implements OnKeyListener, OnClickListener, TextWatcher, OnFocusChangeListener {

    int beforeLength;

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          if (TextSecurePreferences.isEnterSendsEnabled(getContext())) {
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public void onClick(View v) {
      showSoftkey(composeText);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,int after) {
      beforeLength = composeText.getTextTrimmed().length();
    }

    @Override
    public void afterTextChanged(Editable s) {
      presentCharactersRemaining();
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before,int count) {}

    @Override
    public void onFocusChange(View v, boolean hasFocus) {}
  }

  public enum Mode {
    NONE, DRAW, HIGHLIGHT, TEXT, STICKER
  }

  public interface EventListener {
    void onModeStarted(@NonNull Mode mode);
    void onColorChange(int color);
    void onUndo();
    void onDelete();
    void onEditComplete(@NonNull Optional<String> message, @NonNull Optional<TransportOption> transport);
  }
}
