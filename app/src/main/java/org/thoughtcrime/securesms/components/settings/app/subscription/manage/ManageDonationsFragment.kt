package org.thoughtcrime.securesms.components.settings.app.subscription.manage

import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.util.DimensionUnit
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.BadgePreview
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.SubscriptionsRepository
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.components.settings.models.IndeterminateLoadingCircle
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.help.HelpFragment
import org.thoughtcrime.securesms.subscription.Subscription
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.util.Currency
import java.util.concurrent.TimeUnit

/**
 * Fragment displayed when a user enters "Subscriptions" via app settings but is already
 * a subscriber. Used to manage their current subscription, view badges, and boost.
 */
class ManageDonationsFragment : DSLSettingsFragment() {

  private val viewModel: ManageDonationsViewModel by viewModels(
    factoryProducer = {
      ManageDonationsViewModel.Factory(SubscriptionsRepository(ApplicationDependencies.getDonationsService()))
    }
  )

  private val lifecycleDisposable = LifecycleDisposable()

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    ActiveSubscriptionPreference.register(adapter)
    IndeterminateLoadingCircle.register(adapter)
    BadgePreview.register(adapter)

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }

    lifecycleDisposable.bindTo(viewLifecycleOwner.lifecycle)
    lifecycleDisposable += viewModel.events.subscribe { event: ManageDonationsEvent ->
      when (event) {
        ManageDonationsEvent.NOT_SUBSCRIBED -> handleUserIsNotSubscribed()
        ManageDonationsEvent.ERROR_GETTING_SUBSCRIPTION -> handleErrorGettingSubscription()
      }
    }
  }

  private fun getConfiguration(state: ManageDonationsState): DSLConfiguration {
    return configure {
      customPref(
        BadgePreview.Model(
          badge = state.featuredBadge
        )
      )

      space(DimensionUnit.DP.toPixels(8f).toInt())

      sectionHeaderPref(
        title = DSLSettingsText.from(
          R.string.SubscribeFragment__signal_is_powered_by_people_like_you,
          DSLSettingsText.CenterModifier, DSLSettingsText.Title2BoldModifier
        )
      )

      space(DimensionUnit.DP.toPixels(32f).toInt())

      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.ManageDonationsFragment__my_support,
          DSLSettingsText.Body1BoldModifier, DSLSettingsText.BoldModifier
        )
      )

      if (state.transactionState is ManageDonationsState.TransactionState.NotInTransaction) {
        val activeSubscription = state.transactionState.activeSubscription.activeSubscription
        if (activeSubscription != null) {
          val subscription: Subscription? = state.availableSubscriptions.firstOrNull { activeSubscription.level == it.level }
          if (subscription != null) {
            space(DimensionUnit.DP.toPixels(12f).toInt())

            val activeCurrency = Currency.getInstance(activeSubscription.currency)
            val activeAmount = activeSubscription.amount.movePointLeft(activeCurrency.defaultFractionDigits)

            customPref(
              ActiveSubscriptionPreference.Model(
                price = FiatMoney(activeAmount, activeCurrency),
                subscription = subscription,
                onAddBoostClick = {
                  findNavController().safeNavigate(ManageDonationsFragmentDirections.actionManageDonationsFragmentToBoosts())
                },
                renewalTimestamp = TimeUnit.SECONDS.toMillis(activeSubscription.endOfCurrentPeriod),
                redemptionState = state.getRedemptionState(),
                onContactSupport = {
                  requireActivity().finish()
                  requireActivity().startActivity(AppSettingsActivity.help(requireContext(), HelpFragment.DONATION_INDEX))
                },
                activeSubscription = activeSubscription
              )
            )

            dividerPref()
          } else {
            customPref(IndeterminateLoadingCircle)
          }
        } else {
          customPref(IndeterminateLoadingCircle)
        }
      } else {
        customPref(IndeterminateLoadingCircle)
      }

      clickPref(
        title = DSLSettingsText.from(R.string.ManageDonationsFragment__manage_subscription),
        icon = DSLSettingsIcon.from(R.drawable.ic_person_white_24dp),
        isEnabled = state.getRedemptionState() != ManageDonationsState.SubscriptionRedemptionState.IN_PROGRESS,
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

      externalLinkPref(
        title = DSLSettingsText.from(R.string.ManageDonationsFragment__subscription_faq),
        icon = DSLSettingsIcon.from(R.drawable.ic_help_24),
        linkId = R.string.donate_url
      )

      clickPref(
        title = DSLSettingsText.from(R.string.ManageDonationsFragment__tax_receipts),
        icon = DSLSettingsIcon.from(R.drawable.ic_receipt_24),
        onClick = {
          findNavController().safeNavigate(ManageDonationsFragmentDirections.actionManageDonationsFragmentToDonationReceiptListFragment())
        }
      )
    }
  }

  private fun handleUserIsNotSubscribed() {
    findNavController().popBackStack()
  }

  private fun handleErrorGettingSubscription() {
    Toast.makeText(requireContext(), R.string.ManageDonationsFragment__error_getting_subscription, Toast.LENGTH_LONG).show()
  }
}
