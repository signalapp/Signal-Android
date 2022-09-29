package org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentComponent
import org.thoughtcrime.securesms.databinding.StripePaymentInProgressFragmentBinding
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.fragments.requireListener

class StripePaymentInProgressFragment : DialogFragment(R.layout.stripe_payment_in_progress_fragment) {

  companion object {
    const val REQUEST_KEY = "REQUEST_KEY"
  }

  private val binding by ViewBinderDelegate(StripePaymentInProgressFragmentBinding::bind)
  private val args: StripePaymentInProgressFragmentArgs by navArgs()
  private val disposables = LifecycleDisposable()

  private val viewModel: StripePaymentInProgressViewModel by navGraphViewModels(
    R.id.donate_to_signal,
    factoryProducer = {
      StripePaymentInProgressViewModel.Factory(requireListener<DonationPaymentComponent>().donationPaymentRepository)
    }
  )

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    isCancelable = false
    return super.onCreateDialog(savedInstanceState).apply {
      window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    disposables.bindTo(viewLifecycleOwner)
    disposables += viewModel.state.subscribeBy { stage ->
      presentUiState(stage)
    }

    if (savedInstanceState == null) {
      when (args.action) {
        StripeAction.PROCESS_NEW_DONATION -> {
          viewModel.processNewDonation(args.request)
        }
        StripeAction.UPDATE_SUBSCRIPTION -> {
          viewModel.updateSubscription(args.request)
        }
        StripeAction.CANCEL_SUBSCRIPTION -> {
          viewModel.cancelSubscription()
        }
      }
    }
  }

  private fun presentUiState(stage: StripeStage) {
    when (stage) {
      StripeStage.INIT -> binding.progressCardStatus.setText(R.string.SubscribeFragment__processing_payment)
      StripeStage.PAYMENT_PIPELINE -> binding.progressCardStatus.setText(R.string.SubscribeFragment__processing_payment)
      StripeStage.FAILED -> {
        findNavController().popBackStack()
        setFragmentResult(
          REQUEST_KEY,
          bundleOf(
            REQUEST_KEY to StripeActionResult(
              action = args.action,
              request = args.request,
              status = StripeActionResult.Status.FAILURE
            )
          )
        )
      }
      StripeStage.COMPLETE -> {
        findNavController().popBackStack()
        setFragmentResult(
          REQUEST_KEY,
          bundleOf(
            REQUEST_KEY to StripeActionResult(
              action = args.action,
              request = args.request,
              status = StripeActionResult.Status.SUCCESS
            )
          )
        )
      }
      StripeStage.CANCELLING -> binding.progressCardStatus.setText(R.string.StripePaymentInProgressFragment__cancelling)
    }
  }
}
