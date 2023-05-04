package org.signal.core.util.concurrent

import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import org.junit.Assert.assertEquals
import org.junit.Test

class RxExtensionsTest {
  @Test
  fun `Given a subject, when I subscribeWithBehaviorSubject, then I expect proper disposals`() {
    val subject = PublishSubject.create<Int>()
    val disposables = CompositeDisposable()
    val sub2 = subject.subscribeWithSubject(
      BehaviorSubject.create(),
      disposables
    )

    val obs = sub2.test()
    subject.onNext(1)
    obs.dispose()
    subject.onNext(2)
    disposables.dispose()
    subject.onNext(3)

    obs.assertValues(1)
    assertEquals(sub2.value, 2)
  }
}
