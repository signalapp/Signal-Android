package org.thoughtcrime.securesms.components.webrtc;

import com.annimon.stream.Stream;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientDetails;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class CallParticipantListUpdateTest {

  @Test
  public void givenEmptySets_thenExpectNoChanges() {
    // GIVEN
    Set<CallParticipantListUpdate.Holder> added   = Collections.emptySet();
    Set<CallParticipantListUpdate.Holder> removed = Collections.emptySet();
    CallParticipantListUpdate             update  = new CallParticipantListUpdate(added, removed);

    // THEN
    assertTrue(update.hasNoChanges());
    assertFalse(update.hasSingleChange());
  }

  @Test
  public void givenOneEmptySet_thenExpectMultipleChanges() {
    // GIVEN
    Set<CallParticipantListUpdate.Holder> added   = new HashSet<>(Arrays.asList(createHolders(1, 2, 3)));
    Set<CallParticipantListUpdate.Holder> removed = Collections.emptySet();
    CallParticipantListUpdate             update  = new CallParticipantListUpdate(added, removed);

    // THEN
    assertFalse(update.hasNoChanges());
    assertFalse(update.hasSingleChange());
  }

  @Test
  public void givenNoEmptySets_thenExpectMultipleChanges() {
    // GIVEN
    Set<CallParticipantListUpdate.Holder> added   = new HashSet<>(Arrays.asList(createHolders(1, 2, 3)));
    Set<CallParticipantListUpdate.Holder> removed = new HashSet<>(Arrays.asList(createHolders(4, 5, 6)));
    CallParticipantListUpdate             update  = new CallParticipantListUpdate(added, removed);

    // THEN
    assertFalse(update.hasNoChanges());
    assertFalse(update.hasSingleChange());
  }

  @Test
  public void givenOneSetWithSingleItemAndAnEmptySet_thenExpectSingleChange() {
    // GIVEN
    Set<CallParticipantListUpdate.Holder> added   = new HashSet<>(Arrays.asList(createHolders(1)));
    Set<CallParticipantListUpdate.Holder> removed = Collections.emptySet();
    CallParticipantListUpdate             update  = new CallParticipantListUpdate(added, removed);

    // THEN
    assertFalse(update.hasNoChanges());
    assertTrue(update.hasSingleChange());
  }

  @Test
  public void whenFirstListIsAdded_thenIExpectAnUpdateWithAllItemsFromListAdded() {
    // GIVEN
    List<CallParticipant> newList = createParticipants(1, 2, 3, 4, 5);

    // WHEN
    CallParticipantListUpdate update = CallParticipantListUpdate.computeDeltaUpdate(Collections.emptyList(), newList);

    // THEN
    assertFalse(update.hasNoChanges());
    assertTrue(update.getRemoved().isEmpty());
    assertThat(update.getAdded(), Matchers.containsInAnyOrder(createHolders(1, 2, 3, 4, 5)));
  }

  @Test
  public void whenSameListIsAddedTwiceInARowWithinTimeout_thenIExpectAnEmptyUpdate() {
    // GIVEN
    List<CallParticipant> newList = createParticipants(1, 2, 3, 4, 5);

    // WHEN
    CallParticipantListUpdate update = CallParticipantListUpdate.computeDeltaUpdate(newList, newList);

    // THEN
    assertTrue(update.hasNoChanges());
  }

  @Test
  public void whenPlaceholdersAreUsed_thenIExpectAnEmptyUpdate() {
    // GIVEN
    List<CallParticipant> newList = createPlaceholderParticipants(1, 2, 3, 4, 5);

    // WHEN
    CallParticipantListUpdate update = CallParticipantListUpdate.computeDeltaUpdate(Collections.emptyList(), newList);

    // THEN
    assertTrue(update.hasNoChanges());
  }

  @Test
  public void whenNewListIsAdded_thenIExpectAReducedUpdate() {
    // GIVEN
    List<CallParticipant> list1 = createParticipants(1, 2, 3, 4, 5);
    List<CallParticipant> list2 = createParticipants(2, 3, 4, 5, 6);

    // WHEN
    CallParticipantListUpdate update = CallParticipantListUpdate.computeDeltaUpdate(list1, list2);

    // THEN
    assertFalse(update.hasNoChanges());
    assertThat(update.getAdded(), Matchers.containsInAnyOrder(createHolders(6)));
    assertThat(update.getRemoved(), Matchers.containsInAnyOrder(createHolders(1)));
  }

  @Test
  public void whenRecipientExistsMultipleTimes_thenIExpectOneInstancePrimaryAndOthersSecondary() {
    // GIVEN
    List<CallParticipant> list = createParticipants(new long[]{1, 1, 1}, new long[]{1, 2, 3});

    // WHEN
    CallParticipantListUpdate update = CallParticipantListUpdate.computeDeltaUpdate(Collections.emptyList(), list);

    // THEN
    List<Boolean> isPrimaryList = Stream.of(update.getAdded()).map(CallParticipantListUpdate.Holder::isPrimary).toList();
    assertThat(isPrimaryList, Matchers.containsInAnyOrder(true, false, false));
  }

  static CallParticipantListUpdate.Holder[] createHolders(long ... recipientIds) {
    CallParticipantListUpdate.Holder[] ids = new CallParticipantListUpdate.Holder[recipientIds.length];

    for (int i = 0; i < recipientIds.length; i++) {
      CallParticipant participant = createParticipant(recipientIds[i], recipientIds[i]);

      ids[i] = CallParticipantListUpdate.createHolder(participant, true);
    }

    return ids;
  }

  private static List<CallParticipant> createPlaceholderParticipants(long ... recipientIds) {
    long[] deMuxIds = new long[recipientIds.length];
    Arrays.fill(deMuxIds, -1);
    return createParticipants(recipientIds, deMuxIds);
  }

  private static List<CallParticipant> createParticipants(long ... recipientIds) {
    return createParticipants(recipientIds, recipientIds);
  }

  private static List<CallParticipant> createParticipants(long[] recipientIds, long[] placeholderIds) {
    List<CallParticipant> participants = new ArrayList<>(recipientIds.length);
    for (int i = 0; i < recipientIds.length; i++) {
      participants.add(createParticipant(recipientIds[i], placeholderIds[i]));
    }

    return participants;
  }

  private static CallParticipant createParticipant(long recipientId, long deMuxId) {
    Recipient recipient = new Recipient(RecipientId.from(recipientId), mock(RecipientDetails.class), true);

    return CallParticipant.createRemote(new CallParticipantId(deMuxId, recipient.getId()), recipient, null, new BroadcastVideoSink(null), false, false, -1, false);
  }

}