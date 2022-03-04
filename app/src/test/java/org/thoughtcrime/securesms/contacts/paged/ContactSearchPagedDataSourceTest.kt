package org.thoughtcrime.securesms.contacts.paged

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.thoughtcrime.securesms.MockCursor
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

@RunWith(JUnit4::class)
class ContactSearchPagedDataSourceTest {

  private val repository = mock(ContactSearchPagedDataSourceRepository::class.java)
  private val cursor = mock(MockCursor::class.java)
  private val groupStoryData = ContactSearchData.Story(Recipient.UNKNOWN, 0)

  @Before
  fun setUp() {
    `when`(repository.getRecipientFromGroupCursor(cursor)).thenReturn(Recipient.UNKNOWN)
    `when`(repository.getRecipientFromRecipientCursor(cursor)).thenReturn(Recipient.UNKNOWN)
    `when`(repository.getRecipientFromThreadCursor(cursor)).thenReturn(Recipient.UNKNOWN)
    `when`(repository.getRecipientFromDistributionListCursor(cursor)).thenReturn(Recipient.UNKNOWN)
    `when`(repository.getGroupStories()).thenReturn(emptySet())
    `when`(cursor.moveToPosition(anyInt())).thenCallRealMethod()
    `when`(cursor.moveToNext()).thenCallRealMethod()
    `when`(cursor.position).thenCallRealMethod()
  }

  @Test
  fun `Given recentsWHeader and individualsWHeaderWExpand, when I size, then I expect 15`() {
    val testSubject = createTestSubject()
    Assert.assertEquals(15, testSubject.size())
  }

  @Test
  fun `Given recentsWHeader and individualsWHeaderWExpand, when I load 12, then I expect properly structured output`() {
    val testSubject = createTestSubject()
    val result = testSubject.load(0, 12) { false }

    val expected = listOf(
      ContactSearchKey.Header(ContactSearchConfiguration.SectionKey.RECENTS),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.Header(ContactSearchConfiguration.SectionKey.INDIVIDUALS)
    )

    val resultKeys = result.map { it.contactSearchKey }

    Assert.assertEquals(expected, resultKeys)
  }

  @Test
  fun `Given recentsWHeader and individualsWHeaderWExpand, when I load 10 with offset 5, then I expect properly structured output`() {
    val testSubject = createTestSubject()
    val result = testSubject.load(5, 10) { false }

    val expected = listOf(
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.Header(ContactSearchConfiguration.SectionKey.INDIVIDUALS),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.Expand(ContactSearchConfiguration.SectionKey.INDIVIDUALS)
    )

    val resultKeys = result.map { it.contactSearchKey }

    Assert.assertEquals(expected, resultKeys)
  }

  @Test
  fun `Given storiesWithHeaderAndExtras, when I load 11, then I expect properly structured output`() {
    val testSubject = createStoriesSubject()
    val result = testSubject.load(0, 12) { false }

    val expected = listOf(
      ContactSearchKey.Header(ContactSearchConfiguration.SectionKey.STORIES),
      ContactSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.Story(RecipientId.UNKNOWN),
    )

    val resultKeys = result.map { it.contactSearchKey }

    Assert.assertEquals(expected, resultKeys)
  }

  private fun createStoriesSubject(): ContactSearchPagedDataSource {
    val configuration = ContactSearchConfiguration.build {
      addSection(
        ContactSearchConfiguration.Section.Stories(
          groupStories = setOf(
            groupStoryData
          ),
          includeHeader = true,
          expandConfig = ContactSearchConfiguration.ExpandConfig(isExpanded = true)
        )
      )
    }

    `when`(repository.getStories(any())).thenReturn(cursor)
    `when`(repository.recipientNameContainsQuery(Recipient.UNKNOWN, null)).thenReturn(true)
    `when`(cursor.count).thenReturn(10)

    return ContactSearchPagedDataSource(configuration, repository)
  }

  private fun createTestSubject(): ContactSearchPagedDataSource {
    val recents = ContactSearchConfiguration.Section.Recents(
      includeHeader = true
    )

    val configuration = ContactSearchConfiguration.build {
      addSection(recents)

      addSection(
        ContactSearchConfiguration.Section.Individuals(
          includeHeader = true,
          includeSelf = false,
          transportType = ContactSearchConfiguration.TransportType.ALL,
          expandConfig = ContactSearchConfiguration.ExpandConfig(isExpanded = false)
        )
      )
    }

    `when`(repository.getRecents(recents)).thenReturn(cursor)
    `when`(repository.queryNonGroupContacts(isNull(), anyBoolean())).thenReturn(cursor)
    `when`(cursor.count).thenReturn(10)

    return ContactSearchPagedDataSource(configuration, repository)
  }
}
