package org.thoughtcrime.securesms.testing

import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.rules.ExternalResource

/**
 * JUnit Rule which initialises Rx thread schedulers. If a specific
 * scheduler is not specified, it defaults to the `defaultTestScheduler`
 */
class RxTestSchedulerRule(
  val defaultTestScheduler: TestScheduler = TestScheduler(),
  val ioTestScheduler: TestScheduler = defaultTestScheduler,
  val computationTestScheduler: TestScheduler = defaultTestScheduler,
  val singleTestScheduler: TestScheduler = defaultTestScheduler,
  val newThreadTestScheduler: TestScheduler = defaultTestScheduler
) : ExternalResource() {

  override fun before() {
    RxJavaPlugins.setInitIoSchedulerHandler { ioTestScheduler }
    RxJavaPlugins.setIoSchedulerHandler { ioTestScheduler }

    RxJavaPlugins.setInitComputationSchedulerHandler { computationTestScheduler }
    RxJavaPlugins.setComputationSchedulerHandler { computationTestScheduler }

    RxJavaPlugins.setInitSingleSchedulerHandler { singleTestScheduler }
    RxJavaPlugins.setSingleSchedulerHandler { singleTestScheduler }

    RxJavaPlugins.setInitNewThreadSchedulerHandler { newThreadTestScheduler }
    RxJavaPlugins.setNewThreadSchedulerHandler { newThreadTestScheduler }
  }

  override fun after() {
    RxJavaPlugins.reset()
  }
}
