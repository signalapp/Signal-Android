package org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.navArgs
import org.signal.donations.StripeIntentAccessor
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonationWebViewOnBackPressedCallback
import org.thoughtcrime.securesms.databinding.DonationWebviewFragmentBinding
import org.thoughtcrime.securesms.util.visible

/**
 * Full-screen dialog for displaying Stripe 3DS confirmation.
 */
class Stripe3DSDialogFragment : DialogFragment(R.layout.donation_webview_fragment) {

  companion object {
    const val REQUEST_KEY = "stripe_3ds_dialog_fragment"
  }

  val binding by ViewBinderDelegate(DonationWebviewFragmentBinding::bind) {
    it.webView.clearCache(true)
    it.webView.clearHistory()
  }

  val args: Stripe3DSDialogFragmentArgs by navArgs()

  var result: Bundle? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen)
  }

  @SuppressLint("SetJavaScriptEnabled")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.webView.webViewClient = Stripe3DSWebClient()
    binding.webView.settings.javaScriptEnabled = true
    binding.webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
    binding.webView.loadUrl(args.uri.toString())

    (requireDialog() as ComponentDialog).onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      DonationWebViewOnBackPressedCallback(
        this::dismissAllowingStateLoss,
        binding.webView
      )
    )
  }

  override fun onDismiss(dialog: DialogInterface) {
    val result = this.result
    this.result = null
    setFragmentResult(REQUEST_KEY, result ?: Bundle())
  }

  private inner class Stripe3DSWebClient : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
      binding.progress.visible = true
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
      binding.progress.visible = false
    }

    override fun onPageFinished(view: WebView?, url: String?) {
      if (url?.startsWith(args.returnUri.toString()) == true) {
        val stripeIntentAccessor = StripeIntentAccessor.fromUri(url)

        result = bundleOf(REQUEST_KEY to stripeIntentAccessor)
        dismissAllowingStateLoss()
      }
    }
  }
}
