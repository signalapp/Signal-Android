package org.thoughtcrime.securesms.jobmanager

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.util.logging.Log.initialize
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec
import org.thoughtcrime.securesms.jobmanager.persistence.JobStorage
import org.thoughtcrime.securesms.testutil.EmptyLogger

class JobMigratorTest {
  @Test
  fun test_JobMigrator_crashWhenTooFewMigrations() {
    val error = assertThrows(AssertionError::class.java) {
      JobMigrator(1, 2, emptyList())
    }
    assertEquals("You must have a migration for every version!", error.message)
  }

  @Test
  fun test_JobMigrator_crashWhenTooManyMigrations() {
    val error = assertThrows(AssertionError::class.java) {
      JobMigrator(1, 2, listOf<JobMigration>(EmptyMigration(2), EmptyMigration(3)))
    }
    assertEquals("You must have a migration for every version!", error.message)
  }

  @Test
  fun test_JobMigrator_crashWhenSkippingMigrations() {
    val error = assertThrows(AssertionError::class.java) {
      JobMigrator(1, 3, listOf<JobMigration>(EmptyMigration(2), EmptyMigration(4)))
    }
    assertEquals("Missing migration for version 3!", error.message)
  }

  @Test
  fun test_JobMigrator_properInitialization() {
    JobMigrator(1, 3, listOf<JobMigration>(EmptyMigration(2), EmptyMigration(3)))
  }

  @Test
  fun migrate_callsAppropriateMigrations_fullSet() {
    val migration1 = EmptyMigration(2)
    val migration2 = EmptyMigration(3)

    val subject = JobMigrator(1, 3, listOf(migration1, migration2))
    val version = subject.migrate(simpleJobStorage())

    assertEquals(3, version)
    assertTrue(migration1.migrated)
    assertTrue(migration2.migrated)
  }

  @Test
  fun migrate_callsAppropriateMigrations_subset() {
    val migration1 = EmptyMigration(2)
    val migration2 = EmptyMigration(3)

    val subject = JobMigrator(2, 3, listOf(migration1, migration2))
    val version = subject.migrate(simpleJobStorage())

    assertEquals(3, version)
    assertFalse(migration1.migrated)
    assertTrue(migration2.migrated)
  }

  @Test
  fun migrate_callsAppropriateMigrations_none() {
    val migration1 = EmptyMigration(2)
    val migration2 = EmptyMigration(3)

    val subject = JobMigrator(3, 3, listOf(migration1, migration2))
    val version = subject.migrate(simpleJobStorage())

    assertEquals(3, version)
    assertFalse(migration1.migrated)
    assertFalse(migration2.migrated)
  }

  private class EmptyMigration(endVersion: Int) : JobMigration(endVersion) {
    private var _migrated: Boolean = false
    val migrated: Boolean get() = _migrated

    override fun migrate(jobData: JobData): JobData {
      _migrated = true
      return jobData
    }
  }

  companion object {
    @JvmStatic
    @BeforeClass
    fun init() {
      initialize(EmptyLogger())
    }

    private fun simpleJobStorage(): JobStorage {
      val job = JobSpec(
        id = "1",
        factoryKey = "f1",
        queueKey = null,
        createTime = 1,
        lastRunAttemptTime = 1,
        nextBackoffInterval = 1,
        runAttempt = 1,
        maxAttempts = 1,
        lifespan = 1,
        serializedData = null,
        serializedInputData = null,
        isRunning = false,
        isMemoryOnly = false,
        globalPriority = 0,
        queuePriority = 0,
        initialDelay = 0
      )
      return mockk<JobStorage> {
        every { debugGetJobSpecs(any()) } returns listOf(job)
        every { transformJobs(any()) } answers {
          @Suppress("UNCHECKED_CAST")
          val transformer = invocation.args.single() as Function1<JobSpec, JobSpec>
          transformer(job)
        }
      }
    }
  }
}
