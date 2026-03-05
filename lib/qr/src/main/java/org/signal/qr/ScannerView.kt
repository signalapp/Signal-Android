package org.signal.qr

import androidx.lifecycle.LifecycleOwner

/**
 * Common interface for interacting with QR scanning views.
 */
interface ScannerView {
  fun start(lifecycleOwner: LifecycleOwner)
  fun toggleCamera()
}
