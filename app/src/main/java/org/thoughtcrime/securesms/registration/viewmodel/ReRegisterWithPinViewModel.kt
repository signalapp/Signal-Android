package org.thoughtcrime.securesms.registration.viewmodel

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.thoughtcrime.securesms.pin.TokenData

/**
 * Used during re-registration flow when pin entry is required to skip SMS verification. Mostly tracks
 * guesses remaining in both the local and remote check flows.
 */
class ReRegisterWithPinViewModel : ViewModel() {
  var isLocalVerification: Boolean = false
    private set

  var hasIncorrectGuess: Boolean = false

  private val _triesRemaining: BehaviorSubject<Int> = BehaviorSubject.createDefault(10)
  val triesRemaining: Observable<Int> = _triesRemaining.observeOn(AndroidSchedulers.mainThread())

  fun updateTokenData(tokenData: TokenData?) {
    if (tokenData == null) {
      isLocalVerification = true
      if (hasIncorrectGuess) {
        _triesRemaining.onNext((_triesRemaining.value!! - 1).coerceAtLeast(0))
      }
    } else {
      _triesRemaining.onNext(tokenData.triesRemaining)
    }
  }
}
