package org.thoughtcrime.securesms.service.webrtc.collections;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents the participants to be displayed in the grid at any given time.
 */
public class ParticipantCollection {

  private static final Comparator<CallParticipant> LEAST_RECENTLY_ADDED     = (a, b) -> Long.compare(a.getAddedToCallTime(), b.getAddedToCallTime());
  private static final Comparator<CallParticipant> MOST_RECENTLY_SPOKEN     = (a, b) -> Long.compare(b.getLastSpoke(), a.getLastSpoke());
  private static final Comparator<CallParticipant> HAND_RAISED              = (a, b) -> {
    if (a.isHandRaised() && b.isHandRaised()) {
      return Long.compare(a.getHandRaisedTimestamp(), b.getHandRaisedTimestamp());
    } else if (a.isHandRaised()) {
      return -1;
    } else if (b.isHandRaised()) {
      return 1;
    } else {
      return 0;
    }
  };
  private static final Comparator<CallParticipant> COMPLEX_COMPARATOR_CHAIN = ComparatorCompat.chain(HAND_RAISED)
                                                                                              .thenComparing(MOST_RECENTLY_SPOKEN)
                                                                                              .thenComparing(LEAST_RECENTLY_ADDED);

  private final int                   maxGridCellCount;
  private final List<CallParticipant> participants;

  public ParticipantCollection(int maxGridCellCount) {
    this(maxGridCellCount, Collections.emptyList());
  }

  private ParticipantCollection(int maxGridCellCount, @NonNull List<CallParticipant> callParticipants) {
    this.maxGridCellCount = maxGridCellCount;
    this.participants     = Collections.unmodifiableList(callParticipants);
  }

  @CheckResult
  public @NonNull ParticipantCollection getNext(@NonNull List<CallParticipant> participants) {
    if (participants.isEmpty()) {
      return new ParticipantCollection(maxGridCellCount);
    } else if (this.participants.isEmpty()) {
      List<CallParticipant> newParticipants = new ArrayList<>(participants);
      Collections.sort(newParticipants, participants.size() <= maxGridCellCount ? LEAST_RECENTLY_ADDED : COMPLEX_COMPARATOR_CHAIN);

      return new ParticipantCollection(maxGridCellCount, newParticipants);
    } else {
      List<CallParticipant> newParticipants = new ArrayList<>(participants);
      Collections.sort(newParticipants, COMPLEX_COMPARATOR_CHAIN);

      List<CallParticipantId> oldGridParticipantIds = Stream.of(getGridParticipants())
                                                            .map(CallParticipant::getCallParticipantId)
                                                            .toList();

      for (int i = 0; i < oldGridParticipantIds.size(); i++) {
        CallParticipantId oldId = oldGridParticipantIds.get(i);

        int newIndex = Stream.of(newParticipants)
                             .takeUntilIndexed((j, p) -> j >= maxGridCellCount)
                             .map(CallParticipant::getCallParticipantId)
                             .toList()
                             .indexOf(oldId);

        if (newIndex != -1 && newIndex != i) {
          Collections.swap(newParticipants, newIndex, Math.min(i, newParticipants.size() - 1));
        }
      }

      return new ParticipantCollection(maxGridCellCount, newParticipants);
    }
  }

  public List<CallParticipant> getGridParticipants() {
    return participants.size() > maxGridCellCount
           ? Collections.unmodifiableList(participants.subList(0, maxGridCellCount))
           : Collections.unmodifiableList(participants);
  }

  public List<CallParticipant> getListParticipants() {
    return participants.size() > maxGridCellCount
           ? Collections.unmodifiableList(participants.subList(maxGridCellCount, participants.size()))
           : Collections.emptyList();
  }

  public boolean isEmpty() {
    return participants.isEmpty();
  }

  public List<CallParticipant> getAllParticipants() {
    return participants;
  }

  public @NonNull ParticipantCollection map(@NonNull Function<CallParticipant, CallParticipant> mapper) {
    return new ParticipantCollection(maxGridCellCount, participants.stream().map(mapper).collect(Collectors.toList()));
  }

  public int size() {
    return participants.size();
  }

  public @NonNull CallParticipant get(int i) {
    return participants.get(i);
  }
}
