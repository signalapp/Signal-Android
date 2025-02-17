package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.recipients.RecipientId

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MentionUtilTest {
  @Test
  fun vanillaUpdate() {
    val mentions = listOf(Mention(RecipientId.from(1L), 0, 1))

    val update: MentionUtil.UpdatedBodyAndMentions = MentionUtil.update("T test", mentions) { it.recipientId.toString() }

    assertThat(update.body.toString()).isEqualTo("RecipientId::1 test")
  }

  @Test
  fun nextToEachOtherUpdate() {
    val mentions = listOf(
      Mention(RecipientId.from(1L), 0, 3),
      Mention(RecipientId.from(2L), 3, 3)
    )

    val update: MentionUtil.UpdatedBodyAndMentions = MentionUtil.update("ONETWO test", mentions) { it.recipientId.toString() }

    assertThat(update.body.toString()).isEqualTo("RecipientId::1RecipientId::2 test")
  }

  @Test
  fun overlapUpdate() {
    val mentions = listOf(
      Mention(RecipientId.from(1L), 0, 3),
      Mention(RecipientId.from(2L), 1, 5)
    )

    val update: MentionUtil.UpdatedBodyAndMentions = MentionUtil.update("T test", mentions) { it.recipientId.toString() }

    assertThat(update.body.toString()).isEqualTo("RecipientId::1est")
  }
}
