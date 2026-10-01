/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.contacts

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract

/**
 * A contacts provider that serves fixed rows, for testing how [SystemContactsRepository] reads them.
 *
 * [dataRows] are served from [ContactsContract.Data.CONTENT_URI], sorted by the query's sort order as
 * the real provider would, so a test can list them in any order. A query by contact id keeps only
 * that contact's rows. [contacts] answer lookup URI queries, by lookup key, or by an older key given
 * under [RESOLVES_FROM], as the real provider resolves a key from before contacts were joined.
 */
class FakeContactsProvider : ContentProvider() {

  companion object {
    const val RESOLVES_FROM = "fake_resolves_from"

    var dataRows: List<Map<String, Any?>> = emptyList()
    var contacts: List<Map<String, Any?>> = emptyList()

    /** Makes queries that ask for the name source columns fail, as some providers' do. */
    var rejectNameSourceColumns: Boolean = false

    /** Lookup keys the provider refuses to parse, as the real one does malformed keys. */
    var unparseableLookupKeys: Set<String> = emptySet()

    fun reset() {
      dataRows = emptyList()
      contacts = emptyList()
      rejectNameSourceColumns = false
      unparseableLookupKeys = emptySet()
    }
  }

  override fun onCreate(): Boolean = true

  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
    val columns: Array<out String> = projection ?: emptyArray()

    return when {
      uri.toString().startsWith(ContactsContract.Data.CONTENT_URI.toString()) -> {
        if (rejectNameSourceColumns && ContactsContract.Contacts.NAME_RAW_CONTACT_ID in columns) {
          throw IllegalArgumentException("Invalid column ${ContactsContract.Contacts.NAME_RAW_CONTACT_ID}")
        }

        val rows = if (selection?.contains(ContactsContract.Data.CONTACT_ID) == true) {
          dataRows.filter { it[ContactsContract.Data.CONTACT_ID].toString() == selectionArgs!![0] }
        } else {
          dataRows
        }

        rows.sortedBy(sortOrder).toCursor(columns)
      }

      uri.pathSegments.firstOrNull() == "contacts" && uri.pathSegments.getOrNull(1) == "lookup" -> {
        val lookupKey = uri.pathSegments[2]
        if (lookupKey in unparseableLookupKeys) {
          throw IllegalArgumentException("Invalid lookup id: $lookupKey")
        }
        contacts.filter { it[ContactsContract.Contacts.LOOKUP_KEY] == lookupKey || it[RESOLVES_FROM] == lookupKey }.toCursor(columns)
      }

      else -> throw IllegalArgumentException("Unexpected uri $uri")
    }
  }

  /** Sorts rows by an SQL-style sort order, such as "lookup ASC, _id DESC". */
  @Suppress("UNCHECKED_CAST")
  private fun List<Map<String, Any?>>.sortedBy(sortOrder: String?): List<Map<String, Any?>> {
    if (sortOrder.isNullOrBlank()) {
      return this
    }

    val comparator = sortOrder.split(",")
      .map { term ->
        val (column, direction) = term.trim().split(Regex("\\s+")).let { it[0] to it.getOrElse(1) { "ASC" } }
        val ascending = Comparator<Map<String, Any?>> { a, b -> compareValues(a[column] as Comparable<Any>?, b[column] as Comparable<Any>?) }
        if (direction.equals("DESC", ignoreCase = true)) ascending.reversed() else ascending
      }
      .reduce { first, second -> first.then(second) }

    return sortedWith(comparator)
  }

  private fun List<Map<String, Any?>>.toCursor(columns: Array<out String>): Cursor {
    val cursor = MatrixCursor(columns)
    for (row in this) {
      cursor.addRow(columns.map { row[it] })
    }
    return cursor
  }

  override fun getType(uri: Uri): String? = null
  override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
