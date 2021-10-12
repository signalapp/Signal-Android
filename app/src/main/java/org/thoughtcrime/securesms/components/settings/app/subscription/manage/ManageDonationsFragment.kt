package org.thoughtcrime.securesms.components.settings.app.subscription.manage

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.subscription.SubscriptionsRepository
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.util.LifecycleDisposable

/**
 * Fragment displayed when a user enters "Subscriptions" via app settings but is already
 * a subscriber. Used to manage their current subscription, view badges, and boost.
 */
class ManageDonationsFragment : DSLSettingsFragment() {

  private val viewModel: ManageDonationsViewModel by viewModels(
    factoryProducer = {
      ManageDonationsViewModel.Factory(SubscriptionsRepository())
    }
  )

  private val lifecycleDisposable = LifecycleDisposable()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val args = ManageDonationsFragmentArgs.fromBundle(requireArguments())
    if (args.skipToSubscribe) {
      findNavController().navigate(
        ManageDonationsFragmentDirections.actionManageDonationsFragmentToSubscribeFragment(),
        NavOptions.Builder().setPopUpTo(R.id.manageDonationsFragment, true).build()
      )
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    val args = ManageDonationsFragmentArgs.fromBundle(requireArguments())
    if (args.skipToSubscribe) {
      return
    }

    ActiveSubscriptionPreference.register(adapter)

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
      sectionHeaderPref(
        title = DSLSettingsText.from(
          R.string.SubscribeFragment__signal_is_powered_by_people_like_you,
          DSLSettingsText.CenterModifier, DSLSettingsText.Title2BoldModifier
        )
      )

      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.ManageDonationsFragment__my_support,
          DSLSettingsText.Title2BoldModifier
        )
      )

      if (state.activeSubscription != null) {
        customPref(
          ActiveSubscriptionPreference.Model(
            subscription = state.activeSubscription,
            onAddBoostClick = {
              findNavController().navigate(ManageDonationsFragmentDirections.actionManageDonationsFragmentToBoosts())
            }
          )
        )

        dividerPref()
      }

      clickPref(
        title = DSLSettingsText.from(R.string.ManageDonationsFragment__manage_subscription),
        icon = DSLSettingsIcon.from(R.drawable.ic_person_white_24dp),
        onClick = {
          findNavController().navigate(ManageDonationsFragmentDirections.actionManageDonationsFragmentToSubscribeFragment())
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.ManageDonationsFragment__badges),
        icon = DSLSettingsIcon.from(R.drawable.ic_badge_24),
        onClick = {
          findNavController().navigate(ManageDonationsFragmentDirections.actionManageDonationsFragmentToSubscriptionBadgeManageFragment())
        }
      )

      externalLinkPref(
        title = DSLSettingsText.from(R.string.ManageDonationsFragment__subscription_faq),
        icon = DSLSettingsIcon.from(R.drawable.ic_help_24),
        linkId = R.string.donate_url
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
