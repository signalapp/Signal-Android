package org.thoughtcrime.securesms.components.settings.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.isNotNullOrBlank
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.components.reminder.ExpiredBuildReminder
import org.thoughtcrime.securesms.components.reminder.Reminder
import org.thoughtcrime.securesms.components.reminder.ReminderView
import org.thoughtcrime.securesms.components.reminder.UnauthorizedReminder
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.components.settings.PreferenceViewHolder
import org.thoughtcrime.securesms.components.settings.app.subscription.completed.TerminalDonationDelegate
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.events.ReminderUpdateEvent
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.RegistrationNavigationActivity
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.views.Stub
import org.thoughtcrime.securesms.util.visible

class AppSettingsFragment : DSLSettingsFragment(
  titleId = R.string.text_secure_normal__menu_settings,
  layoutId = R.layout.dsl_settings_fragment_with_reminder
) {

  private val viewModel: AppSettingsViewModel by viewModels()

  private lateinit var reminderView: Stub<ReminderView>

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewLifecycleOwner.lifecycle.addObserver(TerminalDonationDelegate(childFragmentManager, viewLifecycleOwner))

    super.onViewCreated(view, savedInstanceState)
    reminderView = ViewUtil.findStubById(view, R.id.reminder_stub)

    updateReminders()
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    adapter.registerFactory(BioPreference::class.java, LayoutFactory(::BioPreferenceViewHolder, R.layout.bio_preference_item))
    adapter.registerFactory(PaymentsPreference::class.java, LayoutFactory(::PaymentsPreferenceViewHolder, R.layout.dsl_payments_preference))
    adapter.registerFactory(SubscriptionPreference::class.java, LayoutFactory(::SubscriptionPreferenceViewHolder, R.layout.dsl_preference_item))

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onEvent(event: ReminderUpdateEvent?) {
    updateReminders()
  }

  private fun updateReminders() {
    if (ExpiredBuildReminder.isEligible()) {
      showReminder(ExpiredBuildReminder(context))
    } else if (UnauthorizedReminder.isEligible(context)) {
      showReminder(UnauthorizedReminder())
    } else {
      hideReminders()
    }
    viewModel.refreshDeprecatedOrUnregistered()
  }

  private fun showReminder(reminder: Reminder) {
    if (!reminderView.resolved()) {
      reminderView.get().addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
        recyclerView?.setPadding(0, bottom - top, 0, 0)
      }
      recyclerView?.clipToPadding = false
    }
    reminderView.get().showReminder(reminder)
    reminderView.get().setOnActionClickListener { reminderActionId: Int -> this.handleReminderAction(reminderActionId) }
  }

  private fun hideReminders() {
    if (reminderView.resolved()) {
      reminderView.get().hide()
      recyclerView?.clipToPadding = true
    }
  }

  private fun handleReminderAction(@IdRes reminderActionId: Int) {
    when (reminderActionId) {
      R.id.reminder_action_update_now -> {
        PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext())
      }
      R.id.reminder_action_re_register -> {
        startActivity(RegistrationNavigationActivity.newIntentForReRegistration(requireContext()))
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshExpiredGiftBadge()
    EventBus.getDefault().register(this)
  }

  override fun onPause() {
    super.onPause()
    EventBus.getDefault().unregister(this)
  }

  private fun getConfiguration(state: AppSettingsState): DSLConfiguration {
    return configure {
      customPref(
        BioPreference(
          recipient = state.self,
          onRowClicked = {
            findNavController().safeNavigate(R.id.action_appSettingsFragment_to_manageProfileActivity)
          },
          onQrButtonClicked = {
            if (SignalStore.account().username != null) {
              findNavController().safeNavigate(R.id.action_appSettingsFragment_to_usernameLinkSettingsFragment)
            } else {
              findNavController().safeNavigate(R.id.action_appSettingsFragment_to_usernameEducationFragment)
            }
          }
        )
      )

      clickPref(
        title = DSLSettingsText.from(R.string.AccountSettingsFragment__account),
        icon = DSLSettingsIcon.from(R.drawable.symbol_person_circle_24),
        onClick = {
          findNavController().safeNavigate(R.id.action_appSettingsFragment_to_accountSettingsFragment)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__linked_devices),
        icon = DSLSettingsIcon.from(R.drawable.symbol_devices_24),
        onClick = {
          findNavController().safeNavigate(R.id.action_appSettingsFragment_to_deviceActivity)
        },
        isEnabled = state.isDeprecatedOrUnregistered()
      )

      if (state.allowUserToGoToDonationManagementScreen) {
        clickPref(
          title = DSLSettingsText.from(R.string.preferences__donate_to_signal),
          icon = DSLSettingsIcon.from(R.drawable.symbol_heart_24),
          iconEnd = if (state.hasExpiredGiftBadge) DSLSettingsIcon.from(R.drawable.symbol_info_fill_24, R.color.signal_accent_primary) else null,
          onClick = {
            findNavController().safeNavigate(AppSettingsFragmentDirections.actionAppSettingsFragmentToManageDonationsFragment())
          },
          onLongClick = this@AppSettingsFragment::copySubscriberIdToClipboard
        )
      } else {
        externalLinkPref(
          title = DSLSettingsText.from(R.string.preferences__donate_to_signal),
          icon = DSLSettingsIcon.from(R.drawable.symbol_heart_24),
          linkId = R.string.donate_url
        )
      }

      dividerPref()

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__appearance),
        icon = DSLSettingsIcon.from(R.drawable.symbol_appearance_24),
        onClick = {
          findNavController().safeNavigate(R.id.action_appSettingsFragment_to_appearanceSettingsFragment)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences_chats__chats),
        icon = DSLSettingsIcon.from(R.drawable.symbol_chat_24),
        onClick = {
          findNavController().safeNavigate(R.id.action_appSettingsFragment_to_chatsSettingsFragment)
        },
        isEnabled = state.isDeprecatedOrUnregistered()
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__stories),
        icon = DSLSettingsIcon.from(R.drawable.symbol_stories_24),
        onClick = {
          findNavController().safeNavigate(AppSettingsFragmentDirections.actionAppSettingsFragmentToStoryPrivacySettings(R.string.preferences__stories))
        },
        isEnabled = state.isDeprecatedOrUnregistered()
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__notifications),
        icon = DSLSettingsIcon.from(R.drawable.symbol_bell_24),
        onClick = {
          findNavController().safeNavigate(R.id.action_appSettingsFragment_to_notificationsSettingsFragment)
        },
        isEnabled = state.isDeprecatedOrUnregistered()
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__privacy),
        icon = DSLSettingsIcon.from(R.drawable.symbol_lock_white_48),
        onClick = {
          findNavController().safeNavigate(R.id.action_appSettingsFragment_to_privacySettingsFragment)
        },
        isEnabled = state.isDeprecatedOrUnregistered()
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__data_and_storage),
        icon = DSLSettingsIcon.from(R.drawable.symbol_data_24),
        onClick = {
          findNavController().safeNavigate(R.id.action_appSettingsFragment_to_dataAndStorageSettingsFragment)
        }
      )

      if (Environment.IS_NIGHTLY) {
        clickPref(
          title = DSLSettingsText.from("App updates"),
          icon = DSLSettingsIcon.from(R.drawable.symbol_calendar_24),
          onClick = {
            findNavController().safeNavigate(R.id.action_appSettingsFragment_to_appUpdatesSettingsFragment)
          }
        )
      }

      dividerPref()

      if (SignalStore.paymentsValues().paymentsAvailability.showPaymentsMenu()) {
        customPref(
          PaymentsPreference(
            unreadCount = state.unreadPaymentsCount
          ) {
            findNavController().safeNavigate(R.id.action_appSettingsFragment_to_paymentsActivity)
          }
        )

        dividerPref()
      }

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__help),
        icon = DSLSettingsIcon.from(R.drawable.symbol_help_24),
        onClick = {
          findNavController().safeNavigate(R.id.action_appSettingsFragment_to_helpSettingsFragment)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.AppSettingsFragment__invite_your_friends),
        icon = DSLSettingsIcon.from(R.drawable.symbol_invite_24),
        onClick = {
          findNavController().safeNavigate(R.id.action_appSettingsFragment_to_inviteActivity)
        }
      )

      if (FeatureFlags.internalUser()) {
        dividerPref()

        clickPref(
          title = DSLSettingsText.from(R.string.preferences__internal_preferences),
          onClick = {
            findNavController().safeNavigate(R.id.action_appSettingsFragment_to_internalSettingsFragment)
          }
        )
      }
    }
  }

  private fun copySubscriberIdToClipboard(): Boolean {
    val subscriber = SignalStore.donationsValues().getSubscriber()
    return if (subscriber == null) {
      false
    } else {
      Toast.makeText(requireContext(), R.string.AppSettingsFragment__copied_subscriber_id_to_clipboard, Toast.LENGTH_LONG).show()
      Util.copyToClipboard(requireContext(), subscriber.subscriberId.serialize())
      true
    }
  }

  private class SubscriptionPreference(
    override val title: DSLSettingsText,
    override val summary: DSLSettingsText? = null,
    override val icon: DSLSettingsIcon? = null,
    override val isEnabled: Boolean = true,
    val isActive: Boolean = false,
    val onClick: (Boolean) -> Unit,
    val onLongClick: () -> Boolean
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
      itemView.setOnLongClickListener { model.onLongClick() }
    }
  }

  private class BioPreference(val recipient: Recipient, val onRowClicked: () -> Unit, val onQrButtonClicked: () -> Unit) : PreferenceModel<BioPreference>() {
    override fun areContentsTheSame(newItem: BioPreference): Boolean {
      return super.areContentsTheSame(newItem) && recipient.hasSameContent(newItem.recipient)
    }

    override fun areItemsTheSame(newItem: BioPreference): Boolean {
      return recipient == newItem.recipient
    }
  }

  private class BioPreferenceViewHolder(itemView: View) : PreferenceViewHolder<BioPreference>(itemView) {

    private val avatarView: AvatarImageView = itemView.findViewById(R.id.icon)
    private val aboutView: EmojiTextView = itemView.findViewById(R.id.about)
    private val badgeView: BadgeImageView = itemView.findViewById(R.id.badge)
    private val qrButton: View = itemView.findViewById(R.id.qr_button)
    private val usernameView: TextView = itemView.findViewById(R.id.username)

    init {
      aboutView.setOverflowText(" ")
    }

    override fun bind(model: BioPreference) {
      super.bind(model)

      itemView.setOnClickListener { model.onRowClicked() }

      titleView.text = model.recipient.profileName.toString()
      summaryView.text = PhoneNumberFormatter.prettyPrint(model.recipient.requireE164())
      usernameView.text = model.recipient.username.orElse("")
      usernameView.visible = model.recipient.username.isPresent
      avatarView.setRecipient(Recipient.self())
      badgeView.setBadgeFromRecipient(Recipient.self())

      titleView.visibility = View.VISIBLE
      summaryView.visibility = View.VISIBLE
      avatarView.visibility = View.VISIBLE

      if (SignalStore.account().username.isNotNullOrBlank()) {
        qrButton.visibility = View.VISIBLE
        qrButton.isClickable = true
        qrButton.setOnClickListener { model.onQrButtonClicked() }
      } else {
        qrButton.visibility = View.GONE
      }

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
