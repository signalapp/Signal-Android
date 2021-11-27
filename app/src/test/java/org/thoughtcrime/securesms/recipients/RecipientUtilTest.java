package org.thoughtcrime.securesms.recipients;

import android.content.Context;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.util.FeatureFlags;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({SignalDatabase.class, FeatureFlags.class})
public class RecipientUtilTest {

  private Context           context               = mock(Context.class);
  private Recipient         recipient             = mock(Recipient.class);
  private ThreadDatabase    mockThreadDatabase    = mock(ThreadDatabase.class);
  private MmsSmsDatabase    mockMmsSmsDatabase    = mock(MmsSmsDatabase.class);
  private RecipientDatabase mockRecipientDatabase = mock(RecipientDatabase.class);

  @Before
  public void setUp() {
    mockStatic(SignalDatabase.class);
    when(SignalDatabase.threads()).thenReturn(mockThreadDatabase);
    when(SignalDatabase.mmsSms()).thenReturn(mockMmsSmsDatabase);
    when(SignalDatabase.recipients()).thenReturn(mockRecipientDatabase);
    mockStatic(FeatureFlags.class);

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
    when(mockThreadDatabase.getRecipientForThreadId(anyLong())).thenReturn(recipient);
    when(mockMmsSmsDatabase.getOutgoingSecureConversationCount(1L)).thenReturn(5);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, 1L);

    // THEN
    assertTrue(result);
  }

  @Test
  public void givenIHaveNotSentASecureMessageInThisThreadAndIAmProfileSharing_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    when(recipient.isProfileSharing()).thenReturn(true);
    when(mockThreadDatabase.getRecipientForThreadId(anyLong())).thenReturn(recipient);
    when(mockMmsSmsDatabase.getOutgoingSecureConversationCount(1L)).thenReturn(0);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, 1L);

    // THEN
    assertTrue(result);
  }

  @Test
  public void givenIHaveNotSentASecureMessageInThisThreadAndRecipientIsSystemContact_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    when(recipient.isSystemContact()).thenReturn(true);
    when(mockThreadDatabase.getRecipientForThreadId(anyLong())).thenReturn(recipient);
    when(mockMmsSmsDatabase.getOutgoingSecureConversationCount(1L)).thenReturn(0);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, 1L);

    // THEN
    assertTrue(result);
  }

  @Ignore
  @Test
  public void givenIHaveReceivedASecureMessageIHaveNotSentASecureMessageAndRecipientIsNotSystemContactAndNotProfileSharing_whenIsThreadMessageRequestAccepted_thenIExpectFalse() {
    // GIVEN
    when(mockThreadDatabase.getRecipientForThreadId(anyLong())).thenReturn(recipient);
    when(mockMmsSmsDatabase.getOutgoingSecureConversationCount(1L)).thenReturn(0);
    when(mockMmsSmsDatabase.getSecureConversationCount(1L)).thenReturn(5);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, 1L);

    // THEN
    assertFalse(result);
  }

  @Test
  public void givenIHaveNotReceivedASecureMessageIHaveNotSentASecureMessageAndRecipientIsNotSystemContactAndNotProfileSharing_whenIsThreadMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    when(mockThreadDatabase.getRecipientForThreadId(anyLong())).thenReturn(recipient);
    when(mockMmsSmsDatabase.getOutgoingSecureConversationCount(1L)).thenReturn(0);
    when(mockMmsSmsDatabase.getSecureConversationCount(1L)).thenReturn(0);

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
    when(mockMmsSmsDatabase.getOutgoingSecureConversationCount(anyLong())).thenReturn(1);

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
    when(mockMmsSmsDatabase.getSecureConversationCount(anyLong())).thenReturn(5);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, recipient);

    // THEN
    assertFalse(result);
  }

  @Test
  public void givenNoSecureMessagesSentNoSecureMessagesReceivedNotSharingAndNotSystemContact_whenIsRecipientMessageRequestAccepted_thenIExpectTrue() {
    // GIVEN
    when(mockMmsSmsDatabase.getSecureConversationCount(anyLong())).thenReturn(0);

    // WHEN
    boolean result = RecipientUtil.isMessageRequestAccepted(context, recipient);

    // THEN
    assertTrue(result);
  }

  @Ignore
  @Test
  public void givenNoSecureMessagesSent_whenIShareProfileIfFirstSecureMessage_thenIShareProfile() {
    // GIVEN
    when(mockMmsSmsDatabase.getOutgoingSecureConversationCount(anyLong())).thenReturn(0);

    // WHEN
    RecipientUtil.shareProfileIfFirstSecureMessage(context, recipient);

    // THEN
    verify(mockRecipientDatabase).setProfileSharing(recipient.getId(), true);
  }

  @Ignore
  @Test
  public void givenSecureMessagesSent_whenIShareProfileIfFirstSecureMessage_thenIShareProfile() {
    // GIVEN
    when(mockMmsSmsDatabase.getOutgoingSecureConversationCount(anyLong())).thenReturn(5);

    // WHEN
    RecipientUtil.shareProfileIfFirstSecureMessage(context, recipient);

    // THEN
    verify(mockRecipientDatabase, never()).setProfileSharing(recipient.getId(), true);
  }
}