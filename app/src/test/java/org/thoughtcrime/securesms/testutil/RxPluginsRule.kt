/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testutil

import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.rules.ExternalResource

/**
 * Sets up RxJava / RxAndroid scheduler overrides.
 */
class RxPluginsRule(
  val defaultScheduler: TestScheduler = TestScheduler(),
  val computationScheduler: TestScheduler = defaultScheduler,
  val ioScheduler: TestScheduler = defaultScheduler,
  val singleScheduler: TestScheduler = defaultScheduler,
  val newThreadScheduler: TestScheduler = defaultScheduler,
  val mainThreadScheduler: TestScheduler = defaultScheduler
) : ExternalResource() {

  override fun before() {
    RxJavaPlugins.setInitComputationSchedulerHandler { computationScheduler }
    RxJavaPlugins.setComputationSchedulerHandler { computationScheduler }

    RxJavaPlugins.setInitIoSchedulerHandler { ioScheduler }
    RxJavaPlugins.setIoSchedulerHandler { ioScheduler }

    RxJavaPlugins.setInitSingleSchedulerHandler { singleScheduler }
    RxJavaPlugins.setSingleSchedulerHandler { singleScheduler }

    RxJavaPlugins.setInitNewThreadSchedulerHandler { newThreadScheduler }
    RxJavaPlugins.setNewThreadSchedulerHandler { newThreadScheduler }

    RxAndroidPlugins.setInitMainThreadSchedulerHandler { mainThreadScheduler }
    RxAndroidPlugins.setMainThreadSchedulerHandler { mainThreadScheduler }
  }

  override fun after() {
    RxJavaPlugins.reset()
    RxAndroidPlugins.reset()
  }
}
