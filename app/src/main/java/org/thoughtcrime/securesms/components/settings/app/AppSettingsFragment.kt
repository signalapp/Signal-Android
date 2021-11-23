package org.thoughtcrime.securesms.components.settings.app

import android.view.View
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.components.settings.PreferenceViewHolder
import org.thoughtcrime.securesms.components.settings.app.subscription.SubscriptionsRepository
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder
import org.thoughtcrime.securesms.util.PlayServicesUtil

class AppSettingsFragment : DSLSettingsFragment(R.string.text_secure_normal__menu_settings) {

  private val viewModel: AppSettingsViewModel by viewModels(
    factoryProducer = {
      AppSettingsViewModel.Factory(SubscriptionsRepository(ApplicationDependencies.getDonationsService()))
    }
  )

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    adapter.registerFactory(BioPreference::class.java, MappingAdapter.LayoutFactory(::BioPreferenceViewHolder, R.layout.bio_preference_item))
    adapter.registerFactory(PaymentsPreference::class.java, MappingAdapter.LayoutFactory(::PaymentsPreferenceViewHolder, R.layout.dsl_payments_preference))
    adapter.registerFactory(SubscriptionPreference::class.java, MappingAdapter.LayoutFactory(::SubscriptionPreferenceViewHolder, R.layout.dsl_preference_item))

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshActiveSubscription()
  }

  private fun getConfiguration(state: AppSettingsState): DSLConfiguration {
    return configure {

      customPref(
        BioPreference(state.self) {
          Navigation.findNavController(requireView()).navigate(R.id.action_appSettingsFragment_to_manageProfileActivity)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.AccountSettingsFragment__account),
        icon = DSLSettingsIcon.from(R.drawable.ic_profile_circle_24),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_appSettingsFragment_to_accountSettingsFragment)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__linked_devices),
        icon = DSLSettingsIcon.from(R.drawable.ic_linked_devices_24),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_appSettingsFragment_to_deviceActivity)
        }
      )

      if (SignalStore.paymentsValues().paymentsAvailability.showPaymentsMenu()) {
        customPref(
          PaymentsPreference(
            unreadCount = state.unreadPaymentsCount
          ) {
            Navigation.findNavController(requireView()).navigate(R.id.action_appSettingsFragment_to_paymentsActivity)
          }
        )
      }

      dividerPref()

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__appearance),
        icon = DSLSettingsIcon.from(R.drawable.ic_appearance_24),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_appSettingsFragment_to_appearanceSettingsFragment)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences_chats__chats),
        icon = DSLSettingsIcon.from(R.drawable.ic_message_tinted_bitmap_24),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_appSettingsFragment_to_chatsSettingsFragment)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__notifications),
        icon = DSLSettingsIcon.from(R.drawable.ic_bell_24),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_appSettingsFragment_to_notificationsSettingsFragment)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__privacy),
        icon = DSLSettingsIcon.from(R.drawable.ic_lock_24),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_appSettingsFragment_to_privacySettingsFragment)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__data_and_storage),
        icon = DSLSettingsIcon.from(R.drawable.ic_archive_24dp),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_appSettingsFragment_to_dataAndStorageSettingsFragment)
        }
      )

      dividerPref()

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__help),
        icon = DSLSettingsIcon.from(R.drawable.ic_help_24),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_appSettingsFragment_to_helpSettingsFragment)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.AppSettingsFragment__invite_your_friends),
        icon = DSLSettingsIcon.from(R.drawable.ic_invite_24),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_appSettingsFragment_to_inviteActivity)
        }
      )

      if (FeatureFlags.donorBadges() && PlayServicesUtil.getPlayServicesStatus(requireContext()) == PlayServicesUtil.PlayServicesStatus.SUCCESS) {
        customPref(
          SubscriptionPreference(
            title = DSLSettingsText.from(
              if (state.hasActiveSubscription) {
                R.string.preferences__subscription
              } else {
                R.string.preferences__become_a_signal_sustainer
              }
            ),
            icon = DSLSettingsIcon.from(R.drawable.ic_heart_24),
            isActive = state.hasActiveSubscription,
            onClick = { isActive ->
              if (isActive) {
                findNavController().navigate(AppSettingsFragmentDirections.actionAppSettingsFragmentToManageDonationsFragment())
              } else {
                findNavController().navigate(AppSettingsFragmentDirections.actionAppSettingsFragmentToSubscribeFragment())
              }
            }
          )
        )
        clickPref(
          title = DSLSettingsText.from(R.string.preferences__signal_boost),
          icon = DSLSettingsIcon.from(R.drawable.ic_boost_24),
          onClick = {
            findNavController().navigate(AppSettingsFragmentDirections.actionAppSettingsFragmentToBoostsFragment())
          }
        )
      } else {
        externalLinkPref(
          title = DSLSettingsText.from(R.string.preferences__donate_to_signal),
          icon = DSLSettingsIcon.from(R.drawable.ic_heart_24),
          linkId = R.string.donate_url
        )
      }

      if (FeatureFlags.internalUser()) {
        dividerPref()

        clickPref(
          title = DSLSettingsText.from(R.string.preferences__internal_preferences),
          onClick = {
            Navigation.findNavController(requireView()).navigate(R.id.action_appSettingsFragment_to_internalSettingsFragment)
          }
        )
      }
    }
  }

  private class SubscriptionPreference(
    override val title: DSLSettingsText,
    override val summary: DSLSettingsText? = null,
    override val icon: DSLSettingsIcon? = null,
    override val isEnabled: Boolean = true,
    val isActive: Boolean = false,
    val onClick: (Boolean) -> Unit
  ) : PreferenceModel<SubscriptionPreference>() {
    override fun areItemsTheSame(newItem: SubscriptionPreference): Boolean {
      return true
    }

    override fun areContentsTheSame(newItem: SubscriptionPreference): Boolean {
      return super.areContentsTheSame(newItem) && isActive == newItem.isActive
    }
  }

  private class SubscriptionPreferenceViewHolder(itemView: View) : PreferenceViewHolder<SubscriptionPreference>(itemView) {
    override fun bind(model: SubscriptionPreference) {
      super.bind(model)
      itemView.setOnClickListener { model.onClick(model.isActive) }
    }
  }

  private class BioPreference(val recipient: Recipient, val onClick: () -> Unit) : PreferenceModel<BioPreference>() {
    override fun areContentsTheSame(newItem: BioPreference): Boolean {
      return super.areContentsTheSame(newItem) && recipient.hasSameContent(newItem.recipient)
    }

    override fun areItemsTheSame(newItem: BioPreference): Boolean {
      return recipient == newItem.recipient
    }
  }

  private class BioPreferenceViewHolder(itemView: View) : PreferenceViewHolder<BioPreference>(itemView) {

    private val avatarView: AvatarImageView = itemView.findViewById(R.id.icon)
    private val aboutView: TextView = itemView.findViewById(R.id.about)
    private val badgeView: BadgeImageView = itemView.findViewById(R.id.badge)

    override fun bind(model: BioPreference) {
      super.bind(model)

      itemView.setOnClickListener { model.onClick() }

      titleView.text = model.recipient.profileName.toString()
      summaryView.text = PhoneNumberFormatter.prettyPrint(model.recipient.requireE164())
      avatarView.setRecipient(Recipient.self())
      badgeView.setBadgeFromRecipient(Recipient.self())

      titleView.visibility = View.VISIBLE
      summaryView.visibility = View.VISIBLE
      avatarView.visibility = View.VISIBLE

      if (model.recipient.combinedAboutAndEmoji != null) {
        aboutView.text = model.recipient.combinedAboutAndEmoji
        aboutView.visibility = View.VISIBLE
      } else {
        aboutView.visibility = View.GONE
      }
    }
  }

  private class PaymentsPreference(val unreadCount: Int, val onClick: () -> Unit) : PreferenceModel<PaymentsPreference>() {
    override fun areContentsTheSame(newItem: PaymentsPreference): Boolean {
      return super.areContentsTheSame(newItem) && unreadCount == newItem.unreadCount
    }

    override fun areItemsTheSame(newItem: PaymentsPreference): Boolean {
      return true
    }
  }

  private class PaymentsPreferenceViewHolder(itemView: View) : MappingViewHolder<PaymentsPreference>(itemView) {

    private val unreadCountView: TextView = itemView.findViewById(R.id.unread_indicator)

    override fun bind(model: PaymentsPreference) {
      unreadCountView.text = model.unreadCount.toString()
      unreadCountView.visibility = if (model.unreadCount > 0) View.VISIBLE else View.GONE

      itemView.setOnClickListener {
        model.onClick()
      }
    }
  }
}
