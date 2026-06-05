/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaoverview

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.ComposeDialogFragment
import org.thoughtcrime.securesms.R

/**
 * Non-cancelable Compose dialog that observes [MediaDeleteProgressViewModel] and shows
 * determinate "X / Y" progress while a bulk media delete runs. Dismisses itself when the
 * underlying job completes.
 */
class MediaDeleteProgressDialogFragment : ComposeDialogFragment() {

  private val viewModel: MediaDeleteProgressViewModel by viewModels(ownerProducer = { requireParentFragment() })

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    isCancelable = false
    return super.onCreateDialog(savedInstanceState).apply {
      setCanceledOnTouchOutside(false)
      window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
  }

  @Composable
  override fun DialogContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isDone) {
      if (state.isDone) {
        dismissAllowingStateLoss()
      }
    }

    Surface(
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 8.dp,
      modifier = Modifier.width(280.dp)
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 24.dp)
      ) {
        Text(
          text = stringResource(R.string.MediaOverviewActivity_Media_delete_progress_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface
        )

        Box(contentAlignment = Alignment.Center) {
          val total = state.total
          val processed = state.processed
          if (total > 0) {
            CircularProgressIndicator(
              progress = { processed.toFloat() / total },
              modifier = Modifier.size(56.dp)
            )
          } else {
            CircularProgressIndicator(modifier = Modifier.size(56.dp))
          }
        }

        Text(
          text = stringResource(
            R.string.MediaOverviewActivity_Media_delete_progress_count,
            state.processed,
            state.total.coerceAtLeast(state.processed)
          ),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }

  companion object {
    private const val TAG = "MediaDeleteProgressDialog"

    fun show(parent: Fragment) {
      MediaDeleteProgressDialogFragment().show(parent.childFragmentManager, TAG)
    }
  }
}
