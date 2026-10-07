/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.data

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.concurrent.ConcurrentHashMap

/** Loaded messages that carry a shared contact. Only ever grows, so [hasAny] flips to true at most once. */
class SharedContactMessages {
  private val ids: MutableSet<Long> = ConcurrentHashMap.newKeySet()
  private val hasAnySubject = BehaviorSubject.createDefault(false)

  val messageIds: Set<Long>
    get() = ids

  val hasAny: Observable<Boolean> = hasAnySubject.distinctUntilChanged()

  fun add(messageIds: Collection<Long>) {
    if (ids.addAll(messageIds) && hasAnySubject.value != true) {
      hasAnySubject.onNext(true)
    }
  }
}
