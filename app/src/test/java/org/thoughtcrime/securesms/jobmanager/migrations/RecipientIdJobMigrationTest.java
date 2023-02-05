package org.thoughtcrime.securesms.jobmanager.migrations;

import android.app.Application;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobMigration.JobData;
import org.thoughtcrime.securesms.jobmanager.migrations.RecipientIdJobMigration.NewSerializableSyncMessageId;
import org.thoughtcrime.securesms.jobmanager.migrations.RecipientIdJobMigration.OldSerializableSyncMessageId;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceContactUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceReadUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceVerifiedUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceViewOnceOpenJob;
import org.thoughtcrime.securesms.jobs.PushGroupSendJob;
import org.thoughtcrime.securesms.jobs.IndividualSendJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob;
import org.thoughtcrime.securesms.jobs.SendDeliveryReceiptJob;
import org.thoughtcrime.securesms.jobs.SmsSendJob;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.JsonUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RecipientIdJobMigrationTest {

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Mock
  private MockedStatic<Recipient> recipientMockedStatic;

  @Mock
  private MockedStatic<Job.Parameters> jobParametersMockStatic;

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

    new MultiDeviceContactUpdateJob.Factory().create(mock(Job.Parameters.class), converted.getData());
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

    new MultiDeviceViewOnceOpenJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_sendDeliveryReceiptJob() throws Exception {
    JobData testData = new JobData("SendDeliveryReceiptJob", null, new Data.Builder().putString("address", "+16101234567")
                                                                                     .putLong("message_id", 1)
                                                                                     .putLong("timestamp", 2)
                                                                                     .build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("SendDeliveryReceiptJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());
    assertEquals("1", converted.getData().getString("recipient"));
    assertEquals(1, converted.getData().getLong("message_id"));
    assertEquals(2, converted.getData().getLong("timestamp"));

    new SendDeliveryReceiptJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_multiDeviceVerifiedUpdateJob() throws Exception {
    JobData testData = new JobData("MultiDeviceVerifiedUpdateJob", "__MULTI_DEVICE_VERIFIED_UPDATE__", new Data.Builder().putString("destination", "+16101234567")
                                                                                                                         .putString("identity_key", "abcd")
                                                                                                                         .putInt("verified_status", 1)
                                                                                                                         .putLong("timestamp", 123)
                                                                                                                         .build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("MultiDeviceVerifiedUpdateJob", converted.getFactoryKey());
    assertEquals("__MULTI_DEVICE_VERIFIED_UPDATE__", converted.getQueueKey());
    assertEquals("abcd", converted.getData().getString("identity_key"));
    assertEquals(1, converted.getData().getInt("verified_status"));
    assertEquals(123, converted.getData().getLong("timestamp"));
    assertEquals("1", converted.getData().getString("destination"));

    new MultiDeviceVerifiedUpdateJob.Factory().create(mock(Job.Parameters.class), converted.getData());
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

    new PushGroupSendJob.Factory().create(mock(Job.Parameters.class), converted.getData());
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

    new PushGroupSendJob.Factory().create(mock(Job.Parameters.class), converted.getData());
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

    new DirectoryRefreshJob.Factory().create(mock(Job.Parameters.class), converted.getData());
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

    new DirectoryRefreshJob.Factory().create(mock(Job.Parameters.class), converted.getData());
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


    new RetrieveProfileAvatarJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_multiDeviceReadUpdateJob_empty() throws Exception {
    JobData testData = new JobData("MultiDeviceReadUpdateJob", null, new Data.Builder().putStringArray("message_ids", new String[0]).build());

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("MultiDeviceReadUpdateJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());
    assertEquals(0, converted.getData().getStringArray("message_ids").length);

    new MultiDeviceReadUpdateJob.Factory().create(mock(Job.Parameters.class), converted.getData());
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

    new MultiDeviceReadUpdateJob.Factory().create(mock(Job.Parameters.class), converted.getData());
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

    new IndividualSendJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_smsSendJob_nonNull() throws Exception {
    JobData testData = new JobData("SmsSendJob", "+16101234567", new Data.Builder().putLong("message_id", 1).putInt("run_attempt", 0).build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("SmsSendJob", converted.getFactoryKey());
    assertEquals(RecipientId.from(1).toQueueKey(), converted.getQueueKey());
    assertEquals(1, converted.getData().getLong("message_id"));
    assertEquals(0, converted.getData().getInt("run_attempt"));

    new SmsSendJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_smsSendJob_null() throws Exception {
    JobData testData = new JobData("SmsSendJob", null, new Data.Builder().putLong("message_id", 1).putInt("run_attempt", 0).build());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);

    assertEquals("SmsSendJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());
    assertEquals(1, converted.getData().getLong("message_id"));
    assertEquals(0, converted.getData().getInt("run_attempt"));

    new SmsSendJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  private void mockRecipientResolve(String address, long recipientId) {
    Recipient mockRecipient = mockRecipient(recipientId);
    recipientMockedStatic.when(() -> Recipient.external(any(), eq(address))).thenReturn(mockRecipient);
  }

  private Recipient mockRecipient(long id) {
    Recipient recipient = mock(Recipient.class);
    when(recipient.getId()).thenReturn(RecipientId.from(id));
    return recipient;
  }

}
