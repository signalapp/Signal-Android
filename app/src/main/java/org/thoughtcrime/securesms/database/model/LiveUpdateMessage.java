package org.thoughtcrime.securesms.database.model;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.guava.Function;

import java.util.List;

public final class LiveUpdateMessage {

  /**
   * Creates a live data that observes the recipients mentioned in the {@link UpdateDescription} and
   * recreates the string asynchronously when they change.
   */
  @AnyThread
  public static LiveData<String> fromMessageDescription(@NonNull UpdateDescription updateDescription) {
    if (updateDescription.isStringStatic()) {
      return LiveDataUtil.just(updateDescription.getStaticString());
    }

    List<LiveData<Recipient>> allMentionedRecipients = Stream.of(updateDescription.getMentioned())
                                                             .map(uuid -> Recipient.resolved(RecipientId.from(uuid, null)).live().getLiveData())
                                                             .toList();

    LiveData<?> mentionedRecipientChangeStream = allMentionedRecipients.isEmpty() ? LiveDataUtil.just(new Object())
                                                                                  : LiveDataUtil.merge(allMentionedRecipients);

    return LiveDataUtil.mapAsync(mentionedRecipientChangeStream, event -> updateDescription.getString());
  }

  /**
   * Observes a single recipient and recreates the string asynchronously when they change.
   */
  public static LiveData<String> recipientToStringAsync(@NonNull RecipientId recipientId,
                                                        @NonNull Function<Recipient, String> createStringInBackground)
  {
    return LiveDataUtil.mapAsync(Recipient.live(recipientId).getLiveData(), createStringInBackground);
  }

}
