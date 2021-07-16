package org.thoughtcrime.securesms.database

import org.junit.Assert
import org.junit.Test
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.SqlUtil

class ContactSearchSelectionBuilderTest {
  @Test(expected = IllegalStateException::class)
  fun `Given non registered and registered are false, when I build, then I expect an IllegalStateException`() {
    RecipientDatabase.ContactSearchSelection.Builder()
      .withNonRegistered(false)
      .withRegistered(false)
      .build()
  }

  @Test
  fun `Given registered, when I build, then I expect SIGNAL_CONTACT`() {
    val result = RecipientDatabase.ContactSearchSelection.Builder()
      .withRegistered(true)
      .build()

    Assert.assertTrue(result.where.contains(RecipientDatabase.ContactSearchSelection.SIGNAL_CONTACT))
    Assert.assertTrue(result.where.contains(RecipientDatabase.ContactSearchSelection.FILTER_BLOCKED))
    Assert.assertArrayEquals(SqlUtil.buildArgs(RecipientDatabase.RegisteredState.REGISTERED.id, 1, 0), result.args)
  }

  @Test
  fun `Given exclude id, when I build, then I expect FILTER_ID`() {
    val result = RecipientDatabase.ContactSearchSelection.Builder()
      .withRegistered(true)
      .excludeId(RecipientId.from(12))
      .build()

    Assert.assertTrue(result.where.contains(RecipientDatabase.ContactSearchSelection.FILTER_ID))
  }

  @Test
  fun `Given all non group contacts, when I build, then I expect both CONTACT and FILTER_GROUP`() {
    val result = RecipientDatabase.ContactSearchSelection.Builder()
      .withRegistered(true)
      .withNonRegistered(true)
      .withGroups(false)
      .build()

    Assert.assertTrue(result.where.contains(RecipientDatabase.ContactSearchSelection.SIGNAL_CONTACT))
    Assert.assertTrue(result.where.contains(") OR ("))
    Assert.assertTrue(result.where.contains(RecipientDatabase.ContactSearchSelection.NON_SIGNAL_CONTACT))
    Assert.assertTrue(result.where.contains(RecipientDatabase.ContactSearchSelection.FILTER_GROUPS))
    Assert.assertTrue(result.where.contains(RecipientDatabase.ContactSearchSelection.FILTER_BLOCKED))
    Assert.assertArrayEquals(
      SqlUtil.buildArgs(
        RecipientDatabase.RegisteredState.REGISTERED.id, 1,
        RecipientDatabase.RegisteredState.REGISTERED.id,
        0
      ),
      result.args
    )
  }

  @Test
  fun `Given a query, when I build, then I expect QUERY_SIGNAL_CONTACT`() {
    val result = RecipientDatabase.ContactSearchSelection.Builder()
      .withRegistered(true)
      .withGroups(false)
      .withSearchQuery("query")
      .build()

    Assert.assertTrue(result.where.contains(RecipientDatabase.ContactSearchSelection.QUERY_SIGNAL_CONTACT))
    Assert.assertTrue(result.args.contains("query"))
  }
}
