package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiUtil
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.NO_TINT
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.models.NotificationProfileAddMembers
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.models.NotificationProfilePreference
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.models.NotificationProfileRecipient
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.components.settings.conversation.preferences.LargeIconClickPreference
import org.thoughtcrime.securesms.components.settings.conversation.preferences.RecipientPreference
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.formatHours
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.orderOfDaysInWeek
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private const val MEMBER_COUNT_TO_SHOW_EXPAND: Int = 5

class NotificationProfileDetailsFragment : DSLSettingsFragment() {

  private val viewModel: NotificationProfileDetailsViewModel by viewModels(factoryProducer = this::createFactory)

  private val lifecycleDisposable = LifecycleDisposable()
  private var toolbar: Toolbar? = null

  private fun createFactory(): ViewModelProvider.Factory {
    return NotificationProfileDetailsViewModel.Factory(NotificationProfileDetailsFragmentArgs.fromBundle(requireArguments()).profileId)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    toolbar = view.findViewById(R.id.toolbar)
    toolbar?.inflateMenu(R.menu.notification_profile_details)

    lifecycleDisposable.bindTo(viewLifecycleOwner.lifecycle)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    toolbar = null
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    NotificationProfilePreference.register(adapter)
    NotificationProfileAddMembers.register(adapter)
    NotificationProfileRecipient.register(adapter)
    LargeIconClickPreference.register(adapter)

    viewModel.state.observe(viewLifecycleOwner) { state ->
      when (state) {
        is NotificationProfileDetailsViewModel.State.Valid -> {
          toolbar?.title = state.profile.name
          toolbar?.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_edit) {
              findNavController().safeNavigate(NotificationProfileDetailsFragmentDirections.actionNotificationProfileDetailsFragmentToEditNotificationProfileFragment().setProfileId(state.profile.id))
              true
            } else {
              false
            }
          }
          adapter.submitList(getConfiguration(state).toMappingModelList())
        }
        NotificationProfileDetailsViewModel.State.NotLoaded -> Unit
        NotificationProfileDetailsViewModel.State.Invalid -> requireActivity().onBackPressed()
      }
    }
  }

  private fun getConfiguration(state: NotificationProfileDetailsViewModel.State.Valid): DSLConfiguration {
    val (profile: NotificationProfile, recipients: List<Recipient>, isOn: Boolean, expanded: Boolean) = state

    return configure {
      customPref(
        NotificationProfilePreference.Model(
          title = DSLSettingsText.from(profile.name),
          summary = if (isOn) DSLSettingsText.from(NotificationProfiles.getActiveProfileDescription(requireContext(), profile)) else null,
          icon = if (profile.emoji.isNotEmpty()) EmojiUtil.convertToDrawable(requireContext(), profile.emoji)?.let { DSLSettingsIcon.from(it) } else DSLSettingsIcon.from(R.drawable.ic_moon_24, NO_TINT),
          color = profile.color,
          isOn = isOn,
          showSwitch = true,
          onClick = {
            lifecycleDisposable += viewModel.toggleEnabled(profile)
              .subscribe()
          }
        )
      )

      dividerPref()

      sectionHeaderPref(R.string.AddAllowedMembers__allowed_notifications)
      customPref(
        NotificationProfileAddMembers.Model(
          onClick = { id, currentSelection ->
            findNavController().safeNavigate(
              NotificationProfileDetailsFragmentDirections.actionNotificationProfileDetailsFragmentToSelectRecipientsFragment(id)
                .setCurrentSelection(currentSelection.toTypedArray())
            )
          },
          profileId = profile.id,
          currentSelection = profile.allowedMembers
        )
      )

      val membersToShow = if (expanded || recipients.size <= MEMBER_COUNT_TO_SHOW_EXPAND) {
        recipients
      } else {
        recipients.slice(0 until MEMBER_COUNT_TO_SHOW_EXPAND)
      }

      for (member in membersToShow) {
        customPref(
          NotificationProfileRecipient.Model(
            recipientModel = RecipientPreference.Model(
              recipient = member
            ),
            onRemoveClick = { id ->
              lifecycleDisposable += viewModel.removeMember(id)
                .subscribeBy(
                  onSuccess = { removed ->
                    view?.let { view ->
                      Snackbar.make(view, getString(R.string.NotificationProfileDetails__s_removed, removed.getDisplayName(requireContext())), Snackbar.LENGTH_LONG)
                        .setAction(R.string.NotificationProfileDetails__undo) { undoRemove(id) }
                        .show()
                    }
                  }
                )
            }
          )
        )
      }

      if (!expanded && membersToShow != recipients) {
        customPref(
          LargeIconClickPreference.Model(
            title = DSLSettingsText.from(R.string.NotificationProfileDetails__see_all),
            icon = DSLSettingsIcon.from(R.drawable.show_more, NO_TINT),
            onClick = viewModel::showAllMembers
          )
        )
      }

      dividerPref()
      sectionHeaderPref(R.string.NotificationProfileDetails__schedule)
      clickPref(
        title = DSLSettingsText.from(profile.schedule.describe()),
        summary = DSLSettingsText.from(if (profile.schedule.enabled) R.string.NotificationProfileDetails__on else R.string.NotificationProfileDetails__off),
        icon = DSLSettingsIcon.from(R.drawable.symbol_recent_24),
        onClick = {
          findNavController().safeNavigate(NotificationProfileDetailsFragmentDirections.actionNotificationProfileDetailsFragmentToEditNotificationProfileScheduleFragment(profile.id, false))
        }
      )

      dividerPref()
      sectionHeaderPref(R.string.NotificationProfileDetails__exceptions)
      switchPref(
        title = DSLSettingsText.from(R.string.NotificationProfileDetails__allow_all_calls),
        isChecked = profile.allowAllCalls,
        icon = DSLSettingsIcon.from(R.drawable.symbol_phone_24),
        onClick = {
          lifecycleDisposable += viewModel.toggleAllowAllCalls()
            .subscribe()
        }
      )
      switchPref(
        title = DSLSettingsText.from(R.string.NotificationProfileDetails__notify_for_all_mentions),
        icon = DSLSettingsIcon.from(R.drawable.symbol_at_24),
        isChecked = profile.allowAllMentions,
        onClick = {
          lifecycleDisposable += viewModel.toggleAllowAllMentions()
            .subscribe()
        }
      )

      dividerPref()
      clickPref(
        title = DSLSettingsText.from(R.string.NotificationProfileDetails__delete_profile, ContextCompat.getColor(requireContext(), R.color.signal_alert_primary)),
        icon = DSLSettingsIcon.from(R.drawable.symbol_trash_24, R.color.signal_alert_primary),
        onClick = {
          MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.NotificationProfileDetails__permanently_delete_profile)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
              SpanUtil.color(
                ContextCompat.getColor(requireContext(), R.color.signal_alert_primary),
                getString(R.string.NotificationProfileDetails__delete)
              )
            ) { _, _ -> deleteProfile() }
            .show()
        }
      )
    }
  }

  private fun deleteProfile() {
    lifecycleDisposable += viewModel.deleteProfile()
      .subscribe()
  }

  private fun undoRemove(id: RecipientId) {
    lifecycleDisposable += viewModel.addMember(id)
      .subscribe()
  }

  private fun NotificationProfileSchedule.describe(): String {
    if (!enabled) {
      return getString(R.string.NotificationProfileDetails__schedule)
    }

    val startTime = startTime().formatHours(requireContext())
    val endTime = endTime().formatHours(requireContext())

    val days = StringBuilder()
    if (daysEnabled.size == 7) {
      days.append(getString(R.string.NotificationProfileDetails__everyday))
    } else {
      for (day: DayOfWeek in Locale.getDefault().orderOfDaysInWeek()) {
        if (daysEnabled.contains(day)) {
          if (days.isNotEmpty()) {
            days.append(", ")
          }
          days.append(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
        }
      }
    }

    return getString(R.string.NotificationProfileDetails__s_to_s, startTime, endTime).let { hours ->
      if (days.isNotEmpty()) "$hours\n$days" else hours
    }
  }
}
