package org.thoughtcrime.securesms.subscription

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject

object LevelUpdate {

  private var isProcessingSubject = BehaviorSubject.createDefault(false)

  var isProcessing: Observable<Boolean> = isProcessingSubject

  fun updateProcessingState(isProcessing: Boolean) {
    isProcessingSubject.onNext(isProcessing)
  }
}
