package org.thoughtcrime.securesms.safety

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.conversation.ui.error.TrustAndVerifyResult
import org.thoughtcrime.securesms.util.fragments.findListener
import org.thoughtcrime.securesms.util.viewModel

/**
 * Displays a bottom sheet containing information about safety number changes and allows the user to
 * address these changes.
 */
class SafetyNumberBottomSheetFragment : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 1f

  @get:MainThread
  private val args: SafetyNumberBottomSheetArgs by lazy(LazyThreadSafetyMode.NONE) {
    SafetyNumberBottomSheet.getArgsFromBundle(requireArguments())
  }

  private val viewModel: SafetyNumberBottomSheetViewModel by viewModel {
    SafetyNumberBottomSheetViewModel(args = args)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.effects.collect { effect ->
        when (effect) {
          is SafetyNumberBottomSheetEffect.TrustCompleted -> {
            when (effect.result.result) {
              TrustAndVerifyResult.Result.TRUST_AND_VERIFY -> {
                findListener<SafetyNumberBottomSheet.Callbacks>()?.sendAnywayAfterSafetyNumberChangedInBottomSheet(effect.destinations)
              }
              TrustAndVerifyResult.Result.TRUST_VERIFY_AND_RESEND -> {
                findListener<SafetyNumberBottomSheet.Callbacks>()?.onMessageResentAfterSafetyNumberChangeInBottomSheet()
              }
              TrustAndVerifyResult.Result.UNKNOWN -> Log.w(TAG, "Unknown Result")
            }
            dismissAllowingStateLoss()
          }
        }
      }
    }
  }

  @Composable
  override fun SheetContent() {
    val state by viewModel.state.collectAsState()
    val emitter = remember { viewModel::onEvent }

    SafetyNumberBottomSheetContent(
      state = state,
      initialUntrustedCount = args.untrustedRecipients.size,
      getIdentityRecord = viewModel::getIdentityRecord,
      emitter = emitter
    )
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    if (!viewModel.sendAnywayFired) {
      findListener<SafetyNumberBottomSheet.Callbacks>()?.onCanceled()
    }
  }

  companion object {
    private val TAG = Log.tag(SafetyNumberBottomSheetFragment::class.java)
  }
}
