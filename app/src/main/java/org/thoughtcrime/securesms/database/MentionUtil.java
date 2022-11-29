package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Function;
import com.google.protobuf.InvalidProtocolBufferException;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.RecipientTable.MentionSetting;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public final class MentionUtil {

  public static final char   MENTION_STARTER     = '@';
         static final String MENTION_PLACEHOLDER = "\uFFFC";

  private MentionUtil() { }

  @WorkerThread
  public static @NonNull CharSequence updateBodyWithDisplayNames(@NonNull Context context, @NonNull MessageRecord messageRecord) {
    return updateBodyWithDisplayNames(context, messageRecord, messageRecord.getDisplayBody(context));
  }

  @WorkerThread
  public static @NonNull CharSequence updateBodyWithDisplayNames(@NonNull Context context, @NonNull MessageRecord messageRecord, @NonNull CharSequence body) {
    if (messageRecord.isMms()) {
      List<Mention> mentions = SignalDatabase.mentions().getMentionsForMessage(messageRecord.getId());
      CharSequence  updated  = updateBodyAndMentionsWithDisplayNames(context, body, mentions).getBody();
      if (updated != null) {
        return updated;
      }
    }
    return body;
  }

  @WorkerThread
  public static @NonNull UpdatedBodyAndMentions updateBodyAndMentionsWithDisplayNames(@NonNull Context context, @NonNull MessageRecord messageRecord, @NonNull List<Mention> mentions) {
    return updateBodyAndMentionsWithDisplayNames(context, messageRecord.getDisplayBody(context), mentions);
  }

  @WorkerThread
  public static @NonNull UpdatedBodyAndMentions updateBodyAndMentionsWithDisplayNames(@NonNull Context context, @NonNull CharSequence body, @NonNull List<Mention> mentions) {
    return update(body, mentions, m -> MENTION_STARTER + Recipient.resolved(m.getRecipientId()).getMentionDisplayName(context));
  }

  public static @NonNull UpdatedBodyAndMentions updateBodyAndMentionsWithPlaceholders(@Nullable CharSequence body, @NonNull List<Mention> mentions) {
    return update(body, mentions, m -> MENTION_PLACEHOLDER);
  }

  private static @NonNull UpdatedBodyAndMentions update(@Nullable CharSequence body, @NonNull List<Mention> mentions, @NonNull Function<Mention, CharSequence> replacementTextGenerator) {
    if (body == null || mentions.isEmpty()) {
      return new UpdatedBodyAndMentions(body, mentions);
    }

    SortedSet<Mention>     sortedMentions  = new TreeSet<>(mentions);
    SpannableStringBuilder updatedBody     = new SpannableStringBuilder();
    List<Mention>          updatedMentions = new ArrayList<>();

    int bodyIndex = 0;

    for (Mention mention : sortedMentions) {
      if (invalidMention(body, mention)) {
        continue;
      }

      updatedBody.append(body.subSequence(bodyIndex, mention.getStart()));
      CharSequence replaceWith    = replacementTextGenerator.apply(mention);
      Mention      updatedMention = new Mention(mention.getRecipientId(), updatedBody.length(), replaceWith.length());

      updatedBody.append(replaceWith);
      updatedMentions.add(updatedMention);

      bodyIndex = mention.getStart() + mention.getLength();
    }

    if (bodyIndex < body.length()) {
      updatedBody.append(body.subSequence(bodyIndex, body.length()));
    }

    return new UpdatedBodyAndMentions(updatedBody.toString(), updatedMentions);
  }

  public static @Nullable BodyRangeList mentionsToBodyRangeList(@Nullable List<Mention> mentions) {
    if (mentions == null || mentions.isEmpty()) {
      return null;
    }

    BodyRangeList.Builder builder = BodyRangeList.newBuilder();

    for (Mention mention : mentions) {
      String uuid = Recipient.resolved(mention.getRecipientId()).requireServiceId().toString();
      builder.addRanges(BodyRangeList.BodyRange.newBuilder()
                                               .setMentionUuid(uuid)
                                               .setStart(mention.getStart())
                                               .setLength(mention.getLength()));
    }

    return builder.build();
  }

  public static @NonNull List<Mention> bodyRangeListToMentions(@NonNull Context context, @Nullable byte[] data) {
    if (data != null) {
      try {
        return Stream.of(BodyRangeList.parseFrom(data).getRangesList())
                     .filter(bodyRange -> bodyRange.getAssociatedValueCase() == BodyRangeList.BodyRange.AssociatedValueCase.MENTIONUUID)
                     .map(mention -> {
                       RecipientId id = Recipient.externalPush(ServiceId.parseOrThrow(mention.getMentionUuid())).getId();
                       return new Mention(id, mention.getStart(), mention.getLength());
                     })
                     .toList();
      } catch (InvalidProtocolBufferException e) {
        return Collections.emptyList();
      }
    } else {
      return Collections.emptyList();
    }
  }

  public static @NonNull String getMentionSettingDisplayValue(@NonNull Context context, @NonNull MentionSetting mentionSetting) {
    switch (mentionSetting) {
      case ALWAYS_NOTIFY:
        return context.getString(R.string.GroupMentionSettingDialog_always_notify_me);
      case DO_NOT_NOTIFY:
        return context.getString(R.string.GroupMentionSettingDialog_dont_notify_me);
    }
    throw new IllegalArgumentException("Unknown mention setting: " + mentionSetting);
  }

  private static boolean invalidMention(@NonNull CharSequence body, @NonNull Mention mention) {
    int start  = mention.getStart();
    int length = mention.getLength();

    return start < 0 || length < 0 || (start + length) > body.length();
  }

  public static class UpdatedBodyAndMentions {
    @Nullable private final CharSequence  body;
    @NonNull  private final List<Mention> mentions;

    public UpdatedBodyAndMentions(@Nullable CharSequence body, @NonNull List<Mention> mentions) {
      this.body     = body;
      this.mentions = mentions;
    }

    public @Nullable CharSequence getBody() {
      return body;
    }

    public @NonNull List<Mention> getMentions() {
      return mentions;
    }

    @Nullable String getBodyAsString() {
      return body != null ? body.toString() : null;
    }
  }
}
