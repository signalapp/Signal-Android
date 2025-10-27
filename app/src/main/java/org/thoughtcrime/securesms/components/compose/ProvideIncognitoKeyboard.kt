/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.compose

import android.os.Build
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * When [enabled]=true, this function sets the [EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING] flag for all text fields within its content to enable the
 * incognito keyboard.
 *
 * This workaround is needed until it's possible to configure granular IME options for a [TextField].
 * https://issuetracker.google.com/issues/359257538
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProvideIncognitoKeyboard(
  enabled: Boolean,
  content: @Composable () -> Unit
) {
  if (enabled) {
    InterceptPlatformTextInput(
      interceptor = { request, nextHandler ->
        val modifiedRequest = PlatformTextInputMethodRequest { outAttributes ->
          request.createInputConnection(outAttributes).also {
            if (Build.VERSION.SDK_INT >= 26) {
              outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
          }
        }
        nextHandler.startInputMethod(modifiedRequest)
      }
    ) {
      content()
    }
  } else {
    content()
  }
}
