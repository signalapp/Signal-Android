/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.LogDatabase.IssueTable
import org.thoughtcrime.securesms.database.model.IssueEntry
import org.thoughtcrime.securesms.database.model.IssuePriority
import org.thoughtcrime.securesms.testing.JdbcSqliteDatabase
import kotlin.time.Duration.Companion.days

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class IssueTableTest {

  private lateinit var db: JdbcSqliteDatabase
  private lateinit var issues: IssueTable

  @Before
  fun setUp() {
    db = JdbcSqliteDatabase.createInMemory()
    db.execSQL(IssueTable.CREATE_TABLE)
    IssueTable.CREATE_INDEXES.forEach { db.execSQL(it) }
    issues = IssueTable({ db }, { db })
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `insert then getRecent returns issues newest first`() {
    val now = System.currentTimeMillis()

    issues.insert(
      sequenceOf(
        issueEntry(createdAt = now - 1, name = "Slow Database Write", priority = IssuePriority.MEDIUM, duration = 1500),
        issueEntry(createdAt = now, name = "Slow Database Read", stackTrace = null, priority = IssuePriority.LOW, duration = null)
      ),
      now
    )

    val recent = issues.getRecent()

    assertThat(recent.size).isEqualTo(2)
    assertThat(recent[0].name).isEqualTo("Slow Database Read")
    assertThat(recent[0].priority).isEqualTo(IssuePriority.LOW)
    assertThat(recent[0].stackTrace).isNull()
    assertThat(recent[0].duration).isNull()
    assertThat(recent[1].name).isEqualTo("Slow Database Write")
    assertThat(recent[1].priority).isEqualTo(IssuePriority.MEDIUM)
    assertThat(recent[1].duration).isEqualTo(1500L)
  }

  @Test
  fun `getSummary groups by name with max priority and latest version`() {
    val now = System.currentTimeMillis()

    issues.insert(
      sequenceOf(
        issueEntry(createdAt = now - 10, version = "1.0", name = "Slow Database Read", priority = IssuePriority.LOW, duration = 1000),
        issueEntry(createdAt = now - 5, version = "1.1", name = "Slow Database Read", priority = IssuePriority.MEDIUM, duration = 3000),
        issueEntry(createdAt = now, version = "1.2", name = "Slow Database Write", priority = IssuePriority.MEDIUM, duration = null)
      ),
      now
    )

    val summary = issues.getSummary()

    assertThat(summary.size).isEqualTo(2)

    val reads = summary.first { it.name == "Slow Database Read" }
    assertThat(reads.count).isEqualTo(2)
    assertThat(reads.maxPriority).isEqualTo(IssuePriority.MEDIUM)
    assertThat(reads.firstSeen).isEqualTo(now - 10)
    assertThat(reads.lastSeen).isEqualTo(now - 5)
    assertThat(reads.lastVersion).isEqualTo("1.1")
    assertThat(reads.averageDuration).isEqualTo(2000L)

    val writes = summary.first { it.name == "Slow Database Write" }
    assertThat(writes.count).isEqualTo(1)
    assertThat(writes.maxPriority).isEqualTo(IssuePriority.MEDIUM)
    assertThat(writes.averageDuration).isNull()
  }

  @Test
  fun `insert trims issues older than the max lifespan`() {
    val now = System.currentTimeMillis()
    val old = now - 31.days.inWholeMilliseconds

    issues.insert(sequenceOf(issueEntry(createdAt = old, name = "Old")), now)
    issues.insert(sequenceOf(issueEntry(createdAt = now, name = "New")), now)

    val recent = issues.getRecent()

    assertThat(recent.size).isEqualTo(1)
    assertThat(recent[0].name).isEqualTo("New")
  }

  private fun issueEntry(
    createdAt: Long,
    version: String = "1.0",
    name: String = "Test Issue",
    description: String = "description",
    stackTrace: String? = "stack\ntrace",
    priority: IssuePriority = IssuePriority.LOW,
    duration: Long? = null
  ): IssueEntry {
    return IssueEntry(
      createdAt = createdAt,
      version = version,
      name = name,
      description = description,
      stackTrace = stackTrace,
      priority = priority,
      duration = duration
    )
  }
}
