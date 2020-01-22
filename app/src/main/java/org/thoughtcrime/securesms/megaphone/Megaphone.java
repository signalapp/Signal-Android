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

  /** For {@link #getMaxAppearances()}. */
  public static final int UNLIMITED = -1;

  private final Event             event;
  private final Style             style;
  private final boolean           mandatory;
  private final boolean           canSnooze;
  private final int               maxAppearances;
  private final int               titleRes;
  private final int               bodyRes;
  private final int               imageRes;
  private final int               buttonTextRes;
  private final OnClickListener   buttonListener;
  private final OnVisibleListener onVisibleListener;

  private Megaphone(@NonNull Builder builder) {
    this.event             = builder.event;
    this.style             = builder.style;
    this.mandatory         = builder.mandatory;
    this.canSnooze         = builder.canSnooze;
    this.maxAppearances    = builder.maxAppearances;
    this.titleRes          = builder.titleRes;
    this.bodyRes           = builder.bodyRes;
    this.imageRes          = builder.imageRes;
    this.buttonTextRes     = builder.buttonTextRes;
    this.buttonListener    = builder.buttonListener;
    this.onVisibleListener = builder.onVisibleListener;
  }

  public @NonNull Event getEvent() {
    return event;
  }

  public boolean isMandatory() {
    return mandatory;
  }

  public int getMaxAppearances() {
    return maxAppearances;
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

  public @Nullable OnClickListener getButtonClickListener() {
    return buttonListener;
  }

  public @Nullable OnVisibleListener getOnVisibleListener() {
    return onVisibleListener;
  }

  public static class Builder {

    private final Event  event;
    private final Style  style;

    private boolean           mandatory;
    private boolean           canSnooze;
    private int               maxAppearances;
    private int               titleRes;
    private int               bodyRes;
    private int               imageRes;
    private int               buttonTextRes;
    private OnClickListener   buttonListener;
    private OnVisibleListener onVisibleListener;


    public Builder(@NonNull Event event, @NonNull Style style) {
      this.event          = event;
      this.style          = style;
      this.maxAppearances = 1;
    }

    public @NonNull Builder setMandatory(boolean mandatory) {
      this.mandatory = mandatory;
      return this;
    }

    public @NonNull Builder setSnooze(boolean canSnooze) {
      this.canSnooze = canSnooze;
      return this;
    }

    public @NonNull Builder setMaxAppearances(int maxAppearances) {
      this.maxAppearances = maxAppearances;
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

    public @NonNull Builder setButtonText(@StringRes int buttonTextRes, @NonNull OnClickListener listener) {
      this.buttonTextRes  = buttonTextRes;
      this.buttonListener = listener;
      return this;
    }

    public @NonNull Builder setOnVisibleListener(@Nullable OnVisibleListener listener) {
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

  public interface OnVisibleListener {
    void onVisible(@NonNull Megaphone megaphone, @NonNull MegaphoneListener listener);
  }

  public interface OnClickListener {
    void onClick(@NonNull Megaphone megaphone, @NonNull MegaphoneListener listener);
  }
}
