/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.blocked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.Recipient.Companion.resolved
import org.thoughtcrime.securesms.recipients.RecipientId

class BlockedUsersViewModel(private val repository: BlockedUsersRepository) : ViewModel() {
  private val recipients: Subject<List<Recipient>> = BehaviorSubject.create()
  private val events: Subject<Event> = PublishSubject.create()
  init {
    loadRecipients()
  }

  fun getRecipients(): Observable<List<Recipient>> {
    return recipients.observeOn(AndroidSchedulers.mainThread())
  }

  fun getEvents(): Observable<Event> {
    return events.observeOn(AndroidSchedulers.mainThread())
  }

  fun block(recipientId: RecipientId) {
    viewModelScope.launch {
      repository.block(
        recipientId,
        onSuccess = {
          loadRecipients()
          events.onNext(Event(EventType.BLOCK_SUCCEEDED, resolved(recipientId)))
        },
        onFailure = {
          events.onNext(Event(EventType.BLOCK_FAILED, resolved(recipientId)))
        })
    }
  }

  fun createAndBlock(number: String) {
    viewModelScope.launch {
      repository.createAndBlock(number) {
        loadRecipients()
        events.onNext(Event(EventType.BLOCK_SUCCEEDED, number))
      }
    }
  }

  fun unblock(recipientId: RecipientId) {
    viewModelScope.launch {
      repository.unblock(recipientId) {
        loadRecipients()
        events.onNext(Event(EventType.UNBLOCK_SUCCEEDED, resolved(recipientId)))
      }
    }
  }

  private fun loadRecipients() {
    viewModelScope.launch {
      repository.getBlocked(recipients::onNext)
    }
  }

  enum class EventType{
    BLOCK_SUCCEEDED,
    BLOCK_FAILED,
    UNBLOCK_SUCCEEDED
  }

  data class Event private constructor(val eventType: EventType,
                                       val recipient: Recipient?,
                                       val number: String?){
    constructor(eventType: EventType, recipient: Recipient) : this(eventType, recipient, null)
    constructor(eventType: EventType, number: String) : this(eventType, null, number)
  }
}