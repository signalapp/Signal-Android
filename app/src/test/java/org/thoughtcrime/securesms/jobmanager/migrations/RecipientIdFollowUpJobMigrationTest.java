package org.thoughtcrime.securesms.jobmanager.migrations;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobMigration.JobData;
import org.thoughtcrime.securesms.jobs.FailingJob;
import org.thoughtcrime.securesms.jobs.RequestGroupInfoJob;
import org.thoughtcrime.securesms.jobs.SendDeliveryReceiptJob;
import org.thoughtcrime.securesms.recipients.Recipient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ Recipient.class, Job.Parameters.class })
public class RecipientIdFollowUpJobMigrationTest {

  @Before
  public void init() {
    mockStatic(Recipient.class);
    mockStatic(Job.Parameters.class);
  }

  @Test
  public void migrate_requestGroupInfoJob_good() throws Exception {
    JobData testData = new JobData("RequestGroupInfoJob", null, new Data.Builder().putString("source", "1")
                                                                                  .putString("group_id", "__textsecure_group__!abcd")
                                                                                  .build());
    RecipientIdFollowUpJobMigration subject   = new RecipientIdFollowUpJobMigration();
    JobData                         converted = subject.migrate(testData);

    assertEquals("RequestGroupInfoJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());
    assertEquals("1", converted.getData().getString("source"));
    assertEquals("__textsecure_group__!abcd", converted.getData().getString("group_id"));

    new RequestGroupInfoJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_requestGroupInfoJob_bad() throws Exception {
    JobData testData = new JobData("RequestGroupInfoJob", null, new Data.Builder().putString("source", "1")
                                                                                  .build());
    RecipientIdFollowUpJobMigration subject   = new RecipientIdFollowUpJobMigration();
    JobData                         converted = subject.migrate(testData);

    assertEquals("FailingJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());

    new FailingJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_sendDeliveryReceiptJob_good() throws Exception {
    JobData testData = new JobData("SendDeliveryReceiptJob", null, new Data.Builder().putString("recipient", "1")
                                                                                     .putLong("message_id", 1)
                                                                                     .putLong("timestamp", 2)
                                                                                     .build());
    RecipientIdFollowUpJobMigration subject   = new RecipientIdFollowUpJobMigration();
    JobData                         converted = subject.migrate(testData);

    assertEquals("SendDeliveryReceiptJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());
    assertEquals("1", converted.getData().getString("recipient"));
    assertEquals(1, converted.getData().getLong("message_id"));
    assertEquals(2, converted.getData().getLong("timestamp"));

    new SendDeliveryReceiptJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }

  @Test
  public void migrate_sendDeliveryReceiptJob_bad() throws Exception {
    JobData testData = new JobData("SendDeliveryReceiptJob", null, new Data.Builder().putString("recipient", "1")
                                                                                     .build());
    RecipientIdFollowUpJobMigration subject   = new RecipientIdFollowUpJobMigration();
    JobData                         converted = subject.migrate(testData);

    assertEquals("FailingJob", converted.getFactoryKey());
    assertNull(converted.getQueueKey());

    new FailingJob.Factory().create(mock(Job.Parameters.class), converted.getData());
  }
}
