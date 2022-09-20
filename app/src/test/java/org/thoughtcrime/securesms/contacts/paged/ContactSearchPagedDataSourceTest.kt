package org.thoughtcrime.securesms.contacts.paged

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.thoughtcrime.securesms.MockCursor
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

@RunWith(JUnit4::class)
class ContactSearchPagedDataSourceTest {

  private val repository: ContactSearchPagedDataSourceRepository = mock()
  private val cursor: MockCursor = mock()
  private val groupStoryData = ContactSearchData.Story(Recipient.UNKNOWN, 0, DistributionListPrivacyMode.ALL)

  @Before
  fun setUp() {
    whenever(repository.getRecipientFromGroupRecord(any())).thenReturn(Recipient.UNKNOWN)
    whenever(repository.getRecipientFromRecipientCursor(cursor)).thenReturn(Recipient.UNKNOWN)
    whenever(repository.getRecipientFromThreadCursor(cursor)).thenReturn(Recipient.UNKNOWN)
    whenever(repository.getRecipientFromDistributionListCursor(cursor)).thenReturn(Recipient.UNKNOWN)
    whenever(repository.getPrivacyModeFromDistributionListCursor(cursor)).thenReturn(DistributionListPrivacyMode.ALL)
    whenever(repository.getGroupStories()).thenReturn(emptySet())
    whenever(repository.getLatestStorySends(any())).thenReturn(emptyList())
    whenever(cursor.moveToPosition(any())).thenCallRealMethod()
    whenever(cursor.moveToNext()).thenCallRealMethod()
    whenever(cursor.position).thenCallRealMethod()
    whenever(cursor.isLast).thenCallRealMethod()
    whenever(cursor.isAfterLast).thenCallRealMethod()
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
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
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
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.Header(ContactSearchConfiguration.SectionKey.INDIVIDUALS),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.KnownRecipient(RecipientId.UNKNOWN),
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
      ContactSearchKey.RecipientSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.Story(RecipientId.UNKNOWN),
      ContactSearchKey.RecipientSearchKey.Story(RecipientId.UNKNOWN),
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

    whenever(repository.getStories(anyOrNull())).thenReturn(cursor)
    whenever(repository.recipientNameContainsQuery(Recipient.UNKNOWN, null)).thenReturn(true)
    whenever(cursor.count).thenReturn(10)

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

    whenever(repository.getRecents(recents)).thenReturn(cursor)
    whenever(repository.queryNonGroupContacts(isNull(), any())).thenReturn(cursor)
    whenever(cursor.count).thenReturn(10)

    return ContactSearchPagedDataSource(configuration, repository)
  }
}
