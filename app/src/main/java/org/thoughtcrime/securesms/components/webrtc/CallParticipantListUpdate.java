package org.thoughtcrime.securesms.components.webrtc;

import androidx.annotation.NonNull;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.SetUtil;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the delta between two lists of CallParticipant objects. This is used along with
 * {@link CallParticipantsListUpdatePopupWindow} to display in-call notifications to the user
 * whenever remote participants leave or reconnect to the call.
 */
public final class CallParticipantListUpdate {

  private final Set<Holder> added;
  private final Set<Holder> removed;

  CallParticipantListUpdate(@NonNull Set<Holder> added, @NonNull Set<Holder> removed) {
    this.added   = added;
    this.removed = removed;
  }

  public @NonNull Set<Holder> getAdded() {
    return added;
  }

  public @NonNull Set<Holder> getRemoved() {
    return removed;
  }

  public boolean hasNoChanges() {
    return added.isEmpty() && removed.isEmpty();
  }

  public boolean hasSingleChange() {
    return added.size() + removed.size() == 1;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CallParticipantListUpdate that = (CallParticipantListUpdate) o;
    return added.equals(that.added) && removed.equals(that.removed);
  }

  @Override
  public int hashCode() {
    return Objects.hash(added, removed);
  }

  /**
   * Generates a new Update Object for given lists. This new update object will ignore any participants
   * that have the demux id set to {@link CallParticipantId#DEFAULT_ID}.
   *
   * @param oldList The old list of CallParticipants
   * @param newList The new (or current) list of CallParticipants
   */
  public static @NonNull CallParticipantListUpdate computeDeltaUpdate(@NonNull List<CallParticipant> oldList,
                                                                      @NonNull List<CallParticipant> newList)
  {
    Set<CallParticipantId>                primaries       = getPrimaries(oldList, newList);
    Set<CallParticipantListUpdate.Holder> oldParticipants = Stream.of(oldList)
                                                                  .filter(p -> p.getCallParticipantId().getDemuxId() != CallParticipantId.DEFAULT_ID)
                                                                  .map(p -> createHolder(p, primaries.contains(p.getCallParticipantId())))
                                                                  .collect(Collectors.toSet());
    Set<CallParticipantListUpdate.Holder> newParticipants = Stream.of(newList)
                                                                  .filter(p -> p.getCallParticipantId().getDemuxId() != CallParticipantId.DEFAULT_ID)
                                                                  .map(p -> createHolder(p, primaries.contains(p.getCallParticipantId())))
                                                                  .collect(Collectors.toSet());
    Set<CallParticipantListUpdate.Holder> added           = SetUtil.difference(newParticipants, oldParticipants);
    Set<CallParticipantListUpdate.Holder> removed         = SetUtil.difference(oldParticipants, newParticipants);

    return new CallParticipantListUpdate(added, removed);
  }

  static Holder createHolder(@NonNull CallParticipant callParticipant, boolean isPrimary) {
    return new Holder(callParticipant.getCallParticipantId(), callParticipant.getRecipient(), isPrimary);
  }

  private static @NonNull Set<CallParticipantId> getPrimaries(@NonNull List<CallParticipant> oldList, @NonNull List<CallParticipant> newList) {
    return Stream.concat(Stream.of(oldList), Stream.of(newList))
                 .map(CallParticipant::getCallParticipantId)
                 .distinctBy(CallParticipantId::getRecipientId)
                 .collect(Collectors.toSet());
  }

  static final class Holder {
    private final CallParticipantId callParticipantId;
    private final Recipient         recipient;
    private final boolean           isPrimary;

    private Holder(@NonNull CallParticipantId callParticipantId, @NonNull Recipient recipient, boolean isPrimary) {
      this.callParticipantId = callParticipantId;
      this.recipient         = recipient;
      this.isPrimary         = isPrimary;
    }

    public @NonNull Recipient getRecipient() {
      return recipient;
    }

    /**
     * Denotes whether this was the first detected instance of this recipient when generating an update. See
     * {@link CallParticipantListUpdate#computeDeltaUpdate(List, List)}
     */
    public boolean isPrimary() {
      return isPrimary;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Holder holder = (Holder) o;
      return callParticipantId.equals(holder.callParticipantId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(callParticipantId);
    }
  }
}
