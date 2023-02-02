package org.thoughtcrime.securesms.recipients;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.signal.core.util.DatabaseId;
import org.signal.core.util.LongSerializer;
import org.thoughtcrime.securesms.util.DelimiterUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class RecipientId implements Parcelable, Comparable<RecipientId>, DatabaseId {

  private static final long UNKNOWN_ID = -1;
  private static final char DELIMITER  = ',';

  public static final RecipientId UNKNOWN = RecipientId.from(UNKNOWN_ID);
  public static final LongSerializer<RecipientId> SERIALIZER = new Serializer();

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

  public static @Nullable RecipientId fromNullable(@Nullable String id) {
    return id != null ? from(id) : null;
  }

  @AnyThread
  public static @NonNull RecipientId from(@NonNull SignalServiceAddress address) {
    return from(address.getServiceId(), address.getNumber().orElse(null));
  }

  @AnyThread
  public static @NonNull RecipientId from(@NonNull ServiceId serviceId) {
    return from(serviceId, null);
  }

  @AnyThread
  public static @NonNull RecipientId fromE164(@NonNull String identifier) {
    return from(null, identifier);
  }

  /**
   * Used for when you have a string that could be either a UUID or an e164. This was primarily
   * created for interacting with protocol stores.
   * @param identifier A UUID or e164
   */
  @AnyThread
  public static @NonNull RecipientId fromSidOrE164(@NonNull String identifier) {
    if (UuidUtil.isUuid(identifier)) {
      return from(ServiceId.parseOrThrow(identifier));
    } else {
      return from(null, identifier);
    }
  }

  @AnyThread
  @SuppressLint("WrongThread")
  private static @NonNull RecipientId from(@Nullable ServiceId serviceId, @Nullable String e164) {
    if (serviceId != null && serviceId.isUnknown()) {
      return RecipientId.UNKNOWN;
    }

    RecipientId recipientId = RecipientIdCache.INSTANCE.get(serviceId, e164);

    if (recipientId == null) {
      Recipient recipient = Recipient.externalPush(serviceId, e164);
      RecipientIdCache.INSTANCE.put(recipient);
      recipientId = recipient.getId();
    }

    return recipientId;
  }

  @AnyThread
  public static void clearCache() {
    RecipientIdCache.INSTANCE.clear();
  }

  private RecipientId(long id) {
    this.id = id;
  }

  private RecipientId(Parcel in) {
    id = in.readLong();
  }

  public static @NonNull String toSerializedList(@NonNull Collection<RecipientId> ids) {
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

  @Override
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

  public @NonNull String toScheduledSendQueueKey() {
    return "RecipientId::" + id + "::SCHEDULED";
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

  private static class Serializer implements LongSerializer<RecipientId> {
    @Override
    public Long serialize(RecipientId data) {
      return data.toLong();
    }

    @Override
    public @NonNull RecipientId deserialize(Long data) {
      return RecipientId.from(data);
    }
  }
}
