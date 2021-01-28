package org.thoughtcrime.securesms.components.webrtc;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

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

  private final Set<Wrapper> added;
  private final Set<Wrapper> removed;

  CallParticipantListUpdate(@NonNull Set<Wrapper> added, @NonNull Set<Wrapper> removed) {
    this.added   = added;
    this.removed = removed;
  }

  public @NonNull Set<Wrapper> getAdded() {
    return added;
  }

  public @NonNull Set<Wrapper> getRemoved() {
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
    Set<CallParticipantListUpdate.Wrapper> oldParticipants = Stream.of(oldList)
                                                                  .filter(p -> p.getCallParticipantId().getDemuxId() != CallParticipantId.DEFAULT_ID)
                                                                  .map(CallParticipantListUpdate::createWrapper)
                                                                  .collect(Collectors.toSet());
    Set<CallParticipantListUpdate.Wrapper> newParticipants = Stream.of(newList)
                                                                  .filter(p -> p.getCallParticipantId().getDemuxId() != CallParticipantId.DEFAULT_ID)
                                                                  .map(CallParticipantListUpdate::createWrapper)
                                                                  .collect(Collectors.toSet());
    Set<CallParticipantListUpdate.Wrapper> added           = SetUtil.difference(newParticipants, oldParticipants);
    Set<CallParticipantListUpdate.Wrapper> removed         = SetUtil.difference(oldParticipants, newParticipants);

    return new CallParticipantListUpdate(added, removed);
  }

  @VisibleForTesting
  static Wrapper createWrapper(@NonNull CallParticipant callParticipant) {
    return new Wrapper(callParticipant);
  }

  static final class Wrapper {
    private final CallParticipant callParticipant;

    private Wrapper(@NonNull CallParticipant callParticipant) {
      this.callParticipant = callParticipant;
    }

    public @NonNull CallParticipant getCallParticipant() {
      return callParticipant;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Wrapper wrapper = (Wrapper) o;
      return callParticipant.getCallParticipantId().equals(wrapper.callParticipant.getCallParticipantId());
    }

    @Override
    public int hashCode() {
      return Objects.hash(callParticipant.getCallParticipantId());
    }
  }
}
