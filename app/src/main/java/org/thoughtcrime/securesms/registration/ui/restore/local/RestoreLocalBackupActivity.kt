/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import kotlin.math.max

/**
 * Handles the synchronous restoration of the proto files from a V2 backup. Media is
 * handled by background tasks.
 */
class RestoreLocalBackupActivity : BaseActivity() {
  companion object {
    private const val KEY_FINISH = "finish"

    fun getIntent(context: Context, finish: Boolean = true): Intent {
      return Intent(context, RestoreLocalBackupActivity::class.java).apply {
        putExtra(KEY_FINISH, finish)
      }
    }
  }

  private val viewModel: RestoreLocalBackupActivityViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      val state by viewModel.state.collectAsStateWithLifecycle()

      LaunchedEffect(state.restorePhase) {
        when (state.restorePhase) {
          RestorePhase.COMPLETE -> {
            startActivity(MainActivity.clearTop(this@RestoreLocalBackupActivity))
            if (intent.getBooleanExtra(KEY_FINISH, false)) {
              finishAffinity()
            }
          }
          RestorePhase.FAILED -> {
            Toast.makeText(this@RestoreLocalBackupActivity, getString(R.string.RestoreLocalBackupActivity__backup_restore_failed), Toast.LENGTH_LONG).show()
          }
          else -> Unit
        }
      }

      SignalTheme {
        RestoreLocalBackupScreen(state = state)
      }
    }
  }
}

@Composable
private fun RestoreLocalBackupScreen(state: RestoreLocalBackupScreenState) {
  val density = LocalDensity.current
  var headerHeightPx by remember { mutableIntStateOf(0) }
  var contentHeightPx by remember { mutableIntStateOf(0) }

  Surface {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .horizontalGutters()
    ) {
      val totalHeightPx = with(density) { maxHeight.roundToPx() }
      val screenCenterTop = with(density) { max((totalHeightPx - contentHeightPx) / 2, headerHeightPx).toDp() }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .onSizeChanged { headerHeightPx = it.height }
      ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
          text = stringResource(R.string.RestoreLocalBackupActivity__restoring_backup),
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.fillMaxWidth()
        )

        Text(
          text = stringResource(R.string.RestoreLocalBackupActivity__depending_on_the_size),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 16.dp)
        )
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = screenCenterTop)
          .onSizeChanged { contentHeightPx = it.height }
      ) {
        if (state.progress > 0f) {
          CircularProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.size(60.dp),
            strokeWidth = 5.dp,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
            gapSize = 0.dp
          )
        } else {
          CircularProgressIndicator(
            modifier = Modifier.size(60.dp),
            strokeWidth = 5.dp,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
            gapSize = 0.dp
          )
        }

        val statusText = when (state.restorePhase) {
          RestorePhase.RESTORING -> stringResource(R.string.RestoreLocalBackupActivity__restoring_messages)
          RestorePhase.FINALIZING -> stringResource(R.string.RestoreLocalBackupActivity__finalizing)
          RestorePhase.COMPLETE -> stringResource(R.string.RestoreLocalBackupActivity__restore_complete)
          RestorePhase.FAILED -> stringResource(R.string.RestoreLocalBackupActivity__restore_failed)
        }

        Text(
          text = statusText,
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(top = 16.dp)
        )

        if (state.restorePhase == RestorePhase.RESTORING && state.totalBytes.inWholeBytes > 0) {
          val progressPercent = (state.progress * 100).toInt()
          Text(
            text = stringResource(R.string.RestoreLocalBackupActivity__s_of_s_d_percent, state.bytesRead.toUnitString(), state.totalBytes.toUnitString(), progressPercent),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
          )
        }
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun RestoreLocalBackupScreenPreview() {
  Previews.Preview {
    RestoreLocalBackupScreen(state = RestoreLocalBackupScreenState())
  }
}
