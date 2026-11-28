/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.screener.LayoutStressTest

/**
 * [LayoutStressTest] for content that uses [SignalTheme].
 */
abstract class SignalLayoutStressTest : LayoutStressTest() {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  override val previewAnnotations = listOf("@PreviewWrapper(SignalPreviewWrapper::class)")

  @Composable
  override fun Wrap(content: @Composable () -> Unit) {
    SignalTheme {
      content()
    }
  }
}
