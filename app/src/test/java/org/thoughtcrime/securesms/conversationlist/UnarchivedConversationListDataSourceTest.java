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
import org.thoughtcrime.securesms.conversationlist.model.ConversationReader;
import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
  private MockedStatic<ApplicationDependencies> applicationDependenciesMockedStatic;

  @Mock
  private MockedStatic<SignalDatabase> signalDatabaseMockedStatic;

  private ConversationListDataSource.UnarchivedConversationListDataSource testSubject;

  private ThreadDatabase threadDatabase;

  @Before
  public void setUp() {
    threadDatabase = mock(ThreadDatabase.class);

    when(SignalDatabase.threads()).thenReturn(threadDatabase);
    when(ApplicationDependencies.getDatabaseObserver()).thenReturn(mock(DatabaseObserver.class));

    testSubject = new ConversationListDataSource.UnarchivedConversationListDataSource(mock(Application.class));
  }

  @Test
  public void givenNoConversations_whenIGetTotalCount_thenIExpectZero() {
    // WHEN
    int result = testSubject.getTotalCount();

    // THEN
    assertEquals(0, result);
    assertEquals(0, testSubject.getHeaderOffset());
    assertFalse(testSubject.hasPinnedHeader());
    assertFalse(testSubject.hasUnpinnedHeader());
    assertFalse(testSubject.hasArchivedFooter());
  }

  @Test
  public void givenArchivedConversations_whenIGetTotalCount_thenIExpectOne() {
    // GIVEN
    when(threadDatabase.getArchivedConversationListCount()).thenReturn(12);

    // WHEN
    int result = testSubject.getTotalCount();

    // THEN
    assertEquals(1, result);
    assertEquals(0, testSubject.getHeaderOffset());
    assertFalse(testSubject.hasPinnedHeader());
    assertFalse(testSubject.hasUnpinnedHeader());
    assertTrue(testSubject.hasArchivedFooter());
  }

  @Test
  public void givenSinglePinnedAndArchivedConversations_whenIGetTotalCount_thenIExpectThree() {
    // GIVEN
    when(threadDatabase.getPinnedConversationListCount()).thenReturn(1);
    when(threadDatabase.getUnarchivedConversationListCount()).thenReturn(1);
    when(threadDatabase.getArchivedConversationListCount()).thenReturn(12);

    // WHEN
    int result = testSubject.getTotalCount();

    // THEN
    assertEquals(3, result);
    assertEquals(1, testSubject.getHeaderOffset());
    assertTrue(testSubject.hasPinnedHeader());
    assertFalse(testSubject.hasUnpinnedHeader());
    assertTrue(testSubject.hasArchivedFooter());
  }

  @Test
  public void givenSingleUnpinnedAndArchivedConversations_whenIGetTotalCount_thenIExpectTwo() {
    // GIVEN
    when(threadDatabase.getUnarchivedConversationListCount()).thenReturn(1);
    when(threadDatabase.getArchivedConversationListCount()).thenReturn(12);

    // WHEN
    int result = testSubject.getTotalCount();

    // THEN
    assertEquals(2, result);
    assertEquals(0, testSubject.getHeaderOffset());
    assertFalse(testSubject.hasPinnedHeader());
    assertFalse(testSubject.hasUnpinnedHeader());
    assertTrue(testSubject.hasArchivedFooter());
  }

  @Test
  public void givenSinglePinnedAndSingleUnpinned_whenIGetTotalCount_thenIExpectFour() {
    // GIVEN
    when(threadDatabase.getPinnedConversationListCount()).thenReturn(1);
    when(threadDatabase.getUnarchivedConversationListCount()).thenReturn(2);

    // WHEN
    int result = testSubject.getTotalCount();

    // THEN
    assertEquals(4, result);
    assertEquals(2, testSubject.getHeaderOffset());
    assertTrue(testSubject.hasPinnedHeader());
    assertTrue(testSubject.hasUnpinnedHeader());
    assertFalse(testSubject.hasArchivedFooter());
  }

  @Test
  public void givenNoConversations_whenIGetCursor_thenIExpectAnEmptyCursor() {
    // GIVEN
    setupThreadDatabaseCursors(0, 0);

    // WHEN
    Cursor cursor = testSubject.getCursor(0, 100);

    // THEN
    verify(threadDatabase).getUnarchivedConversationList(true, 0, 100);
    verify(threadDatabase).getUnarchivedConversationList(false, 0, 100);
    assertEquals(0, cursor.getCount());
  }

  @Test
  public void givenArchivedConversations_whenIGetCursor_thenIExpectOne() {
    // GIVEN
    setupThreadDatabaseCursors(0, 0);
    when(threadDatabase.getArchivedConversationListCount()).thenReturn(12);
    testSubject.getTotalCount();

    // WHEN
    Cursor cursor = testSubject.getCursor(0, 100);

    // THEN
    verify(threadDatabase).getUnarchivedConversationList(true, 0, 100);
    verify(threadDatabase).getUnarchivedConversationList(false, 0, 100);
    assertEquals(1, cursor.getCount());
  }

  @Test
  public void givenSinglePinnedAndArchivedConversations_whenIGetCursor_thenIExpectThree() {
    // GIVEN
    setupThreadDatabaseCursors(1, 0);
    when(threadDatabase.getPinnedConversationListCount()).thenReturn(1);
    when(threadDatabase.getUnarchivedConversationListCount()).thenReturn(1);
    when(threadDatabase.getArchivedConversationListCount()).thenReturn(12);
    testSubject.getTotalCount();

    // WHEN
    Cursor cursor = testSubject.getCursor(0, 100);

    // THEN
    verify(threadDatabase).getUnarchivedConversationList(true, 0, 99);
    verify(threadDatabase).getUnarchivedConversationList(false, 0, 98);
    assertEquals(3, cursor.getCount());
  }

  @Test
  public void givenSingleUnpinnedAndArchivedConversations_whenIGetCursor_thenIExpectTwo() {
    // GIVEN
    setupThreadDatabaseCursors(0, 1);
    when(threadDatabase.getUnarchivedConversationListCount()).thenReturn(1);
    when(threadDatabase.getArchivedConversationListCount()).thenReturn(12);
    testSubject.getTotalCount();

    // WHEN
    Cursor cursor = testSubject.getCursor(0, 100);

    // THEN
    verify(threadDatabase).getUnarchivedConversationList(true, 0, 100);
    verify(threadDatabase).getUnarchivedConversationList(false, 0, 100);
    assertEquals(2, cursor.getCount());
  }

  @Test
  public void givenSinglePinnedAndSingleUnpinned_whenIGetCursor_thenIExpectFour() {
    // GIVEN
    setupThreadDatabaseCursors(1, 1);
    when(threadDatabase.getPinnedConversationListCount()).thenReturn(1);
    when(threadDatabase.getUnarchivedConversationListCount()).thenReturn(2);
    testSubject.getTotalCount();

    // WHEN
    Cursor cursor = testSubject.getCursor(0, 100);

    // THEN
    verify(threadDatabase).getUnarchivedConversationList(true, 0, 99);
    verify(threadDatabase).getUnarchivedConversationList(false, 0, 97);
    assertEquals(4, cursor.getCount());
  }

  @Test
  public void givenLoadingSecondPage_whenIGetCursor_thenIExpectProperOffsetAndCursorCount() {
    // GIVEN
    setupThreadDatabaseCursors(0, 100);
    when(threadDatabase.getPinnedConversationListCount()).thenReturn(4);
    when(threadDatabase.getUnarchivedConversationListCount()).thenReturn(104);
    testSubject.getTotalCount();

    // WHEN
    Cursor cursor = testSubject.getCursor(50, 100);

    // THEN
    verify(threadDatabase).getUnarchivedConversationList(true, 50, 100);
    verify(threadDatabase).getUnarchivedConversationList(false, 44, 100);
    assertEquals(100, cursor.getCount());
  }

  @Test
  public void givenHasArchivedAndLoadingLastPage_whenIGetCursor_thenIExpectProperOffsetAndCursorCount() {
    // GIVEN
    setupThreadDatabaseCursors(0, 99);
    when(threadDatabase.getPinnedConversationListCount()).thenReturn(4);
    when(threadDatabase.getUnarchivedConversationListCount()).thenReturn(103);
    when(threadDatabase.getArchivedConversationListCount()).thenReturn(12);
    testSubject.getTotalCount();

    // WHEN
    Cursor cursor = testSubject.getCursor(50, 100);

    // THEN
    verify(threadDatabase).getUnarchivedConversationList(true, 50, 100);
    verify(threadDatabase).getUnarchivedConversationList(false, 44, 100);
    assertEquals(100, cursor.getCount());

    cursor.moveToLast();
    assertEquals(0, cursor.getColumnIndex(ConversationReader.HEADER_COLUMN[0]));
  }


  private void setupThreadDatabaseCursors(int pinned, int unpinned) {
    Cursor pinnedCursor = mock(Cursor.class);
    when(pinnedCursor.getCount()).thenReturn(pinned);

    Cursor unpinnedCursor = mock(Cursor.class);
    when(unpinnedCursor.getCount()).thenReturn(unpinned);

    when(threadDatabase.getUnarchivedConversationList(eq(true), anyLong(), anyLong())).thenReturn(pinnedCursor);
    when(threadDatabase.getUnarchivedConversationList(eq(false), anyLong(), anyLong())).thenReturn(unpinnedCursor);
  }
}