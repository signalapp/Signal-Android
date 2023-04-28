package org.thoughtcrime.securesms.jobmanager.migrations;

import org.junit.Test;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.JobMigration;
import org.thoughtcrime.securesms.jobs.SendReadReceiptJob;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SendReadReceiptsJobMigrationTest {

  private final MessageTable                 mockDatabase = mock(MessageTable.class);
  private final SendReadReceiptsJobMigration testSubject  = new SendReadReceiptsJobMigration(mockDatabase);

  @Test
  public void givenSendReadReceiptJobDataWithoutThreadIdAndThreadIdFound_whenIMigrate_thenIInsertThreadId() {
    // GIVEN
    SendReadReceiptJob   job     = new SendReadReceiptJob(1, RecipientId.from(2), new ArrayList<>(), new ArrayList<>());
    JobMigration.JobData jobData = new JobMigration.JobData(job.getFactoryKey(),
                                                            "asdf",
                                                            new JsonJobData.Builder()
                                                                    .putString("recipient", RecipientId.from(2).serialize())
                                                                    .putLongArray("message_ids", new long[]{1, 2, 3, 4, 5})
                                                                    .putLong("timestamp", 292837649).serialize());
    when(mockDatabase.getThreadIdForMessage(anyLong())).thenReturn(1234L);

    // WHEN
    JobMigration.JobData result = testSubject.migrate(jobData);
    JsonJobData          data   = JsonJobData.deserialize(result.getData());

    // THEN
    assertEquals(1234L, data.getLong("thread"));
    assertEquals(RecipientId.from(2).serialize(), data.getString("recipient"));
    assertTrue(data.hasLongArray("message_ids"));
    assertTrue(data.hasLong("timestamp"));
  }

  @Test
  public void givenSendReadReceiptJobDataWithoutThreadIdAndThreadIdNotFound_whenIMigrate_thenIGetAFailingJob() {
    // GIVEN
    SendReadReceiptJob   job     = new SendReadReceiptJob(1, RecipientId.from(2), new ArrayList<>(), new ArrayList<>());
    JobMigration.JobData jobData = new JobMigration.JobData(job.getFactoryKey(),
                                                            "asdf",
                                                            new JsonJobData.Builder()
                                                                .putString("recipient", RecipientId.from(2).serialize())
                                                                .putLongArray("message_ids", new long[]{})
                                                                .putLong("timestamp", 292837649).serialize());
    when(mockDatabase.getThreadIdForMessage(anyLong())).thenReturn(-1L);

    // WHEN
    JobMigration.JobData result = testSubject.migrate(jobData);

    // THEN
    assertEquals("FailingJob", result.getFactoryKey());
  }

  @Test
  public void givenSendReadReceiptJobDataWithThreadId_whenIMigrate_thenIDoNotReplace() {
    // GIVEN
    SendReadReceiptJob   job     = new SendReadReceiptJob(1, RecipientId.from(2), new ArrayList<>(), new ArrayList<>());
    JobMigration.JobData jobData = new JobMigration.JobData(job.getFactoryKey(), "asdf", job.serialize());

    // WHEN
    JobMigration.JobData result = testSubject.migrate(jobData);

    // THEN
    assertEquals(jobData, result);
  }

  @Test
  public void givenSomeOtherJobDataWithThreadId_whenIMigrate_thenIDoNotReplace() {
    // GIVEN
    JobMigration.JobData jobData = new JobMigration.JobData("SomeOtherJob", "asdf", new JsonJobData.Builder().putLong("thread", 1).serialize());

    // WHEN
    JobMigration.JobData result = testSubject.migrate(jobData);

    // THEN
    assertEquals(jobData, result);
  }

  @Test
  public void givenSomeOtherJobDataWithoutThreadId_whenIMigrate_thenIDoNotReplace() {
    // GIVEN
    JobMigration.JobData jobData = new JobMigration.JobData("SomeOtherJob", "asdf", new JsonJobData.Builder().serialize());

    // WHEN
    JobMigration.JobData result = testSubject.migrate(jobData);

    // THEN
    assertEquals(jobData, result);
  }


}
