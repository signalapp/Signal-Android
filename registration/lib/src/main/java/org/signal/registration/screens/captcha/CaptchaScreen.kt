/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.captcha

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews

/**
 * Screen to display a captcha verification using a WebView.
 * The WebView loads the Signal captcha URL and intercepts the callback
 * when the user completes the captcha.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaScreen(
  state: CaptchaState,
  onEvent: (CaptchaScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  var loadState by remember { mutableStateOf(state.loadState) }

  Column(
    modifier = modifier
      .fillMaxSize()
  ) {
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
    ) {
      AndroidView(
        factory = { context ->
          WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            clearCache(true)

            webViewClient = object : WebViewClient() {
              @Deprecated("Deprecated in Java")
              override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith(state.captchaScheme)) {
                  val token = url.substring(state.captchaScheme.length)
                  onEvent(CaptchaScreenEvents.CaptchaCompleted(token))
                  return true
                }
                return false
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadState = CaptchaLoadState.Loaded
              }

              override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
              ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                loadState = CaptchaLoadState.Error
              }
            }

            loadUrl(state.captchaUrl)
          }
        },
        modifier = Modifier.fillMaxSize()
      )

      when (loadState) {
        CaptchaLoadState.Loaded -> Unit
        CaptchaLoadState.Loading -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
          }
        }
        CaptchaLoadState.Error -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "Failed to load captcha",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.error
            )
          }
        }
      }
    }

    TextButton(
      onClick = { onEvent(CaptchaScreenEvents.Cancel) },
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(16.dp)
    ) {
      Text("Cancel")
    }
  }
}

@DayNightPreviews
@Composable
private fun CaptchaScreenLoadingPreview() {
  Previews.Preview {
    CaptchaScreen(
      state = CaptchaState(
        captchaUrl = "https://example.com/captcha",
        loadState = CaptchaLoadState.Loading
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun CaptchaScreenErrorPreview() {
  Previews.Preview {
    CaptchaScreen(
      state = CaptchaState(
        captchaUrl = "https://example.com/captcha",
        loadState = CaptchaLoadState.Error
      ),
      onEvent = {}
    )
  }
}
