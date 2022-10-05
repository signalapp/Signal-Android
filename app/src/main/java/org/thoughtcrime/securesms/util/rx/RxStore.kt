package org.thoughtcrime.securesms.util.rx

import androidx.annotation.CheckResult
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject

/**
 * Rx replacement for Store.
 * Actions are run on the computation thread by default.
 *
 * This class is disposable, and should be explicitly disposed of in a ViewModel's  onCleared method
 * to prevent memory leaks. Disposing instances of this class is a terminal action.
 */
class RxStore<T : Any>(
  defaultValue: T,
  scheduler: Scheduler = Schedulers.computation()
) : Disposable {

  private val behaviorProcessor = BehaviorProcessor.createDefault(defaultValue)
  private val actionSubject = PublishSubject.create<(T) -> T>().toSerialized()

  val state: T get() = behaviorProcessor.value!!
  val stateFlowable: Flowable<T> = behaviorProcessor.onBackpressureLatest()

  val actionDisposable: Disposable = actionSubject
    .observeOn(scheduler)
    .scan(defaultValue) { v, f -> f(v) }
    .subscribe { behaviorProcessor.onNext(it) }

  fun update(transformer: (T) -> T) {
    actionSubject.onNext(transformer)
  }

  @CheckResult
  fun <U : Any> update(flowable: Flowable<U>, transformer: (U, T) -> T): Disposable {
    return flowable.subscribe {
      actionSubject.onNext { t -> transformer(it, t) }
    }
  }

  /**
   * Dispose of the underlying scan chain. This is terminal.
   */
  override fun dispose() {
    actionDisposable.dispose()
  }

  override fun isDisposed(): Boolean {
    return actionDisposable.isDisposed
  }
}
