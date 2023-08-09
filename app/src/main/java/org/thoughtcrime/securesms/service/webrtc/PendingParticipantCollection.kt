/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Holds state information about what users wish to join a given call link.
 *
 * Also holds information about approvals and denials, so that this state is
 * persisted over the lifecycle of the user's time in the call.
 */
data class PendingParticipantCollection(
  private val participantMap: Map<RecipientId, Entry> = emptyMap(),
  private val nowProvider: () -> Duration = { System.currentTimeMillis().milliseconds }
) {

  /**
   * Creates a new collection with the given recipients applied to it with the following rules:
   *
   * 1. If the recipient is already in the collection, ignore it
   * 1. Otherwise, insert the recipient at the end of the collection in the pending state
   * 1. Any recipients in the resulting collection that are [State.PENDING] and NOT in the passed recipient list are removed.
   * 1. Any recipients in the resulting collection that are [State.DENIED] and have a denial count less than [MAX_DENIALS] is moved to [State.PENDING]
   */
  fun withRecipients(recipients: List<Recipient>): PendingParticipantCollection {
    val now = nowProvider()
    val newEntries = recipients.filterNot { it.id in participantMap.keys }.map {
      it.id to Entry(
        it,
        State.PENDING,
        now
      )
    }

    val submittedIdSet = recipients.map { it.id }.toSet()
    val newEntryMap = (participantMap + newEntries)
      .filterNot { it.value.state == State.PENDING && it.key !in submittedIdSet }
      .mapValues {
        if (it.value.state == State.DENIED && it.key in submittedIdSet) {
          it.value.copy(state = State.PENDING, stateChangeAt = now)
        } else {
          it.value
        }
      }

    return copy(participantMap = newEntryMap)
  }

  /**
   * Creates a new collection with the given recipient marked as [State.APPROVED].
   * Resets the denial count for that recipient.
   */
  fun withApproval(recipient: Recipient): PendingParticipantCollection {
    val now = nowProvider()
    val entry = participantMap[recipient.id] ?: return this

    return copy(
      participantMap = participantMap + (
        recipient.id to entry.copy(
          denialCount = 0,
          state = State.APPROVED,
          stateChangeAt = now
        )
        )
    )
  }

  /**
   * Creates a new collection with the given recipient marked as [State.DENIED]
   */
  fun withDenial(recipient: Recipient): PendingParticipantCollection {
    val now = nowProvider()
    val entry = participantMap[recipient.id] ?: return this

    return copy(
      participantMap = participantMap + (
        recipient.id to entry.copy(
          denialCount = entry.denialCount + 1,
          state = State.DENIED,
          stateChangeAt = now
        )
        )
    )
  }

  /**
   * Gets all of the pending participants in the [State.PENDING] state.
   */
  fun getUnresolvedPendingParticipants(): Set<Entry> {
    return participantMap.values.filter { it.state == State.PENDING }.toSet()
  }

  /**
   * Gets all of the pending participants regardless of state. Filterable
   * via a 'since' parameter so that we only display non-[State.PENDING] entries with
   * state change timestamps AFTER that parameter. [State.PENDING] entries will always
   * be returned.
   *
   * @param since A timestamp, for which we will only return non-[State.PENDING] occurring afterwards.
   */
  fun getAllPendingParticipants(since: Duration): Set<Entry> {
    return participantMap.values.filter {
      it.state == State.PENDING || it.stateChangeAt > since
    }.toSet()
  }

  /**
   * A [Recipient] and some [State] metadata
   */
  data class Entry(
    val recipient: Recipient,
    val state: State,
    val stateChangeAt: Duration,
    val denialCount: Int = 0
  )

  /**
   * The state of a given recipient's approval
   */
  enum class State {
    /**
     * No action has been taken
     */
    PENDING,

    /**
     * The user has approved this recipient
     */
    APPROVED,

    /**
     * The user has denied this recipient
     */
    DENIED
  }
}
