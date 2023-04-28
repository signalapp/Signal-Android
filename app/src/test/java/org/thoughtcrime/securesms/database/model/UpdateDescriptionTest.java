package org.thoughtcrime.securesms.database.model;

import android.app.Application;
import android.text.SpannableString;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public final class UpdateDescriptionTest {

  @Test
  public void staticDescription_byGetStaticString() {
    UpdateDescription description = UpdateDescription.staticDescription("update", 0);

    assertEquals("update", description.getStaticSpannable().toString());
  }

  @Test
  public void staticDescription_has_empty_mentions() {
    UpdateDescription description = UpdateDescription.staticDescription("update", 0);

    assertTrue(description.getMentioned().isEmpty());
  }

  @Test
  public void staticDescription_byString() {
    UpdateDescription description = UpdateDescription.staticDescription("update", 0);

    assertEquals("update", description.getSpannable().toString());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void stringFactory_cannot_call_static_string() {
    UpdateDescription description = UpdateDescription.mentioning(Collections.singletonList(ServiceId.from(UUID.randomUUID())), () -> new SpannableString("update"), 0);

    description.getStaticSpannable();
  }

  @Test
  public void stringFactory_not_evaluated_until_getString() {
    AtomicInteger factoryCalls = new AtomicInteger();

    UpdateDescription.SpannableFactory stringFactory = () -> {
      factoryCalls.incrementAndGet();
      return new SpannableString("update");
    };

    UpdateDescription description = UpdateDescription.mentioning(Collections.singletonList(ServiceId.from(UUID.randomUUID())), stringFactory, 0);

    assertEquals(0, factoryCalls.get());

    String string = description.getSpannable().toString();

    assertEquals("update", string);
    assertEquals(1, factoryCalls.get());
  }

  @Test
  public void stringFactory_reevaluated_on_every_call() {
    AtomicInteger                      factoryCalls  = new AtomicInteger();
    UpdateDescription.SpannableFactory stringFactory = () -> new SpannableString( "call" + factoryCalls.incrementAndGet());
    UpdateDescription                  description   = UpdateDescription.mentioning(Collections.singletonList(ServiceId.from(UUID.randomUUID())), stringFactory, 0);

    assertEquals("call1", description.getSpannable().toString());
    assertEquals("call2", description.getSpannable().toString());
    assertEquals("call3", description.getSpannable().toString());
  }

  @Test
  public void concat_static_lines() {
    UpdateDescription description1 = UpdateDescription.staticDescription("update1", 0);
    UpdateDescription description2 = UpdateDescription.staticDescription("update2", 0);

    UpdateDescription description = UpdateDescription.concatWithNewLines(Arrays.asList(description1, description2));

    assertTrue(description.isStringStatic());
    assertEquals("update1\nupdate2", description.getStaticSpannable().toString());
    assertEquals("update1\nupdate2", description.getSpannable().toString());
  }

  @Test
  public void concat_single_does_not_make_new_object() {
    UpdateDescription description = UpdateDescription.staticDescription("update1", 0);

    UpdateDescription concat = UpdateDescription.concatWithNewLines(Collections.singletonList(description));

    assertSame(description, concat);
  }

  @Test
  public void concat_dynamic_lines() {
    AtomicInteger                      factoryCalls1  = new AtomicInteger();
    AtomicInteger                      factoryCalls2  = new AtomicInteger();
    UpdateDescription.SpannableFactory stringFactory1 = () -> new SpannableString("update." + factoryCalls1.incrementAndGet());
    UpdateDescription.SpannableFactory stringFactory2 = () -> new SpannableString("update." + factoryCalls2.incrementAndGet());
    UpdateDescription                  description1   = UpdateDescription.mentioning(Collections.singletonList(ServiceId.from(UUID.randomUUID())), stringFactory1, 0);
    UpdateDescription                  description2   = UpdateDescription.mentioning(Collections.singletonList(ServiceId.from(UUID.randomUUID())), stringFactory2, 0);

    factoryCalls1.set(10);
    factoryCalls2.set(20);

    UpdateDescription description = UpdateDescription.concatWithNewLines(Arrays.asList(description1, description2));

    assertFalse(description.isStringStatic());

    assertEquals("update.11\nupdate.21", description.getSpannable().toString());
    assertEquals("update.12\nupdate.22", description.getSpannable().toString());
    assertEquals("update.13\nupdate.23", description.getSpannable().toString());
  }

  @Test
  public void concat_dynamic_lines_and_static_lines() {
    AtomicInteger                      factoryCalls1  = new AtomicInteger();
    AtomicInteger                      factoryCalls2  = new AtomicInteger();
    UpdateDescription.SpannableFactory stringFactory1 = () -> new SpannableString("update." + factoryCalls1.incrementAndGet());
    UpdateDescription.SpannableFactory stringFactory2 = () -> new SpannableString("update." + factoryCalls2.incrementAndGet());
    UpdateDescription                  description1   = UpdateDescription.mentioning(Collections.singletonList(ServiceId.from(UUID.randomUUID())), stringFactory1, 0);
    UpdateDescription                  description2   = UpdateDescription.staticDescription("static", 0);
    UpdateDescription                  description3   = UpdateDescription.mentioning(Collections.singletonList(ServiceId.from(UUID.randomUUID())), stringFactory2, 0);

    factoryCalls1.set(100);
    factoryCalls2.set(200);

    UpdateDescription description = UpdateDescription.concatWithNewLines(Arrays.asList(description1, description2, description3));

    assertFalse(description.isStringStatic());

    assertEquals("update.101\nstatic\nupdate.201", description.getSpannable().toString());
    assertEquals("update.102\nstatic\nupdate.202", description.getSpannable().toString());
    assertEquals("update.103\nstatic\nupdate.203", description.getSpannable().toString());
  }
}
