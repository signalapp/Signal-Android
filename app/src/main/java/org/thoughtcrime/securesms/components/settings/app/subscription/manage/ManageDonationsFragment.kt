package org.thoughtcrime.securesms.components.settings.app.subscription.manage

import android.content.Intent
import android.text.SpannableStringBuilder
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.util.DimensionUnit
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.gifts.ExpiredGiftSheet
import org.thoughtcrime.securesms.badges.gifts.flow.GiftFlowActivity
import org.thoughtcrime.securesms.badges.models.BadgePreview
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.SubscriptionsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.models.NetworkFailure
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.components.settings.models.IndeterminateLoadingCircle
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.help.HelpFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.subscription.Subscription
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import java.util.Currency
import java.util.concurrent.TimeUnit

/**
 * Fragment displayed when a user enters "Subscriptions" via app settings but is already
 * a subscriber. Used to manage their current subscription, view badges, and boost.
 */
class ManageDonationsFragment : DSLSettingsFragment(), ExpiredGiftSheet.Callback {

  private val supportTechSummary: CharSequence by lazy {
    SpannableStringBuilder(requireContext().getString(R.string.SubscribeFragment__make_a_recurring_monthly_donation))
      .append(" ")
      .append(
        SpanUtil.readMore(requireContext(), ContextCompat.getColor(requireContext(), R.color.signal_button_secondary_text)) {
          findNavController().safeNavigate(ManageDonationsFragmentDirections.actionManageDonationsFragmentToSubscribeLearnMoreBottomSheetDialog())
        }
      )
  }

  private val viewModel: ManageDonationsViewModel by viewModels(
    factoryProducer = {
      ManageDonationsViewModel.Factory(SubscriptionsRepository(ApplicationDependencies.getDonationsService()))
    }
  )

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    ActiveSubscriptionPreference.register(adapter)
    IndeterminateLoadingCircle.register(adapter)
    BadgePreview.register(adapter)
    NetworkFailure.register(adapter)

    val expiredGiftBadge = SignalStore.donationsValues().getExpiredGiftBadge()
    if (expiredGiftBadge != null) {
      SignalStore.donationsValues().setExpiredGiftBadge(null)
      ExpiredGiftSheet.show(childFragmentManager, expiredGiftBadge)
    }

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: ManageDonationsState): DSLConfiguration {
    return configure {
      customPref(
        BadgePreview.BadgeModel.FeaturedModel(
          badge = state.featuredBadge
        )
      )

      space(DimensionUnit.DP.toPixels(8f).toInt())

      sectionHeaderPref(
        title = DSLSettingsText.from(
          R.string.SubscribeFragment__signal_is_powered_by_people_like_you,
          DSLSettingsText.CenterModifier, DSLSettingsText.TitleLargeModifier
        )
      )

      if (state.transactionState is ManageDonationsState.TransactionState.NotInTransaction) {
        val activeSubscription = state.transactionState.activeSubscription.activeSubscription
        if (activeSubscription != null) {
          val subscription: Subscription? = state.availableSubscriptions.firstOrNull { activeSubscription.level == it.level }
          if (subscription != null) {
            presentSubscriptionSettings(activeSubscription, subscription, state.getRedemptionState())
          } else {
            customPref(IndeterminateLoadingCircle)
          }
        } else {
          presentNoSubscriptionSettings()
        }
      } else if (state.transactionState == ManageDonationsState.TransactionState.NetworkFailure) {
        presentNetworkFailureSettings(state.getRedemptionState())
      } else {
        customPref(IndeterminateLoadingCircle)
      }
    }
  }

  private fun DSLConfiguration.presentNetworkFailureSettings(redemptionState: ManageDonationsState.SubscriptionRedemptionState) {
    if (SignalStore.donationsValues().isLikelyASustainer()) {
      presentSubscriptionSettingsWithNetworkError(redemptionState)
    } else {
      presentNoSubscriptionSettings()
    }
  }

  private fun DSLConfiguration.presentSubscriptionSettingsWithNetworkError(redemptionState: ManageDonationsState.SubscriptionRedemptionState) {
    presentSubscriptionSettingsWithState(redemptionState) {
      customPref(
        NetworkFailure.Model(
          onRetryClick = {
            viewModel.retry()
          }
        )
      )
    }
  }

  private fun DSLConfiguration.presentSubscriptionSettings(
    activeSubscription: ActiveSubscription.Subscription,
    subscription: Subscription,
    redemptionState: ManageDonationsState.SubscriptionRedemptionState
  ) {
    presentSubscriptionSettingsWithState(redemptionState) {
      val activeCurrency = Currency.getInstance(activeSubscription.currency)
      val activeAmount = activeSubscription.amount.movePointLeft(activeCurrency.defaultFractionDigits)

      customPref(
        ActiveSubscriptionPreference.Model(
          price = FiatMoney(activeAmount, activeCurrency),
          subscription = subscription,
          renewalTimestamp = TimeUnit.SECONDS.toMillis(activeSubscription.endOfCurrentPeriod),
          redemptionState = redemptionState,
          onContactSupport = {
            requireActivity().finish()
            requireActivity().startActivity(AppSettingsActivity.help(requireContext(), HelpFragment.DONATION_INDEX))
          },
          activeSubscription = activeSubscription
        )
      )
    }
  }

  private fun DSLConfiguration.presentSubscriptionSettingsWithState(
    redemptionState: ManageDonationsState.SubscriptionRedemptionState,
    subscriptionBlock: DSLConfiguration.() -> Unit
  ) {
    space(DimensionUnit.DP.toPixels(32f).toInt())

    noPadTextPref(
      title = DSLSettingsText.from(
        R.string.ManageDonationsFragment__my_subscription,
        DSLSettingsText.Body1BoldModifier, DSLSettingsText.BoldModifier
      )
    )

    space(DimensionUnit.DP.toPixels(12f).toInt())

    subscriptionBlock()

    clickPref(
      title = DSLSettingsText.from(R.string.ManageDonationsFragment__manage_subscription),
      icon = DSLSettingsIcon.from(R.drawable.ic_person_white_24dp),
      isEnabled = redemptionState != ManageDonationsState.SubscriptionRedemptionState.IN_PROGRESS,
      onClick = {
        findNavController().safeNavigate(ManageDonationsFragmentDirections.actionManageDonationsFragmentToSubscribeFragment())
      }
    )

    clickPref(
      title = DSLSettingsText.from(R.string.ManageDonationsFragment__badges),
      icon = DSLSettingsIcon.from(R.drawable.ic_badge_24),
      onClick = {
        findNavController().safeNavigate(ManageDonationsFragmentDirections.actionManageDonationsFragmentToManageBadges())
      }
    )

    presentOtherWaysToGive()

    sectionHeaderPref(R.string.ManageDonationsFragment__more)

    presentDonationReceipts()

    externalLinkPref(
      title = DSLSettingsText.from(R.string.ManageDonationsFragment__subscription_faq),
      icon = DSLSettingsIcon.from(R.drawable.ic_help_24),
      linkId = R.string.donate_url
    )
  }

  private fun DSLConfiguration.presentNoSubscriptionSettings() {
    space(DimensionUnit.DP.toPixels(16f).toInt())

    noPadTextPref(
      title = DSLSettingsText.from(supportTechSummary, DSLSettingsText.CenterModifier)
    )

    space(DimensionUnit.DP.toPixels(16f).toInt())

    tonalButton(
      text = DSLSettingsText.from(R.string.ManageDonationsFragment__make_a_monthly_donation),
      onClick = {
        findNavController().safeNavigate(R.id.action_manageDonationsFragment_to_subscribeFragment)
      }
    )

    presentOtherWaysToGive()

    sectionHeaderPref(R.string.ManageDonationsFragment__receipts)

    presentDonationReceipts()
  }

  private fun DSLConfiguration.presentOtherWaysToGive() {
    dividerPref()

    sectionHeaderPref(R.string.ManageDonationsFragment__other_ways_to_give)

    clickPref(
      title = DSLSettingsText.from(R.string.preferences__one_time_donation),
      icon = DSLSettingsIcon.from(R.drawable.ic_boost_24),
      onClick = {
        findNavController().safeNavigate(ManageDonationsFragmentDirections.actionManageDonationsFragmentToBoosts())
      }
    )

    if (FeatureFlags.giftBadgeSendSupport() && Recipient.self().giftBadgesCapability == Recipient.Capability.SUPPORTED) {
      clickPref(
        title = DSLSettingsText.from(R.string.ManageDonationsFragment__gift_a_badge),
        icon = DSLSettingsIcon.from(R.drawable.ic_gift_24),
        onClick = {
          startActivity(Intent(requireContext(), GiftFlowActivity::class.java))
        }
      )
    }
  }

  private fun DSLConfiguration.presentDonationReceipts() {
    clickPref(
      title = DSLSettingsText.from(R.string.ManageDonationsFragment__donation_receipts),
      icon = DSLSettingsIcon.from(R.drawable.ic_receipt_24),
      onClick = {
        findNavController().safeNavigate(ManageDonationsFragmentDirections.actionManageDonationsFragmentToDonationReceiptListFragment())
      }
    )
  }

  override fun onMakeAMonthlyDonation() {
    findNavController().safeNavigate(ManageDonationsFragmentDirections.actionManageDonationsFragmentToSubscribeFragment())
  }
}
