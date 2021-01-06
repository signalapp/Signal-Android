package org.thoughtcrime.securesms.jobmanager;

import androidx.annotation.NonNull;

import org.junit.BeforeClass;
import org.junit.Test;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.JobStorage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JobMigratorTest {

  @BeforeClass
  public static void init() {
    Log.initialize(mock(Log.Logger.class));
  }

  @Test(expected = AssertionError.class)
  public void JobMigrator_crashWhenTooFewMigrations() {
    new JobMigrator(1, 2, Collections.emptyList());
  }

  @Test(expected = AssertionError.class)
  public void JobMigrator_crashWhenTooManyMigrations() {
    new JobMigrator(1, 2, Arrays.asList(new EmptyMigration(2), new EmptyMigration(3)));
  }

  @Test(expected = AssertionError.class)
  public void JobMigrator_crashWhenSkippingMigrations() {
    new JobMigrator(1, 3, Arrays.asList(new EmptyMigration(2), new EmptyMigration(4)));
  }

  @Test
  public void JobMigrator_properInitialization() {
    new JobMigrator(1, 3, Arrays.asList(new EmptyMigration(2), new EmptyMigration(3)));
  }

  @Test
  public void migrate_callsAppropriateMigrations_fullSet() {
    JobMigration migration1 = spy(new EmptyMigration(2));
    JobMigration migration2 = spy(new EmptyMigration(3));

    JobMigrator subject = new JobMigrator(1, 3, Arrays.asList(migration1, migration2));
    int         version = subject.migrate(simpleJobStorage(), mock(Data.Serializer.class));

    assertEquals(3, version);
    verify(migration1).migrate(any());
    verify(migration2).migrate(any());
  }

  @Test
  public void migrate_callsAppropriateMigrations_subset() {
    JobMigration migration1 = spy(new EmptyMigration(2));
    JobMigration migration2 = spy(new EmptyMigration(3));

    JobMigrator subject = new JobMigrator(2, 3, Arrays.asList(migration1, migration2));
    int         version = subject.migrate(simpleJobStorage(), mock(Data.Serializer.class));

    assertEquals(3, version);
    verify(migration1, never()).migrate(any());
    verify(migration2).migrate(any());
  }

  @Test
  public void migrate_callsAppropriateMigrations_none() {
    JobMigration migration1 = spy(new EmptyMigration(2));
    JobMigration migration2 = spy(new EmptyMigration(3));

    JobMigrator subject = new JobMigrator(3, 3, Arrays.asList(migration1, migration2));
    int         version = subject.migrate(simpleJobStorage(), mock(Data.Serializer.class));

    assertEquals(3, version);
    verify(migration1, never()).migrate(any());
    verify(migration2, never()).migrate(any());
  }

  private static JobStorage simpleJobStorage() {
    JobStorage jobStorage = mock(JobStorage.class);
    when(jobStorage.getAllJobSpecs()).thenReturn(new ArrayList<>(Collections.singletonList(new JobSpec("1", "f1", null, 1, 1, 1, 1, 1, 1, "", null, false, false))));
    return jobStorage;
  }

  private static class EmptyMigration extends JobMigration {

    protected EmptyMigration(int endVersion) {
      super(endVersion);
    }

    @Override
    protected @NonNull JobData migrate(@NonNull JobData jobData) {
      return jobData;
    }
  }
}
