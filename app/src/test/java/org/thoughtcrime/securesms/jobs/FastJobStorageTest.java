package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.junit.Test;
import org.thoughtcrime.securesms.database.JobDatabase;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
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
import static org.mockito.Mockito.times;
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
  public void insertJobs_memoryOnlyJob_doesNotWriteToDatabase() {
    JobDatabase    database = noopDatabase();
    FastJobStorage subject  = new FastJobStorage(database);

    subject.insertJobs(DataSetMemory.FULL_SPECS);

    verify(database, times(0)).insertJobs(DataSet1.FULL_SPECS);
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
    FullSpec fullSpec1 = new FullSpec(new JobSpec("1", "f1", null, 1, 1, 1, 1, 1, 1, EMPTY_DATA, null, true, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());
    FullSpec fullSpec2 = new FullSpec(new JobSpec("2", "f2", null, 1, 1, 1, 1, 1, 1, EMPTY_DATA, null, true, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(fullSpec1, fullSpec2)));

    subject.init();
    subject.updateAllJobsToBePending();

    assertFalse(subject.getJobSpec("1").isRunning());
    assertFalse(subject.getJobSpec("2").isRunning());
  }

  @Test
  public void updateJobs_writesToDatabase() {
    JobDatabase    database = fixedDataDatabase(DataSet1.FULL_SPECS);
    FastJobStorage subject  = new FastJobStorage(database);
    List<JobSpec>  jobs     = Collections.singletonList(new JobSpec("id1", "f1", null, 1, 1, 1, 1, 1, 1, EMPTY_DATA, null, false, false));

    subject.init();
    subject.updateJobs(jobs);

    verify(database).updateJobs(jobs);
  }

  @Test
  public void updateJobs_memoryOnly_doesNotWriteToDatabase() {
    JobDatabase    database = fixedDataDatabase(DataSetMemory.FULL_SPECS);
    FastJobStorage subject  = new FastJobStorage(database);
    List<JobSpec>  jobs     = Collections.singletonList(new JobSpec("id1", "f1", null, 1, 1, 1, 1, 1, 1, EMPTY_DATA, null, false, false));

    subject.init();
    subject.updateJobs(jobs);

    verify(database, times(0)).updateJobs(jobs);
  }

  @Test
  public void updateJobs_updatesAllFields() {
    FullSpec fullSpec1 = new FullSpec(new JobSpec("1", "f1", null, 1, 1, 1, 1, 1, 1, EMPTY_DATA, null, false, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());
    FullSpec fullSpec2 = new FullSpec(new JobSpec("2", "f2", null, 1, 1, 1, 1, 1, 1, EMPTY_DATA, null, false, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());
    FullSpec fullSpec3 = new FullSpec(new JobSpec("3", "f3", null, 1, 1, 1, 1, 1, 1, EMPTY_DATA, null, false, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(fullSpec1, fullSpec2, fullSpec3)));

    JobSpec update1 = new JobSpec("1", "g1", "q1", 2, 2, 2, 2, 2, 2, "abc", null, true, false);
    JobSpec update2 = new JobSpec("2", "g2", "q2", 3, 3, 3, 3, 3, 3, "def", "ghi", true, false);

    subject.init();
    subject.updateJobs(Arrays.asList(update1, update2));

    assertEquals(update1, subject.getJobSpec("1"));
    assertEquals(update2, subject.getJobSpec("2"));
    assertEquals(fullSpec3.getJobSpec(), subject.getJobSpec("3"));
  }

  @Test
  public void updateJobRunningState_writesToDatabase() {
    JobDatabase    database = fixedDataDatabase(DataSet1.FULL_SPECS);
    FastJobStorage subject  = new FastJobStorage(database);

    subject.init();
    subject.updateJobRunningState("id1", true);

    verify(database).updateJobRunningState("id1", true);
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
    JobDatabase    database = fixedDataDatabase(DataSet1.FULL_SPECS);
    FastJobStorage subject  = new FastJobStorage(database);

    subject.init();
    subject.updateJobAfterRetry("id1", true, 1, 10, "a");

    verify(database).updateJobAfterRetry("id1", true, 1, 10, "a");
  }

  @Test
  public void updateJobAfterRetry_memoryOnly_doesNotWriteToDatabase() {
    JobDatabase    database = fixedDataDatabase(DataSetMemory.FULL_SPECS);
    FastJobStorage subject  = new FastJobStorage(database);

    subject.init();
    subject.updateJobAfterRetry("id1", true, 1, 10, "a");

    verify(database, times(0)).updateJobAfterRetry("id1", true, 1, 10, "a");
  }

  @Test
  public void updateJobAfterRetry_stateUpdated() {
    FullSpec fullSpec = new FullSpec(new JobSpec("1", "f1", null, 0, 0, 0, 3, 30000, -1, EMPTY_DATA, null, true, false),
                                     Collections.emptyList(),
                                     Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Collections.singletonList(fullSpec)));

    subject.init();
    subject.updateJobAfterRetry("1", false, 1, 10, "a");

    JobSpec job = subject.getJobSpec("1");

    assertNotNull(job);
    assertFalse(job.isRunning());
    assertEquals(1, job.getRunAttempt());
    assertEquals(10, job.getNextRunAttemptTime());
    assertEquals("a", job.getSerializedData());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_noneWhenEarlierItemInQueueInRunning() {
    FullSpec fullSpec1 = new FullSpec(new JobSpec("1", "f1", "q", 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, true, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());
    FullSpec fullSpec2 = new FullSpec(new JobSpec("2", "f2", "q", 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(fullSpec1, fullSpec2)));
    subject.init();

    assertEquals(0, subject.getPendingJobsWithNoDependenciesInCreatedOrder(1).size());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_noneWhenAllJobsAreRunning() {
    FullSpec fullSpec = new FullSpec(new JobSpec("1", "f1", "q", 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, true, false),
                                     Collections.emptyList(),
                                     Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Collections.singletonList(fullSpec)));
    subject.init();

    assertEquals(0, subject.getPendingJobsWithNoDependenciesInCreatedOrder(10).size());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_noneWhenNextRunTimeIsAfterCurrentTime() {
    FullSpec fullSpec = new FullSpec(new JobSpec("1", "f1", "q", 0, 10, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
                                     Collections.emptyList(),
                                     Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Collections.singletonList(fullSpec)));
    subject.init();

    assertEquals(0, subject.getPendingJobsWithNoDependenciesInCreatedOrder(0).size());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_noneWhenDependentOnAnotherJob() {
    FullSpec fullSpec1 = new FullSpec(new JobSpec("1", "f1", null, 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, true, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());
    FullSpec fullSpec2 = new FullSpec(new JobSpec("2", "f2", null, 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
                                      Collections.emptyList(),
                                      Collections.singletonList(new DependencySpec("2", "1", false)));


    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(fullSpec1, fullSpec2)));
    subject.init();

    assertEquals(0, subject.getPendingJobsWithNoDependenciesInCreatedOrder(0).size());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_singleEligibleJob() {
    FullSpec fullSpec = new FullSpec(new JobSpec("1", "f1", "q", 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
                                     Collections.emptyList(),
                                     Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Collections.singletonList(fullSpec)));
    subject.init();

    assertEquals(1, subject.getPendingJobsWithNoDependenciesInCreatedOrder(10).size());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_multipleEligibleJobs() {
    FullSpec fullSpec1 = new FullSpec(new JobSpec("1", "f1", null, 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());
    FullSpec fullSpec2 = new FullSpec(new JobSpec("2", "f2", null, 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());


    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(fullSpec1, fullSpec2)));
    subject.init();

    assertEquals(2, subject.getPendingJobsWithNoDependenciesInCreatedOrder(10).size());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_singleEligibleJobInMixedList() {
    FullSpec fullSpec1 = new FullSpec(new JobSpec("1", "f1", null, 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, true, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());
    FullSpec fullSpec2 = new FullSpec(new JobSpec("2", "f2", null, 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
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
    FullSpec fullSpec1 = new FullSpec(new JobSpec("1", "f1", "q", 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());
    FullSpec fullSpec2 = new FullSpec(new JobSpec("2", "f2", "q", 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
                                      Collections.emptyList(),
                                      Collections.emptyList());


    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(fullSpec1, fullSpec2)));
    subject.init();

    List<JobSpec> jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10);

    assertEquals(1, jobs.size());
    assertEquals("1", jobs.get(0).getId());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_migrationJobTakesPrecedence() {
    FullSpec plainSpec     = new FullSpec(new JobSpec("1", "f1", "q", 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
                                          Collections.emptyList(),
                                          Collections.emptyList());
    FullSpec migrationSpec = new FullSpec(new JobSpec("2", "f2", Job.Parameters.MIGRATION_QUEUE_KEY, 5, 0, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
                                          Collections.emptyList(),
                                          Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(plainSpec, migrationSpec)));
    subject.init();

    List<JobSpec> jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10);

    assertEquals(1, jobs.size());
    assertEquals("2", jobs.get(0).getId());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_runningMigrationBlocksNormalJobs() {
    FullSpec plainSpec     = new FullSpec(new JobSpec("1", "f1", "q", 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
                                          Collections.emptyList(),
                                          Collections.emptyList());
    FullSpec migrationSpec = new FullSpec(new JobSpec("2", "f2", Job.Parameters.MIGRATION_QUEUE_KEY, 5, 0, 0, 0, 0, -1, EMPTY_DATA, null, true, false),
                                          Collections.emptyList(),
                                          Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(plainSpec, migrationSpec)));
    subject.init();

    List<JobSpec> jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10);

    assertEquals(0, jobs.size());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_runningMigrationBlocksLaterMigrationJobs() {
    FullSpec migrationSpec1 = new FullSpec(new JobSpec("1", "f1", Job.Parameters.MIGRATION_QUEUE_KEY, 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, true, false),
                                           Collections.emptyList(),
                                           Collections.emptyList());
    FullSpec migrationSpec2 = new FullSpec(new JobSpec("2", "f2", Job.Parameters.MIGRATION_QUEUE_KEY, 5, 0, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
                                           Collections.emptyList(),
                                           Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(migrationSpec1, migrationSpec2)));
    subject.init();

    List<JobSpec> jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10);

    assertEquals(0, jobs.size());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_onlyReturnFirstEligibleMigrationJob() {
    FullSpec migrationSpec1 = new FullSpec(new JobSpec("1", "f1", Job.Parameters.MIGRATION_QUEUE_KEY, 0, 0, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
                                           Collections.emptyList(),
                                           Collections.emptyList());
    FullSpec migrationSpec2 = new FullSpec(new JobSpec("2", "f2", Job.Parameters.MIGRATION_QUEUE_KEY, 5, 0, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
                                           Collections.emptyList(),
                                           Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(migrationSpec1, migrationSpec2)));
    subject.init();

    List<JobSpec> jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10);

    assertEquals(1, jobs.size());
    assertEquals("1", jobs.get(0).getId());
  }

  @Test
  public void getPendingJobsWithNoDependenciesInCreatedOrder_onlyMigrationJobWithAppropriateNextRunTime() {
    FullSpec migrationSpec1 = new FullSpec(new JobSpec("1", "f1", Job.Parameters.MIGRATION_QUEUE_KEY, 0, 999, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
        Collections.emptyList(),
        Collections.emptyList());
    FullSpec migrationSpec2 = new FullSpec(new JobSpec("2", "f2", Job.Parameters.MIGRATION_QUEUE_KEY, 5, 0, 0, 0, 0, -1, EMPTY_DATA, null, false, false),
        Collections.emptyList(),
        Collections.emptyList());

    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(Arrays.asList(migrationSpec1, migrationSpec2)));
    subject.init();

    List<JobSpec> jobs = subject.getPendingJobsWithNoDependenciesInCreatedOrder(10);

    assertTrue(jobs.isEmpty());
  }

  @Test
  public void deleteJobs_writesToDatabase() {
    JobDatabase    database = fixedDataDatabase(DataSet1.FULL_SPECS);
    FastJobStorage subject  = new FastJobStorage(database);
    List<String>   ids      = Arrays.asList("id1", "id2");

    subject.init();
    subject.deleteJobs(ids);

    verify(database).deleteJobs(ids);
  }

  @Test
  public void deleteJobs_memoryOnly_doesNotWriteToDatabase() {
    JobDatabase    database = fixedDataDatabase(DataSetMemory.FULL_SPECS);
    FastJobStorage subject  = new FastJobStorage(database);
    List<String>   ids      = Collections.singletonList("id1");

    subject.init();
    subject.deleteJobs(ids);

    verify(database, times(0)).deleteJobs(ids);
  }

  @Test
  public void deleteJobs_deletesAllRelevantPieces() {
    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS));

    subject.init();
    subject.deleteJobs(Collections.singletonList("id1"));

    List<JobSpec>        jobs         = subject.getAllJobSpecs();
    List<ConstraintSpec> constraints  = subject.getAllConstraintSpecs();
    List<DependencySpec> dependencies = subject.getAllDependencySpecs();

    assertEquals(2, jobs.size());
    assertEquals(DataSet1.JOB_2, jobs.get(0));
    assertEquals(DataSet1.JOB_3, jobs.get(1));
    assertEquals(1, constraints.size());
    assertEquals(DataSet1.CONSTRAINT_2, constraints.get(0));
    assertEquals(1, dependencies.size());
  }

  @Test
  public void getDependencySpecsThatDependOnJob_startOfChain() {
    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS));

    subject.init();

    List<DependencySpec> result = subject.getDependencySpecsThatDependOnJob("id1");

    assertEquals(2, result.size());
    assertEquals(DataSet1.DEPENDENCY_2, result.get(0));
    assertEquals(DataSet1.DEPENDENCY_3, result.get(1));
  }

  @Test
  public void getDependencySpecsThatDependOnJob_midChain() {
    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS));

    subject.init();

    List<DependencySpec> result = subject.getDependencySpecsThatDependOnJob("id2");

    assertEquals(1, result.size());
    assertEquals(DataSet1.DEPENDENCY_3, result.get(0));
  }

  @Test
  public void getDependencySpecsThatDependOnJob_endOfChain() {
    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS));

    subject.init();

    List<DependencySpec> result = subject.getDependencySpecsThatDependOnJob("id3");

    assertTrue(result.isEmpty());
  }

  @Test
  public void getJobsInQueue_empty() {
    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS));

    subject.init();

    List<JobSpec> result = subject.getJobsInQueue("x");

    assertTrue(result.isEmpty());
  }

  @Test
  public void getJobsInQueue_singleJob() {
    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS));

    subject.init();

    List<JobSpec> result = subject.getJobsInQueue("q1");

    assertEquals(1, result.size());
    assertEquals("id1", result.get(0).getId());
  }

  @Test
  public void getJobCountForFactory_general() {
    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS));

    subject.init();

    assertEquals(1, subject.getJobCountForFactory("f1"));
    assertEquals(0, subject.getJobCountForFactory("does-not-exist"));
  }

  @Test
  public void getJobCountForQueue_general() {
    FastJobStorage subject = new FastJobStorage(fixedDataDatabase(DataSet1.FULL_SPECS));

    subject.init();

    assertEquals(1, subject.getJobCountForQueue("q1"));
    assertEquals(0, subject.getJobCountForQueue("does-not-exist"));
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
    static final JobSpec        JOB_1        = new JobSpec("id1", "f1", "q1", 1, 2, 3, 4, 5, 6, EMPTY_DATA, null, false, false);
    static final JobSpec        JOB_2        = new JobSpec("id2", "f2", "q2", 1, 2, 3, 4, 5, 6, EMPTY_DATA, null, false, false);
    static final JobSpec        JOB_3        = new JobSpec("id3", "f3", "q3", 1, 2, 3, 4, 5, 6, EMPTY_DATA, null, false, false);
    static final ConstraintSpec CONSTRAINT_1 = new ConstraintSpec("id1", "f1", false);
    static final ConstraintSpec CONSTRAINT_2 = new ConstraintSpec("id2", "f2", false);
    static final DependencySpec DEPENDENCY_2 = new DependencySpec("id2", "id1", false);
    static final DependencySpec DEPENDENCY_3 = new DependencySpec("id3", "id2", false);
    static final FullSpec       FULL_SPEC_1  = new FullSpec(JOB_1, Collections.singletonList(CONSTRAINT_1), Collections.emptyList());
    static final FullSpec       FULL_SPEC_2  = new FullSpec(JOB_2, Collections.singletonList(CONSTRAINT_2), Collections.singletonList(DEPENDENCY_2));
    static final FullSpec       FULL_SPEC_3  = new FullSpec(JOB_3, Collections.emptyList(), Collections.singletonList(DEPENDENCY_3));
    static final List<FullSpec> FULL_SPECS   = Arrays.asList(FULL_SPEC_1, FULL_SPEC_2, FULL_SPEC_3);

    static void assertJobsMatch(@NonNull List<JobSpec> jobs) {
      assertEquals(jobs.size(), 3);
      assertTrue(jobs.contains(DataSet1.JOB_1));
      assertTrue(jobs.contains(DataSet1.JOB_1));
      assertTrue(jobs.contains(DataSet1.JOB_3));
    }

    static void assertConstraintsMatch(@NonNull List<ConstraintSpec> constraints) {
      assertEquals(constraints.size(), 2);
      assertTrue(constraints.contains(DataSet1.CONSTRAINT_1));
      assertTrue(constraints.contains(DataSet1.CONSTRAINT_2));
    }

    static void assertDependenciesMatch(@NonNull List<DependencySpec> dependencies) {
      assertEquals(dependencies.size(), 2);
      assertTrue(dependencies.contains(DataSet1.DEPENDENCY_2));
      assertTrue(dependencies.contains(DataSet1.DEPENDENCY_3));
    }
  }

  private static final class DataSetMemory {
    static final JobSpec        JOB_1        = new JobSpec("id1", "f1", "q1", 1, 2, 3, 4, 5, 6, EMPTY_DATA, null, false, true);
    static final ConstraintSpec CONSTRAINT_1 = new ConstraintSpec("id1", "f1", true);
    static final FullSpec       FULL_SPEC_1  = new FullSpec(JOB_1, Collections.singletonList(CONSTRAINT_1), Collections.emptyList());
    static final List<FullSpec> FULL_SPECS   = Collections.singletonList(FULL_SPEC_1);
  }
}
