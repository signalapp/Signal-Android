package org.thoughtcrime.securesms.contacts.paged

import android.app.Application
import androidx.core.os.bundleOf
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.MockCursor
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ContactSearchPagedDataSourceTest {

  private val repository: ContactSearchPagedDataSourceRepository = mock()
  private val cursor: MockCursor = mock()
  private val groupStoryData = ContactSearchData.Story(Recipient.UNKNOWN, 0, DistributionListPrivacyMode.ALL)

  @Before
  fun setUp() {
    whenever(repository.getRecipientFromGroupRecord(any())).thenReturn(Recipient.UNKNOWN)
    whenever(repository.getRecipientFromSearchCursor(any())).thenReturn(Recipient.UNKNOWN)
    whenever(repository.getRecipientFromThreadCursor(cursor)).thenReturn(Recipient.UNKNOWN)
    whenever(repository.getRecipientFromDistributionListCursor(cursor)).thenReturn(Recipient.UNKNOWN)
    whenever(repository.getPrivacyModeFromDistributionListCursor(cursor)).thenReturn(DistributionListPrivacyMode.ALL)
    whenever(repository.getGroupStories()).thenReturn(emptySet())
    whenever(repository.getLatestStorySends(any())).thenReturn(emptyList())
    whenever(cursor.getString(any())).thenReturn("A")
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
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
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
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.Header(ContactSearchConfiguration.SectionKey.INDIVIDUALS),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, false),
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
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, true),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, true),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, true),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, true),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, true),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, true),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, true),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, true),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, true),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, true),
      ContactSearchKey.RecipientSearchKey(RecipientId.UNKNOWN, true)
    )

    val resultKeys = result.map { it.contactSearchKey }

    Assert.assertEquals(expected, resultKeys)
  }

  @Test
  fun `Given only arbitrary elements, when I size, then I expect 3`() {
    val testSubject = createArbitrarySubject()
    val expected = 3
    val actual = testSubject.size()

    Assert.assertEquals(expected, actual)
  }

  @Test
  fun `Given only arbitrary elements, when I load 1, then I expect 1`() {
    val testSubject = createArbitrarySubject()
    val expected = ContactSearchData.Arbitrary("two", bundleOf("n" to "two"))
    val actual = testSubject.load(1, 1) { false }[0] as ContactSearchData.Arbitrary

    Assert.assertEquals(expected.data?.getString("n"), actual.data?.getString("n"))
  }

  private fun createArbitrarySubject(): ContactSearchPagedDataSource {
    val configuration = ContactSearchConfiguration.build {
      arbitrary(
        "one",
        "two",
        "three"
      )

      withEmptyState {
        arbitrary(
          "one",
          "two",
          "three"
        )
      }
    }

    return ContactSearchPagedDataSource(configuration, repository, ArbitraryRepoFake())
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

  private class ArbitraryModel : MappingModel<ArbitraryModel> {
    override fun areItemsTheSame(newItem: ArbitraryModel): Boolean = true

    override fun areContentsTheSame(newItem: ArbitraryModel): Boolean = true
  }

  private class ArbitraryRepoFake : ArbitraryRepository {
    override fun getSize(section: ContactSearchConfiguration.Section.Arbitrary, query: String?): Int = section.types.size

    override fun getData(section: ContactSearchConfiguration.Section.Arbitrary, query: String?, startIndex: Int, endIndex: Int, totalSearchSize: Int): List<ContactSearchData.Arbitrary> {
      return section.types.toList().slice(startIndex..endIndex).map {
        ContactSearchData.Arbitrary(it, bundleOf("n" to it))
      }
    }

    override fun getMappingModel(arbitrary: ContactSearchData.Arbitrary): MappingModel<*> {
      return ArbitraryModel()
    }
  }
}
