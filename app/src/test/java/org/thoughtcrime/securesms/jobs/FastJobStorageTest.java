package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.junit.Test;
import org.thoughtcrime.securesms.database.JobDatabase;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.impl.JsonDataSerializer;
import org.thoughtcrime.securesms.jobmanager.persistence.ConstraintSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.DependencySpec;
import org.thoughtcrime.securesms.jobmanager.persistence.FullSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FastJobStorageTest {

  private static final JsonDataSerializer serializer = new JsonDataSerializer();
  private static final String             EMPTY_DATA = serializer.serialize(Data.EMPTY);

  @Test
  public void init_allStoredDataAvailable() {
    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS));

    subject.init();

    DataSet1.assertJobsMatch(subject.getAllJobSpecs());
    DataSet1.assertConstraintsMatch(subject.getAllConstraintSpecs());
    DataSet1.assertDependenciesMatch(subject.getAllDependencySpecs());
  }

  @Test
  public void insertJobs_writesToDatabase() {
    JobDatabase    database = noopDatabase();
    FastJobStorage subject  = new FastJobStorage(database);

    subject.insertJobs(DataSet1.FULL_SPECS);

    verify(database).insertJobs(DataSet1.FULL_SPECS);
  }

  @Test
  public void insertJobs_dataCanBeFound() {
    FastJobStorage subject = new FastJobStorage(noopDatabase());

    subject.insertJobs(DataSet1.FULL_SPECS);

    DataSet1.assertJobsMatch(subject.getAllJobSpecs());
    DataSet1.assertConstraintsMatch(subject.getAllConstraintSpecs());
    DataSet1.assertDependenciesMatch(subject.getAllDependencySpecs());
  }

  @Test
  public void insertJobs_individualJobCanBeFound() {
    FastJobStorage subject = new FastJobStorage(noopDatabase());

    subject.insertJobs(DataSet1.FULL_SPECS);

    assertEquals(DataSet1.JOB_1, subject.getJobSpec(DataSet1.JOB_1.getId()));
    assertEquals(DataSet1.JOB_2, subject.getJobSpec(DataSet1.JOB_2.getId()));
  }

  @Test
  public void updateAllJobsToBePending_writesToDatabase() {
    JobDatabase    database = noopDatabase();
    FastJobStorage subject  = new FastJobStorage(database);

    subject.updateAllJobsToBePending();

    verify(database).updateAllJobsToBePending();
  }

  @Test
  public void updateAllJobsToBePending_allArePending() {
    FullSpec fullSpec1 = new FullSpec(new JobSpec("1", "f1", null, 1, 1, 1, 1, 1, 1, 1, EMPTY_DATA, true),
                                      Collections.emptyList(),
                                      Collections.emptyList());
    FullSpec fullSpec2 = new FullSpec(new JobSpec("2", "f2", null, 1, 1, 1, 1, 1, 1, 1, EMPTY_DATA, true),
                                      Collections.emptyList(),
                                      Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(fullSpec1, fullSpec2)));

    subject.init();
    subject.updateAllJobsToBePending();

    assertFalse(subject.getJobSpec("1").isRunning());
    assertFalse(subject.getJobSpec("2").isRunning());
  }

  @Test
  public void updateJobRunningState_writesToDatabase() {
    JobDatabase    database = noopDatabase();
    FastJobStorage subject  = new FastJobStorage(database);

    subject.updateJobRunningState("1", true);

    verify(database).updateJobRunningState("1", true);
  }

  @Test
  public void updateJobRunningState_stateUpdated() {
    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS));
    subject.init();

    subject.updateJobRunningState(DataSet1.JOB_1.getId(), true);
    assertTrue(subject.getJobSpec(DataSet1.JOB_1.getId()).isRunning());

    subject.updateJobRunningState(DataSet1.JOB_1.getId(), false);
    assertFalse(subject.getJobSpec(DataSet1.JOB_1.getId()).isRunning());
  }

  @Test
  public void updateJobAfterRetry_writesToDatabase() {
    JobDatabase    database = noopDatabase();
    FastJobStorage subject  = new FastJobStorage(database);

    subject.updateJobAfterRetry("1", true, 1, 10);

    verify(database).updateJobAfterRetry("1", true, 1, 10);
  }

  @Test
  public void updateJobAfterRetry_stateUpdated() {
    FullSpec fullSpec = new FullSpec(new JobSpec("1", "f1", null, 0, 0, 0, 3, 30000, -1, -1, EMPTY_DATA, true),
                                     Collections.emptyList(),
                                     Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Collections.singletonList(fullSpec)));

    subject.init();
    subject.updateJobAfterRetry("1", false, 1, 10);

    JobSpec job = subject.getJobSpec("1");

    assertNotNull(job);
    assertFalse(job.isRunning());
    assertEquals(1, job.getRunAttempt());
    assertEquals(10, job.getNextRunAttemptTime());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_noneWhenEarlierItemInQueueInRunning() {
    FullSpec fullSpec1 = new FullSpec(new JobSpec("1", "f1", "q", 0, 0, 0, 0, 0, -1, -1, EMPTY_DATA, true),
                                      Collections.emptyList(),
                                      Collections.emptyList());
    FullSpec fullSpec2 = new FullSpec(new JobSpec("2", "f2", "q", 0, 0, 0, 0, 0, -1, -1, EMPTY_DATA, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(fullSpec1, fullSpec2)));
    subject.init();

    assertEquals(0, subject.getPendingJobsWithNoDependenciesInCreatedOrder(1).size());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_noneWhenAllJobsAreRunning() {
    FullSpec fullSpec = new FullSpec(new JobSpec("1", "f1", "q", 0, 0, 0, 0, 0, -1, -1, EMPTY_DATA, true),
                                     Collections.emptyList(),
                                     Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Collections.singletonList(fullSpec)));
    subject.init();

    assertEquals(0, subject.getPendingJobsWithNoDependenciesInCreatedOrder(10).size());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_noneWhenNextRunTimeIsAfterCurrentTime() {
    FullSpec fullSpec = new FullSpec(new JobSpec("1", "f1", "q", 0, 10, 0, 0, 0, -1, -1, EMPTY_DATA, false),
                                     Collections.emptyList(),
                                     Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Collections.singletonList(fullSpec)));
    subject.init();

    assertEquals(0, subject.getPendingJobsWithNoDependenciesInCreatedOrder(0).size());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_noneWhenDependentOnAnotherJob() {
    FullSpec fullSpec1 = new FullSpec(new JobSpec("1", "f1", null, 0, 0, 0, 0, 0, -1, -1, EMPTY_DATA, true),
                                      Collections.emptyList(),
                                      Collections.emptyList());
    FullSpec fullSpec2 = new FullSpec(new JobSpec("2", "f2", null, 0, 0, 0, 0, 0, -1, -1, EMPTY_DATA, false),
                                      Collections.emptyList(),
                                      Collections.singletonList(new DependencySpec("2", "1")));


    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(fullSpec1, fullSpec2)));
    subject.init();

    assertEquals(0, subject.getPendingJobsWithNoDependenciesInCreatedOrder(0).size());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_singleEligibleJob() {
    FullSpec fullSpec = new FullSpec(new JobSpec("1", "f1", "q", 0, 0, 0, 0, 0, -1, -1, EMPTY_DATA, false),
                                     Collections.emptyList(),
                                     Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Collections.singletonList(fullSpec)));
    subject.init();

    assertEquals(1, subject.getPendingJobsWithNoDependenciesInCreatedOrder(10).size());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_multipleEligibleJobs() {
    FullSpec fullSpec1 = new FullSpec(new JobSpec("1", "f1", null, 0, 0, 0, 0, 0, -1, -1, EMPTY_DATA, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());
    FullSpec fullSpec2 = new FullSpec(new JobSpec("2", "f2", null, 0, 0, 0, 0, 0, -1, -1, EMPTY_DATA, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());


    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(fullSpec1, fullSpec2)));
    subject.init();

    assertEquals(2, subject.getPendingJobsWithNoDependenciesInCreatedOrder(10).size());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_singleEligibleJobInMixedList() {
    FullSpec fullSpec1 = new FullSpec(new JobSpec("1", "f1", null, 0, 0, 0, 0, 0, -1, -1, EMPTY_DATA, true),
                                      Collections.emptyList(),
                                      Collections.emptyList());
    FullSpec fullSpec2 = new FullSpec(new JobSpec("2", "f2", null, 0, 0, 0, 0, 0, -1, -1, EMPTY_DATA, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());


    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(fullSpec1, fullSpec2)));
    subject.init();

    List<JobSpec> jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10);

    assertEquals(1, jobs.size());
    assertEquals("2", jobs.get(0).getId());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_firstItemInQueue() {
    FullSpec fullSpec1 = new FullSpec(new JobSpec("1", "f1", "q", 0, 0, 0, 0, 0, -1, -1, EMPTY_DATA, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());
    FullSpec fullSpec2 = new FullSpec(new JobSpec("2", "f2", "q", 0, 0, 0, 0, 0, -1, -1, EMPTY_DATA, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());


    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(fullSpec1, fullSpec2)));
    subject.init();

    List<JobSpec> jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10);

    assertEquals(1, jobs.size());
    assertEquals("1", jobs.get(0).getId());
  }

  @Test
  public void deleteJobs_writesToDatabase() {
    JobDatabase    database = noopDatabase();
    FastJobStorage subject  = new FastJobStorage(database);
    List<String>   ids      = Arrays.asList("1", "2");

    subject.deleteJobs(ids);

    verify(database).deleteJobs(ids);
  }

  @Test
  public void deleteJobs_deletesAllRelevantPieces() {
    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS));

    subject.init();
    subject.deleteJobs(Collections.singletonList("id1"));

    List<JobSpec>        jobs         = subject.getAllJobSpecs();
    List<ConstraintSpec> constraints  = subject.getAllConstraintSpecs();
    List<DependencySpec> dependencies = subject.getAllDependencySpecs();

    assertEquals(1, jobs.size());
    assertEquals(DataSet1.JOB_2, jobs.get(0));
    assertEquals(1, constraints.size());
    assertEquals(DataSet1.CONSTRAINT_2, constraints.get(0));
    assertEquals(0, dependencies.size());
  }


  private JobDatabase noopDatabase() {
    JobDatabase database = mock(JobDatabase.class);

    when(database.getAllJobSpecs()).thenReturn(Collections.emptyList());
    when(database.getAllConstraintSpecs()).thenReturn(Collections.emptyList());
    when(database.getAllDependencySpecs()).thenReturn(Collections.emptyList());

    return database;
  }

  private JobDatabase fixedDataDatabase(List<FullSpec> fullSpecs) {
    JobDatabase database = mock(JobDatabase.class);

    when(database.getAllJobSpecs()).thenReturn(Stream.of(fullSpecs).map(FullSpec::getJobSpec).toList());
    when(database.getAllConstraintSpecs()).thenReturn(Stream.of(fullSpecs).map(FullSpec::getConstraintSpecs).flatMap(Stream::of).toList());
    when(database.getAllDependencySpecs()).thenReturn(Stream.of(fullSpecs).map(FullSpec::getDependencySpecs).flatMap(Stream::of).toList());

    return database;
  }

  private static final class DataSet1 {
    static final JobSpec        JOB_1        = new JobSpec("id1", "f1", "q1", 1, 2, 3, 4, 5, 6, 7, EMPTY_DATA, false);
    static final JobSpec        JOB_2        = new JobSpec("id2", "f2", "q2", 1, 2, 3, 4, 5, 6, 7, EMPTY_DATA, false);
    static final ConstraintSpec CONSTRAINT_1 = new ConstraintSpec("id1", "f1");
    static final ConstraintSpec CONSTRAINT_2 = new ConstraintSpec("id2", "f2");
    static final DependencySpec DEPENDENCY_2 = new DependencySpec("id2", "id1");
    static final FullSpec       FULL_SPEC_1  = new FullSpec(JOB_1, Collections.singletonList(CONSTRAINT_1), Collections.emptyList());
    static final FullSpec       FULL_SPEC_2  = new FullSpec(JOB_2, Collections.singletonList(CONSTRAINT_2), Collections.singletonList(DEPENDENCY_2));
    static final List<FullSpec> FULL_SPECS   = Arrays.asList(FULL_SPEC_1, FULL_SPEC_2);

    static void assertJobsMatch(@NonNull List<JobSpec> jobs) {
      assertEquals(jobs.size(), 2);
      assertTrue(jobs.contains(DataSet1.JOB_1));
      assertTrue(jobs.contains(DataSet1.JOB_1));
    }

    static void assertConstraintsMatch(@NonNull List<ConstraintSpec> constraints) {
      assertEquals(constraints.size(), 2);
      assertTrue(constraints.contains(DataSet1.CONSTRAINT_1));
      assertTrue(constraints.contains(DataSet1.CONSTRAINT_2));
    }

    static void assertDependenciesMatch(@NonNull List<DependencySpec> dependencies) {
      assertEquals(dependencies.size(), 1);
      assertTrue(dependencies.contains(DataSet1.DEPENDENCY_2));
    }
  }
}
