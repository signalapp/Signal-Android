package org.thoughtcrime.securesms.service.webrtc.collections;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class ParticipantCollectionTest {

  private final ParticipantCollection testSubject = new ParticipantCollection(3);

  @Test
  public void givenAnEmptyCollection_whenIAdd3Participants_thenIExpectThemToBeOrderedByAddedToCallTime() {
    // GIVEN
    List<CallParticipant> input = Arrays.asList(participant(1, 1, 4), participant(2, 1, 2), participant(3, 1, 3));

    // WHEN
    ParticipantCollection result = testSubject.getNext(input);

    // THEN
    assertThat(result.getGridParticipants(), Matchers.contains(id(2), id(3), id(1)));
  }

  @Test
  public void givenAnEmptyCollection_whenIAdd3Participants_thenIExpectNoListParticipants() {
    // GIVEN
    List<CallParticipant> input = Arrays.asList(participant(1, 1, 4), participant(2, 1, 2), participant(3, 1, 3));

    // WHEN
    ParticipantCollection result = testSubject.getNext(input);

    // THEN
    assertEquals(result.getListParticipants().size(), 0);
  }

  @Test
  public void givenAnEmptyColletion_whenIAdd4Participants_thenIExpectThemToBeOrderedByLastSpokenThenAddedToCallTime() {
    // GIVEN
    List<CallParticipant> input = Arrays.asList(participant(1, 1, 2),
                                                participant(2, 5, 2),
                                                participant(3, 1, 1),
                                                participant(4, 1, 0));

    // WHEN
    ParticipantCollection result = testSubject.getNext(input);

    // THEN
    assertThat(result.getGridParticipants(), Matchers.contains(id(2), id(4), id(3)));
  }

  @Test
  public void givenACollection_whenIUpdateWithEmptyList_thenIExpectEmptyList() {
    // GIVEN
    List<CallParticipant> initial           = Arrays.asList(participant(1, 1, 2), participant(2, 1, 3), participant(3, 1, 4));
    ParticipantCollection initialCollection = testSubject.getNext(initial);
    List<CallParticipant> next              = Collections.emptyList();

    // WHEN
    ParticipantCollection result = initialCollection.getNext(next);

    // THEN
    assertEquals(0, result.getGridParticipants().size());
  }

  @Test
  public void givenACollection_whenIUpdateWithLatestSpeakerAndSpeakerIsAlreadyInGridSection_thenIExpectTheSameGridSectionOrder() {
    // GIVEN
    List<CallParticipant> initial           = Arrays.asList(participant(1, 1, 2), participant(2, 1, 3), participant(3, 1, 4));
    ParticipantCollection initialCollection = testSubject.getNext(initial);
    List<CallParticipant> next              = Arrays.asList(participant(1, 1, 2), participant(2, 2, 3), participant(3, 1, 4));

    // WHEN
    ParticipantCollection result = initialCollection.getNext(next);

    // THEN
    assertThat(result.getGridParticipants(), Matchers.contains(id(1), id(2), id(3)));
  }

  @Test
  public void givenACollection_whenSomeoneLeaves_thenIDoNotExpectToSeeThemInTheNewList() {
    // GIVEN
    List<CallParticipant> initial           = Arrays.asList(participant(1, 1, 2), participant(2, 1, 3), participant(3, 1, 4));
    ParticipantCollection initialCollection = testSubject.getNext(initial);
    List<CallParticipant> next              = Arrays.asList(participant(2, 2, 3), participant(3, 1, 4));

    // WHEN
    ParticipantCollection result = initialCollection.getNext(next);

    // THEN
    assertThat(result.getGridParticipants(), Matchers.contains(id(2), id(3)));
  }

  @Test
  public void givenACollection_whenMultipleLeave_thenIDoNotExpectToSeeThemInTheNewList() {
    // GIVEN
    ParticipantCollection testSubject       = new ParticipantCollection(4);
    List<CallParticipant> initial           = Arrays.asList(participant(1, 1, 2), participant(2, 1, 3), participant(3, 1, 4), participant(4, 1, 5));
    ParticipantCollection initialCollection = testSubject.getNext(initial);
    List<CallParticipant> next              = Arrays.asList(participant(3, 1, 4), participant(2, 1, 3));

    // WHEN
    ParticipantCollection result = initialCollection.getNext(next);

    // THEN
    assertThat(result.getGridParticipants(), Matchers.contains(id(2), id(3)));
  }



  @Test
  public void bigTest() {

    // Welcome to the Thunder dome. 10 people enter...

    ParticipantCollection testSubject = new ParticipantCollection(6);
    List<CallParticipant> init = Arrays.asList(participant(1, 1, 1),    // Alice
                                               participant(2, 1, 1),    // Bob
                                               participant(3, 1, 1),    // Charlie
                                               participant(4, 1, 1),    // Diane
                                               participant(5, 1, 1),    // Ethel
                                               participant(6, 1, 1),    // Francis
                                               participant(7, 1, 1),    // Georgina
                                               participant(8, 1, 1),    // Henry
                                               participant(9, 1, 1),    // Ignace
                                               participant(10, 1, 1));  // Jericho

    ParticipantCollection initialCollection = testSubject.getNext(init);

    assertThat(initialCollection.getGridParticipants(), Matchers.contains(id(1), id(2), id(3), id(4), id(5), id(6)));
    assertThat(initialCollection.getListParticipants(), Matchers.contains(id(7), id(8), id(9), id(10)));

    // Bob speaks about his trip to antigua...

    List<CallParticipant> bobSpoke = Arrays.asList(participant(1, 1, 1),
                                                   participant(2, 2, 1),
                                                   participant(3, 1, 1),
                                                   participant(4, 1, 1),
                                                   participant(5, 1, 1),
                                                   participant(6, 1, 1),
                                                   participant(7, 1, 1),
                                                   participant(8, 1, 1),
                                                   participant(9, 1, 1),
                                                   participant(10, 1, 1));

    ParticipantCollection afterBobSpoke = initialCollection.getNext(bobSpoke);

    assertThat(afterBobSpoke.getGridParticipants(), Matchers.contains(id(1), id(2), id(3), id(4), id(5), id(6)));
    assertThat(afterBobSpoke.getListParticipants(), Matchers.contains(id(7), id(8), id(9), id(10)));

    // Henry interjects and says now is not the time, this is the thunderdome.

    List<CallParticipant> henrySpoke = Arrays.asList(participant(1, 1, 1),
                                                     participant(2, 2, 1),
                                                     participant(3, 1, 1),
                                                     participant(4, 1, 1),
                                                     participant(5, 1, 1),
                                                     participant(6, 1, 1),
                                                     participant(7, 1, 1),
                                                     participant(8, 3, 1),
                                                     participant(9, 1, 1),
                                                     participant(10, 1, 1));

    ParticipantCollection afterHenrySpoke = afterBobSpoke.getNext(henrySpoke);

    assertThat(afterHenrySpoke.getGridParticipants(), Matchers.contains(id(1), id(2), id(3), id(4), id(5), id(8)));
    assertThat(afterHenrySpoke.getListParticipants(), Matchers.contains(id(6), id(7), id(9), id(10)));

    // Ignace asks how everone's holidays were

    List<CallParticipant> ignaceSpoke = Arrays.asList(participant(1, 1, 1),
                                                        participant(2, 2, 1),
                                                        participant(3, 1, 1),
                                                        participant(4, 1, 1),
                                                        participant(5, 1, 1),
                                                        participant(6, 1, 1),
                                                        participant(7, 1, 1),
                                                        participant(8, 3, 1),
                                                        participant(9, 4, 1),
                                                        participant(10, 1, 1));

    ParticipantCollection afterIgnaceSpoke = afterHenrySpoke.getNext(ignaceSpoke);

    assertThat(afterIgnaceSpoke.getGridParticipants(), Matchers.contains(id(1), id(2), id(3), id(4), id(9), id(8)));
    assertThat(afterIgnaceSpoke.getListParticipants(), Matchers.contains(id(5), id(6), id(7), id(10)));

    // Alice is the first to fall

    List<CallParticipant> aliceLeft = Arrays.asList(participant(2, 2, 1),
                                                    participant(3, 1, 1),
                                                    participant(4, 1, 1),
                                                    participant(5, 1, 1),
                                                    participant(6, 1, 1),
                                                    participant(7, 1, 1),
                                                    participant(8, 3, 1),
                                                    participant(9, 4, 1),
                                                    participant(10, 1, 1));

    ParticipantCollection afterAliceLeft = afterIgnaceSpoke.getNext(aliceLeft);

    assertThat(afterAliceLeft.getGridParticipants(), Matchers.contains(id(5), id(2), id(3), id(4), id(9), id(8)));
    assertThat(afterAliceLeft.getListParticipants(), Matchers.contains(id(6), id(7), id(10)));

    // Just kidding, Alice is back. Georgina and Charlie gasp!

    List<CallParticipant> mixUp = Arrays.asList(participant(1, 1, 5),
                                                participant(2, 2, 1),
                                                participant(3, 6, 1),
                                                participant(4, 1, 1),
                                                participant(5, 1, 1),
                                                participant(6, 1, 1),
                                                participant(7, 5, 1),
                                                participant(8, 3, 1),
                                                participant(9, 4, 1),
                                                participant(10, 1, 1));

    ParticipantCollection afterMixUp = afterAliceLeft.getNext(mixUp);

    assertThat(afterMixUp.getGridParticipants(), Matchers.contains(id(7), id(2), id(3), id(4), id(9), id(8)));
    assertThat(afterMixUp.getListParticipants(), Matchers.contains(id(5), id(6), id(10), id(1)));
  }

  private Matcher<CallParticipant> id(long serializedId) {
    return Matchers.hasProperty("callParticipantId", Matchers.equalTo(new CallParticipantId(serializedId, RecipientId.from(serializedId))));
  }

  private static CallParticipant participant(long serializedId,long lastSpoke, long added) {
    return CallParticipant.createRemote(
        new CallParticipantId(serializedId, RecipientId.from(serializedId)),
        Recipient.UNKNOWN,
        null,
        new BroadcastVideoSink(null),
        false,
        false,
        lastSpoke,
        false,
        added,
        CallParticipant.DeviceOrdinal.PRIMARY);
  }
}