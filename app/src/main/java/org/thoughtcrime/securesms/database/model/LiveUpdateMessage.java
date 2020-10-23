package org.thoughtcrime.securesms.database.model;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.guava.Function;

import java.util.List;

public final class LiveUpdateMessage {

  /**
   * Creates a live data that observes the recipients mentioned in the {@link UpdateDescription} and
   * recreates the string asynchronously when they change.
   */
  @AnyThread
  public static LiveData<Spannable> fromMessageDescription(@NonNull Context context, @NonNull UpdateDescription updateDescription) {
    if (updateDescription.isStringStatic()) {
      return LiveDataUtil.just(toSpannable(context, updateDescription, updateDescription.getStaticString()));
    }

    List<LiveData<Recipient>> allMentionedRecipients = Stream.of(updateDescription.getMentioned())
                                                             .map(uuid -> Recipient.resolved(RecipientId.from(uuid, null)).live().getLiveData())
                                                             .toList();

    LiveData<?> mentionedRecipientChangeStream = allMentionedRecipients.isEmpty() ? LiveDataUtil.just(new Object())
                                                                                  : LiveDataUtil.merge(allMentionedRecipients);

    return LiveDataUtil.mapAsync(mentionedRecipientChangeStream, event -> toSpannable(context, updateDescription, updateDescription.getString()));
  }

  /**
   * Observes a single recipient and recreates the string asynchronously when they change.
   */
  public static LiveData<Spannable> recipientToStringAsync(@NonNull RecipientId recipientId,
                                                           @NonNull Function<Recipient, Spannable> createStringInBackground)
  {
    return LiveDataUtil.mapAsync(Recipient.live(recipientId).getLiveData(), createStringInBackground);
  }

  private static @NonNull Spannable toSpannable(@NonNull Context context, @NonNull UpdateDescription updateDescription, @NonNull String string) {
    boolean  isDarkTheme      = ThemeUtil.isDarkTheme(context);
    int      drawableResource = isDarkTheme ? updateDescription.getDarkIconResource() : updateDescription.getLightIconResource();
    int      tint             = isDarkTheme ? updateDescription.getDarkTint() : updateDescription.getLightTint();

    if (tint == 0) {
      tint = ThemeUtil.getThemedColor(context, R.attr.conversation_item_update_text_color);
    }

    if (drawableResource == 0) {
      return new SpannableString(string);
    } else {
      Drawable drawable = ContextCompat.getDrawable(context, drawableResource);
      drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
      drawable.setColorFilter(tint, PorterDuff.Mode.SRC_ATOP);

      Spannable stringWithImage = new SpannableStringBuilder().append(SpanUtil.buildImageSpan(drawable)).append("  ").append(string);

      return new SpannableString(SpanUtil.color(tint, stringWithImage));
    }
  }
}
