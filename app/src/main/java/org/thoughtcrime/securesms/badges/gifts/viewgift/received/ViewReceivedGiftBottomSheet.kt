package org.thoughtcrime.securesms.badges.gifts.viewgift.received

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.DimensionUnit
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.badges.gifts.ExpiredGiftSheetConfiguration.forExpiredGiftBadge
import org.thoughtcrime.securesms.badges.gifts.viewgift.ViewGiftRepository
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.models.BadgeDisplay112
import org.thoughtcrime.securesms.badges.models.BadgeDisplay160
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsBottomSheetFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorDialogs
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.components.settings.models.IndeterminateLoadingCircle
import org.thoughtcrime.securesms.components.settings.models.OutlinedSwitch
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.BottomSheetUtil
import java.util.concurrent.TimeUnit

/**
 * Handles all interactions for received gift badges.
 */
class ViewReceivedGiftBottomSheet : DSLSettingsBottomSheetFragment() {

  companion object {
    private val TAG = Log.tag(ViewReceivedGiftBottomSheet::class.java)

    private const val ARG_GIFT_BADGE = "arg.gift.badge"
    private const val ARG_SENT_FROM = "arg.sent.from"
    private const val ARG_MESSAGE_ID = "arg.message.id"

    @JvmField
    val REQUEST_KEY: String = TAG

    const val RESULT_NOT_NOW = "result.not.now"

    @JvmStatic
    fun show(fragmentManager: FragmentManager, messageRecord: MmsMessageRecord) {
      ViewReceivedGiftBottomSheet().apply {
        arguments = Bundle().apply {
          putParcelable(ARG_SENT_FROM, messageRecord.recipient.id)
          putByteArray(ARG_GIFT_BADGE, messageRecord.giftBadge!!.toByteArray())
          putLong(ARG_MESSAGE_ID, messageRecord.id)
        }
        show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
      }
    }
  }

  private val lifecycleDisposable = LifecycleDisposable()

  private val sentFrom: RecipientId
    get() = requireArguments().getParcelableCompat(ARG_SENT_FROM, RecipientId::class.java)!!

  private val messageId: Long
    get() = requireArguments().getLong(ARG_MESSAGE_ID)

  private val viewModel: ViewReceivedGiftViewModel by viewModels(
    factoryProducer = { ViewReceivedGiftViewModel.Factory(sentFrom, messageId, ViewGiftRepository(), BadgeRepository(requireContext())) }
  )

  private var errorDialog: DialogInterface? = null
  private lateinit var progressDialog: AlertDialog

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    BadgeDisplay112.register(adapter)
    OutlinedSwitch.register(adapter)
    BadgeDisplay160.register(adapter)
    IndeterminateLoadingCircle.register(adapter)

    progressDialog = MaterialAlertDialogBuilder(requireContext())
      .setView(R.layout.redeeming_gift_dialog)
      .setCancelable(false)
      .create()

    lifecycleDisposable.bindTo(viewLifecycleOwner)
    lifecycleDisposable += DonationError
      .getErrorsForSource(DonationErrorSource.GIFT_REDEMPTION)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { donationError ->
        onRedemptionError(donationError)
      }

    lifecycleDisposable += viewModel.state.observeOn(AndroidSchedulers.mainThread()).subscribe { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    progressDialog.hide()
  }

  private fun onRedemptionError(throwable: Throwable?) {
    Log.w(TAG, "onRedemptionError", throwable, true)

    if (errorDialog != null) {
      Log.i(TAG, "Already displaying an error dialog. Skipping.")
      return
    }

    errorDialog = DonationErrorDialogs.show(
      requireContext(),
      throwable,
      object : DonationErrorDialogs.DialogCallback() {
        override fun onDialogDismissed() {
          findNavController().popBackStack()
        }
      }
    )
  }

  private fun getConfiguration(state: ViewReceivedGiftState): DSLConfiguration {
    return configure {
      if (state.giftBadge == null) {
        customPref(IndeterminateLoadingCircle)
      } else if (isGiftBadgeExpired(state.giftBadge)) {
        forExpiredGiftBadge(
          giftBadge = state.giftBadge,
          onMakeAMonthlyDonation = {
            requireActivity().startActivity(AppSettingsActivity.subscriptions(requireContext()))
            requireActivity().finish()
          },
          onNotNow = {
            dismissAllowingStateLoss()
          }
        )
      } else {
        if (state.giftBadge.redemptionState == GiftBadge.RedemptionState.STARTED) {
          progressDialog.show()
        } else {
          progressDialog.hide()
        }

        if (state.recipient != null && !isGiftBadgeRedeemed(state.giftBadge)) {
          noPadTextPref(
            title = DSLSettingsText.from(
              charSequence = requireContext().getString(R.string.ViewReceivedGiftBottomSheet__s_made_a_donation_for_you, state.recipient.getShortDisplayName(requireContext())),
              DSLSettingsText.CenterModifier,
              DSLSettingsText.TitleLargeModifier
            )
          )

          space(DimensionUnit.DP.toPixels(12f).toInt())
          presentSubheading(state.recipient)

          space(DimensionUnit.DP.toPixels(37f).toInt())
        }

        if (state.badge != null && state.controlState != null) {
          presentForUnexpiredGiftBadge(state, state.giftBadge, state.controlState, state.badge)
          space(DimensionUnit.DP.toPixels(16f).toInt())
        }
      }
    }
  }

  private fun DSLConfiguration.presentSubheading(recipient: Recipient) {
    noPadTextPref(
      title = DSLSettingsText.from(
        charSequence = requireContext().getString(R.string.ViewReceivedGiftBottomSheet__s_made_a_donation_to_signal, recipient.getShortDisplayName(requireContext())),
        DSLSettingsText.CenterModifier
      )
    )
  }

  private fun DSLConfiguration.presentForUnexpiredGiftBadge(
    state: ViewReceivedGiftState,
    giftBadge: GiftBadge,
    controlState: ViewReceivedGiftState.ControlState,
    badge: Badge
  ) {
    when (giftBadge.redemptionState) {
      GiftBadge.RedemptionState.REDEEMED -> {
        customPref(
          BadgeDisplay160.Model(
            badge = badge
          )
        )

        state.recipient?.run {
          presentSubheading(this)
        }
      }
      else -> {
        customPref(
          BadgeDisplay112.Model(
            badge = badge,
            withDisplayText = false
          )
        )

        customPref(
          OutlinedSwitch.Model(
            text = DSLSettingsText.from(
              when (controlState) {
                ViewReceivedGiftState.ControlState.DISPLAY -> R.string.SubscribeThanksForYourSupportBottomSheetDialogFragment__display_on_profile
                ViewReceivedGiftState.ControlState.FEATURE -> R.string.SubscribeThanksForYourSupportBottomSheetDialogFragment__make_featured_badge
              }
            ),
            isEnabled = giftBadge.redemptionState != GiftBadge.RedemptionState.STARTED,
            isChecked = state.getControlChecked(),
            onClick = {
              viewModel.setChecked(!it.isChecked)
            }
          )
        )

        if (state.hasOtherBadges && state.displayingOtherBadges) {
          noPadTextPref(DSLSettingsText.from(R.string.ThanksForYourSupportBottomSheetFragment__when_you_have_more))
        }

        space(DimensionUnit.DP.toPixels(36f).toInt())

        primaryButton(
          text = DSLSettingsText.from(R.string.ViewReceivedGiftSheet__redeem),
          isEnabled = giftBadge.redemptionState != GiftBadge.RedemptionState.STARTED,
          onClick = {
            lifecycleDisposable += viewModel.redeem().subscribeBy(
              onComplete = {
                dismissAllowingStateLoss()
              },
              onError = {
                onRedemptionError(it)
              }
            )
          }
        )

        secondaryButtonNoOutline(
          text = DSLSettingsText.from(R.string.ViewReceivedGiftSheet__not_now),
          isEnabled = giftBadge.redemptionState != GiftBadge.RedemptionState.STARTED,
          onClick = {
            setFragmentResult(
              REQUEST_KEY,
              Bundle().apply {
                putBoolean(RESULT_NOT_NOW, true)
              }
            )
            dismissAllowingStateLoss()
          }
        )
      }
    }
  }

  private fun isGiftBadgeRedeemed(giftBadge: GiftBadge): Boolean {
    return giftBadge.redemptionState == GiftBadge.RedemptionState.REDEEMED
  }

  private fun isGiftBadgeExpired(giftBadge: GiftBadge): Boolean {
    return try {
      val receiptCredentialPresentation = ReceiptCredentialPresentation(giftBadge.redemptionToken.toByteArray())

      receiptCredentialPresentation.receiptExpirationTime <= TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
    } catch (e: InvalidInputException) {
      Log.w(TAG, "Failed to check expiration of given badge.", e)
      true
    }
  }
}
