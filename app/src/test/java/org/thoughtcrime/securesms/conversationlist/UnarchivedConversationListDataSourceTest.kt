package org.thoughtcrime.securesms.conversationlist

import android.app.Application
import android.database.Cursor
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.conversationlist.ConversationListDataSource.UnarchivedConversationListDataSource
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.conversationlist.model.ConversationReader
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.util.RemoteConfig

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class UnarchivedConversationListDataSourceTest {
  private lateinit var testSubject: UnarchivedConversationListDataSource
  private lateinit var allChatsFolder: ChatFolderRecord
  private lateinit var threadTable: ThreadTable

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @Before
  fun setUp() {
    threadTable = mockk<ThreadTable>(relaxed = true)

    mockkStatic(RemoteConfig::class)
    every { RemoteConfig.init() } just runs
    every { RemoteConfig.inlinePinnedChats } returns true

    mockkObject(SignalDatabase)
    every { SignalDatabase.threads } returns threadTable

    allChatsFolder = setupAllChatsFolder()
    testSubject = UnarchivedConversationListDataSource(allChatsFolder, ConversationFilter.OFF, false)
  }

  @After
  fun cleanup() {
    unmockkAll()
  }

  @Test
  fun givenNoConversations_whenIGetTotalCount_thenIExpectZero() {
    // WHEN
    val result = testSubject.totalCount

    // THEN
    assertEquals(0, result)
    assertFalse(testSubject.hasConversationFilterFooter())
    assertFalse(testSubject.hasArchivedFooter())
  }

  @Test
  fun givenArchivedConversations_whenIGetTotalCount_thenIExpectOne() {
    // GIVEN
    every { threadTable.getArchivedConversationListCount(ConversationFilter.OFF) } returns 12

    // WHEN
    val result = testSubject.totalCount

    // THEN
    assertEquals(1, result)
    assertFalse(testSubject.hasConversationFilterFooter())
    assertTrue(testSubject.hasArchivedFooter())
  }

  @Test
  fun givenSinglePinnedAndArchivedConversations_whenIGetTotalCount_thenIExpectTwo() {
    // GIVEN
    every { threadTable.getPinnedConversationListCount(ConversationFilter.OFF, allChatsFolder) } returns 1
    every { threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder) } returns 1
    every { threadTable.getArchivedConversationListCount(ConversationFilter.OFF) } returns 12

    // WHEN
    val result = testSubject.totalCount

    // THEN
    assertEquals(2, result)
    assertFalse(testSubject.hasConversationFilterFooter())
    assertTrue(testSubject.hasArchivedFooter())
  }

  @Test
  fun givenSingleUnpinnedAndArchivedConversations_whenIGetTotalCount_thenIExpectTwo() {
    // GIVEN
    every { threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder) } returns 1
    every { threadTable.getArchivedConversationListCount(ConversationFilter.OFF) } returns 12

    // WHEN
    val result = testSubject.totalCount

    // THEN
    assertEquals(2, result)
    assertFalse(testSubject.hasConversationFilterFooter())
    assertTrue(testSubject.hasArchivedFooter())
  }

  @Test
  fun givenSinglePinnedAndSingleUnpinned_whenIGetTotalCount_thenIExpectTwo() {
    // GIVEN
    every { threadTable.getPinnedConversationListCount(ConversationFilter.OFF, allChatsFolder) } returns 1
    every { threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder) } returns 2

    // WHEN
    val result = testSubject.totalCount

    // THEN
    assertEquals(2, result)
    assertFalse(testSubject.hasConversationFilterFooter())
    assertFalse(testSubject.hasArchivedFooter())
  }

  @Test
  fun givenNoConversations_whenIGetCursor_thenIExpectAnEmptyCursor() {
    // GIVEN
    setupThreadDatabaseCursors(0, 0)

    // WHEN
    val cursor = testSubject.getCursor(0, 100)

    // THEN
    verify { threadTable.getUnarchivedConversationList(ConversationFilter.OFF, true, 0, 100, allChatsFolder) }
    verify { threadTable.getUnarchivedConversationList(ConversationFilter.OFF, false, 0, 100, allChatsFolder) }
    assertEquals(0, cursor.count)
  }

  @Test
  fun givenArchivedConversations_whenIGetCursor_thenIExpectOne() {
    // GIVEN
    setupThreadDatabaseCursors(0, 0)
    every { threadTable.getArchivedConversationListCount(ConversationFilter.OFF) } returns 12
    testSubject.totalCount

    // WHEN
    val cursor = testSubject.getCursor(0, 100)

    // THEN
    verify { threadTable.getUnarchivedConversationList(ConversationFilter.OFF, true, 0, 100, allChatsFolder) }
    verify { threadTable.getUnarchivedConversationList(ConversationFilter.OFF, false, 0, 100, allChatsFolder) }
    assertEquals(1, cursor.count)
  }

  @Test
  fun givenSinglePinnedAndArchivedConversations_whenIGetCursor_thenIExpectTwo() {
    // GIVEN
    setupThreadDatabaseCursors(1, 0)
    every { threadTable.getPinnedConversationListCount(ConversationFilter.OFF, allChatsFolder) } returns 1
    every { threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder) } returns 1
    every { threadTable.getArchivedConversationListCount(ConversationFilter.OFF) } returns 12
    testSubject.totalCount

    // WHEN
    val cursor = testSubject.getCursor(0, 100)

    // THEN
    verify { threadTable.getUnarchivedConversationList(ConversationFilter.OFF, true, 0, 100, allChatsFolder) }
    verify { threadTable.getUnarchivedConversationList(ConversationFilter.OFF, false, 0, 99, allChatsFolder) }
    assertEquals(2, cursor.count)
  }

  @Test
  fun givenSingleUnpinnedAndArchivedConversations_whenIGetCursor_thenIExpectTwo() {
    // GIVEN
    setupThreadDatabaseCursors(0, 1)
    every { threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder) } returns 1
    every { threadTable.getArchivedConversationListCount(ConversationFilter.OFF) } returns 12
    testSubject.totalCount

    // WHEN
    val cursor = testSubject.getCursor(0, 100)

    // THEN
    verify { threadTable.getUnarchivedConversationList(ConversationFilter.OFF, true, 0, 100, allChatsFolder) }
    verify { threadTable.getUnarchivedConversationList(ConversationFilter.OFF, false, 0, 100, allChatsFolder) }
    assertEquals(2, cursor.count)
  }

  @Test
  fun givenSinglePinnedAndSingleUnpinned_whenIGetCursor_thenIExpectTwo() {
    // GIVEN
    setupThreadDatabaseCursors(1, 1)
    every { threadTable.getPinnedConversationListCount(ConversationFilter.OFF, allChatsFolder) } returns 1
    every { threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder) } returns 2
    testSubject.totalCount

    // WHEN
    val cursor = testSubject.getCursor(0, 100)

    // THEN
    verify { threadTable.getUnarchivedConversationList(ConversationFilter.OFF, true, 0, 100, allChatsFolder) }
    verify { threadTable.getUnarchivedConversationList(ConversationFilter.OFF, false, 0, 99, allChatsFolder) }
    assertEquals(2, cursor.count)
  }

  @Test
  fun givenLoadingSecondPage_whenIGetCursor_thenIExpectProperOffsetAndCursorCount() {
    // GIVEN
    setupThreadDatabaseCursors(0, 100)
    every { threadTable.getPinnedConversationListCount(ConversationFilter.OFF, allChatsFolder) } returns 4
    every { threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder) } returns 104
    testSubject.totalCount

    // WHEN
    val cursor = testSubject.getCursor(50, 100)

    // THEN
    verify { threadTable.getUnarchivedConversationList(ConversationFilter.OFF, true, 50, 100, allChatsFolder) }
    verify { threadTable.getUnarchivedConversationList(ConversationFilter.OFF, false, 46, 100, allChatsFolder) }
    assertEquals(100, cursor.count)
  }

  @Test
  fun givenHasArchivedAndLoadingLastPage_whenIGetCursor_thenIExpectProperOffsetAndCursorCount() {
    // GIVEN
    setupThreadDatabaseCursors(0, 99)
    every { threadTable.getPinnedConversationListCount(ConversationFilter.OFF, allChatsFolder) } returns 4
    every { threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder) } returns 103
    every { threadTable.getArchivedConversationListCount(ConversationFilter.OFF) } returns 12
    testSubject.totalCount

    // WHEN
    val cursor = testSubject.getCursor(50, 100)

    // THEN
    verify { threadTable.getUnarchivedConversationList(ConversationFilter.OFF, true, 50, 100, allChatsFolder) }
    verify { threadTable.getUnarchivedConversationList(ConversationFilter.OFF, false, 46, 100, allChatsFolder) }
    assertEquals(100, cursor.count)

    cursor.moveToLast()
    assertEquals(0, cursor.getColumnIndex(ConversationReader.HEADER_COLUMN[0]))
  }

  @Test
  fun givenHasNoArchivedAndIsFiltered_whenIGetCursor_thenIExpectConversationFilterFooter() {
    // GIVEN
    val testSubject = UnarchivedConversationListDataSource(allChatsFolder, ConversationFilter.UNREAD, false)
    setupThreadDatabaseCursors(0, 3)
    every { threadTable.getPinnedConversationListCount(ConversationFilter.UNREAD, allChatsFolder) } returns 0
    every { threadTable.getUnarchivedConversationListCount(ConversationFilter.UNREAD, allChatsFolder) } returns 3
    every { threadTable.getArchivedConversationListCount(ConversationFilter.UNREAD) } returns 0
    testSubject.totalCount

    // WHEN
    val cursor = testSubject.getCursor(0, 5)

    //  THEN
    assertEquals(4, cursor.count)
    assertTrue(testSubject.hasConversationFilterFooter())

    cursor.moveToLast()
    assertEquals(0, cursor.getColumnIndex(ConversationReader.HEADER_COLUMN[0]))
  }

  private fun setupThreadDatabaseCursors(pinned: Int, unpinned: Int) {
    every {
      threadTable.getUnarchivedConversationList(any(), true, any(), any(), any())
    } returns mockk<Cursor>(relaxed = true) {
      every { count } returns pinned
    }
    every {
      threadTable.getUnarchivedConversationList(any(), false, any(), any(), any())
    } returns mockk<Cursor>(relaxed = true) {
      every { count } returns unpinned
    }
  }

  private fun setupAllChatsFolder(): ChatFolderRecord {
    return ChatFolderRecord(
      id = 1,
      name = "",
      position = -1,
      includedChats = emptyList(),
      excludedChats = emptyList(),
      includedRecipients = emptySet(),
      excludedRecipients = emptySet(),
      showUnread = false,
      showMutedChats = false,
      showIndividualChats = false,
      showGroupChats = false,
      isMuted = false,
      folderType = ChatFolderRecord.FolderType.ALL,
      unreadCount = 0
    )
  }
}
