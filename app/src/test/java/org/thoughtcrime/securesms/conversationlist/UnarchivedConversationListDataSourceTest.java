package org.thoughtcrime.securesms.conversationlist;

import android.app.Application;
import android.database.Cursor;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord;
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter;
import org.thoughtcrime.securesms.conversationlist.model.ConversationReader;
import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.util.RemoteConfig;

import java.util.ArrayList;
import java.util.HashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public class UnarchivedConversationListDataSourceTest {

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Mock
  private MockedStatic<AppDependencies> applicationDependenciesMockedStatic;

  @Mock
  private MockedStatic<SignalDatabase> signalDatabaseMockedStatic;

  @Mock
  private MockedStatic<RemoteConfig> remoteConfigMockedStatic;

  private ConversationListDataSource.UnarchivedConversationListDataSource testSubject;

  private ChatFolderRecord allChatsFolder;

  private ThreadTable threadTable;

  @Before
  public void setUp() {
    threadTable = mock(ThreadTable.class);

    when(SignalDatabase.threads()).thenReturn(threadTable);
    when(AppDependencies.getDatabaseObserver()).thenReturn(mock(DatabaseObserver.class));
    when(RemoteConfig.getInlinePinnedChats()).thenReturn(true);

    allChatsFolder = setupAllChatsFolder();
    testSubject = new ConversationListDataSource.UnarchivedConversationListDataSource(allChatsFolder, ConversationFilter.OFF, false);
  }


  @Test
  public void givenNoConversations_whenIGetTotalCount_thenIExpectZero() {
    // WHEN
    int result = testSubject.getTotalCount();

    // THEN
    assertEquals(0, result);
    assertFalse(testSubject.hasConversationFilterFooter());
    assertFalse(testSubject.hasArchivedFooter());
  }

  @Test
  public void givenArchivedConversations_whenIGetTotalCount_thenIExpectOne() {
    // GIVEN
    when(threadTable.getArchivedConversationListCount(ConversationFilter.OFF)).thenReturn(12);

    // WHEN
    int result = testSubject.getTotalCount();

    // THEN
    assertEquals(1, result);
    assertFalse(testSubject.hasConversationFilterFooter());
    assertTrue(testSubject.hasArchivedFooter());
  }

  @Test
  public void givenSinglePinnedAndArchivedConversations_whenIGetTotalCount_thenIExpectTwo() {
    // GIVEN
    when(threadTable.getPinnedConversationListCount(ConversationFilter.OFF, allChatsFolder)).thenReturn(1);
    when(threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder)).thenReturn(1);
    when(threadTable.getArchivedConversationListCount(ConversationFilter.OFF)).thenReturn(12);

    // WHEN
    int result = testSubject.getTotalCount();

    // THEN
    assertEquals(2, result);
    assertFalse(testSubject.hasConversationFilterFooter());
    assertTrue(testSubject.hasArchivedFooter());
  }

  @Test
  public void givenSingleUnpinnedAndArchivedConversations_whenIGetTotalCount_thenIExpectTwo() {
    // GIVEN
    when(threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder)).thenReturn(1);
    when(threadTable.getArchivedConversationListCount(ConversationFilter.OFF)).thenReturn(12);

    // WHEN
    int result = testSubject.getTotalCount();

    // THEN
    assertEquals(2, result);
    assertFalse(testSubject.hasConversationFilterFooter());
    assertTrue(testSubject.hasArchivedFooter());
  }

  @Test
  public void givenSinglePinnedAndSingleUnpinned_whenIGetTotalCount_thenIExpectTwo() {
    // GIVEN
    when(threadTable.getPinnedConversationListCount(ConversationFilter.OFF, allChatsFolder)).thenReturn(1);
    when(threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder)).thenReturn(2);

    // WHEN
    int result = testSubject.getTotalCount();

    // THEN
    assertEquals(2, result);
    assertFalse(testSubject.hasConversationFilterFooter());
    assertFalse(testSubject.hasArchivedFooter());
  }

  @Test
  public void givenNoConversations_whenIGetCursor_thenIExpectAnEmptyCursor() {
    // GIVEN
    setupThreadDatabaseCursors(0, 0);

    // WHEN
    Cursor cursor = testSubject.getCursor(0, 100);

    // THEN
    verify(threadTable).getUnarchivedConversationList(ConversationFilter.OFF, true, 0, 100, allChatsFolder);
    verify(threadTable).getUnarchivedConversationList(ConversationFilter.OFF, false, 0, 100, allChatsFolder);
    assertEquals(0, cursor.getCount());
  }

  @Test
  public void givenArchivedConversations_whenIGetCursor_thenIExpectOne() {
    // GIVEN
    setupThreadDatabaseCursors(0, 0);
    when(threadTable.getArchivedConversationListCount(ConversationFilter.OFF)).thenReturn(12);
    testSubject.getTotalCount();

    // WHEN
    Cursor cursor = testSubject.getCursor(0, 100);

    // THEN
    verify(threadTable).getUnarchivedConversationList(ConversationFilter.OFF, true, 0, 100, allChatsFolder);
    verify(threadTable).getUnarchivedConversationList(ConversationFilter.OFF, false, 0, 100, allChatsFolder);
    assertEquals(1, cursor.getCount());
  }

  @Test
  public void givenSinglePinnedAndArchivedConversations_whenIGetCursor_thenIExpectTwo() {
    // GIVEN
    setupThreadDatabaseCursors(1, 0);
    when(threadTable.getPinnedConversationListCount(ConversationFilter.OFF, allChatsFolder)).thenReturn(1);
    when(threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder)).thenReturn(1);
    when(threadTable.getArchivedConversationListCount(ConversationFilter.OFF)).thenReturn(12);
    testSubject.getTotalCount();

    // WHEN
    Cursor cursor = testSubject.getCursor(0, 100);

    // THEN
    verify(threadTable).getUnarchivedConversationList(ConversationFilter.OFF, true, 0, 100, allChatsFolder);
    verify(threadTable).getUnarchivedConversationList(ConversationFilter.OFF, false, 0, 99, allChatsFolder);
    assertEquals(2, cursor.getCount());
  }

  @Test
  public void givenSingleUnpinnedAndArchivedConversations_whenIGetCursor_thenIExpectTwo() {
    // GIVEN
    setupThreadDatabaseCursors(0, 1);
    when(threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder)).thenReturn(1);
    when(threadTable.getArchivedConversationListCount(ConversationFilter.OFF)).thenReturn(12);
    testSubject.getTotalCount();

    // WHEN
    Cursor cursor = testSubject.getCursor(0, 100);

    // THEN
    verify(threadTable).getUnarchivedConversationList(ConversationFilter.OFF, true, 0, 100, allChatsFolder);
    verify(threadTable).getUnarchivedConversationList(ConversationFilter.OFF, false, 0, 100, allChatsFolder);
    assertEquals(2, cursor.getCount());
  }

  @Test
  public void givenSinglePinnedAndSingleUnpinned_whenIGetCursor_thenIExpectTwo() {
    // GIVEN
    setupThreadDatabaseCursors(1, 1);
    when(threadTable.getPinnedConversationListCount(ConversationFilter.OFF, allChatsFolder)).thenReturn(1);
    when(threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder)).thenReturn(2);
    testSubject.getTotalCount();

    // WHEN
    Cursor cursor = testSubject.getCursor(0, 100);

    // THEN
    verify(threadTable).getUnarchivedConversationList(ConversationFilter.OFF, true, 0, 100, allChatsFolder);
    verify(threadTable).getUnarchivedConversationList(ConversationFilter.OFF, false, 0, 99, allChatsFolder);
    assertEquals(2, cursor.getCount());
  }

  @Test
  public void givenLoadingSecondPage_whenIGetCursor_thenIExpectProperOffsetAndCursorCount() {
    // GIVEN
    setupThreadDatabaseCursors(0, 100);
    when(threadTable.getPinnedConversationListCount(ConversationFilter.OFF, allChatsFolder)).thenReturn(4);
    when(threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder)).thenReturn(104);
    testSubject.getTotalCount();

    // WHEN
    Cursor cursor = testSubject.getCursor(50, 100);

    // THEN
    verify(threadTable).getUnarchivedConversationList(ConversationFilter.OFF, true, 50, 100, allChatsFolder);
    verify(threadTable).getUnarchivedConversationList(ConversationFilter.OFF, false, 46, 100, allChatsFolder);
    assertEquals(100, cursor.getCount());
  }

  @Test
  public void givenHasArchivedAndLoadingLastPage_whenIGetCursor_thenIExpectProperOffsetAndCursorCount() {
    // GIVEN
    setupThreadDatabaseCursors(0, 99);
    when(threadTable.getPinnedConversationListCount(ConversationFilter.OFF, allChatsFolder)).thenReturn(4);
    when(threadTable.getUnarchivedConversationListCount(ConversationFilter.OFF, allChatsFolder)).thenReturn(103);
    when(threadTable.getArchivedConversationListCount(ConversationFilter.OFF)).thenReturn(12);
    testSubject.getTotalCount();

    // WHEN
    Cursor cursor = testSubject.getCursor(50, 100);

    // THEN
    verify(threadTable).getUnarchivedConversationList(ConversationFilter.OFF, true, 50, 100, allChatsFolder);
    verify(threadTable).getUnarchivedConversationList(ConversationFilter.OFF, false, 46, 100, allChatsFolder);
    assertEquals(100, cursor.getCount());

    cursor.moveToLast();
    assertEquals(0, cursor.getColumnIndex(ConversationReader.HEADER_COLUMN[0]));
  }

  @Test
  public void givenHasNoArchivedAndIsFiltered_whenIGetCursor_thenIExpectConversationFilterFooter() {
    // GIVEN
    ConversationListDataSource.UnarchivedConversationListDataSource testSubject = new ConversationListDataSource.UnarchivedConversationListDataSource(allChatsFolder, ConversationFilter.UNREAD, false);
    setupThreadDatabaseCursors(0, 3);
    when(threadTable.getPinnedConversationListCount(ConversationFilter.UNREAD, allChatsFolder)).thenReturn(0);
    when(threadTable.getUnarchivedConversationListCount(ConversationFilter.UNREAD, allChatsFolder)).thenReturn(3);
    when(threadTable.getArchivedConversationListCount(ConversationFilter.UNREAD)).thenReturn(0);
    testSubject.getTotalCount();

    // WHEN
    Cursor cursor = testSubject.getCursor(0, 5);

    //  THEN
    assertEquals(4, cursor.getCount());
    assertTrue(testSubject.hasConversationFilterFooter());

    cursor.moveToLast();
    assertEquals(0, cursor.getColumnIndex(ConversationReader.HEADER_COLUMN[0]));
  }


  private void setupThreadDatabaseCursors(int pinned, int unpinned) {
    Cursor pinnedCursor = mock(Cursor.class);
    when(pinnedCursor.getCount()).thenReturn(pinned);

    Cursor unpinnedCursor = mock(Cursor.class);
    when(unpinnedCursor.getCount()).thenReturn(unpinned);

    when(threadTable.getUnarchivedConversationList(any(), eq(true), anyLong(), anyLong(), any())).thenReturn(pinnedCursor);
    when(threadTable.getUnarchivedConversationList(any(), eq(false), anyLong(), anyLong(), any())).thenReturn(unpinnedCursor);
  }

  private ChatFolderRecord setupAllChatsFolder() {
    return new ChatFolderRecord(
        1,
        "",
        -1,
        new ArrayList<>(),
        new ArrayList<>(),
        new HashSet<>(),
        new HashSet<>(),
        false,
        false,
        false,
        false,
        false,
        ChatFolderRecord.FolderType.ALL,
        0
    );
  }
}