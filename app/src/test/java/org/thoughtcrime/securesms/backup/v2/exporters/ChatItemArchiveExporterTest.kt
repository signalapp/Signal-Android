/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.exporters

import android.app.Application
import android.database.Cursor
import android.database.MatrixCursor
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.backup.v2.ExportState
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.RecipientId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChatItemArchiveExporterTest {

  @Test
  fun `all records exported exactly once when memory limit ends every batch`() {
    val rows = (1L..1000L).map { FakeRow(id = it, dateReceived = it, bodySize = LARGE_BODY) }
    val table = FakeMessageTable(rows)

    val exported = table.exporter(batchSize = 1000).drain()

    assertThat(exported).isEqualTo(rows.map { it.id })
    assertThat(table.requestedCounts.size).isGreaterThan(1)
  }

  @Test
  fun `all records exported exactly once when row limit ends every batch`() {
    val rows = (1L..1000L).map { FakeRow(id = it, dateReceived = it, bodySize = SMALL_BODY) }
    val table = FakeMessageTable(rows)

    val exported = table.exporter(batchSize = 100).drain()

    assertThat(exported).isEqualTo(rows.map { it.id })
  }

  @Test(timeout = 30_000)
  fun `all records exported exactly once when every record shares a dateReceived`() {
    val rows = (1L..500L).map { FakeRow(id = it, dateReceived = 1000L, bodySize = LARGE_BODY) }
    val table = FakeMessageTable(rows)

    val exported = table.exporter(batchSize = 1000).drain()

    assertThat(exported).isEqualTo(rows.map { it.id })
  }

  @Test(timeout = 30_000)
  fun `all records exported exactly once when large groups of records share a dateReceived`() {
    val rows = (1L..1000L).map { FakeRow(id = it, dateReceived = it / 300L, bodySize = LARGE_BODY) }
    val table = FakeMessageTable(rows)

    val exported = table.exporter(batchSize = 1000).drain()

    assertThat(exported).isEqualTo(rows.map { it.id })
  }

  @Test
  fun `asks for fewer rows after memory pressure and more once records shrink`() {
    val large = (1L..600L).map { FakeRow(id = it, dateReceived = it, bodySize = LARGE_BODY) }
    val small = (601L..3000L).map { FakeRow(id = it, dateReceived = it, bodySize = SMALL_BODY) }
    val table = FakeMessageTable(large + small)

    val batches = table.exporter(batchSize = BATCH_SIZE).drainBatches()

    assertThat(table.requestedCounts.first()).isEqualTo(BATCH_SIZE)
    assertThat(table.requestedCounts.min()).isLessThan(BATCH_SIZE)
    assertThat(table.requestedCounts.last()).isGreaterThan(table.requestedCounts.min())
    assertThat(table.requestedCounts.min()).isGreaterThan(MIN_ROW_LIMIT - 1)

    assertThat(batches.first().size).isLessThan(batches.last().size)
  }

  @Test
  fun `no batch exceeds the memory limit by more than one record`() {
    val rows = (1L..1000L).map { FakeRow(id = it, dateReceived = it, bodySize = LARGE_BODY) }
    val exporter = FakeMessageTable(rows).exporter(batchSize = 1000)

    var batch = exporter.records
    while (batch.isNotEmpty()) {
      val batchSize = batch.values.sumOf { it.estimatedSizeInBytes }
      val largestRecord = batch.values.maxOf { it.estimatedSizeInBytes }
      assertThat(batchSize).isLessThan(MAX_MEMORY + largestRecord)
      batch = exporter.readNextMessageRecordBatch()
    }
  }

  @Test
  fun `empty table produces no records`() {
    val exporter = FakeMessageTable(emptyList()).exporter(batchSize = 100)

    assertThat(exporter.records).isEmpty()
    assertThat(exporter.hasNext()).isEqualTo(false)
  }

  @Test(timeout = 30_000)
  fun `all records exported exactly once when the requested batch size is degenerate`() {
    val rows = (1L..500L).map { FakeRow(id = it, dateReceived = it, bodySize = SMALL_BODY) }
    val table = FakeMessageTable(rows)

    val exported = table.exporter(batchSize = 0).drain()

    assertThat(exported).isEqualTo(rows.map { it.id })
  }

  @Test
  fun `records within a batch are ordered by dateReceived`() {
    val rows = (1L..300L).map { FakeRow(id = 301L - it, dateReceived = it, bodySize = SMALL_BODY) }
    val table = FakeMessageTable(rows)

    val exported = table.exporter(batchSize = 100).drain()

    assertThat(exported).hasSize(300)
    assertThat(exported).isEqualTo(rows.sortedBy { it.dateReceived }.map { it.id })
  }

  private fun ChatItemArchiveExporter.drain(): List<Long> {
    return drainBatches().flatten()
  }

  private fun ChatItemArchiveExporter.drainBatches(): List<List<Long>> {
    val batches = mutableListOf<List<Long>>()
    var batch = records
    var reads = 0

    while (batch.isNotEmpty()) {
      batches += batch.keys.toList()
      check(++reads < MAX_READS) { "Read $reads batches without exhausting the data. Export is not making progress." }
      batch = readNextMessageRecordBatch()
    }

    return batches
  }

  private data class FakeRow(val id: Long, val dateReceived: Long, val bodySize: Int)

  /**
   * Stands in for the real export query, which returns rows with `date_received >= lastSeenReceivedTime` ordered by
   * `date_received` ascending, capped at the requested count.
   */
  private class FakeMessageTable(private val rows: List<FakeRow>) {
    val requestedCounts = mutableListOf<Int>()

    fun exporter(batchSize: Int): ChatItemArchiveExporter {
      return ChatItemArchiveExporter(
        db = mockk<SignalDatabase>(relaxed = true),
        selfRecipientId = RecipientId.from(1),
        noteToSelfThreadId = 1,
        backupStartTime = 0,
        batchSize = batchSize,
        exportState = mockk<ExportState>(relaxed = true),
        cursorGenerator = ::query,
        maxBufferMemorySize = MAX_MEMORY
      )
    }

    private fun query(lastSeenReceivedTime: Long, count: Int): Cursor {
      requestedCounts += count

      val cursor = MatrixCursor(COLUMNS)
      rows
        .filter { it.dateReceived >= lastSeenReceivedTime }
        .sortedBy { it.dateReceived }
        .take(count)
        .forEach { row ->
          cursor.newRow()
            .add(MessageTable.ID, row.id)
            .add(MessageTable.DATE_RECEIVED, row.dateReceived)
            .add(MessageTable.DATE_SENT, row.dateReceived)
            .add(MessageTable.BODY, "b".repeat(row.bodySize))
        }

      return cursor
    }
  }

  companion object {
    private const val MAX_MEMORY = 200_000
    private const val BATCH_SIZE = 1000
    private const val LARGE_BODY = 1000
    private const val SMALL_BODY = 10
    private const val MAX_READS = 10_000

    /** Mirrors ChatItemArchiveExporter.MIN_ROW_LIMIT, which is private. */
    private const val MIN_ROW_LIMIT = 100

    private val COLUMNS = arrayOf(
      MessageTable.ID,
      MessageTable.DATE_SENT,
      MessageTable.DATE_RECEIVED,
      MessageTable.DATE_SERVER,
      MessageTable.TYPE,
      MessageTable.THREAD_ID,
      MessageTable.BODY,
      MessageTable.MESSAGE_RANGES,
      MessageTable.FROM_RECIPIENT_ID,
      MessageTable.TO_RECIPIENT_ID,
      MessageTable.EXPIRES_IN,
      MessageTable.EXPIRE_STARTED,
      MessageTable.UNIDENTIFIED,
      MessageTable.LINK_PREVIEWS,
      MessageTable.SHARED_CONTACTS,
      MessageTable.QUOTE_ID,
      MessageTable.QUOTE_AUTHOR,
      MessageTable.QUOTE_BODY,
      MessageTable.QUOTE_MISSING,
      MessageTable.QUOTE_BODY_RANGES,
      MessageTable.QUOTE_TYPE,
      MessageTable.ORIGINAL_MESSAGE_ID,
      MessageTable.LATEST_REVISION_ID,
      MessageTable.HAS_DELIVERY_RECEIPT,
      MessageTable.VIEWED_COLUMN,
      MessageTable.HAS_READ_RECEIPT,
      MessageTable.READ,
      MessageTable.RECEIPT_TIMESTAMP,
      MessageTable.NETWORK_FAILURES,
      MessageTable.MISMATCHED_IDENTITIES,
      MessageTable.MESSAGE_EXTRAS,
      MessageTable.VIEW_ONCE,
      MessageTable.PARENT_STORY_ID,
      MessageTable.PINNED_AT,
      MessageTable.PINNED_UNTIL,
      MessageTable.DELETED_BY
    )
  }
}
