package org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.Stripe3DSDialogFragment
import org.thoughtcrime.securesms.databinding.StripePaymentInProgressFragmentBinding
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class StripePaymentInProgressFragment : DialogFragment(R.layout.stripe_payment_in_progress_fragment) {

  companion object {
    private val TAG = Log.tag(StripePaymentInProgressFragment::class.java)

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
    if (savedInstanceState == null) {
      viewModel.onBeginNewAction()
      when (args.action) {
        StripeAction.PROCESS_NEW_DONATION -> {
          viewModel.processNewDonation(args.request, this::handleSecure3dsAction)
        }
        StripeAction.UPDATE_SUBSCRIPTION -> {
          viewModel.updateSubscription(args.request)
        }
        StripeAction.CANCEL_SUBSCRIPTION -> {
          viewModel.cancelSubscription()
        }
      }
    }

    disposables.bindTo(viewLifecycleOwner)
    disposables += viewModel.state.subscribeBy { stage ->
      presentUiState(stage)
    }
  }

  private fun presentUiState(stage: StripeStage) {
    when (stage) {
      StripeStage.INIT -> binding.progressCardStatus.setText(R.string.SubscribeFragment__processing_payment)
      StripeStage.PAYMENT_PIPELINE -> binding.progressCardStatus.setText(R.string.SubscribeFragment__processing_payment)
      StripeStage.FAILED -> {
        viewModel.onEndAction()
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
        viewModel.onEndAction()
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

  private fun handleSecure3dsAction(secure3dsAction: StripeApi.Secure3DSAction): Completable {
    return when (secure3dsAction) {
      is StripeApi.Secure3DSAction.NotNeeded -> {
        Log.d(TAG, "No 3DS action required.")
        Completable.complete()
      }
      is StripeApi.Secure3DSAction.ConfirmRequired -> {
        Log.d(TAG, "3DS action required. Displaying dialog...")
        Completable.create { emitter ->
          val listener = FragmentResultListener { _, _ ->
            emitter.onComplete()
          }

          parentFragmentManager.setFragmentResultListener(Stripe3DSDialogFragment.REQUEST_KEY, this, listener)

          findNavController().safeNavigate(StripePaymentInProgressFragmentDirections.actionStripePaymentInProgressFragmentToStripe3dsDialogFragment(secure3dsAction.uri))

          emitter.setCancellable {
            parentFragmentManager.clearFragmentResultListener(Stripe3DSDialogFragment.REQUEST_KEY)
          }
        }.subscribeOn(AndroidSchedulers.mainThread()).observeOn(Schedulers.io())
      }
    }
  }
}
