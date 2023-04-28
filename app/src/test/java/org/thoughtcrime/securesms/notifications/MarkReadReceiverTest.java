package org.thoughtcrime.securesms.notifications;

import android.content.Context;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.MultiDeviceReadUpdateJob;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Util;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MarkReadReceiverTest {

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Mock
  private MockedStatic<ApplicationDependencies> applicationDependenciesMockedStatic;

  @Mock
  private MockedStatic<Recipient> recipientMockedStatic;

  private final Context    mockContext    = mock(Context.class);
  private final JobManager mockJobManager = mock(JobManager.class);
  private final Recipient  mockSelf       = mock(Recipient.class);
  private final List<Job>  jobs           = new LinkedList<>();

  @Before
  public void setUp() {
    applicationDependenciesMockedStatic.when(ApplicationDependencies::getJobManager).thenReturn(mockJobManager);
    doAnswer((Answer<Void>) invocation -> {
      jobs.add((Job) invocation.getArguments()[0]);
      return null;
    }).when(mockJobManager).add(any());
    recipientMockedStatic.when(Recipient::self).thenReturn(mockSelf);
    when(mockSelf.getId()).thenReturn(RecipientId.from(-1));
  }

  @Test
  public void givenMultipleThreadsWithMultipleMessagesEach_whenIProcess_thenIProperlyGroupByThreadAndRecipient() {
    // GIVEN
    List<RecipientId> recipients = Stream.range(1L, 4L).map(RecipientId::from).toList();
    List<Long>        threads    = Stream.range(4L, 7L).toList();
    int               expected   = recipients.size() * threads.size() + 1;

    List<MessageTable.MarkedMessageInfo> infoList = Stream.of(threads)
                                                          .flatMap(threadId -> Stream.of(recipients)
                                                                                          .map(recipientId -> createMarkedMessageInfo(threadId, recipientId)))
                                                          .toList();

    List<MessageTable.MarkedMessageInfo> duplicatedList = Util.concatenatedList(infoList, infoList);

    // WHEN
    MarkReadReceiver.process(mockContext, duplicatedList);

    // THEN
    assertEquals("Should have 10 total jobs, including MultiDeviceReadUpdateJob", expected, jobs.size());

    Set<Pair<Long, String>> threadRecipientPairs = new HashSet<>();
    Stream.of(jobs).forEach(job -> {
      if (job instanceof MultiDeviceReadUpdateJob) {
        return;
      }

      JsonJobData data = JsonJobData.deserialize(job.serialize());

      long   threadId    = data.getLong("thread");
      String recipientId = data.getString("recipient");
      long[] messageIds  = data.getLongArray("message_ids");

      assertEquals("Each job should contain two messages.", 2, messageIds.length);
      assertTrue("Each thread recipient pair should only exist once.", threadRecipientPairs.add(new Pair<>(threadId, recipientId)));
    });

    assertEquals("Should have 9 total combinations.", 9, threadRecipientPairs.size());
  }

  private MessageTable.MarkedMessageInfo createMarkedMessageInfo(long threadId, @NonNull RecipientId recipientId) {
    return new MessageTable.MarkedMessageInfo(threadId,
                                              new MessageTable.SyncMessageId(recipientId, 0),
                                              new MessageId(1),
                                              new MessageTable.ExpirationInfo(0, 0, 0, false),
                                              StoryType.NONE);
  }
}
