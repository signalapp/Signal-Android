/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.shared

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity

private const val TAP_TARGET = 8

/**
 * A base framework for rendering the various v3 registration screens.
 */
@Composable
fun RegistrationScreen(
  title: String,
  subtitle: String,
  bottomContent: @Composable (BoxScope.() -> Unit),
  mainContent: @Composable ColumnScope.() -> Unit
) {
  RegistrationScreen(title, AnnotatedString(subtitle), bottomContent, mainContent)
}

/**
 * A base framework for rendering the various v3 registration screens.
 */
@Composable
fun RegistrationScreen(
  title: String,
  subtitle: AnnotatedString?,
  bottomContent: @Composable (BoxScope.() -> Unit),
  mainContent: @Composable ColumnScope.() -> Unit
) {
  Surface {
    Column(
      verticalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
    ) {
      val scrollState = rememberScrollState()
      val context = LocalContext.current
      var titleTapCount by remember { mutableIntStateOf(0) }
      var previousToast by remember { mutableStateOf<Toast?>(null) }

      Column(
        modifier = Modifier
          .verticalScroll(scrollState)
          .weight(weight = 1f, fill = false)
          .padding(top = 40.dp, bottom = 16.dp)
          .horizontalGutters()
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              titleTapCount++

              if (titleTapCount >= TAP_TARGET) {
                context.startActivity(Intent(context, SubmitDebugLogActivity::class.java))
                previousToast?.cancel()
                previousToast = null
              } else {
                val remaining = TAP_TARGET - titleTapCount
                previousToast?.cancel()
                previousToast = Toast.makeText(context, context.resources.getQuantityString(R.plurals.RegistrationActivity_debug_log_hint, remaining, remaining), Toast.LENGTH_SHORT).apply { show() }
              }
            }
        )

        if (subtitle != null) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
          )
        }

        Spacer(modifier = Modifier.height(40.dp))

        mainContent()
      }

      Surface(
        shadowElevation = if (scrollState.canScrollForward) 8.dp else 0.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Box(
          modifier = Modifier
            .padding(top = 8.dp, bottom = 24.dp)
            .horizontalGutters()
        ) {
          bottomContent()
        }
      }
    }
  }
}

@SignalPreview
@Composable
private fun RegistrationScreenPreview() {
  Previews.Preview {
    RegistrationScreen(
      title = "Title",
      subtitle = "Subtitle",
      bottomContent = {
        TextButton(onClick = {}) {
          Text("Bottom Button")
        }
      }
    ) {
      Text("Main content")
    }
  }
}
