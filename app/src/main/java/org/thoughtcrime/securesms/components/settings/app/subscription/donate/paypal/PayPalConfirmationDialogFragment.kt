package org.thoughtcrime.securesms.components.settings.app.subscription.donate.paypal

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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.fragment.navArgs
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.settings.app.subscription.PayPalRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonationWebViewOnBackPressedCallback
import org.thoughtcrime.securesms.databinding.DonationWebviewFragmentBinding
import org.thoughtcrime.securesms.util.visible

/**
 * Full-screen dialog for displaying PayPal confirmation.
 */
class PayPalConfirmationDialogFragment : DialogFragment(R.layout.donation_webview_fragment) {

  companion object {
    private val TAG = Log.tag(PayPalConfirmationDialogFragment::class.java)

    const val REQUEST_KEY = "paypal_confirmation_dialog_fragment"
  }

  private val binding by ViewBinderDelegate(DonationWebviewFragmentBinding::bind) {
    it.webView.clearCache(true)
    it.webView.clearHistory()
  }

  private val args: PayPalConfirmationDialogFragmentArgs by navArgs()

  private var result: Bundle? = null
  private var isFinished = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen)
  }

  @SuppressLint("SetJavaScriptEnabled")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val client = PayPalWebClient()
    viewLifecycleOwner.lifecycle.addObserver(client)
    binding.webView.webViewClient = client
    binding.webView.settings.javaScriptEnabled = true
    binding.webView.settings.domStorageEnabled = true
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
    super.onDismiss(dialog)
    val result = this.result
    this.result = null
    setFragmentResult(REQUEST_KEY, result ?: Bundle())
  }

  private inner class PayPalWebClient : WebViewClient(), DefaultLifecycleObserver {

    private var isDestroyed = false

    override fun onDestroy(owner: LifecycleOwner) {
      isDestroyed = true
    }

    /**
     * Requires API23 for non-deprecated version.
     */
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
      Log.d(TAG, "onReceivedError $errorCode: $description")

      super.onReceivedError(view, errorCode, description, failingUrl)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
      if (!isDestroyed) {
        binding.progress.visible = true
      }
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
      if (!isDestroyed) {
        binding.progress.visible = false
      }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
      if (isDestroyed) {
        return
      }

      if (url?.startsWith(PayPalRepository.ONE_TIME_RETURN_URL) == true) {
        val confirmationResult = PayPalConfirmationResult.fromUrl(url)
        if (confirmationResult != null) {
          Log.d(TAG, "Setting confirmation result on request key...")
          result = bundleOf(REQUEST_KEY to confirmationResult)
        } else {
          Log.w(TAG, "One-Time return URL was missing a required parameter.", false)
          result = null
        }
        isFinished = true
        dismissAllowingStateLoss()
      } else if (url?.startsWith(PayPalRepository.CANCEL_URL) == true) {
        Log.d(TAG, "User cancelled.")
        result = null
        isFinished = true
        dismissAllowingStateLoss()
      } else if (url?.startsWith(PayPalRepository.MONTHLY_RETURN_URL) == true) {
        Log.d(TAG, "User confirmed monthly subscription.")
        result = bundleOf(REQUEST_KEY to true)
        isFinished = true
        dismissAllowingStateLoss()
      }
    }
  }
}
