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
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toErrorSource
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorAction
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorActionResult
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorStage
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.databinding.DonationInProgressFragmentBinding
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

  private val viewModel: PayPalPaymentInProgressViewModel by navGraphViewModels(R.id.checkout_flow, factoryProducer = {
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
        InAppPaymentProcessorAction.PROCESS_NEW_IN_APP_PAYMENT -> {
          viewModel.processNewDonation(args.inAppPayment!!, this::oneTimeConfirmationPipeline, this::monthlyConfirmationPipeline)
        }

        InAppPaymentProcessorAction.UPDATE_SUBSCRIPTION -> {
          viewModel.updateSubscription(args.inAppPayment!!)
        }

        InAppPaymentProcessorAction.CANCEL_SUBSCRIPTION -> {
          viewModel.cancelSubscription(InAppPaymentSubscriberRecord.Type.DONATION) // TODO [message-backups] Remove hardcode
        }
      }
    }

    disposables.bindTo(viewLifecycleOwner)
    disposables += viewModel.state.subscribeBy { stage ->
      presentUiState(stage)
    }
  }

  private fun presentUiState(stage: InAppPaymentProcessorStage) {
    when (stage) {
      InAppPaymentProcessorStage.INIT -> binding.progressCardStatus.text = getProcessingStatus()
      InAppPaymentProcessorStage.PAYMENT_PIPELINE -> binding.progressCardStatus.text = getProcessingStatus()
      InAppPaymentProcessorStage.FAILED -> {
        viewModel.onEndAction()
        findNavController().popBackStack()
        setFragmentResult(
          REQUEST_KEY,
          bundleOf(
            REQUEST_KEY to InAppPaymentProcessorActionResult(
              action = args.action,
              inAppPayment = args.inAppPayment,
              inAppPaymentType = args.inAppPaymentType,
              status = InAppPaymentProcessorActionResult.Status.FAILURE
            )
          )
        )
      }

      InAppPaymentProcessorStage.COMPLETE -> {
        viewModel.onEndAction()
        findNavController().popBackStack()
        setFragmentResult(
          REQUEST_KEY,
          bundleOf(
            REQUEST_KEY to InAppPaymentProcessorActionResult(
              action = args.action,
              inAppPayment = args.inAppPayment,
              inAppPaymentType = args.inAppPaymentType,
              status = InAppPaymentProcessorActionResult.Status.SUCCESS
            )
          )
        )
      }

      InAppPaymentProcessorStage.CANCELLING -> binding.progressCardStatus.setText(R.string.StripePaymentInProgressFragment__cancelling)
    }
  }

  private fun getProcessingStatus(): String {
    return if (args.inAppPaymentType == InAppPaymentType.RECURRING_BACKUP) {
      getString(R.string.InAppPaymentInProgressFragment__processing_payment)
    } else {
      getString(R.string.InAppPaymentInProgressFragment__processing_donation)
    }
  }

  private fun oneTimeConfirmationPipeline(createPaymentIntentResponse: PayPalCreatePaymentIntentResponse): Single<PayPalConfirmationResult> {
    return routeToOneTimeConfirmation(createPaymentIntentResponse)
  }

  private fun monthlyConfirmationPipeline(createPaymentIntentResponse: PayPalCreatePaymentMethodResponse): Single<PayPalPaymentMethodId> {
    return routeToMonthlyConfirmation(createPaymentIntentResponse)
  }

  private fun routeToOneTimeConfirmation(createPaymentIntentResponse: PayPalCreatePaymentIntentResponse): Single<PayPalConfirmationResult> {
    return Single.create { emitter ->
      val listener = FragmentResultListener { _, bundle ->
        val result: PayPalConfirmationResult? = bundle.getParcelableCompat(PayPalConfirmationDialogFragment.REQUEST_KEY, PayPalConfirmationResult::class.java)
        if (result != null) {
          emitter.onSuccess(result.copy(paymentId = createPaymentIntentResponse.paymentId))
        } else {
          emitter.onError(DonationError.UserCancelledPaymentError(args.inAppPaymentType.toErrorSource()))
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
    return Single.create { emitter ->
      val listener = FragmentResultListener { _, bundle ->
        val result: Boolean = bundle.getBoolean(PayPalConfirmationDialogFragment.REQUEST_KEY)
        if (result) {
          emitter.onSuccess(PayPalPaymentMethodId(createPaymentIntentResponse.token))
        } else {
          emitter.onError(DonationError.UserCancelledPaymentError(args.inAppPaymentType.toErrorSource()))
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
}
