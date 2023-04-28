package org.thoughtcrime.securesms.recipients;

import android.content.Context;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RecipientUtilTest {

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  private Context        context            = mock(Context.class);
  private Recipient      recipient          = mock(Recipient.class);
  private ThreadTable    mockThreadTable    = mock(ThreadTable.class);
  private MessageTable   mockMessageTable   = mock(MessageTable.class);
  private RecipientTable mockRecipientTable = mock(RecipientTable.class);

  @Mock
  private MockedStatic<SignalDatabase> signalDatabaseMockedStatic;

  @Before
  public void setUp() {
    signalDatabaseMockedStatic.when(SignalDatabase::threads).thenReturn(mockThreadTable);
    signalDatabaseMockedStatic.when(SignalDatabase::messages).thenReturn(mockMessageTable);
    signalDatabaseMockedStatic.when(SignalDatabase::recipients).thenReturn(mockRecipientTable);

    when(recipient.getId()).thenReturn(RecipientId.from(5));
    when(recipient.resolve()).thenReturn(recipient);
  }

  @Test
  public void givenThreadIsNegativeOne_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, -1L);

    // THEN
    assertTrue(result);
  }

  @Test
  public void givenRecipientIsNullForThreadId_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, 1L);

    // THEN
    assertTrue(result);
  }

  @Test
  public void givenIHaveSentASecureMessageInThisThread_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    when(mockThreadTable.getRecipientForThreadId(anyLong())).thenReturn(recipient);
    when(mockMessageTable.getOutgoingSecureMessageCount(1L)).thenReturn(5);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, 1L);

    // THEN
    assertTrue(result);
  }

  @Test
  public void givenIHaveNotSentASecureMessageInThisThreadAndIAmProfileSharing_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    when(recipient.isProfileSharing()).thenReturn(true);
    when(mockThreadTable.getRecipientForThreadId(anyLong())).thenReturn(recipient);
    when(mockMessageTable.getOutgoingSecureMessageCount(1L)).thenReturn(0);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, 1L);

    // THEN
    assertTrue(result);
  }

  @Test
  public void givenIHaveNotSentASecureMessageInThisThreadAndRecipientIsSystemContact_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    when(recipient.isSystemContact()).thenReturn(true);
    when(mockThreadTable.getRecipientForThreadId(anyLong())).thenReturn(recipient);
    when(mockMessageTable.getOutgoingSecureMessageCount(1L)).thenReturn(0);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, 1L);

    // THEN
    assertTrue(result);
  }

  @Ignore
  @Test
  public void givenIHaveReceivedASecureMessageIHaveNotSentASecureMessageAndRecipientIsNotSystemContactAndNotProfileSharing_whenIsThreadMessageRequestAccepted_thenIExpectFalse() {
    // GIVEN
    when(mockThreadTable.getRecipientForThreadId(anyLong())).thenReturn(recipient);
    when(mockMessageTable.getOutgoingSecureMessageCount(1L)).thenReturn(0);
    when(mockMessageTable.getSecureMessageCount(1L)).thenReturn(5);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, 1L);

    // THEN
    assertFalse(result);
  }

  @Test
  public void givenIHaveNotReceivedASecureMessageIHaveNotSentASecureMessageAndRecipientIsNotSystemContactAndNotProfileSharing_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    when(mockThreadTable.getRecipientForThreadId(anyLong())).thenReturn(recipient);
    when(mockMessageTable.getOutgoingSecureMessageCount(1L)).thenReturn(0);
    when(mockMessageTable.getSecureMessageCount(1L)).thenReturn(0);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, 1L);

    // THEN
    assertTrue(result);
  }

  @Test
  public void givenRecipientIsNull_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, null);

    // THEN
    assertTrue(result);
  }

  @Test
  public void givenNonZeroOutgoingSecureMessageCount_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    when(mockMessageTable.getOutgoingSecureMessageCount(anyLong())).thenReturn(1);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, recipient);

    // THEN
    assertTrue(result);
  }

  @Test
  public void givenIAmProfileSharing_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    when(recipient.isProfileSharing()).thenReturn(true);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, recipient);

    // THEN
    assertTrue(result);
  }

  @Test
  public void givenRecipientIsASystemContact_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    when(recipient.isSystemContact()).thenReturn(true);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, recipient);

    // THEN
    assertTrue(result);
  }

  @Ignore
  @Test
  public void givenNoSecureMessagesSentSomeSecureMessagesReceivedNotSharingAndNotSystemContact_whenIsRecipientMessageRequestAccepted_thenIExpectFalse() {
    // GIVEN
    when(recipient.isRegistered()).thenReturn(true);
    when(mockMessageTable.getSecureMessageCount(anyLong())).thenReturn(5);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, recipient);

    // THEN
    assertFalse(result);
  }

  @Test
  public void givenNoSecureMessagesSentNoSecureMessagesReceivedNotSharingAndNotSystemContact_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    when(mockMessageTable.getSecureMessageCount(anyLong())).thenReturn(0);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, recipient);

    // THEN
    assertTrue(result);
  }

  @Ignore
  @Test
  public void givenNoSecureMessagesSent_whenIShareProfileIfFirstSecureMessage_thenIShareProfile() {
    // GIVEN
    when(mockMessageTable.getOutgoingSecureMessageCount(anyLong())).thenReturn(0);

    // WHEN
    RecipientUtil.shareProfileIfFirstSecureMessage(recipient);

    // THEN
    verify(mockRecipientTable).setProfileSharing(recipient.getId(), true);
  }

  @Ignore
  @Test
  public void givenSecureMessagesSent_whenIShareProfileIfFirstSecureMessage_thenIShareProfile() {
    // GIVEN
    when(mockMessageTable.getOutgoingSecureMessageCount(anyLong())).thenReturn(5);

    // WHEN
    RecipientUtil.shareProfileIfFirstSecureMessage(recipient);

    // THEN
    verify(mockRecipientTable, never()).setProfileSharing(recipient.getId(), true);
  }
}
