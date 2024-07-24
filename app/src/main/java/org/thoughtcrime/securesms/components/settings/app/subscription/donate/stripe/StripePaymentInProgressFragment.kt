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
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.signal.donations.StripeApi
import org.signal.donations.StripeIntentAccessor
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.requireSubscriberType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toErrorSource
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorAction
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorActionResult
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorStage
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.databinding.DonationInProgressFragmentBinding
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class StripePaymentInProgressFragment : DialogFragment(R.layout.donation_in_progress_fragment) {

  companion object {
    private val TAG = Log.tag(StripePaymentInProgressFragment::class.java)

    const val REQUEST_KEY = "REQUEST_KEY"
  }

  private val binding by ViewBinderDelegate(DonationInProgressFragmentBinding::bind)
  private val args: StripePaymentInProgressFragmentArgs by navArgs()
  private val disposables = LifecycleDisposable()

  private val viewModel: StripePaymentInProgressViewModel by navGraphViewModels(
    R.id.checkout_flow,
    factoryProducer = {
      StripePaymentInProgressViewModel.Factory(requireListener<InAppPaymentComponent>().stripeRepository)
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
        InAppPaymentProcessorAction.PROCESS_NEW_IN_APP_PAYMENT -> {
          viewModel.processNewDonation(args.inAppPayment!!, this::handleSecure3dsAction)
        }

        InAppPaymentProcessorAction.UPDATE_SUBSCRIPTION -> {
          viewModel.updateSubscription(args.inAppPayment!!)
        }

        InAppPaymentProcessorAction.CANCEL_SUBSCRIPTION -> {
          viewModel.cancelSubscription(args.inAppPaymentType.requireSubscriberType())
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
  private fun handleSecure3dsAction(secure3dsAction: StripeApi.Secure3DSAction, inAppPayment: InAppPaymentTable.InAppPayment): Single<StripeIntentAccessor> {
    return when (secure3dsAction) {
      is StripeApi.Secure3DSAction.NotNeeded -> {
        Log.d(TAG, "No 3DS action required.")
        Single.just(StripeIntentAccessor.NO_ACTION_REQUIRED)
      }

      is StripeApi.Secure3DSAction.ConfirmRequired -> {
        Log.d(TAG, "3DS action required. Displaying dialog...")
        Single.create { emitter ->
          val listener = FragmentResultListener { _, bundle ->
            val result: StripeIntentAccessor? = bundle.getParcelableCompat(Stripe3DSDialogFragment.REQUEST_KEY, StripeIntentAccessor::class.java)
            if (result != null) {
              emitter.onSuccess(result)
            } else {
              val didLaunchExternal = bundle.getBoolean(Stripe3DSDialogFragment.LAUNCHED_EXTERNAL, false)
              if (didLaunchExternal) {
                emitter.onError(DonationError.UserLaunchedExternalApplication(args.inAppPaymentType.toErrorSource()))
              } else {
                emitter.onError(DonationError.UserCancelledPaymentError(args.inAppPaymentType.toErrorSource()))
              }
            }
          }

          parentFragmentManager.setFragmentResultListener(Stripe3DSDialogFragment.REQUEST_KEY, this, listener)

          findNavController().safeNavigate(StripePaymentInProgressFragmentDirections.actionStripePaymentInProgressFragmentToStripe3dsDialogFragment(secure3dsAction.uri, secure3dsAction.returnUri, inAppPayment))

          emitter.setCancellable {
            parentFragmentManager.clearFragmentResultListener(Stripe3DSDialogFragment.REQUEST_KEY)
          }
        }.subscribeOn(AndroidSchedulers.mainThread()).observeOn(Schedulers.io())
      }
    }
  }
}
