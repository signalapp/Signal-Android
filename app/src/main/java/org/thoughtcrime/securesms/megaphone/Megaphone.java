package org.thoughtcrime.securesms.megaphone;

import android.content.Intent;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.megaphone.Megaphones.Event;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequest;

/**
 * For guidance on creating megaphones, see {@link Megaphones}.
 */
public class Megaphone {

  private final Event                  event;
  private final Style                  style;
  private final Priority               priority;
  private final boolean                canSnooze;
  private final int                    titleRes;
  private final int                    bodyRes;
  private final GlideRequest<Drawable> imageRequest;
  private final int                    buttonTextRes;
  private final EventListener          buttonListener;
  private final EventListener          snoozeListener;
  private final int                    secondaryButtonTextRes;
  private final EventListener          secondaryButtonListener;
  private final EventListener          onVisibleListener;

  private Megaphone(@NonNull Builder builder) {
    this.event                   = builder.event;
    this.style                   = builder.style;
    this.priority                = builder.priority;
    this.canSnooze               = builder.canSnooze;
    this.titleRes                = builder.titleRes;
    this.bodyRes                 = builder.bodyRes;
    this.imageRequest            = builder.imageRequest;
    this.buttonTextRes           = builder.buttonTextRes;
    this.buttonListener          = builder.buttonListener;
    this.snoozeListener          = builder.snoozeListener;
    this.secondaryButtonTextRes  = builder.secondaryButtonTextRes;
    this.secondaryButtonListener = builder.secondaryButtonListener;
    this.onVisibleListener       = builder.onVisibleListener;
  }

  public @NonNull Event getEvent() {
    return event;
  }

  public @NonNull Priority getPriority() {
    return priority;
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

  public @Nullable GlideRequest<Drawable> getImageRequest() {
    return imageRequest;
  }

  public @StringRes int getButtonText() {
    return buttonTextRes;
  }

  public boolean hasButton() {
    return buttonTextRes != 0;
  }

  public @Nullable EventListener getButtonClickListener() {
    return buttonListener;
  }

  public @Nullable EventListener getSnoozeListener() {
    return snoozeListener;
  }

  public @StringRes int getSecondaryButtonText() {
    return secondaryButtonTextRes;
  }

  public boolean hasSecondaryButton() {
    return secondaryButtonTextRes != 0;
  }

  public @Nullable EventListener getSecondaryButtonClickListener() {
    return secondaryButtonListener;
  }

  public @Nullable EventListener getOnVisibleListener() {
    return onVisibleListener;
  }

  public static class Builder {

    private final Event  event;
    private final Style  style;

    private Priority               priority;
    private boolean                canSnooze;
    private int                    titleRes;
    private int                    bodyRes;
    private GlideRequest<Drawable> imageRequest;
    private int                    buttonTextRes;
    private EventListener          buttonListener;
    private EventListener          snoozeListener;
    private int                    secondaryButtonTextRes;
    private EventListener          secondaryButtonListener;
    private EventListener          onVisibleListener;


    public Builder(@NonNull Event event, @NonNull Style style) {
      this.event          = event;
      this.style          = style;
      this.priority       = Priority.DEFAULT;
    }

    /**
     * Prioritizes this megaphone over others that do not set this flag.
     */
    public @NonNull Builder setPriority(@NonNull Priority priority) {
      this.priority = priority;
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
      return setImageRequest(GlideApp.with(ApplicationDependencies.getApplication()).load(imageRes));
    }

    public @NonNull Builder setImageRequest(@Nullable GlideRequest<Drawable> imageRequest) {
      this.imageRequest = imageRequest;
      return this;
    }

    public @NonNull Builder setActionButton(@StringRes int buttonTextRes, @NonNull EventListener listener) {
      this.buttonTextRes  = buttonTextRes;
      this.buttonListener = listener;
      return this;
    }

    public @NonNull Builder setSecondaryButton(@StringRes int secondaryButtonTextRes, @NonNull EventListener listener) {
      this.secondaryButtonTextRes  = secondaryButtonTextRes;
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
    /** Specialized style for announcing reactions. */
    REACTIONS,

    /** Specialized style for announcing link previews. */
    LINK_PREVIEWS,

    /** Specialized style for onboarding. */
    ONBOARDING,

    /** Basic bottom of the screen megaphone with optional snooze and action buttons. */
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

  enum Priority {
    DEFAULT(0), HIGH(1), CLIENT_EXPIRATION(1000);

    int priorityValue;

    Priority(int priorityValue) {
      this.priorityValue = priorityValue;
    }

    public int getPriorityValue() {
      return priorityValue;
    }
  }

  public interface EventListener {
    void onEvent(@NonNull Megaphone megaphone, @NonNull MegaphoneActionController listener);
  }
}
