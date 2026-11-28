/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.index

import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.database.RecipientTable

/**
 * Matches address book entries to linked recipients, falling back to a shared lookup key segment when a
 * stored key has gone stale. Each segment names one raw contact, which belongs to one aggregate at a time.
 *
 * A stale key whose raw contacts were split across several current contacts is claimed by each of them,
 * so a fallback match is only trusted when one contact claims it or the contact carries the recipient's
 * number.
 */
class SystemContactLinks(
  links: List<RecipientTable.SystemContactLink>,
  currentLookupKeys: Collection<String>
) {

  private val byLookupKey: Map<String, List<RecipientTable.SystemContactLink>> = links.groupBy { it.lookupKey }

  private val bySegment: Map<String, List<RecipientTable.SystemContactLink>>

  private val fallbackClaims: Map<RecipientId, Int>

  init {
    val currentKeys = currentLookupKeys.toHashSet()

    bySegment = links
      .filterNot { it.lookupKey in currentKeys }
      .flatMap { link -> segmentsOf(link.lookupKey).map { it to link } }
      .groupBy({ it.first }, { it.second })

    fallbackClaims = currentKeys
      .asSequence()
      .filterNot { it in byLookupKey }
      .flatMap { fallbackCandidates(it) }
      .groupingBy { it.recipientId }
      .eachCount()
  }

  fun find(lookupKey: String, e164s: () -> Set<String>): List<RecipientTable.SystemContactLink> {
    byLookupKey[lookupKey]?.let { return it }

    val candidates = fallbackCandidates(lookupKey)
    if (candidates.isEmpty()) {
      return candidates
    }

    val numbers by lazy(e164s)

    return candidates.filter { link ->
      (fallbackClaims[link.recipientId] ?: 0) <= 1 || (link.e164 != null && link.e164 in numbers)
    }
  }

  private fun fallbackCandidates(lookupKey: String): List<RecipientTable.SystemContactLink> {
    return segmentsOf(lookupKey)
      .flatMap { bySegment[it].orEmpty() }
      .distinctBy { it.recipientId }
  }

  companion object {
    /** The provider escapes a literal `.` in a source id as `..`, so only a lone `.` separates segments. */
    private val SEGMENT_SEPARATOR = Regex("(?<!\\.)\\.(?!\\.)")

    private fun segmentsOf(lookupKey: String): List<String> {
      val segments = if (lookupKey.contains("..")) lookupKey.split(SEGMENT_SEPARATOR) else lookupKey.split('.')
      return segments.filter { it.isNotEmpty() }
    }
  }
}
