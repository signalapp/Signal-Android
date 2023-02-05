package org.thoughtcrime.securesms.components.settings.app.subscription.donate.paypal

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
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
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonationProcessorAction
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonationProcessorActionResult
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonationProcessorStage
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.databinding.DonationInProgressFragmentBinding
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.whispersystems.signalservice.api.subscriptions.PayPalCreatePaymentIntentResponse
import org.whispersystems.signalservice.api.subscriptions.PayPalCreatePaymentMethodResponse

class PayPalPaymentInProgressFragment : DialogFragment(R.layout.donation_in_progress_fragment) {

  companion object {
    private val TAG = Log.tag(PayPalPaymentInProgressFragment::class.java)

    const val REQUEST_KEY = "REQUEST_KEY"
  }

  private val disposables = LifecycleDisposable()
  private val binding by ViewBinderDelegate(DonationInProgressFragmentBinding::bind)
  private val args: PayPalPaymentInProgressFragmentArgs by navArgs()

  private val viewModel: PayPalPaymentInProgressViewModel by navGraphViewModels(R.id.donate_to_signal, factoryProducer = {
    PayPalPaymentInProgressViewModel.Factory()
  })

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
        DonationProcessorAction.PROCESS_NEW_DONATION -> {
          viewModel.processNewDonation(args.request, this::oneTimeConfirmationPipeline, this::monthlyConfirmationPipeline)
        }
        DonationProcessorAction.UPDATE_SUBSCRIPTION -> {
          viewModel.updateSubscription(args.request)
        }
        DonationProcessorAction.CANCEL_SUBSCRIPTION -> {
          viewModel.cancelSubscription()
        }
      }
    }

    disposables.bindTo(viewLifecycleOwner)
    disposables += viewModel.state.subscribeBy { stage ->
      presentUiState(stage)
    }
  }

  private fun presentUiState(stage: DonationProcessorStage) {
    when (stage) {
      DonationProcessorStage.INIT -> binding.progressCardStatus.setText(R.string.SubscribeFragment__processing_payment)
      DonationProcessorStage.PAYMENT_PIPELINE -> binding.progressCardStatus.setText(R.string.SubscribeFragment__processing_payment)
      DonationProcessorStage.FAILED -> {
        viewModel.onEndAction()
        findNavController().popBackStack()
        setFragmentResult(
          REQUEST_KEY,
          bundleOf(
            REQUEST_KEY to DonationProcessorActionResult(
              action = args.action,
              request = args.request,
              status = DonationProcessorActionResult.Status.FAILURE
            )
          )
        )
      }
      DonationProcessorStage.COMPLETE -> {
        viewModel.onEndAction()
        findNavController().popBackStack()
        setFragmentResult(
          REQUEST_KEY,
          bundleOf(
            REQUEST_KEY to DonationProcessorActionResult(
              action = args.action,
              request = args.request,
              status = DonationProcessorActionResult.Status.SUCCESS
            )
          )
        )
      }
      DonationProcessorStage.CANCELLING -> binding.progressCardStatus.setText(R.string.StripePaymentInProgressFragment__cancelling)
    }
  }

  private fun oneTimeConfirmationPipeline(createPaymentIntentResponse: PayPalCreatePaymentIntentResponse): Single<PayPalConfirmationResult> {
    return routeToOneTimeConfirmation(createPaymentIntentResponse)
  }

  private fun monthlyConfirmationPipeline(createPaymentIntentResponse: PayPalCreatePaymentMethodResponse): Single<PayPalPaymentMethodId> {
    return routeToMonthlyConfirmation(createPaymentIntentResponse)
  }

  private fun routeToOneTimeConfirmation(createPaymentIntentResponse: PayPalCreatePaymentIntentResponse): Single<PayPalConfirmationResult> {
    return Single.create<PayPalConfirmationResult> { emitter ->
      val listener = FragmentResultListener { _, bundle ->
        val result: PayPalConfirmationResult? = bundle.getParcelable(PayPalConfirmationDialogFragment.REQUEST_KEY)
        if (result != null) {
          emitter.onSuccess(result)
        } else {
          emitter.onError(DonationError.UserCancelledPaymentError(args.request.donateToSignalType.toErrorSource()))
        }
      }

      parentFragmentManager.clearFragmentResult(PayPalConfirmationDialogFragment.REQUEST_KEY)
      parentFragmentManager.setFragmentResultListener(PayPalConfirmationDialogFragment.REQUEST_KEY, this, listener)

      findNavController().safeNavigate(
        PayPalPaymentInProgressFragmentDirections.actionPaypalPaymentInProgressFragmentToPaypalConfirmationFragment(
          Uri.parse(createPaymentIntentResponse.approvalUrl)
        )
      )

      emitter.setCancellable {
        Log.d(TAG, "Clearing one-time confirmation result listener.")
        parentFragmentManager.clearFragmentResult(PayPalConfirmationDialogFragment.REQUEST_KEY)
        parentFragmentManager.clearFragmentResultListener(PayPalConfirmationDialogFragment.REQUEST_KEY)
      }
    }.subscribeOn(AndroidSchedulers.mainThread()).observeOn(Schedulers.io())
  }

  private fun routeToMonthlyConfirmation(createPaymentIntentResponse: PayPalCreatePaymentMethodResponse): Single<PayPalPaymentMethodId> {
    return Single.create<PayPalPaymentMethodId> { emitter ->
      val listener = FragmentResultListener { _, bundle ->
        val result: Boolean = bundle.getBoolean(PayPalConfirmationDialogFragment.REQUEST_KEY)
        if (result) {
          emitter.onSuccess(PayPalPaymentMethodId(createPaymentIntentResponse.token))
        } else {
          emitter.onError(DonationError.UserCancelledPaymentError(args.request.donateToSignalType.toErrorSource()))
        }
      }

      parentFragmentManager.clearFragmentResult(PayPalConfirmationDialogFragment.REQUEST_KEY)
      parentFragmentManager.setFragmentResultListener(PayPalConfirmationDialogFragment.REQUEST_KEY, this, listener)

      findNavController().safeNavigate(
        PayPalPaymentInProgressFragmentDirections.actionPaypalPaymentInProgressFragmentToPaypalConfirmationFragment(
          Uri.parse(createPaymentIntentResponse.approvalUrl)
        )
      )

      emitter.setCancellable {
        Log.d(TAG, "Clearing monthly confirmation result listener.")
        parentFragmentManager.clearFragmentResult(PayPalConfirmationDialogFragment.REQUEST_KEY)
        parentFragmentManager.clearFragmentResultListener(PayPalConfirmationDialogFragment.REQUEST_KEY)
      }
    }.subscribeOn(AndroidSchedulers.mainThread()).observeOn(Schedulers.io())
  }

  private fun <T : Any> displayCompleteOrderSheet(confirmationData: T): Single<T> {
    return Single.create<T> { emitter ->
      val listener = FragmentResultListener { _, bundle ->
        val result: Boolean = bundle.getBoolean(PayPalCompleteOrderBottomSheet.REQUEST_KEY)
        if (result) {
          Log.d(TAG, "User confirmed order. Continuing...")
          emitter.onSuccess(confirmationData)
        } else {
          emitter.onError(DonationError.UserCancelledPaymentError(args.request.donateToSignalType.toErrorSource()))
        }
      }

      parentFragmentManager.clearFragmentResult(PayPalCompleteOrderBottomSheet.REQUEST_KEY)
      parentFragmentManager.setFragmentResultListener(PayPalCompleteOrderBottomSheet.REQUEST_KEY, this, listener)

      findNavController().safeNavigate(
        PayPalPaymentInProgressFragmentDirections.actionPaypalPaymentInProgressFragmentToPaypalCompleteOrderBottomSheet(args.request)
      )

      emitter.setCancellable {
        Log.d(TAG, "Clearing complete order result listener.")
        parentFragmentManager.clearFragmentResult(PayPalCompleteOrderBottomSheet.REQUEST_KEY)
        parentFragmentManager.clearFragmentResultListener(PayPalCompleteOrderBottomSheet.REQUEST_KEY)
      }
    }.subscribeOn(AndroidSchedulers.mainThread()).observeOn(Schedulers.io())
  }
}
