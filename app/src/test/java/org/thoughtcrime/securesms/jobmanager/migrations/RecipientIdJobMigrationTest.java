package org.thoughtcrime.securesms.jobmanager.migrations;

import android.app.Application;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
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
    JobData testData = new JobData("MultiDeviceContactUpdateJob", "MultiDeviceContactUpdateJob", new JsonJobData.Builder().putBoolean("force_sync", false).putString("address", "+16101234567").serialize());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);
    JsonJobData             data      = JsonJobData.deserialize(converted.getData());

    assertEquals("MultiDeviceContactUpdateJob", converted.getFactoryKey());
    assertEquals("MultiDeviceContactUpdateJob", converted.getQueueKey());
    assertFalse(data.getBoolean("force_sync"));
    assertFalse(data.hasString("address"));
    assertEquals("1", data.getString("recipient"));

    new MultiDeviceContactUpdateJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_multiDeviceViewOnceOpenJob() throws Exception {
    OldSerializableSyncMessageId oldId    = new OldSerializableSyncMessageId("+16101234567", 1);
    JobData                      testData = new JobData("MultiDeviceRevealUpdateJob", null, new JsonJobData.Builder().putString("message_id", JsonUtils.toJson(oldId)).serialize());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);
    JsonJobData             data      = JsonJobData.deserialize(converted.getData());

    assertEquals("MultiDeviceRevealUpdateJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());
    assertEquals(JsonUtils.toJson(new NewSerializableSyncMessageId("1", 1)), data.getString("message_id"));

    new MultiDeviceViewOnceOpenJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_sendDeliveryReceiptJob() throws Exception {
    JobData testData = new JobData("SendDeliveryReceiptJob", null, new JsonJobData.Builder().putString("address", "+16101234567")
                                                                                            .putLong("message_id", 1)
                                                                                            .putLong("timestamp", 2)
                                                                                            .serialize());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);
    JsonJobData             data      = JsonJobData.deserialize(converted.getData());

    assertEquals("SendDeliveryReceiptJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());
    assertEquals("1", data.getString("recipient"));
    assertEquals(1, data.getLong("message_id"));
    assertEquals(2, data.getLong("timestamp"));

    new SendDeliveryReceiptJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_multiDeviceVerifiedUpdateJob() throws Exception {
    JobData testData = new JobData("MultiDeviceVerifiedUpdateJob", "__MULTI_DEVICE_VERIFIED_UPDATE__", new JsonJobData.Builder().putString("destination", "+16101234567")
                                                                                                                                .putString("identity_key", "abcd")
                                                                                                                                .putInt("verified_status", 1)
                                                                                                                                .putLong("timestamp", 123)
                                                                                                                                .serialize());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);
    JsonJobData             data      = JsonJobData.deserialize(converted.getData());

    assertEquals("MultiDeviceVerifiedUpdateJob", converted.getFactoryKey());
    assertEquals("__MULTI_DEVICE_VERIFIED_UPDATE__", converted.getQueueKey());
    assertEquals("abcd", data.getString("identity_key"));
    assertEquals(1, data.getInt("verified_status"));
    assertEquals(123, data.getLong("timestamp"));
    assertEquals("1", data.getString("destination"));

    new MultiDeviceVerifiedUpdateJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_pushGroupSendJob_null() throws Exception {
    JobData testData = new JobData("PushGroupSendJob", "someGroupId", new JsonJobData.Builder().putString("filter_address", null)
                                                                                               .putLong("message_id", 123)
                                                                                               .serialize());
    mockRecipientResolve("someGroupId", 5);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);
    JsonJobData             data      = JsonJobData.deserialize(converted.getData());

    assertEquals("PushGroupSendJob", converted.getFactoryKey());
    assertEquals(RecipientId.from(5).toQueueKey(), converted.getQueueKey());
    assertNull(data.getString("filter_recipient"));
    assertFalse(data.hasString("filter_address"));

    new PushGroupSendJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_pushGroupSendJob_nonNull() throws Exception {
    JobData testData = new JobData("PushGroupSendJob", "someGroupId", new JsonJobData.Builder().putString("filter_address", "+16101234567")
                                                                                               .putLong("message_id", 123)
                                                                                               .serialize());
    mockRecipientResolve("+16101234567", 1);
    mockRecipientResolve("someGroupId", 5);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);
    JsonJobData             data      = JsonJobData.deserialize(converted.getData());

    assertEquals("PushGroupSendJob", converted.getFactoryKey());
    assertEquals(RecipientId.from(5).toQueueKey(), converted.getQueueKey());
    assertEquals("1", data.getString("filter_recipient"));
    assertFalse(data.hasString("filter_address"));

    new PushGroupSendJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_directoryRefreshJob_null() throws Exception {
    JobData testData = new JobData("DirectoryRefreshJob", "DirectoryRefreshJob", new JsonJobData.Builder().putString("address", null).putBoolean("notify_of_new_users", true).serialize());

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);
    JsonJobData             data      = JsonJobData.deserialize(converted.getData());

    assertEquals("DirectoryRefreshJob", converted.getFactoryKey());
    assertEquals("DirectoryRefreshJob", converted.getQueueKey());
    assertNull(data.getString("recipient"));
    assertTrue(data.getBoolean("notify_of_new_users"));
    assertFalse(data.hasString("address"));

    new DirectoryRefreshJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_directoryRefreshJob_nonNull() throws Exception {
    JobData testData = new JobData("DirectoryRefreshJob", "DirectoryRefreshJob", new JsonJobData.Builder().putString("address", "+16101234567").putBoolean("notify_of_new_users", true).serialize());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);
    JsonJobData             data      = JsonJobData.deserialize(converted.getData());

    assertEquals("DirectoryRefreshJob", converted.getFactoryKey());
    assertEquals("DirectoryRefreshJob", converted.getQueueKey());
    assertTrue(data.getBoolean("notify_of_new_users"));
    assertEquals("1", data.getString("recipient"));
    assertFalse(data.hasString("address"));

    new DirectoryRefreshJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_retrieveProfileAvatarJob() throws Exception {
    JobData testData = new JobData("RetrieveProfileAvatarJob", "RetrieveProfileAvatarJob+16101234567", new JsonJobData.Builder().putString("address", "+16101234567").putString("profile_avatar", "abc").serialize());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);
    JsonJobData             data      = JsonJobData.deserialize(converted.getData());

    assertEquals("RetrieveProfileAvatarJob", converted.getFactoryKey());
    assertEquals("RetrieveProfileAvatarJob::" + RecipientId.from(1).toQueueKey(), converted.getQueueKey());
    assertEquals("1", data.getString("recipient"));


    new RetrieveProfileAvatarJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_multiDeviceReadUpdateJob_empty() throws Exception {
    JobData testData = new JobData("MultiDeviceReadUpdateJob", null, new JsonJobData.Builder().putStringArray("message_ids", new String[0]).serialize());

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);
    JsonJobData             data      = JsonJobData.deserialize(converted.getData());

    assertEquals("MultiDeviceReadUpdateJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());
    assertEquals(0, data.getStringArray("message_ids").length);

    new MultiDeviceReadUpdateJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_multiDeviceReadUpdateJob_twoIds() throws Exception {
    OldSerializableSyncMessageId id1 = new OldSerializableSyncMessageId("+16101234567", 1);
    OldSerializableSyncMessageId id2 = new OldSerializableSyncMessageId("+16101112222", 2);

    JobData testData = new JobData("MultiDeviceReadUpdateJob", null, new JsonJobData.Builder().putStringArray("message_ids", new String[]{ JsonUtils.toJson(id1), JsonUtils.toJson(id2) }).serialize());
    mockRecipientResolve("+16101234567", 1);
    mockRecipientResolve("+16101112222", 2);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);
    JsonJobData             data      = JsonJobData.deserialize(converted.getData());

    assertEquals("MultiDeviceReadUpdateJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());

    String[] updated = data.getStringArray("message_ids");
    assertEquals(2, updated.length);

    assertEquals(JsonUtils.toJson(new NewSerializableSyncMessageId("1", 1)), updated[0]);
    assertEquals(JsonUtils.toJson(new NewSerializableSyncMessageId("2", 2)), updated[1]);

    new MultiDeviceReadUpdateJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_pushMediaSendJob() throws Exception {
    JobData testData = new JobData("PushMediaSendJob", "+16101234567", new JsonJobData.Builder().putLong("message_id", 1).serialize());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);
    JsonJobData             data      = JsonJobData.deserialize(converted.getData());

    assertEquals("PushMediaSendJob", converted.getFactoryKey());
    assertEquals(RecipientId.from(1).toQueueKey(), converted.getQueueKey());
    assertEquals(1, data.getLong("message_id"));

    new IndividualSendJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_smsSendJob_nonNull() throws Exception {
    JobData testData = new JobData("SmsSendJob", "+16101234567", new JsonJobData.Builder().putLong("message_id", 1).putInt("run_attempt", 0).serialize());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);
    JsonJobData             data      = JsonJobData.deserialize(converted.getData());

    assertEquals("SmsSendJob", converted.getFactoryKey());
    assertEquals(RecipientId.from(1).toQueueKey(), converted.getQueueKey());
    assertEquals(1, data.getLong("message_id"));
    assertEquals(0, data.getInt("run_attempt"));

    new SmsSendJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_smsSendJob_null() throws Exception {
    JobData testData = new JobData("SmsSendJob", null, new JsonJobData.Builder().putLong("message_id", 1).putInt("run_attempt", 0).serialize());
    mockRecipientResolve("+16101234567", 1);

    RecipientIdJobMigration subject   = new RecipientIdJobMigration(mock(Application.class));
    JobData                 converted = subject.migrate(testData);
    JsonJobData             data      = JsonJobData.deserialize(converted.getData());

    assertEquals("SmsSendJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());
    assertEquals(1, data.getLong("message_id"));
    assertEquals(0, data.getInt("run_attempt"));

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
