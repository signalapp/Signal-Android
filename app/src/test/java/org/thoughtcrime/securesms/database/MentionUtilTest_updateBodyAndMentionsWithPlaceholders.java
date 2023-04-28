package org.thoughtcrime.securesms.database;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.thoughtcrime.securesms.database.MentionUtil.MENTION_PLACEHOLDER;
import static org.thoughtcrime.securesms.database.MentionUtil.MENTION_STARTER;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public class MentionUtilTest_updateBodyAndMentionsWithPlaceholders {

  private final String        body;
  private final List<Mention> mentions;
  private final String        updatedBody;
  private final List<Mention> updatedMentions;

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      /* Empty states */
      { null, Collections.emptyList(), null, Collections.emptyList() },
      builder().text("no mentions").build(),
      builder().text("").build(),
      builder().text("no mentions but @tester text").build(),

      /* Singles */
      builder().mention("test").text(" start").build(),
      builder().text("middle ").mention("test").text(" middle").build(),
      builder().text("end end ").mention("test").build(),
      builder().mention("test").build(),

      /* Doubles */
      builder().mention("foo").text(" ").mention("barbaz").build(),
      builder().text("start text ").mention("barbazbuzz").text(" ").mention("barbaz").build(),
      builder().text("what what ").mention("foo").text(" ").mention("barbaz").text(" more text").build(),
      builder().mention("barbazbuzz").text(" ").mention("foo").build(),

      /* Triples */
      builder().mention("test").text(" ").mention("test2").text(" ").mention("test3").build(),
      builder().text("Starting ").mention("test").text(" ").mention("test2").text(" ").mention("test3").build(),
      builder().mention("test").text(" ").mention("test2").text(" ").mention("test3").text(" ending").build(),
      builder().mention("test").text(" ").mention("test2").text(" ").mention("test3").build(),
      builder().mention("no").mention("spaces").mention("atall").build(),

      /* Emojis and Spaces */
      builder().mention("test").text(" start 🤘").build(),
      builder().mention("test").text(" start 🤘🤘").build(),
      builder().mention("test").text(" start 👍🏾").build(),
      builder().text("middle 🤡 ").mention("foo").text(" 👍🏾 middle").build(),
      builder().text("middle 🤡👍🏾 ").mention("test").text(" 👍🏾 middle").build(),
      builder().text("end end 💀 💀 💀 ").mention("bar baz buzz").build(),
      builder().text("end end 🖖🏼 🖖🏼 🖖🏼 ").mention("really long name").build(),
      builder().text("middle 🤡👍🏾 👨🏼‍🤝‍👨🏽 ").mention("a").text(" 👍🏾 middle 👩‍👩‍👦‍👦").build(),
      builder().text("start ").mention("emoji 🩳").build(),
      builder().text("start ").mention("emoji 🩳").text(" middle ").mention("emoji 🩳").text(" end").build(),

      { "message", Collections.singletonList(new Mention(RecipientId.from(1), 30, 5)), "message", Collections.<Mention>emptyList() }
    });
  }

  public MentionUtilTest_updateBodyAndMentionsWithPlaceholders(String body, List<Mention> mentions, String updatedBody, List<Mention> updatedMentions) {
    this.body            = body;
    this.mentions        = mentions;
    this.updatedBody     = updatedBody;
    this.updatedMentions = updatedMentions;
  }

  @Test
  public void updateBodyAndMentions() {
    MentionUtil.UpdatedBodyAndMentions updated = MentionUtil.updateBodyAndMentionsWithPlaceholders(body, mentions);
    assertEquals(updatedBody, updated.getBodyAsString());
    assertEquals(updatedMentions, updated.getMentions());
  }

  private static Builder builder() {
    return new Builder();
  }

  private static class Builder {
    private StringBuilder bodyBuilder     = new StringBuilder();
    private StringBuilder expectedBuilder = new StringBuilder();

    private List<Mention> mentions         = new ArrayList<>();
    private List<Mention> expectedMentions = new ArrayList<>();

    Builder text(String text) {
      bodyBuilder.append(text);
      expectedBuilder.append(text);
      return this;
    }

    Builder mention(String name) {
      Mention input = new Mention(RecipientId.from(new Random().nextLong()), bodyBuilder.length(), name.length() + 1);
      bodyBuilder.append(MENTION_STARTER).append(name);
      mentions.add(input);

      Mention output = new Mention(input.getRecipientId(), expectedBuilder.length(), MENTION_PLACEHOLDER.length());
      expectedBuilder.append(MENTION_PLACEHOLDER);
      expectedMentions.add(output);
      return this;
    }

    Object[] build() {
      return new Object[]{ bodyBuilder.toString(), mentions, expectedBuilder.toString(), expectedMentions };
    }
  }
}
