package org.thoughtcrime.securesms.megaphone;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.megaphone.Megaphones.Event;

/**
 * For guidance on creating megaphones, see {@link Megaphones}.
 */
public class Megaphone {

  private final Event         event;
  private final Style         style;
  private final boolean       mandatory;
  private final boolean       canSnooze;
  private final int           titleRes;
  private final int           bodyRes;
  private final int           imageRes;
  private final int           buttonTextRes;
  private final EventListener buttonListener;
  private final EventListener snoozeListener;
  private final EventListener onVisibleListener;

  private Megaphone(@NonNull Builder builder) {
    this.event             = builder.event;
    this.style             = builder.style;
    this.mandatory         = builder.mandatory;
    this.canSnooze         = builder.canSnooze;
    this.titleRes          = builder.titleRes;
    this.bodyRes           = builder.bodyRes;
    this.imageRes          = builder.imageRes;
    this.buttonTextRes     = builder.buttonTextRes;
    this.buttonListener    = builder.buttonListener;
    this.snoozeListener    = builder.snoozeListener;
    this.onVisibleListener = builder.onVisibleListener;
  }

  public @NonNull Event getEvent() {
    return event;
  }

  public boolean isMandatory() {
    return mandatory;
  }

  public boolean canSnooze() {
    return canSnooze;
  }

  public @NonNull Style getStyle() {
    return style;
  }

  public @StringRes int getTitle() {
    return titleRes;
  }

  public @StringRes int getBody() {
    return bodyRes;
  }

  public @DrawableRes int getImage() {
    return imageRes;
  }

  public @StringRes int getButtonText() {
    return buttonTextRes;
  }

  public @Nullable EventListener getButtonClickListener() {
    return buttonListener;
  }

  public @Nullable EventListener getSnoozeListener() {
    return snoozeListener;
  }

  public @Nullable EventListener getOnVisibleListener() {
    return onVisibleListener;
  }

  public static class Builder {

    private final Event  event;
    private final Style  style;

    private boolean       mandatory;
    private boolean       canSnooze;
    private int           titleRes;
    private int           bodyRes;
    private int           imageRes;
    private int           buttonTextRes;
    private EventListener buttonListener;
    private EventListener snoozeListener;
    private EventListener onVisibleListener;


    public Builder(@NonNull Event event, @NonNull Style style) {
      this.event          = event;
      this.style          = style;
    }

    public @NonNull Builder setMandatory(boolean mandatory) {
      this.mandatory = mandatory;
      return this;
    }

    public @NonNull Builder enableSnooze(@Nullable EventListener listener) {
      this.canSnooze      = true;
      this.snoozeListener = listener;
      return this;
    }

    public @NonNull Builder disableSnooze() {
      this.canSnooze = false;
      this.snoozeListener = null;
      return this;
    }

    public @NonNull Builder setTitle(@StringRes int titleRes) {
      this.titleRes = titleRes;
      return this;
    }

    public @NonNull Builder setBody(@StringRes int bodyRes) {
      this.bodyRes = bodyRes;
      return this;
    }

    public @NonNull Builder setImage(@DrawableRes int imageRes) {
      this.imageRes = imageRes;
      return this;
    }

    public @NonNull Builder setButtonText(@StringRes int buttonTextRes, @NonNull EventListener listener) {
      this.buttonTextRes  = buttonTextRes;
      this.buttonListener = listener;
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
    REACTIONS, BASIC, FULLSCREEN
  }

  public interface EventListener {
    void onEvent(@NonNull Megaphone megaphone, @NonNull MegaphoneListener listener);
  }
}
