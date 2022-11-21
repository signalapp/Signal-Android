package org.thoughtcrime.securesms.database.model;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.guava.Function;

import java.util.List;

public final class LiveUpdateMessage {

  /**
   * Creates a live data that observes the recipients mentioned in the {@link UpdateDescription} and
   * recreates the string asynchronously when they change.
   */
  @AnyThread
  public static LiveData<SpannableString> fromMessageDescription(@NonNull Context context,
                                                                 @NonNull UpdateDescription updateDescription,
                                                                 @ColorInt int defaultTint,
                                                                 boolean adjustPosition) {
    if (updateDescription.isStringStatic()) {
      return LiveDataUtil.just(toSpannable(context, updateDescription, updateDescription.getStaticString(), defaultTint, adjustPosition));
    }

    List<LiveData<Recipient>> allMentionedRecipients = Stream.of(updateDescription.getMentioned())
                                                             .map(uuid -> Recipient.resolved(RecipientId.from(uuid, null)).live().getLiveData())
                                                             .toList();

    LiveData<?> mentionedRecipientChangeStream = allMentionedRecipients.isEmpty() ? LiveDataUtil.just(new Object())
                                                                                  : LiveDataUtil.merge(allMentionedRecipients);

    return LiveDataUtil.mapAsync(mentionedRecipientChangeStream, event -> toSpannable(context, updateDescription, updateDescription.getString(), defaultTint, adjustPosition));
  }

  /**
   * Observes a single recipient and recreates the string asynchronously when they change.
   */
  public static LiveData<SpannableString> recipientToStringAsync(@NonNull RecipientId recipientId,
                                                                 @NonNull Function<Recipient, SpannableString> createStringInBackground)
  {
    return LiveDataUtil.mapAsync(Recipient.live(recipientId).getLiveDataResolved(), createStringInBackground);
  }

  private static @NonNull SpannableString toSpannable(@NonNull Context context, @NonNull UpdateDescription updateDescription, @NonNull String string, @ColorInt int defaultTint, boolean adjustPosition) {
    boolean  isDarkTheme      = ThemeUtil.isDarkTheme(context);
    int      drawableResource = updateDescription.getIconResource();
    int      tint             = isDarkTheme ? updateDescription.getDarkTint() : updateDescription.getLightTint();

    if (tint == 0) {
      tint = defaultTint;
    }

    if (drawableResource == 0) {
      return new SpannableString(string);
    } else {
      Drawable drawable = ContextUtil.requireDrawable(context, drawableResource);
      drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
      drawable.setColorFilter(tint, PorterDuff.Mode.SRC_ATOP);

      int insetTop = adjustPosition ? ViewUtil.dpToPx(2) : 0;
      InsetDrawable insetDrawable = new InsetDrawable(drawable, 0, insetTop, 0, 0);
      insetDrawable.setBounds(0, 0, drawable.getIntrinsicWidth(), insetDrawable.getIntrinsicHeight());

      Drawable spaceDrawable = new ColorDrawable(Color.TRANSPARENT);
      spaceDrawable.setBounds(0, 0, ViewUtil.dpToPx(8), drawable.getIntrinsicHeight());

      Spannable stringWithImage = new SpannableStringBuilder().append(SpanUtil.buildImageSpan(drawable)).append(SpanUtil.buildImageSpan(spaceDrawable)).append(string);

      return new SpannableString(SpanUtil.color(tint, stringWithImage));
    }
  }
}
