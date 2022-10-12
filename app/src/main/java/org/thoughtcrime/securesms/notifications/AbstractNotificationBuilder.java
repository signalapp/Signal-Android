package org.thoughtcrime.securesms.notifications;

import android.app.Notification;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.session.libsession.utilities.NotificationPrivacyPreference;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.Util;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsession.utilities.recipients.Recipient.VibrateState;

import network.loki.messenger.R;

public abstract class AbstractNotificationBuilder extends NotificationCompat.Builder {

  @SuppressWarnings("unused")
  private static final String TAG = AbstractNotificationBuilder.class.getSimpleName();

  private static final int MAX_DISPLAY_LENGTH = 50;

  protected Context                       context;
  protected NotificationPrivacyPreference privacy;
  protected final Bundle                  extras;

  public AbstractNotificationBuilder(Context context, NotificationPrivacyPreference privacy) {
    super(context);
    extras = new Bundle();
    this.context = context;
    this.privacy = privacy;

    setChannelId(NotificationChannels.getMessagesChannel(context));
    setLed();
  }

  protected CharSequence getStyledMessage(@NonNull Recipient recipient, @Nullable CharSequence message) {
    SpannableStringBuilder builder = new SpannableStringBuilder();
    builder.append(Util.getBoldedString(recipient.toShortString()));
    builder.append(": ");
    builder.append(message == null ? "" : message);

    return builder;
  }

  public void setAlarms(@Nullable Uri ringtone, VibrateState vibrate) {
    Uri     defaultRingtone = NotificationChannels.supported() ? NotificationChannels.getMessageRingtone(context) : TextSecurePreferences.getNotificationRingtone(context);
    boolean defaultVibrate  = NotificationChannels.supported() ? NotificationChannels.getMessageVibrate(context)  : TextSecurePreferences.isNotificationVibrateEnabled(context);

    if      (ringtone == null && !TextUtils.isEmpty(defaultRingtone.toString())) setSound(defaultRingtone);
    else if (ringtone != null && !ringtone.toString().isEmpty())                 setSound(ringtone);

    if (vibrate == VibrateState.ENABLED ||
        (vibrate == VibrateState.DEFAULT && defaultVibrate))
    {
      setDefaults(Notification.DEFAULT_VIBRATE);
    }
  }

  private void setLed() {
    int ledColor = TextSecurePreferences.getNotificationLedColor(context);
    setLights(ledColor, 500,2000);
  }

  public void setTicker(@NonNull Recipient recipient, @Nullable CharSequence message) {
    if (privacy.isDisplayMessage()) {
      setTicker(getStyledMessage(recipient, trimToDisplayLength(message)));
    } else if (privacy.isDisplayContact()) {
      setTicker(getStyledMessage(recipient, context.getString(R.string.AbstractNotificationBuilder_new_message)));
    } else {
      setTicker(context.getString(R.string.AbstractNotificationBuilder_new_message));
    }
  }

  protected @NonNull CharSequence trimToDisplayLength(@Nullable CharSequence text) {
    text = text == null ? "" : text;

    return text.length() <= MAX_DISPLAY_LENGTH ? text
                                               : text.subSequence(0, MAX_DISPLAY_LENGTH);
  }

  @Override
  public Notification build() {
    addExtras(extras);
    return super.build();
  }
}
