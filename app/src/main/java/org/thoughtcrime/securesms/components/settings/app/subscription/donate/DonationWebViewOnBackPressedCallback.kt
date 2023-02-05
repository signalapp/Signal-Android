package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import android.webkit.WebView
import androidx.activity.OnBackPressedCallback

/**
 * Utilized in the 3DS and PayPal WebView fragments to handle WebView back navigation.
 */
class DonationWebViewOnBackPressedCallback(
  private val dismissAllowingStateLoss: () -> Unit,
  private val webView: WebView,
) : OnBackPressedCallback(true) {
  override fun handleOnBackPressed() {
    if (webView.canGoBack()) {
      webView.goBack()
    } else {
      dismissAllowingStateLoss()
    }
  }
}
