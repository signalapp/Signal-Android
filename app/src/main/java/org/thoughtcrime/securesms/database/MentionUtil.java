package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Function;

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
import java.util.stream.Collectors;

import kotlin.Pair;

public final class MentionUtil {

  public static final char   MENTION_STARTER     = '@';
         static final String MENTION_PLACEHOLDER = "\uFFFC";

  private MentionUtil() { }

  @WorkerThread
  public static @NonNull UpdatedBodyAndMentions updateBodyWithDisplayNames(@NonNull Context context, @NonNull MessageRecord messageRecord) {
    return updateBodyWithDisplayNames(context, messageRecord, messageRecord.getDisplayBody(context));
  }

  @WorkerThread
  public static @NonNull UpdatedBodyAndMentions updateBodyWithDisplayNames(@NonNull Context context, @NonNull MessageRecord messageRecord, @NonNull CharSequence body) {
    List<Mention> mentions = SignalDatabase.mentions().getMentionsForMessage(messageRecord.getId());
    return updateBodyAndMentionsWithDisplayNames(context, body, mentions);
  }

  @WorkerThread
  public static @NonNull UpdatedBodyAndMentions updateBodyAndMentionsWithDisplayNames(@NonNull Context context, @NonNull CharSequence body, @NonNull List<Mention> mentions) {
    return update(body, mentions, m -> MENTION_STARTER + Recipient.resolved(m.getRecipientId()).getMentionDisplayName(context));
  }

  public static @NonNull UpdatedBodyAndMentions updateBodyAndMentionsWithPlaceholders(@Nullable CharSequence body, @NonNull List<Mention> mentions) {
    return update(body, mentions, m -> MENTION_PLACEHOLDER);
  }

  @VisibleForTesting
  static @NonNull UpdatedBodyAndMentions update(@Nullable CharSequence body, @NonNull List<Mention> mentions, @NonNull Function<Mention, CharSequence> replacementTextGenerator) {
    if (body == null || mentions.isEmpty()) {
      return new UpdatedBodyAndMentions(body, mentions, Collections.emptyList());
    }

    SortedSet<Mention>     sortedMentions  = new TreeSet<>(mentions);
    SpannableStringBuilder updatedBody     = new SpannableStringBuilder();
    List<Mention>          updatedMentions = new ArrayList<>();
    List<BodyAdjustment>   bodyAdjustments = new ArrayList<>();

    int bodyIndex = 0;

    for (Mention mention : sortedMentions) {
      if (invalidMention(body, mention) || bodyIndex > mention.getStart()) {
        continue;
      }

      updatedBody.append(body.subSequence(bodyIndex, mention.getStart()));
      CharSequence replaceWith    = replacementTextGenerator.apply(mention);
      Mention      updatedMention = new Mention(mention.getRecipientId(), updatedBody.length(), replaceWith.length());

      updatedBody.append(replaceWith);
      updatedMentions.add(updatedMention);

      bodyAdjustments.add(new BodyAdjustment(mention.getStart(), mention.getLength(), updatedMention.getLength()));

      bodyIndex = mention.getStart() + mention.getLength();
    }

    if (bodyIndex < body.length()) {
      updatedBody.append(body.subSequence(bodyIndex, body.length()));
    }

    return new UpdatedBodyAndMentions(updatedBody, updatedMentions, bodyAdjustments);
  }

  public static @Nullable BodyRangeList mentionsToBodyRangeList(@Nullable List<Mention> mentions) {
    if (mentions == null || mentions.isEmpty()) {
      return null;
    }

    BodyRangeList.Builder builder = new BodyRangeList.Builder();
    builder.ranges(
        mentions.stream()
                .map(mention -> new Pair<>(Recipient.resolved(mention.getRecipientId()), mention))
                .filter(pair -> pair.getFirst().getHasAci())
                .map(pair -> {
                  Recipient recipient = pair.getFirst();
                  Mention   mention   = pair.getSecond();
                  return new BodyRangeList.BodyRange.Builder()
                      .mentionUuid(recipient.requireAci().toString())
                      .start(mention.getStart())
                      .length(mention.getLength())
                      .build();
                })
                .collect(Collectors.toList())
    );
    return builder.build();
  }

  public static @NonNull List<Mention> bodyRangeListToMentions(@Nullable BodyRangeList bodyRanges) {
    if (bodyRanges != null) {
      return Stream.of(bodyRanges.ranges)
                   .filter(bodyRange -> bodyRange.mentionUuid != null)
                   .map(mention -> {
                     RecipientId id = Recipient.externalPush(ServiceId.parseOrThrow(mention.mentionUuid)).getId();
                     return new Mention(id, mention.start, mention.length);
                   })
                   .toList();
    } else {
      return Collections.emptyList();
    }
  }

  private static boolean invalidMention(@NonNull CharSequence body, @NonNull Mention mention) {
    int start  = mention.getStart();
    int length = mention.getLength();

    return start < 0 || length < 0 || (start + length) > body.length();
  }

  public static class UpdatedBodyAndMentions {
    @Nullable private final CharSequence         body;
    @NonNull private final  List<Mention>        mentions;
    @NonNull private final  List<BodyAdjustment> bodyAdjustments;

    private UpdatedBodyAndMentions(@Nullable CharSequence body, @NonNull List<Mention> mentions, @NonNull List<BodyAdjustment> bodyAdjustments) {
      this.body            = body;
      this.mentions        = mentions;
      this.bodyAdjustments = bodyAdjustments;
    }

    public @Nullable CharSequence getBody() {
      return body;
    }

    public @NonNull List<Mention> getMentions() {
      return mentions;
    }

    public @NonNull List<BodyAdjustment> getBodyAdjustments() {
      return bodyAdjustments;
    }

    @Nullable String getBodyAsString() {
      return body != null ? body.toString() : null;
    }
  }
}
