package org.thoughtcrime.securesms.logsubmit;

import org.junit.Test;
import org.thoughtcrime.securesms.logsubmit.util.Scrubber;

import static org.junit.Assert.assertEquals;

public class ScrubberTest {

  @Test
  public void scrub_phoneNumber_solo() {
    Scrubber scrubber = new Scrubber();
    String   output   = scrubber.scrub("+16101234567");

    assertEquals("+*********67", output);
  }

  @Test
  public void scrub_phoneNumber_surrounded() {
    Scrubber scrubber = new Scrubber();
    String   output   = scrubber.scrub("Spider-Man's phone number is +16101234567 -- isn't that crazy?");

    assertEquals("Spider-Man's phone number is +*********67 -- isn't that crazy?", output);
  }

  @Test
  public void scrub_email_solo() {
    Scrubber scrubber = new Scrubber();
    String   output   = scrubber.scrub("jonah@dailybugle.com");

    assertEquals("j*****************om", output);
  }

  @Test
  public void scrub_email_surrounded() {
    Scrubber scrubber = new Scrubber();
    String   output   = scrubber.scrub("Email tips to jonah@dailybugle.com -- it's your civic duty");

    assertEquals("Email tips to j*****************om -- it's your civic duty", output);
  }

  @Test
  public void scrub_groupId_solo() {
    Scrubber scrubber = new Scrubber();
    String   output   = scrubber.scrub("__textsecure_group__!abcdefg1234567890");

    assertEquals("_***********************************90", output);
  }

  @Test
  public void scrub_groupId_surrounded() {
    Scrubber scrubber = new Scrubber();
    String   output   = scrubber.scrub("The group id is __textsecure_group__!abcdefg1234567890 and don't forget it");

    assertEquals("The group id is _***********************************90 and don't forget it", output);
  }
}
