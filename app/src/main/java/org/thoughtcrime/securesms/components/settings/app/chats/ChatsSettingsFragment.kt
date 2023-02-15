package org.thoughtcrime.securesms.components.settings.app.chats

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.chats.sms.SmsExportState
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.exporter.flow.SmsExportActivity
import org.thoughtcrime.securesms.exporter.flow.SmsExportDialogs
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class ChatsSettingsFragment : DSLSettingsFragment(R.string.preferences_chats__chats) {

  private lateinit var viewModel: ChatsSettingsViewModel
  private lateinit var smsExportLauncher: ActivityResultLauncher<Intent>

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  @Suppress("ReplaceGetOrSet")
  override fun bindAdapter(adapter: MappingAdapter) {
    smsExportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      if (it.resultCode == Activity.RESULT_OK) {
        SmsExportDialogs.showSmsRemovalDialog(requireContext(), requireView())
      }
    }

    viewModel = ViewModelProvider(this).get(ChatsSettingsViewModel::class.java)

    viewModel.state.observe(viewLifecycleOwner) {
      adapter.submitList(getConfiguration(it).toMappingModelList())
    }
  }

  private fun getConfiguration(state: ChatsSettingsState): DSLConfiguration {
    return configure {
      if (!state.useAsDefaultSmsApp) {
        when (state.smsExportState) {
          SmsExportState.FETCHING -> Unit
          SmsExportState.HAS_UNEXPORTED_MESSAGES -> {
            clickPref(
              title = DSLSettingsText.from(R.string.SmsSettingsFragment__export_sms_messages),
              summary = DSLSettingsText.from(R.string.SmsSettingsFragment__you_can_export_your_sms_messages_to_your_phones_sms_database),
              onClick = {
                smsExportLauncher.launch(SmsExportActivity.createIntent(requireContext()))
              }
            )

            dividerPref()
          }
          SmsExportState.ALL_MESSAGES_EXPORTED -> {
            clickPref(
              title = DSLSettingsText.from(R.string.SmsSettingsFragment__remove_sms_messages),
              summary = DSLSettingsText.from(R.string.SmsSettingsFragment__remove_sms_messages_from_signal_to_clear_up_storage_space),
              onClick = {
                SmsExportDialogs.showSmsRemovalDialog(requireContext(), requireView())
              }
            )

            clickPref(
              title = DSLSettingsText.from(R.string.SmsSettingsFragment__export_sms_messages_again),
              summary = DSLSettingsText.from(R.string.SmsSettingsFragment__exporting_again_can_result_in_duplicate_messages),
              onClick = {
                SmsExportDialogs.showSmsReExportDialog(requireContext()) {
                  smsExportLauncher.launch(SmsExportActivity.createIntent(requireContext(), isReExport = true))
                }
              }
            )

            dividerPref()
          }
          SmsExportState.NO_SMS_MESSAGES_IN_DATABASE -> Unit
          SmsExportState.NOT_AVAILABLE -> Unit
        }
      } else {
        clickPref(
          title = DSLSettingsText.from(R.string.preferences__sms_mms),
          onClick = {
            Navigation.findNavController(requireView()).safeNavigate(R.id.action_chatsSettingsFragment_to_smsSettingsFragment)
          }
        )

        dividerPref()
      }

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__generate_link_previews),
        summary = DSLSettingsText.from(R.string.preferences__retrieve_link_previews_from_websites_for_messages),
        isChecked = state.generateLinkPreviews,
        onClick = {
          viewModel.setGenerateLinkPreviewsEnabled(!state.generateLinkPreviews)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__pref_use_address_book_photos),
        summary = DSLSettingsText.from(R.string.preferences__display_contact_photos_from_your_address_book_if_available),
        isChecked = state.useAddressBook,
        onClick = {
          viewModel.setUseAddressBook(!state.useAddressBook)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__pref_keep_muted_chats_archived),
        summary = DSLSettingsText.from(R.string.preferences__muted_chats_that_are_archived_will_remain_archived),
        isChecked = state.keepMutedChatsArchived,
        onClick = {
          viewModel.setKeepMutedChatsArchived(!state.keepMutedChatsArchived)
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.ChatsSettingsFragment__keyboard)

      switchPref(
        title = DSLSettingsText.from(R.string.preferences_advanced__use_system_emoji),
        isChecked = state.useSystemEmoji,
        onClick = {
          viewModel.setUseSystemEmoji(!state.useSystemEmoji)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.ChatsSettingsFragment__enter_key_sends),
        isChecked = state.enterKeySends,
        onClick = {
          viewModel.setEnterKeySends(!state.enterKeySends)
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.preferences_chats__backups)

      clickPref(
        title = DSLSettingsText.from(R.string.preferences_chats__chat_backups),
        summary = DSLSettingsText.from(if (state.chatBackupsEnabled) R.string.arrays__enabled else R.string.arrays__disabled),
        onClick = {
          Navigation.findNavController(requireView()).safeNavigate(R.id.action_chatsSettingsFragment_to_backupsPreferenceFragment)
        }
      )
    }
  }
}
