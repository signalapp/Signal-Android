package org.thoughtcrime.securesms.jobmanager.migrations;

import android.app.Application;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.JobMigration.JobData;
import org.thoughtcrime.securesms.jobmanager.migrations.RecipientIdJobMigration.NewSerializableSyncMessageId;
import org.thoughtcrime.securesms.jobmanager.migrations.RecipientIdJobMigration.OldSerializableSyncMessageId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.JsonUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ Recipient.class })
public class RecipientIdJobMigrationTest {

  @Before
  public void init() {
    mockStatic(Recipient.class);
  }

  @Test
  public void migrate_multiDeviceContactUpdateJob() throws Exception {
    JobData testData = new JobData("MultiDeviceContactUpdateJob", "MultiDeviceContactUpdateJob", new Data.Builder().putBoolean("force_sync", false).putString("address", "+16101234567").build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("MultiDeviceContactUpdateJob", converted.getFactoryKey());
    assertEquals("MultiDeviceContactUpdateJob", converted.getQueueKey());
    assertFalse(converted.getData().getBoolean("force_sync"));
    assertFalse(converted.getData().hasString("address"));
    assertEquals("1", converted.getData().getString("recipient"));
  }

  @Test
  public void migrate_multiDeviceViewOnceOpenJob() throws Exception {
    OldSerializableSyncMessageId oldId    = new OldSerializableSyncMessageId("+16101234567", 1);
    JobData                      testData = new JobData("MultiDeviceRevealUpdateJob", null, new Data.Builder().putString("message_id", JsonUtils.toJson(oldId)).build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("MultiDeviceRevealUpdateJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());
    assertEquals(JsonUtils.toJson(new NewSerializableSyncMessageId("1", 1)), converted.getData().getString("message_id"));
  }

  @Test
  public void migrate_requestGroupInfoJob() throws Exception {
    JobData testData = new JobData("RequestGroupInfoJob", null, new Data.Builder().putString("source", "+16101234567").build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("RequestGroupInfoJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());
    assertEquals("1", converted.getData().getString("source"));
  }

  @Test
  public void migrate_sendDeliveryReceiptJob() throws Exception {
    JobData testData = new JobData("SendDeliveryReceiptJob", null, new Data.Builder().putString("address", "+16101234567").build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("SendDeliveryReceiptJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());
    assertEquals("1", converted.getData().getString("recipient"));
  }

  @Test
  public void migrate_multiDeviceVerifiedUpdateJob() throws Exception {
    JobData testData = new JobData("MultiDeviceVerifiedUpdateJob", "__MULTI_DEVICE_VERIFIED_UPDATE__", new Data.Builder().putString("destination", "+16101234567")
                                                                                                                         .putString("identity_key", "abc")
                                                                                                                         .putInt("verified_status", 1)
                                                                                                                         .putLong("timestamp", 123)
                                                                                                                         .build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("MultiDeviceVerifiedUpdateJob", converted.getFactoryKey());
    assertEquals("__MULTI_DEVICE_VERIFIED_UPDATE__", converted.getQueueKey());
    assertEquals("abc", converted.getData().getString("identity_key"));
    assertEquals(1, converted.getData().getInt("verified_status"));
    assertEquals(123, converted.getData().getLong("timestamp"));
    assertEquals("1", converted.getData().getString("destination"));
  }

  @Test
  public void migrate_retrieveProfileJob() throws Exception {
    JobData testData = new JobData("RetrieveProfileJob", null, new Data.Builder().putString("address", "+16101234567").build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("RetrieveProfileJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());
    assertEquals("1", converted.getData().getString("recipient"));
  }

  @Test
  public void migrate_pushGroupSendJob_null() throws Exception {
    JobData testData = new JobData("PushGroupSendJob", "someGroupId", new Data.Builder().putString("filter_address", null)
                                                                                        .putLong("message_id", 123)
                                                                                        .build());
    mockRecipientResolve("someGroupId", 5);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("PushGroupSendJob", converted.getFactoryKey());
    assertEquals(RecipientId.from(5).toQueueKey(), converted.getQueueKey());
    assertNull(converted.getData().getString("filter_recipient"));
    assertFalse(converted.getData().hasString("filter_address"));
  }

  @Test
  public void migrate_pushGroupSendJob_nonNull() throws Exception {
    JobData testData = new JobData("PushGroupSendJob", "someGroupId", new Data.Builder().putString("filter_address", "+16101234567")
                                                                                        .putLong("message_id", 123)
                                                                                        .build());
    mockRecipientResolve("+16101234567", 1);
    mockRecipientResolve("someGroupId", 5);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("PushGroupSendJob", converted.getFactoryKey());
    assertEquals(RecipientId.from(5).toQueueKey(), converted.getQueueKey());
    assertEquals("1", converted.getData().getString("filter_recipient"));
    assertFalse(converted.getData().hasString("filter_address"));
  }

  @Test
  public void migrate_pushGroupUpdateJob() throws Exception {
    JobData testData = new JobData("PushGroupUpdateJob", null, new Data.Builder().putString("source", "+16101234567").putString("group_id", "abc").build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("PushGroupUpdateJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());
    assertEquals("1", converted.getData().getString("source"));
    assertEquals("abc", converted.getData().getString("group_id"));
  }

  @Test
  public void migrate_directoryRefreshJob_null() throws Exception {
    JobData testData = new JobData("DirectoryRefreshJob", "DirectoryRefreshJob", new Data.Builder().putString("address", null).putBoolean("notify_of_new_users", true).build());

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("DirectoryRefreshJob", converted.getFactoryKey());
    assertEquals("DirectoryRefreshJob", converted.getQueueKey());
    assertNull(converted.getData().getString("recipient"));
    assertTrue(converted.getData().getBoolean("notify_of_new_users"));
    assertFalse(converted.getData().hasString("address"));
  }

  @Test
  public void migrate_directoryRefreshJob_nonNull() throws Exception {
    JobData testData = new JobData("DirectoryRefreshJob", "DirectoryRefreshJob", new Data.Builder().putString("address", "+16101234567").putBoolean("notify_of_new_users", true).build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("DirectoryRefreshJob", converted.getFactoryKey());
    assertEquals("DirectoryRefreshJob", converted.getQueueKey());
    assertTrue(converted.getData().getBoolean("notify_of_new_users"));
    assertEquals("1", converted.getData().getString("recipient"));
    assertFalse(converted.getData().hasString("address"));
  }

  @Test
  public void migrate_retrieveProfileAvatarJob() throws Exception {
    JobData testData = new JobData("RetrieveProfileAvatarJob", "RetrieveProfileAvatarJob+16101234567", new Data.Builder().putString("address", "+16101234567").putString("profile_avatar", "abc").build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("RetrieveProfileAvatarJob", converted.getFactoryKey());
    assertEquals("RetrieveProfileAvatarJob::" + RecipientId.from(1).toQueueKey(), converted.getQueueKey());
    assertEquals("1", converted.getData().getString("recipient"));
  }

  @Test
  public void migrate_multiDeviceReadUpdateJob_empty() throws Exception {
    JobData testData = new JobData("MultiDeviceReadUpdateJob", null, new Data.Builder().putStringArray("message_ids", new String[0]).build());

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("MultiDeviceReadUpdateJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());
    assertEquals(0, converted.getData().getStringArray("message_ids").length);
  }

  @Test
  public void migrate_multiDeviceReadUpdateJob_twoIds() throws Exception {
    OldSerializableSyncMessageId id1 = new OldSerializableSyncMessageId("+16101234567", 1);
    OldSerializableSyncMessageId id2 = new OldSerializableSyncMessageId("+16101112222", 2);

    JobData testData = new JobData("MultiDeviceReadUpdateJob", null, new Data.Builder().putStringArray("message_ids", new String[]{ JsonUtils.toJson(id1), JsonUtils.toJson(id2) }).build());
    mockRecipientResolve("+16101234567", 1);
    mockRecipientResolve("+16101112222", 2);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("MultiDeviceReadUpdateJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());

    String[] updated = converted.getData().getStringArray("message_ids");
    assertEquals(2, updated.length);

    assertEquals(JsonUtils.toJson(new NewSerializableSyncMessageId("1", 1)), updated[0]);
    assertEquals(JsonUtils.toJson(new NewSerializableSyncMessageId("2", 2)), updated[1]);
  }

  @Test
  public void migrate_pushTextSendJob() throws Exception {
    JobData testData = new JobData("PushTextSendJob", "+16101234567", new Data.Builder().putLong("message_id", 1).build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("PushTextSendJob", converted.getFactoryKey());
    assertEquals(RecipientId.from(1).toQueueKey(), converted.getQueueKey());
    assertEquals(1, converted.getData().getLong("message_id"));
  }

  @Test
  public void migrate_pushMediaSendJob() throws Exception {
    JobData testData = new JobData("PushMediaSendJob", "+16101234567", new Data.Builder().putLong("message_id", 1).build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("PushMediaSendJob", converted.getFactoryKey());
    assertEquals(RecipientId.from(1).toQueueKey(), converted.getQueueKey());
    assertEquals(1, converted.getData().getLong("message_id"));
  }

  @Test
  public void migrate_smsSendJob() throws Exception {
    JobData testData = new JobData("SmsSendJob", "+16101234567", new Data.Builder().putLong("message_id", 1).putInt("run_attempt", 0).build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("SmsSendJob", converted.getFactoryKey());
    assertEquals(RecipientId.from(1).toQueueKey(), converted.getQueueKey());
    assertEquals(1, converted.getData().getLong("message_id"));
    assertEquals(0, converted.getData().getInt("run_attempt"));
  }

  private void mockRecipientResolve(String address, long recipientId) throws Exception {
    doReturn(mockRecipient(recipientId)).when(Recipient.class, "external", any(Context.class), eq(address));
  }

  private Recipient mockRecipient(long id) {
    Recipient recipient = mock(Recipient.class);
    when(recipient.getId()).thenReturn(RecipientId.from(id));
    return recipient;
  }

}
