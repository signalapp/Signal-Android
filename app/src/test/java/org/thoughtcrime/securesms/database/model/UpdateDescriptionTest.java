package org.thoughtcrime.securesms.database.model;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.signal.core.util.ThreadUtil;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class UpdateDescriptionTest {

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Mock
  private MockedStatic<ThreadUtil> threadUtilMockedStatic;

  @Before
  public void setup() {
    threadUtilMockedStatic.when(ThreadUtil::isMainThread).thenReturn(true);
    threadUtilMockedStatic.when(ThreadUtil::assertMainThread).thenCallRealMethod();
    threadUtilMockedStatic.when(ThreadUtil::assertNotMainThread).thenCallRealMethod();
  }

  @Test
  public void staticDescription_byGetStaticString() {
    UpdateDescription description = UpdateDescription.staticDescription("update", 0);

    assertEquals("update", description.getStaticString());
  }

  @Test
  public void staticDescription_has_empty_mentions() {
    UpdateDescription description = UpdateDescription.staticDescription("update", 0);

    assertTrue(description.getMentioned().isEmpty());
  }

  @Test
  public void staticDescription_byString() {
    UpdateDescription description = UpdateDescription.staticDescription("update", 0);

    assertEquals("update", description.getString());
  }

  @Test(expected = AssertionError.class)
  public void stringFactory_cannot_run_on_main_thread() {
    UpdateDescription description = UpdateDescription.mentioning(Collections.singletonList(ServiceId.from(UUID.randomUUID())), () -> "update", 0);

    threadUtilMockedStatic.when(ThreadUtil::isMainThread).thenReturn(true);

    description.getString();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void stringFactory_cannot_call_static_string() {
    UpdateDescription description = UpdateDescription.mentioning(Collections.singletonList(ServiceId.from(UUID.randomUUID())), () -> "update", 0);

    description.getStaticString();
  }

  @Test
  public void stringFactory_not_evaluated_until_getString() {
    AtomicInteger factoryCalls = new AtomicInteger();

    UpdateDescription.StringFactory stringFactory = () -> {
      factoryCalls.incrementAndGet();
      return "update";
    };

    UpdateDescription description = UpdateDescription.mentioning(Collections.singletonList(ServiceId.from(UUID.randomUUID())), stringFactory, 0);

    assertEquals(0, factoryCalls.get());

    threadUtilMockedStatic.when(ThreadUtil::isMainThread).thenReturn(false);

    String string = description.getString();

    assertEquals("update", string);
    assertEquals(1, factoryCalls.get());
  }

  @Test
  public void stringFactory_reevaluated_on_every_call() {
    AtomicInteger                   factoryCalls  = new AtomicInteger();
    UpdateDescription.StringFactory stringFactory = () -> "call" + factoryCalls.incrementAndGet();
    UpdateDescription               description   = UpdateDescription.mentioning(Collections.singletonList(ServiceId.from(UUID.randomUUID())), stringFactory, 0);

    threadUtilMockedStatic.when(ThreadUtil::isMainThread).thenReturn(false);

    assertEquals("call1", description.getString());
    assertEquals("call2", description.getString());
    assertEquals("call3", description.getString());
  }

  @Test
  public void concat_static_lines() {
    UpdateDescription description1 = UpdateDescription.staticDescription("update1", 0);
    UpdateDescription description2 = UpdateDescription.staticDescription("update2", 0);

    UpdateDescription description = UpdateDescription.concatWithNewLines(Arrays.asList(description1, description2));

    assertTrue(description.isStringStatic());
    assertEquals("update1\nupdate2", description.getStaticString());
    assertEquals("update1\nupdate2", description.getString());
  }

  @Test
  public void concat_single_does_not_make_new_object() {
    UpdateDescription description = UpdateDescription.staticDescription("update1", 0);

    UpdateDescription concat = UpdateDescription.concatWithNewLines(Collections.singletonList(description));

    assertSame(description, concat);
  }

  @Test
  public void concat_dynamic_lines() {
    AtomicInteger                   factoryCalls1  = new AtomicInteger();
    AtomicInteger                   factoryCalls2  = new AtomicInteger();
    UpdateDescription.StringFactory stringFactory1 = () -> "update." + factoryCalls1.incrementAndGet();
    UpdateDescription.StringFactory stringFactory2 = () -> "update." + factoryCalls2.incrementAndGet();
    UpdateDescription               description1   = UpdateDescription.mentioning(Collections.singletonList(ServiceId.from(UUID.randomUUID())), stringFactory1, 0);
    UpdateDescription               description2   = UpdateDescription.mentioning(Collections.singletonList(ServiceId.from(UUID.randomUUID())), stringFactory2, 0);

    factoryCalls1.set(10);
    factoryCalls2.set(20);

    UpdateDescription description = UpdateDescription.concatWithNewLines(Arrays.asList(description1, description2));

    assertFalse(description.isStringStatic());

    threadUtilMockedStatic.when(ThreadUtil::isMainThread).thenReturn(false);

    assertEquals("update.11\nupdate.21", description.getString());
    assertEquals("update.12\nupdate.22", description.getString());
    assertEquals("update.13\nupdate.23", description.getString());
  }

  @Test
  public void concat_dynamic_lines_and_static_lines() {
    AtomicInteger                   factoryCalls1  = new AtomicInteger();
    AtomicInteger                   factoryCalls2  = new AtomicInteger();
    UpdateDescription.StringFactory stringFactory1 = () -> "update." + factoryCalls1.incrementAndGet();
    UpdateDescription.StringFactory stringFactory2 = () -> "update." + factoryCalls2.incrementAndGet();
    UpdateDescription               description1   = UpdateDescription.mentioning(Collections.singletonList(ServiceId.from(UUID.randomUUID())), stringFactory1, 0);
    UpdateDescription               description2   = UpdateDescription.staticDescription("static", 0);
    UpdateDescription               description3   = UpdateDescription.mentioning(Collections.singletonList(ServiceId.from(UUID.randomUUID())), stringFactory2, 0);

    factoryCalls1.set(100);
    factoryCalls2.set(200);

    UpdateDescription description = UpdateDescription.concatWithNewLines(Arrays.asList(description1, description2, description3));

    assertFalse(description.isStringStatic());

    threadUtilMockedStatic.when(ThreadUtil::isMainThread).thenReturn(false);

    assertEquals("update.101\nstatic\nupdate.201", description.getString());
    assertEquals("update.102\nstatic\nupdate.202", description.getString());
    assertEquals("update.103\nstatic\nupdate.203", description.getString());
  }
}