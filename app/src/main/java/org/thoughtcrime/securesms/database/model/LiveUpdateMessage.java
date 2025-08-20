package org.thoughtcrime.securesms.database.model;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;

import androidx.annotation.ColorInt;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.fonts.SignalSymbols;
import org.thoughtcrime.securesms.fonts.SignalSymbols.Glyph;
import org.thoughtcrime.securesms.fonts.SignalSymbols.Weight;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.List;
import java.util.function.Function;

public final class LiveUpdateMessage {

  /**
   * Creates a live data that observes the recipients mentioned in the {@link UpdateDescription} and
   * recreates the string asynchronously when they change.
   */
  @MainThread
  public static LiveData<SpannableString> fromMessageDescription(@NonNull Context context,
                                                                 @NonNull UpdateDescription updateDescription,
                                                                 @ColorInt int defaultTint,
                                                                 boolean adjustPosition)
  {
    if (updateDescription.isStringStatic()) {
      return LiveDataUtil.just(toSpannable(context, updateDescription, updateDescription.getStaticSpannable(), defaultTint, adjustPosition));
    }

    List<LiveData<Recipient>> allMentionedRecipients = Stream.of(updateDescription.getMentioned())
                                                             .map(uuid -> Recipient.resolved(RecipientId.from(uuid)).live().getLiveData())
                                                             .toList();

    LiveData<?> mentionedRecipientChangeStream = allMentionedRecipients.isEmpty() ? LiveDataUtil.just(new Object())
                                                                                  : LiveDataUtil.merge(allMentionedRecipients);

    return Transformations.map(mentionedRecipientChangeStream, event -> toSpannable(context, updateDescription, updateDescription.getSpannable(), defaultTint, adjustPosition));
  }

  /**
   * Observes a single recipient and recreates the string asynchronously when they change.
   */
  @MainThread
  public static LiveData<SpannableString> recipientToStringAsync(@NonNull RecipientId recipientId,
                                                                 @NonNull Function<Recipient, SpannableString> createStringInBackground)
  {
    return Transformations.map(Recipient.live(recipientId).getLiveDataResolved(), createStringInBackground::apply);
  }

  private static @NonNull SpannableString toSpannable(@NonNull Context context, @NonNull UpdateDescription updateDescription, @NonNull Spannable string, @ColorInt int defaultTint, boolean adjustPosition) {
    boolean isDarkTheme = ThemeUtil.isDarkTheme(context);
    Glyph   glyph       = updateDescription.getGlyph();
    int     tint        = isDarkTheme ? updateDescription.getDarkTint() : updateDescription.getLightTint();

    if (tint == 0) {
      tint = defaultTint;
    }

    if (glyph == null) {
      return new SpannableString(string);
    } else {
      SpannableStringBuilder builder   = new SpannableStringBuilder();
      CharSequence           glyphChar = SignalSymbols.getSpannedString(context, Weight.REGULAR, glyph, -1);

      builder.append(glyphChar);
      builder.append(" ");
      builder.append(string);

      return new SpannableString(SpanUtil.color(tint, builder));
    }
  }
}
