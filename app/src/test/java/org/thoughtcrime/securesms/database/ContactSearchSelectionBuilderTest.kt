package org.thoughtcrime.securesms.database

import org.junit.Assert
import org.junit.Test
import org.signal.core.util.SqlUtil
import org.thoughtcrime.securesms.recipients.RecipientId

class ContactSearchSelectionBuilderTest {
  @Test(expected = IllegalStateException::class)
  fun `Given non registered and registered are false, when I build, then I expect an IllegalStateException`() {
    RecipientTable.ContactSearchSelection.Builder()
      .withNonRegistered(false)
      .withRegistered(false)
      .build()
  }

  @Test
  fun `Given registered, when I build, then I expect SIGNAL_CONTACT`() {
    val result = RecipientTable.ContactSearchSelection.Builder()
      .withRegistered(true)
      .build()

    Assert.assertTrue(result.where.contains(RecipientTable.ContactSearchSelection.SIGNAL_CONTACT))
    Assert.assertTrue(result.where.contains(RecipientTable.ContactSearchSelection.FILTER_BLOCKED))
    Assert.assertTrue(result.where.contains(RecipientTable.ContactSearchSelection.FILTER_HIDDEN))
    Assert.assertArrayEquals(SqlUtil.buildArgs(RecipientTable.RegisteredState.REGISTERED.id, 1, 0, 0), result.args)
  }

  @Test
  fun `Given exclude id, when I build, then I expect FILTER_ID`() {
    val result = RecipientTable.ContactSearchSelection.Builder()
      .withRegistered(true)
      .excludeId(RecipientId.from(12))
      .build()

    Assert.assertTrue(result.where.contains(RecipientTable.ContactSearchSelection.FILTER_ID))
  }

  @Test
  fun `Given all non group contacts, when I build, then I expect both CONTACT and FILTER_GROUP`() {
    val result = RecipientTable.ContactSearchSelection.Builder()
      .withRegistered(true)
      .withNonRegistered(true)
      .withGroups(false)
      .build()

    Assert.assertTrue(result.where.contains(RecipientTable.ContactSearchSelection.SIGNAL_CONTACT))
    Assert.assertTrue(result.where.contains(") OR ("))
    Assert.assertTrue(result.where.contains(RecipientTable.ContactSearchSelection.NON_SIGNAL_CONTACT))
    Assert.assertTrue(result.where.contains(RecipientTable.ContactSearchSelection.FILTER_GROUPS))
    Assert.assertTrue(result.where.contains(RecipientTable.ContactSearchSelection.FILTER_BLOCKED))
    Assert.assertTrue(result.where.contains(RecipientTable.ContactSearchSelection.FILTER_HIDDEN))
    Assert.assertArrayEquals(
      SqlUtil.buildArgs(
        RecipientTable.RegisteredState.REGISTERED.id,
        1,
        RecipientTable.RegisteredState.REGISTERED.id,
        0,
        0
      ),
      result.args
    )
  }

  @Test
  fun `Given a query, when I build, then I expect QUERY_SIGNAL_CONTACT`() {
    val result = RecipientTable.ContactSearchSelection.Builder()
      .withRegistered(true)
      .withGroups(false)
      .withSearchQuery("query")
      .build()

    Assert.assertTrue(result.where.contains(RecipientTable.ContactSearchSelection.QUERY_SIGNAL_CONTACT))
    Assert.assertTrue(result.args.contains("query"))
  }
}
