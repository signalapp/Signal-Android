/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import org.junit.Test
import org.signal.core.util.forEach
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.updateAll
import org.thoughtcrime.securesms.crash.CrashConfig
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testing.assertIs

class LogDatabaseTest {

  private val db: LogDatabase = LogDatabase.getInstance(AppDependencies.application)

  @Test
  fun crashTable_matchesNamePattern() {
    val currentTime = System.currentTimeMillis()

    db.crashes.saveCrash(
      createdAt = currentTime,
      name = "TestName",
      message = "Test Message",
      stackTrace = "test\nstack\ntrace"
    )

    val foundMatch = db.crashes.anyMatch(
      listOf(
        CrashConfig.CrashPattern(namePattern = "Test")
      ),
      promptThreshold = currentTime
    )

    foundMatch assertIs true
  }

  @Test
  fun crashTable_matchesMessagePattern() {
    val currentTime = System.currentTimeMillis()

    db.crashes.saveCrash(
      createdAt = currentTime,
      name = "TestName",
      message = "Test Message",
      stackTrace = "test\nstack\ntrace"
    )

    val foundMatch = db.crashes.anyMatch(
      listOf(
        CrashConfig.CrashPattern(messagePattern = "Message")
      ),
      promptThreshold = currentTime
    )

    foundMatch assertIs true
  }

  @Test
  fun crashTable_matchesStackTracePattern() {
    val currentTime = System.currentTimeMillis()

    db.crashes.saveCrash(
      createdAt = currentTime,
      name = "TestName",
      message = "Test Message",
      stackTrace = "test\nstack\ntrace"
    )

    val foundMatch = db.crashes.anyMatch(
      listOf(
        CrashConfig.CrashPattern(stackTracePattern = "stack")
      ),
      promptThreshold = currentTime
    )

    foundMatch assertIs true
  }

  @Test
  fun crashTable_matchesNameAndMessagePattern() {
    val currentTime = System.currentTimeMillis()

    db.crashes.saveCrash(
      createdAt = currentTime,
      name = "TestName",
      message = "Test Message",
      stackTrace = "test\nstack\ntrace"
    )

    val foundMatch = db.crashes.anyMatch(
      listOf(
        CrashConfig.CrashPattern(namePattern = "Test", messagePattern = "Message")
      ),
      promptThreshold = currentTime
    )

    foundMatch assertIs true
  }

  @Test
  fun crashTable_matchesNameAndStackTracePattern() {
    val currentTime = System.currentTimeMillis()

    db.crashes.saveCrash(
      createdAt = currentTime,
      name = "TestName",
      message = "Test Message",
      stackTrace = "test\nstack\ntrace"
    )

    val foundMatch = db.crashes.anyMatch(
      listOf(
        CrashConfig.CrashPattern(namePattern = "Test", stackTracePattern = "stack")
      ),
      promptThreshold = currentTime
    )

    foundMatch assertIs true
  }

  @Test
  fun crashTable_matchesNameAndMessageAndStackTracePattern() {
    val currentTime = System.currentTimeMillis()

    db.crashes.saveCrash(
      createdAt = currentTime,
      name = "TestName",
      message = "Test Message",
      stackTrace = "test\nstack\ntrace"
    )

    val foundMatch = db.crashes.anyMatch(
      listOf(
        CrashConfig.CrashPattern(namePattern = "Test", messagePattern = "Message", stackTracePattern = "stack")
      ),
      promptThreshold = currentTime
    )

    foundMatch assertIs true
  }

  @Test
  fun crashTable_doesNotMatchNamePattern() {
    val currentTime = System.currentTimeMillis()

    db.crashes.saveCrash(
      createdAt = currentTime,
      name = "TestName",
      message = "Test Message",
      stackTrace = "test\nstack\ntrace"
    )

    val foundMatch = db.crashes.anyMatch(
      listOf(
        CrashConfig.CrashPattern(namePattern = "Blah")
      ),
      promptThreshold = currentTime
    )

    foundMatch assertIs false
  }

  @Test
  fun crashTable_matchesNameButNotMessagePattern() {
    val currentTime = System.currentTimeMillis()

    db.crashes.saveCrash(
      createdAt = currentTime,
      name = "TestName",
      message = "Test Message",
      stackTrace = "test\nstack\ntrace"
    )

    val foundMatch = db.crashes.anyMatch(
      listOf(
        CrashConfig.CrashPattern(namePattern = "Test", messagePattern = "Blah")
      ),
      promptThreshold = currentTime
    )

    foundMatch assertIs false
  }

  @Test
  fun crashTable_matchesNameButNotStackTracePattern() {
    val currentTime = System.currentTimeMillis()

    db.crashes.saveCrash(
      createdAt = currentTime,
      name = "TestName",
      message = "Test Message",
      stackTrace = "test\nstack\ntrace"
    )

    val foundMatch = db.crashes.anyMatch(
      listOf(
        CrashConfig.CrashPattern(namePattern = "Test", stackTracePattern = "Blah")
      ),
      promptThreshold = currentTime
    )

    foundMatch assertIs false
  }

  @Test
  fun crashTable_matchesNamePatternButPromptedTooRecently() {
    val currentTime = System.currentTimeMillis()

    db.crashes.saveCrash(
      createdAt = currentTime,
      name = "TestName",
      message = "Test Message",
      stackTrace = "test\nstack\ntrace"
    )

    db.writableDatabase
      .updateAll(LogDatabase.CrashTable.TABLE_NAME)
      .values(LogDatabase.CrashTable.LAST_PROMPTED_AT to currentTime)
      .run()

    val foundMatch = db.crashes.anyMatch(
      listOf(
        CrashConfig.CrashPattern(namePattern = "Test")
      ),
      promptThreshold = currentTime - 100
    )

    foundMatch assertIs false
  }

  @Test
  fun crashTable_noMatches() {
    val currentTime = System.currentTimeMillis()

    val foundMatch = db.crashes.anyMatch(
      listOf(
        CrashConfig.CrashPattern(namePattern = "Test")
      ),
      promptThreshold = currentTime - 100
    )

    foundMatch assertIs false
  }

  @Test
  fun crashTable_updatesLastPromptTime() {
    val currentTime = System.currentTimeMillis()

    db.crashes.saveCrash(
      createdAt = currentTime,
      name = "TestName",
      message = "Test Message",
      stackTrace = "test\nstack\ntrace"
    )

    db.crashes.saveCrash(
      createdAt = currentTime,
      name = "XXX",
      message = "XXX",
      stackTrace = "XXX"
    )

    db.crashes.markAsPrompted(
      listOf(
        CrashConfig.CrashPattern(namePattern = "Test")
      ),
      promptedAt = currentTime
    )

    db.writableDatabase
      .select(LogDatabase.CrashTable.NAME, LogDatabase.CrashTable.LAST_PROMPTED_AT)
      .from(LogDatabase.CrashTable.TABLE_NAME)
      .run()
      .forEach {
        if (it.requireNonNullString(LogDatabase.CrashTable.NAME) == "TestName") {
          it.requireLong(LogDatabase.CrashTable.LAST_PROMPTED_AT) assertIs currentTime
        } else {
          it.requireLong(LogDatabase.CrashTable.LAST_PROMPTED_AT) assertIs 0
        }
      }
  }
}
