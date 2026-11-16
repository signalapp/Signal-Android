/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.index

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * The single naming, sorting, and sectioning rule for the merged contact list.
 *
 * Both the system address book and the recipient table go through here, so that one comparator
 * orders the whole list rather than each source arriving pre-sorted by a comparator we cannot
 * reconcile with the other's.
 *
 * The precedence deliberately matches [org.thoughtcrime.securesms.recipients.Recipient]'s
 * `getNameFromLocalData`, so a contact cannot appear under one name here and a different one
 * everywhere else in the app. It is reimplemented rather than reused because building the index
 * must not resolve a `Recipient` per row, which would populate the recipient cache with an entry
 * for the entire address book.
 */
object ContactDisplayName {

  /** Section for business entries, unnamed entries, and names that do not start with a letter. */
  const val SECTION_OTHER = "#"

  /** Escape character for the LIKE patterns built here, so a typed % or _ is not a wildcard. */
  const val LIKE_ESCAPE = "\\"

  private val COMBINING_MARKS = Regex("\\p{Mn}+")

  private val NUMERIC_QUERY = Regex("[+\\-() 0-9]+")

  /**
   * A name for a recipient that has no address book entry.
   */
  fun forSignalContact(
    nickname: String?,
    systemName: String?,
    profileName: String?,
    username: String?,
    e164: String?,
    email: String?
  ): String? {
    return firstNotBlank(nickname, systemName, profileName, username, e164, email)
  }

  /**
   * A name for an address book entry, which may or may not also be a Signal contact.
   *
   * The provider's own display name wins over the recipient table's cached copy of it. It is
   * fresher, and it already falls back to the company name for business entries.
   */
  fun forSystemContact(providerDisplayName: String?, nickname: String?): String? {
    return firstNotBlank(nickname, providerDisplayName)
  }

  /**
   * Which A-Z section a row belongs to.
   *
   * A row whose display name stands in for a personal name, because the provider had to fall back to
   * a company, email, or phone number, goes to [SECTION_OTHER] rather than under that fallback's
   * initial. A Signal nickname overrides that, since it is a real name to sort under.
   */
  fun sectionFor(displayName: String, hasPersonalName: Boolean, hasNickname: Boolean): String {
    if (!hasPersonalName && !hasNickname) {
      return SECTION_OTHER
    }

    val first = normalize(displayName).firstOrNull { !it.isWhitespace() } ?: return SECTION_OTHER

    return if (first.isLetter()) first.uppercaseChar().toString() else SECTION_OTHER
  }

  /**
   * Normalizes a name to the form stored in the index's single searchable column. Lowercases and
   * strips diacritics, so that a query typed without accents still matches the accented name.
   */
  fun normalize(value: String): String {
    val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
    return COMBINING_MARKS.replace(decomposed, "").lowercase(Locale.getDefault())
  }

  /**
   * Builds the value for the index's single searchable column.
   *
   * Every name we know for a row is normalized into one space delimited string, deduped, and wrapped in
   * spaces. The wrapping is what lets a word prefix search be expressed as a single `LIKE '% q%'`
   * without needing a separate token table.
   */
  fun searchTextOf(vararg names: String?): String {
    val tokens = names
      .asSequence()
      .filterNotNull()
      .map { normalize(it) }
      .flatMap { it.split(' ') }
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .distinct()
      .toList()

    return if (tokens.isEmpty()) " " else tokens.joinToString(separator = " ", prefix = " ", postfix = " ")
  }

  /**
   * Turns a user's query into a LIKE pattern for the index's search column.
   *
   * Names match on word prefix. Stored search text is wrapped in spaces, so one leading space in the
   * pattern matches the start of any word including the first, without needing a token table.
   *
   * Numbers match anywhere, because nobody types a phone number starting from its country code. That
   * only finds Signal contacts, whose E164 is in the index. Address book numbers are not indexed,
   * since indexing them would mean reading the Data table.
   */
  fun searchPatternFor(query: String): String {
    val normalized = normalize(query.trim())
    val escaped = normalized
      .replace(LIKE_ESCAPE, "$LIKE_ESCAPE$LIKE_ESCAPE")
      .replace("%", "$LIKE_ESCAPE%")
      .replace("_", "${LIKE_ESCAPE}_")

    return if (normalized.isNotEmpty() && NUMERIC_QUERY.matches(normalized)) {
      "%${escaped.filter { it.isDigit() }}%"
    } else {
      "% $escaped%"
    }
  }

  private fun firstNotBlank(vararg candidates: String?): String? {
    return candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
  }
}

/**
 * Produces the sort keys the index is ordered by.
 *
 * [Collator] is not thread safe, so one of these belongs to a single index build and must not be
 * shared. `PRIMARY` strength matches the rest of the app's name ordering, and means names differing
 * only by case or accent tie. Ties are broken by display name at insert time so the resulting order
 * is deterministic.
 */
class ContactSortKeyGenerator(locale: Locale = Locale.getDefault()) {

  private val collator: Collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }

  fun of(displayName: String): ByteArray {
    return collator.getCollationKey(displayName).toByteArray()
  }
}
