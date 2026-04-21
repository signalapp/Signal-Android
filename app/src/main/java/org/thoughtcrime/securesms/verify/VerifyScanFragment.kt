package org.thoughtcrime.securesms.verify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.camera.CameraScreenViewModel
import org.signal.core.ui.compose.ComposeFragment
import org.signal.qr.kitkat.ScanListener
import org.thoughtcrime.securesms.util.fragments.findListener

/**
 * QR Scanner for identity verification
 */
class VerifyScanFragment : ComposeFragment() {
  @Composable
  override fun FragmentContent() {
    val viewModel = viewModel {
      CameraScreenViewModel()
    }

    val state by viewModel.state

    LaunchedEffect(viewModel) {
      viewModel.qrCodeDetected.collect {
        findListener<ScanListener>()?.onQrDataFound(it)
      }
    }

    VerifyScanScreen(
      state = state,
      emitter = viewModel::onEvent
    )
  }
}
