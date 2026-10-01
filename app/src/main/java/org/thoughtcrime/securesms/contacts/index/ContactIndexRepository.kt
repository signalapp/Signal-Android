/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.index

import android.app.Application
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import java.io.Closeable

/**
 * Owns the lifetime of one contact index.
 *
 * Belongs to the view model of whatever shows the list rather than to the activity, so that it
 * survives the activity being recreated, and is [close]d when the screen itself goes away. Closing
 * deletes the index, and because the encryption key only ever existed in this object, anything left
 * on disk after an abrupt process death is undecryptable rather than merely deleted.
 */
class ContactIndexRepository(private val application: Application) : Closeable {

  companion object {
    private val TAG = Log.tag(ContactIndexRepository::class.java)

    /** Reclaims space from an index abandoned by a process that died before it could clean up. */
    @JvmStatic
    fun deleteAbandonedIndex(application: Application) {
      ContactIndexDatabase.deleteAbandonedFiles(application)
    }
  }

  private var database: ContactIndexDatabase? = null

  @Synchronized
  @WorkerThread
  fun build(): ContactIndexBuildResult {
    close()

    val start = System.currentTimeMillis()
    val created = ContactIndexDatabase.create(application)

    val result = try {
      ContactIndexBuilder(application).build(created)
    } catch (t: Throwable) {
      created.closeAndDelete()
      throw t
    }

    Log.i(TAG, "Index build took ${System.currentTimeMillis() - start} ms.")

    when (result) {
      is ContactIndexBuildResult.Success, is ContactIndexBuildResult.SignalOnly -> {
        database = created
      }

      else -> {
        created.closeAndDelete()
      }
    }

    return result
  }

  @Synchronized
  @WorkerThread
  fun getPage(startPosition: Long, limit: Int): List<ContactIndexRecord> {
    val db = database ?: return emptyList()

    return timed("page", startPosition, limit) { db.getPage(startPosition, limit) }
  }

  @Synchronized
  @WorkerThread
  fun search(query: String, startPosition: Long, limit: Int): List<ContactIndexRecord> {
    val db = database ?: return emptyList()

    return if (query.isBlank()) {
      timed("page", startPosition, limit) { db.getPage(startPosition, limit) }
    } else {
      timed("search(len=${query.length})", startPosition, limit) { db.search(query, startPosition, limit) }
    }
  }

  @Synchronized
  @WorkerThread
  fun count(): Int {
    return database?.count() ?: 0
  }

  private fun timed(label: String, startPosition: Long, limit: Int, query: () -> List<ContactIndexRecord>): List<ContactIndexRecord> {
    val start = System.currentTimeMillis()
    val rows = query()
    val duration = System.currentTimeMillis() - start

    Log.d(TAG, "$label from=$startPosition limit=$limit rows=${rows.size} took=${duration}ms")

    return rows
  }

  @Synchronized
  override fun close() {
    database?.let {
      it.closeAndDelete()
      Log.i(TAG, "Closed and deleted the contact index.")
    }
    database = null
  }
}
