package org.thoughtcrime.securesms.recipients;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.DelimiterUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public class RecipientId implements Parcelable, Comparable<RecipientId> {

  private static final long UNKNOWN_ID = -1;
  private static final char DELIMITER  = ',';

  public static final RecipientId UNKNOWN = RecipientId.from(UNKNOWN_ID);

  private final long id;

  public static RecipientId from(long id) {
    if (id == 0) {
      throw new InvalidLongRecipientIdError();
    }

    return new RecipientId(id);
  }

  public static RecipientId from(@NonNull String id) {
    try {
      return RecipientId.from(Long.parseLong(id));
    } catch (NumberFormatException e) {
      throw new InvalidStringRecipientIdError();
    }
  }

  @AnyThread
  public static @NonNull RecipientId from(@NonNull SignalServiceAddress address) {
    return from(address.getUuid().orNull(), address.getNumber().orNull());
  }

  /**
   * Always supply both {@param uuid} and {@param e164} if you have both.
   */
  @AnyThread
  @SuppressLint("WrongThread")
  public static @NonNull RecipientId from(@Nullable UUID uuid, @Nullable String e164) {
    RecipientId recipientId = RecipientIdCache.INSTANCE.get(uuid, e164);

    if (recipientId == null) {
      recipientId = Recipient.externalPush(ApplicationDependencies.getApplication(), uuid, e164).getId();
    }

    return recipientId;
  }

  private RecipientId(long id) {
    this.id = id;
  }

  private RecipientId(Parcel in) {
    id = in.readLong();
  }

  public static @NonNull String toSerializedList(@NonNull List<RecipientId> ids) {
    return Util.join(Stream.of(ids).map(RecipientId::serialize).toList(), String.valueOf(DELIMITER));
  }

  public static List<RecipientId> fromSerializedList(@NonNull String serialized) {
    String[]          stringIds = DelimiterUtil.split(serialized, DELIMITER);
    List<RecipientId> out       = new ArrayList<>(stringIds.length);

    for (String stringId : stringIds) {
      RecipientId id = RecipientId.from(Long.parseLong(stringId));
      out.add(id);
    }

    return out;
  }

  public static boolean serializedListContains(@NonNull String serialized, @NonNull RecipientId recipientId) {
    return Pattern.compile("\\b" + recipientId.serialize() + "\\b")
                  .matcher(serialized)
                  .find();
  }

  public boolean isUnknown() {
    return id == UNKNOWN_ID;
  }

  public @NonNull String serialize() {
    return String.valueOf(id);
  }

  public long toLong() {
    return id;
  }

  public @NonNull String toQueueKey() {
    return toQueueKey(false);
  }

  public @NonNull String toQueueKey(boolean forMedia) {
    return "RecipientId::" + id + (forMedia ? "::MEDIA" : "");
  }

  @Override
  public @NonNull String toString() {
    return "RecipientId::" + id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RecipientId that = (RecipientId) o;

    return id == that.id;
  }

  @Override
  public int hashCode() {
    return (int) (id ^ (id >>> 32));
  }

  @Override
  public int compareTo(RecipientId o) {
    return Long.compare(this.id, o.id);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeLong(id);
  }

  public static final Creator<RecipientId> CREATOR = new Creator<RecipientId>() {
    @Override
    public RecipientId createFromParcel(Parcel in) {
      return new RecipientId(in);
    }

    @Override
    public RecipientId[] newArray(int size) {
      return new RecipientId[size];
    }
  };

  private static class InvalidLongRecipientIdError extends AssertionError {}
  private static class InvalidStringRecipientIdError extends AssertionError {}
}
