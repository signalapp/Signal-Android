package org.thoughtcrime.securesms.megaphone;

import android.content.Intent;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.StringRes;

import com.bumptech.glide.RequestBuilder;

import org.thoughtcrime.securesms.megaphone.Megaphones.Event;

/**
 * For guidance on creating megaphones, see {@link Megaphones}.
 */
public class Megaphone {

  @SuppressWarnings("ConstantConditions")
  public static final Megaphone NONE = new Megaphone.Builder(null, null).build();

  private final Event                  event;
  private final Style                  style;
  private final boolean                canSnooze;
  private final MegaphoneText          titleText;
  private final MegaphoneText          bodyText;
  private final int                    imageRes;
  private final int                    lottieRes;
  private final RequestBuilder<Drawable> requestBuilder;
  private final MegaphoneText          buttonText;
  private final EventListener          buttonListener;
  private final EventListener          snoozeListener;
  private final MegaphoneText          secondaryButtonText;
  private final EventListener          secondaryButtonListener;
  private final EventListener          onVisibleListener;

  private Megaphone(@NonNull Builder builder) {
    this.event                   = builder.event;
    this.style                   = builder.style;
    this.canSnooze               = builder.canSnooze;
    this.titleText               = builder.titleText;
    this.bodyText                = builder.bodyText;
    this.imageRes                = builder.imageRes;
    this.lottieRes               = builder.lottieRes;
    this.requestBuilder          = builder.requestBuilder;
    this.buttonText              = builder.buttonText;
    this.buttonListener          = builder.buttonListener;
    this.snoozeListener          = builder.snoozeListener;
    this.secondaryButtonText     = builder.secondaryButtonText;
    this.secondaryButtonListener = builder.secondaryButtonListener;
    this.onVisibleListener       = builder.onVisibleListener;
  }

  public @NonNull Event getEvent() {
    return event;
  }

  public boolean canSnooze() {
    return canSnooze;
  }

  public @NonNull Style getStyle() {
    return style;
  }

  public @NonNull MegaphoneText getTitle() {
    return titleText;
  }

  public @NonNull MegaphoneText getBody() {
    return bodyText;
  }

  public @RawRes int getLottieRes() {
    return lottieRes;
  }

  public @DrawableRes int getImageRes() {
    return imageRes;
  }

  public @Nullable RequestBuilder<Drawable> getImageRequestBuilder() {
    return requestBuilder;
  }

  public @Nullable MegaphoneText getButtonText() {
    return buttonText;
  }

  public boolean hasButton() {
    return buttonText != null && buttonText.hasText();
  }

  public @Nullable EventListener getButtonClickListener() {
    return buttonListener;
  }

  public @Nullable EventListener getSnoozeListener() {
    return snoozeListener;
  }

  public @Nullable MegaphoneText getSecondaryButtonText() {
    return secondaryButtonText;
  }

  public boolean hasSecondaryButton() {
    return secondaryButtonText != null && secondaryButtonText.hasText();
  }

  public @Nullable EventListener getSecondaryButtonClickListener() {
    return secondaryButtonListener;
  }

  public @Nullable EventListener getOnVisibleListener() {
    return onVisibleListener;
  }

  public static class Builder {

    private final Event event;
    private final Style style;

    private boolean                canSnooze;
    private MegaphoneText          titleText;
    private MegaphoneText          bodyText;
    private int                    imageRes;
    private int                    lottieRes;
    private RequestBuilder<Drawable> requestBuilder;
    private MegaphoneText          buttonText;
    private EventListener          buttonListener;
    private EventListener          snoozeListener;
    private MegaphoneText          secondaryButtonText;
    private EventListener          secondaryButtonListener;
    private EventListener          onVisibleListener;

    public Builder(@NonNull Event event, @NonNull Style style) {
      this.event = event;
      this.style = style;
    }

    public @NonNull Builder enableSnooze(@Nullable EventListener listener) {
      this.canSnooze      = true;
      this.snoozeListener = listener;
      return this;
    }

    public @NonNull Builder disableSnooze() {
      this.canSnooze      = false;
      this.snoozeListener = null;
      return this;
    }

    public @NonNull Builder setTitle(@StringRes int titleRes) {
      this.titleText = MegaphoneText.from(titleRes);
      return this;
    }

    public @NonNull Builder setTitle(@Nullable String title) {
      this.titleText = MegaphoneText.from(title);
      return this;
    }

    public @NonNull Builder setBody(@StringRes int bodyRes) {
      this.bodyText = MegaphoneText.from(bodyRes);
      return this;
    }

    public @NonNull Builder setBody(String body) {
      this.bodyText = MegaphoneText.from(body);
      return this;
    }

    public @NonNull Builder setImage(@DrawableRes int imageRes) {
      this.imageRes = imageRes;
      return this;
    }

    public @NonNull Builder setLottie(@RawRes int lottieRes) {
      this.lottieRes = lottieRes;
      return this;
    }

    public @NonNull Builder setImageRequestBuilder(@Nullable RequestBuilder<Drawable> requestBuilder) {
      this.requestBuilder = requestBuilder;
      return this;
    }

    public @NonNull Builder setActionButton(@StringRes int buttonTextRes, @NonNull EventListener listener) {
      this.buttonText     = MegaphoneText.from(buttonTextRes);
      this.buttonListener = listener;
      return this;
    }

    public @NonNull Builder setActionButton(@NonNull String buttonText, @NonNull EventListener listener) {
      this.buttonText     = MegaphoneText.from(buttonText);
      this.buttonListener = listener;
      return this;
    }

    public @NonNull Builder setSecondaryButton(@StringRes int secondaryButtonTextRes, @NonNull EventListener listener) {
      this.secondaryButtonText     = MegaphoneText.from(secondaryButtonTextRes);
      this.secondaryButtonListener = listener;
      return this;
    }

    public @NonNull Builder setSecondaryButton(@NonNull String secondaryButtonText, @NonNull EventListener listener) {
      this.secondaryButtonText     = MegaphoneText.from(secondaryButtonText);
      this.secondaryButtonListener = listener;
      return this;
    }

    public @NonNull Builder setOnVisibleListener(@Nullable EventListener listener) {
      this.onVisibleListener = listener;
      return this;
    }

    public @NonNull Megaphone build() {
      return new Megaphone(this);
    }
  }

  enum Style {
    /**
     * Specialized style for onboarding.
     */
    ONBOARDING,

    /**
     * Basic bottom of the screen megaphone with optional snooze and action buttons.
     */
    BASIC,

    /**
     * Indicates megaphone does not have a view but will call {@link MegaphoneActionController#onMegaphoneNavigationRequested(Intent)}
     * or {@link MegaphoneActionController#onMegaphoneNavigationRequested(Intent, int)} on the controller passed in
     * via the {@link #onVisibleListener}.
     */
    FULLSCREEN,

    /**
     * Similar to {@link Style#BASIC} but only provides a close button that will call {@link #buttonListener} if set,
     * otherwise, the event will be marked finished (it will not be shown again).
     */
    POPUP
  }

  public interface EventListener {
    void onEvent(@NonNull Megaphone megaphone, @NonNull MegaphoneActionController listener);
  }
}
