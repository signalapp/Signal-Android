/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testutil

import androidx.test.core.app.ApplicationProvider
import io.mockk.clearMocks
import org.junit.rules.ExternalResource
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.MockApplicationDependencyProvider
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

/**
 * Facilitates mocking and clearing components of [AppDependencies]. Clearing is particularly important as the
 * mocks will be reused since [AppDependencies] is scoped to the entire test suite and stays initialized with prior
 * test runs leading to unpredictable results based on how tests are run.
 */
class MockAppDependenciesRule : ExternalResource() {

  private val skipList = setOf(
    "application",
    "databaseObserver",
    "groupsV2Authorization",
    "isInitialized",
    "okHttpClient",
    "signalOkHttpClient",
    "webSocketObserver"
  )

  private val properties = AppDependencies::class
    .memberProperties
    .filter { it.visibility == KVisibility.PUBLIC }
    .filterNot { skipList.contains(it.name) }

  override fun before() {
    if (!AppDependencies.isInitialized) {
      AppDependencies.init(ApplicationProvider.getApplicationContext(), MockApplicationDependencyProvider())
    }
  }

  override fun after() {
    properties
      .forEach { property ->
        property.get(AppDependencies)?.let { clearMocks(it) }
      }
  }
}
