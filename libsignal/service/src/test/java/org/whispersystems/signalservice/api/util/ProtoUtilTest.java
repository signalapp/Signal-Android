package org.whispersystems.signalservice.api.util;

import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Assert;
import org.junit.Test;
import org.thoughtcrime.securesms.util.testprotos.TestInnerMessage;
import org.thoughtcrime.securesms.util.testprotos.TestInnerMessageWithNewString;
import org.thoughtcrime.securesms.util.testprotos.TestPerson;
import org.thoughtcrime.securesms.util.testprotos.TestPersonWithNewFieldOnMessage;
import org.thoughtcrime.securesms.util.testprotos.TestPersonWithNewMessage;
import org.thoughtcrime.securesms.util.testprotos.TestPersonWithNewRepeatedString;
import org.thoughtcrime.securesms.util.testprotos.TestPersonWithNewString;
import org.thoughtcrime.securesms.util.testprotos.TestPersonWithNewStringAndInt;
import org.whispersystems.signalservice.api.util.ProtoUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProtoUtilTest {

  @Test
  public void hasUnknownFields_noUnknowns() {
    TestPerson person = TestPerson.newBuilder()
                                  .setName("Peter Parker")
                                  .setAge(23)
                                  .build();

    assertFalse(ProtoUtil.hasUnknownFields(person));
  }

  @Test
  public void hasUnknownFields_unknownString() throws InvalidProtocolBufferException {
    TestPersonWithNewString person = TestPersonWithNewString.newBuilder()
                                                            .setName("Peter Parker")
                                                            .setAge(23)
                                                            .setJob("Reporter")
                                                            .build();

    TestPerson personWithUnknowns = TestPerson.parseFrom(person.toByteArray());

    assertTrue(ProtoUtil.hasUnknownFields(personWithUnknowns));
  }

  @Test
  public void hasUnknownFields_multipleUnknowns() throws InvalidProtocolBufferException {
    TestPersonWithNewStringAndInt person = TestPersonWithNewStringAndInt.newBuilder()
                                                                        .setName("Peter Parker")
                                                                        .setAge(23)
                                                                        .setJob("Reporter")
                                                                        .setSalary(75_000)
                                                                        .build();

    TestPerson personWithUnknowns = TestPerson.parseFrom(person.toByteArray());

    assertTrue(ProtoUtil.hasUnknownFields(personWithUnknowns));
  }

  @Test
  public void hasUnknownFields_unknownMessage() throws InvalidProtocolBufferException {
    TestPersonWithNewMessage person = TestPersonWithNewMessage.newBuilder()
                                                              .setName("Peter Parker")
                                                              .setAge(23)
                                                              .setJob(TestPersonWithNewMessage.Job.newBuilder()
                                                                                                  .setTitle("Reporter")
                                                                                                  .setSalary(75_000))
                                                              .build();

    TestPerson personWithUnknowns = TestPerson.parseFrom(person.toByteArray());

    assertTrue(ProtoUtil.hasUnknownFields(personWithUnknowns));
  }

  @Test
  public void hasUnknownFields_unknownInsideMessage() throws InvalidProtocolBufferException {
    TestPersonWithNewFieldOnMessage person = TestPersonWithNewFieldOnMessage.newBuilder()
                                                                            .setName("Peter Parker")
                                                                            .setAge(23)
                                                                            .setJob(TestPersonWithNewFieldOnMessage.Job.newBuilder()
                                                                                                                       .setTitle("Reporter")
                                                                                                                       .setSalary(75_000)
                                                                                                                       .setStartDate(100))
                                                                            .build();

    TestPersonWithNewMessage personWithUnknowns = TestPersonWithNewMessage.parseFrom(person.toByteArray());

    assertTrue(ProtoUtil.hasUnknownFields(personWithUnknowns));
  }

  @Test
  public void hasUnknownFields_nullInnerMessage() throws InvalidProtocolBufferException {
    TestPersonWithNewMessage person = TestPersonWithNewMessage.newBuilder()
                                                              .setName("Peter Parker")
                                                              .setAge(23)
                                                              .build();

    TestPerson personWithUnknowns = TestPerson.parseFrom(person.toByteArray());

    assertFalse(ProtoUtil.hasUnknownFields(personWithUnknowns));
  }

  @Test
  public void combineWithUnknownFields_noUnknowns() throws InvalidProtocolBufferException {
    TestPerson personWithUnknowns = TestPerson.newBuilder()
                                              .setName("Peter Parker")
                                              .setAge(23)
                                              .build();

    TestPerson localRepresentation = TestPerson.newBuilder()
                                               .setName("Spider-Man")
                                               .setAge(23)
                                               .build();

    TestPerson              combinedWithUnknowns = ProtoUtil.combineWithUnknownFields(localRepresentation, personWithUnknowns.toByteArray());
    TestPersonWithNewString reparsedPerson       = TestPersonWithNewString.parseFrom(combinedWithUnknowns.toByteArray());

    Assert.assertEquals("Spider-Man", reparsedPerson.getName());
    Assert.assertEquals(23, reparsedPerson.getAge());
  }

  @Test
  public void combineWithUnknownFields_appendedString() throws InvalidProtocolBufferException {
    TestPersonWithNewString personWithUnknowns = TestPersonWithNewString.newBuilder()
                                                            .setName("Peter Parker")
                                                            .setAge(23)
                                                            .setJob("Reporter")
                                                            .build();

    TestPerson localRepresentation = TestPerson.newBuilder()
                                               .setName("Spider-Man")
                                               .setAge(23)
                                               .build();

    TestPerson              combinedWithUnknowns = ProtoUtil.combineWithUnknownFields(localRepresentation, personWithUnknowns.toByteArray());
    TestPersonWithNewString reparsedPerson       = TestPersonWithNewString.parseFrom(combinedWithUnknowns.toByteArray());

    Assert.assertEquals("Spider-Man", reparsedPerson.getName());
    Assert.assertEquals(23, reparsedPerson.getAge());
    Assert.assertEquals("Reporter", reparsedPerson.getJob());
  }

  @Test
  public void combineWithUnknownFields_appendedRepeatedString() throws InvalidProtocolBufferException {
    TestPersonWithNewRepeatedString personWithUnknowns = TestPersonWithNewRepeatedString.newBuilder()
                                                                                        .setName("Peter Parker")
                                                                                        .setAge(23)
                                                                                        .addJobs("Reporter")
                                                                                        .addJobs("Super Hero")
                                                                                        .build();

    TestPerson localRepresentation = TestPerson.newBuilder()
                                               .setName("Spider-Man")
                                               .setAge(23)
                                               .build();

    TestPerson                      combinedWithUnknowns = ProtoUtil.combineWithUnknownFields(localRepresentation, personWithUnknowns.toByteArray());
    TestPersonWithNewRepeatedString reparsedPerson       = TestPersonWithNewRepeatedString.parseFrom(combinedWithUnknowns.toByteArray());

    Assert.assertEquals("Spider-Man", reparsedPerson.getName());
    Assert.assertEquals(23, reparsedPerson.getAge());
    Assert.assertEquals(2, reparsedPerson.getJobsCount());
    Assert.assertEquals("Reporter", reparsedPerson.getJobs(0));
    Assert.assertEquals("Super Hero", reparsedPerson.getJobs(1));
  }

  @Test
  public void combineWithUnknownFields_appendedStringAndInt() throws InvalidProtocolBufferException {
    TestPersonWithNewStringAndInt personWithUnknowns = TestPersonWithNewStringAndInt.newBuilder()
                                                                                    .setName("Peter Parker")
                                                                                    .setAge(23)
                                                                                    .setJob("Reporter")
                                                                                    .setSalary(75_000)
                                                                                    .build();

    TestPerson localRepresentation = TestPerson.newBuilder()
                                               .setName("Spider-Man")
                                               .setAge(23)
                                               .build();

    TestPerson                    combinedWithUnknowns = ProtoUtil.combineWithUnknownFields(localRepresentation, personWithUnknowns.toByteArray());
    TestPersonWithNewStringAndInt reparsedPerson       = TestPersonWithNewStringAndInt.parseFrom(combinedWithUnknowns.toByteArray());

    Assert.assertEquals("Spider-Man", reparsedPerson.getName());
    Assert.assertEquals(23, reparsedPerson.getAge());
    Assert.assertEquals("Reporter", reparsedPerson.getJob());
    Assert.assertEquals(75_000, reparsedPerson.getSalary());
  }

  @Test
  public void combineWithUnknownFields_appendedMessage() throws InvalidProtocolBufferException {
    TestPersonWithNewMessage personWithUnknowns = TestPersonWithNewMessage.newBuilder()
                                                                          .setName("Peter Parker")
                                                                          .setAge(23)
                                                                          .setJob(TestPersonWithNewMessage.Job.newBuilder()
                                                                                                              .setTitle("Reporter")
                                                                                                              .setSalary(75_000))
                                                                          .build();

    TestPerson localRepresentation = TestPerson.newBuilder()
                                               .setName("Spider-Man")
                                               .setAge(23)
                                               .build();

    TestPerson               combinedWithUnknowns = ProtoUtil.combineWithUnknownFields(localRepresentation, personWithUnknowns.toByteArray());
    TestPersonWithNewMessage reparsedPerson       = TestPersonWithNewMessage.parseFrom(combinedWithUnknowns.toByteArray());

    Assert.assertEquals("Spider-Man", reparsedPerson.getName());
    Assert.assertEquals(23, reparsedPerson.getAge());
    Assert.assertEquals("Reporter", reparsedPerson.getJob().getTitle());
    Assert.assertEquals(75_000, reparsedPerson.getJob().getSalary());
  }

  /**
   * This isn't ideal behavior. This is more to show how something works. In the future, it'd be
   * nice to support inner unknown fields.
   */
  @Test
  public void combineWithUnknownFields_innerMessagesUnknownsIgnored() throws InvalidProtocolBufferException {
    TestInnerMessageWithNewString test = TestInnerMessageWithNewString.newBuilder()
                                                                      .setInner(TestInnerMessageWithNewString.Inner.newBuilder()
                                                                                                                   .setA("a1")
                                                                                                                   .setB("b1")
                                                                                                                   .build())
                                                                      .build();

    TestInnerMessage localRepresentation = TestInnerMessage.newBuilder()
                                                           .setInner(TestInnerMessage.Inner.newBuilder()
                                                                                           .setA("a2")
                                                                                           .build())
                                                           .build();

    TestInnerMessage              combined     = ProtoUtil.combineWithUnknownFields(localRepresentation, test.toByteArray());
    TestInnerMessageWithNewString reparsedTest = TestInnerMessageWithNewString.parseFrom(combined.toByteArray());

    Assert.assertEquals("a2", reparsedTest.getInner().getA());
    Assert.assertEquals("", reparsedTest.getInner().getB());
  }
}
