package org.thoughtcrime.securesms.notifications;

import android.content.Context;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.MultiDeviceReadUpdateJob;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.Pair;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ApplicationDependencies.class, Recipient.class})
public class MarkReadReceiverTest {

  private final Context    mockContext    = mock(Context.class);
  private final JobManager mockJobManager = mock(JobManager.class);
  private final Recipient  mockSelf       = mock(Recipient.class);
  private final List<Job>  jobs           = new LinkedList<>();

  @Before
  public void setUp() {
    mockStatic(ApplicationDependencies.class);
    mockStatic(Recipient.class);
    when(ApplicationDependencies.getJobManager()).thenReturn(mockJobManager);
    doAnswer((Answer<Void>) invocation -> {
      jobs.add((Job) invocation.getArguments()[0]);
      return null;
    }).when(mockJobManager).add(any());
    when(Recipient.self()).thenReturn(mockSelf);
    when(mockSelf.getId()).thenReturn(RecipientId.from(-1));
  }

  @Test
  public void givenMultipleThreadsWithMultipleMessagesEach_whenIProcess_thenIProperlyGroupByThreadAndRecipient() {
    // GIVEN
    List<RecipientId> recipients = Stream.range(1L, 4L).map(RecipientId::from).toList();
    List<Long>        threads    = Stream.range(4L, 7L).toList();
    int               expected   = recipients.size() * threads.size() + 1;

    List<MessageDatabase.MarkedMessageInfo> infoList = Stream.of(threads)
                                                             .flatMap(threadId -> Stream.of(recipients)
                                                                                          .map(recipientId -> createMarkedMessageInfo(threadId, recipientId)))
                                                             .toList();

    List<MessageDatabase.MarkedMessageInfo> duplicatedList = Util.concatenatedList(infoList, infoList);

    // WHEN
    MarkReadReceiver.process(mockContext, duplicatedList);

    // THEN
    assertEquals("Should have 10 total jobs, including MultiDeviceReadUpdateJob", expected, jobs.size());

    Set<Pair<Long, String>> threadRecipientPairs = new HashSet<>();
    Stream.of(jobs).forEach(job -> {
      if (job instanceof MultiDeviceReadUpdateJob) {
        return;
      }

      Data data = job.serialize();

      long   threadId    = data.getLong("thread");
      String recipientId = data.getString("recipient");
      long[] messageIds  = data.getLongArray("message_ids");

      assertEquals("Each job should contain two messages.", 2, messageIds.length);
      assertTrue("Each thread recipient pair should only exist once.", threadRecipientPairs.add(new Pair<>(threadId, recipientId)));
    });

    assertEquals("Should have 9 total combinations.", 9, threadRecipientPairs.size());
  }

  private MessageDatabase.MarkedMessageInfo createMarkedMessageInfo(long threadId, @NonNull RecipientId recipientId) {
    return new MessageDatabase.MarkedMessageInfo(threadId,
                                                 new MessageDatabase.SyncMessageId(recipientId, 0),
                                                 new MessageId(1, true),
                                                 new MessageDatabase.ExpirationInfo(0, 0, 0, false));
  }
}