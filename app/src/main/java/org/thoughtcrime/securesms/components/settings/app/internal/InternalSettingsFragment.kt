package org.thoughtcrime.securesms.components.settings.app.internal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.AppUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.concurrent.SimpleTask
import org.signal.ringrtc.CallManager
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.database.LocalMetricsDatabase
import org.thoughtcrime.securesms.database.LogDatabase
import org.thoughtcrime.securesms.database.MegaphoneDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.jobs.DownloadLatestEmojiDataJob
import org.thoughtcrime.securesms.jobs.EmojiSearchIndexDownloadJob
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob
import org.thoughtcrime.securesms.jobs.RemoteConfigRefreshJob
import org.thoughtcrime.securesms.jobs.RetrieveRemoteAnnouncementsJob
import org.thoughtcrime.securesms.jobs.RotateProfileKeyJob
import org.thoughtcrime.securesms.jobs.StorageForcePushJob
import org.thoughtcrime.securesms.jobs.SubscriptionKeepAliveJob
import org.thoughtcrime.securesms.jobs.SubscriptionReceiptRequestResponseJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.megaphone.MegaphoneRepository
import org.thoughtcrime.securesms.megaphone.Megaphones
import org.thoughtcrime.securesms.payments.DataExportUtil
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

class InternalSettingsFragment : DSLSettingsFragment(R.string.preferences__internal_preferences) {

  private lateinit var viewModel: InternalSettingsViewModel

  override fun bindAdapter(adapter: MappingAdapter) {
    val repository = InternalSettingsRepository(requireContext())
    val factory = InternalSettingsViewModel.Factory(repository)
    viewModel = ViewModelProvider(this, factory)[InternalSettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) {
      adapter.submitList(getConfiguration(it).toMappingModelList())
    }
  }

  private fun getConfiguration(state: InternalSettingsState): DSLConfiguration {
    return configure {
      sectionHeaderPref(R.string.preferences__internal_account)

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_refresh_attributes),
        summary = DSLSettingsText.from(R.string.preferences__internal_refresh_attributes_description),
        onClick = {
          refreshAttributes()
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_refresh_profile),
        summary = DSLSettingsText.from(R.string.preferences__internal_refresh_profile_description),
        onClick = {
          refreshProfile()
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_rotate_profile_key),
        summary = DSLSettingsText.from(R.string.preferences__internal_rotate_profile_key_description),
        onClick = {
          rotateProfileKey()
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_refresh_remote_config),
        summary = DSLSettingsText.from(R.string.preferences__internal_refresh_remote_config_description),
        onClick = {
          refreshRemoteValues()
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.preferences__internal_misc)

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__internal_user_details),
        summary = DSLSettingsText.from(R.string.preferences__internal_user_details_description),
        isChecked = state.seeMoreUserDetails,
        onClick = {
          viewModel.setSeeMoreUserDetails(!state.seeMoreUserDetails)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__internal_shake_to_report),
        summary = DSLSettingsText.from(R.string.preferences__internal_shake_to_report_description),
        isChecked = state.shakeToReport,
        onClick = {
          viewModel.setShakeToReport(!state.shakeToReport)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_clear_keep_longer_logs),
        onClick = {
          clearKeepLongerLogs()
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.preferences__internal_payments)

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_payment_copy_data),
        summary = DSLSettingsText.from(R.string.preferences__internal_payment_copy_data_description),
        onClick = {
          copyPaymentsDataToClipboard()
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.preferences__internal_storage_service)

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__internal_disable_storage_service),
        summary = DSLSettingsText.from(R.string.preferences__internal_disable_storage_service_description),
        isChecked = state.disableStorageService,
        onClick = {
          viewModel.setDisableStorageService(!state.disableStorageService)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_sync_now),
        summary = DSLSettingsText.from(R.string.preferences__internal_sync_now_description),
        onClick = {
          enqueueStorageServiceSync()
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_force_storage_service_sync),
        summary = DSLSettingsText.from(R.string.preferences__internal_force_storage_service_sync_description),
        onClick = {
          enqueueStorageServiceForcePush()
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.preferences__internal_preferences_groups_v2)

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__internal_force_gv2_invites),
        summary = DSLSettingsText.from(R.string.preferences__internal_force_gv2_invites_description),
        isChecked = state.gv2forceInvites,
        onClick = {
          viewModel.setGv2ForceInvites(!state.gv2forceInvites)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__internal_ignore_gv2_server_changes),
        summary = DSLSettingsText.from(R.string.preferences__internal_ignore_gv2_server_changes_description),
        isChecked = state.gv2ignoreServerChanges,
        onClick = {
          viewModel.setGv2IgnoreServerChanges(!state.gv2ignoreServerChanges)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__internal_ignore_gv2_p2p_changes),
        summary = DSLSettingsText.from(R.string.preferences__internal_ignore_gv2_server_changes_description),
        isChecked = state.gv2ignoreP2PChanges,
        onClick = {
          viewModel.setGv2IgnoreP2PChanges(!state.gv2ignoreP2PChanges)
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.preferences__internal_network)

      switchPref(
        title = DSLSettingsText.from("Force websocket mode"),
        summary = DSLSettingsText.from("Pretend you have no Play Services. Ignores websocket messages and keeps the websocket open in a foreground service. You have to manually force-stop the app for changes to take effect."),
        isChecked = state.forceWebsocketMode,
        onClick = {
          viewModel.setForceWebsocketMode(!state.forceWebsocketMode)
          SimpleTask.run({
            val jobState = ApplicationDependencies.getJobManager().runSynchronously(RefreshAttributesJob(), 10.seconds.inWholeMilliseconds)
            return@run jobState.isPresent && jobState.get().isComplete
          }, { success ->
            if (success) {
              Toast.makeText(context, "Successfully refreshed attributes. Force-stop the app for changes to take effect.", Toast.LENGTH_SHORT).show()
            } else {
              Toast.makeText(context, "Failed to refresh attributes.", Toast.LENGTH_SHORT).show()
            }
          })
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__internal_allow_censorship_toggle),
        summary = DSLSettingsText.from(R.string.preferences__internal_allow_censorship_toggle_description),
        isChecked = state.allowCensorshipSetting,
        onClick = {
          viewModel.setAllowCensorshipSetting(!state.allowCensorshipSetting)
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.preferences__internal_conversations_and_shortcuts)

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_delete_all_dynamic_shortcuts),
        summary = DSLSettingsText.from(R.string.preferences__internal_click_to_delete_all_dynamic_shortcuts),
        onClick = {
          deleteAllDynamicShortcuts()
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.preferences__internal_emoji)

      val emojiSummary = if (state.emojiVersion == null) {
        getString(R.string.preferences__internal_use_built_in_emoji_set)
      } else {
        getString(
          R.string.preferences__internal_current_version_d_at_density_s,
          state.emojiVersion.version,
          state.emojiVersion.density
        )
      }

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__internal_use_built_in_emoji_set),
        summary = DSLSettingsText.from(emojiSummary),
        isChecked = state.useBuiltInEmojiSet,
        onClick = {
          viewModel.setUseBuiltInEmoji(!state.useBuiltInEmojiSet)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_force_emoji_download),
        summary = DSLSettingsText.from(R.string.preferences__internal_force_emoji_download_description),
        onClick = {
          ApplicationDependencies.getJobManager().add(DownloadLatestEmojiDataJob(true))
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_force_search_index_download),
        summary = DSLSettingsText.from(R.string.preferences__internal_force_search_index_download_description),
        onClick = {
          EmojiSearchIndexDownloadJob.scheduleImmediately()
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.preferences__internal_sender_key)

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_clear_all_state),
        summary = DSLSettingsText.from(R.string.preferences__internal_click_to_delete_all_sender_key_state),
        onClick = {
          clearAllSenderKeyState()
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_clear_shared_state),
        summary = DSLSettingsText.from(R.string.preferences__internal_click_to_delete_all_sharing_state),
        onClick = {
          clearAllSenderKeySharedState()
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__internal_remove_two_person_minimum),
        summary = DSLSettingsText.from(R.string.preferences__internal_remove_the_requirement_that_you_need),
        isChecked = state.removeSenderKeyMinimium,
        onClick = {
          viewModel.setRemoveSenderKeyMinimum(!state.removeSenderKeyMinimium)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__internal_delay_resends),
        summary = DSLSettingsText.from(R.string.preferences__internal_delay_resending_messages_in_response_to_retry_receipts),
        isChecked = state.delayResends,
        onClick = {
          viewModel.setDelayResends(!state.delayResends)
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.preferences__internal_local_metrics)

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_clear_local_metrics),
        summary = DSLSettingsText.from(R.string.preferences__internal_click_to_clear_all_local_metrics_state),
        onClick = {
          clearAllLocalMetricsState()
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.preferences__internal_calling_server)

      radioPref(
        title = DSLSettingsText.from(R.string.preferences__internal_calling_server_default),
        summary = DSLSettingsText.from(BuildConfig.SIGNAL_SFU_URL),
        isChecked = state.callingServer == BuildConfig.SIGNAL_SFU_URL,
        onClick = {
          viewModel.setInternalGroupCallingServer(null)
        }
      )

      BuildConfig.SIGNAL_SFU_INTERNAL_NAMES.zip(BuildConfig.SIGNAL_SFU_INTERNAL_URLS)
        .forEach { (name, server) ->
          radioPref(
            title = DSLSettingsText.from(requireContext().getString(R.string.preferences__internal_calling_server_s, name)),
            summary = DSLSettingsText.from(server),
            isChecked = state.callingServer == server,
            onClick = {
              viewModel.setInternalGroupCallingServer(server)
            }
          )
        }

      sectionHeaderPref(R.string.preferences__internal_calling)

      radioListPref(
        title = DSLSettingsText.from(R.string.preferences__internal_calling_audio_processing_method),
        listItems = CallManager.AudioProcessingMethod.values().map { it.name }.toTypedArray(),
        selected = CallManager.AudioProcessingMethod.values().indexOf(state.callingAudioProcessingMethod),
        onSelected = {
          viewModel.setInternalCallingAudioProcessingMethod(CallManager.AudioProcessingMethod.values()[it])
        }
      )

      radioListPref(
        title = DSLSettingsText.from(R.string.preferences__internal_calling_bandwidth_mode),
        listItems = CallManager.BandwidthMode.values().map { it.name }.toTypedArray(),
        selected = CallManager.BandwidthMode.values().indexOf(state.callingBandwidthMode),
        onSelected = {
          viewModel.setInternalCallingBandwidthMode(CallManager.BandwidthMode.values()[it])
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__internal_calling_disable_telecom),
        isChecked = state.callingDisableTelecom,
        onClick = {
          viewModel.setInternalCallingDisableTelecom(!state.callingDisableTelecom)
        }
      )

      if (SignalStore.donationsValues().getSubscriber() != null) {
        dividerPref()

        sectionHeaderPref(R.string.preferences__internal_badges)

        clickPref(
          title = DSLSettingsText.from(R.string.preferences__internal_badges_enqueue_redemption),
          onClick = {
            enqueueSubscriptionRedemption()
          }
        )

        clickPref(
          title = DSLSettingsText.from(R.string.preferences__internal_badges_enqueue_keep_alive),
          onClick = {
            enqueueSubscriptionKeepAlive()
          }
        )

        clickPref(
          title = DSLSettingsText.from(R.string.preferences__internal_badges_set_error_state),
          onClick = {
            findNavController().safeNavigate(InternalSettingsFragmentDirections.actionInternalSettingsFragmentToDonorErrorConfigurationFragment())
          }
        )
      }

      dividerPref()

      sectionHeaderPref(R.string.preferences__internal_release_channel)

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_release_channel_set_last_version),
        onClick = {
          SignalStore.releaseChannelValues().highestVersionNoteReceived = max(SignalStore.releaseChannelValues().highestVersionNoteReceived - 10, 0)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_reset_donation_megaphone),
        onClick = {
          SignalDatabase.remoteMegaphones.debugRemoveAll()
          MegaphoneDatabase.getInstance(ApplicationDependencies.getApplication()).let {
            it.delete(Megaphones.Event.REMOTE_MEGAPHONE)
            it.markFirstVisible(Megaphones.Event.DONATE_Q2_2022, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31))
          }
          // Force repository database cache refresh
          MegaphoneRepository(ApplicationDependencies.getApplication()).onFirstEverAppLaunch()
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_fetch_release_channel),
        onClick = {
          SignalStore.releaseChannelValues().previousManifestMd5 = ByteArray(0)
          RetrieveRemoteAnnouncementsJob.enqueue(force = true)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_add_sample_note),
        onClick = {
          viewModel.addSampleReleaseNote()
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.preferences__internal_cds)

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_clear_history),
        summary = DSLSettingsText.from(R.string.preferences__internal_clear_history_description),
        onClick = {
          clearCdsHistory()
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_clear_all_service_ids),
        summary = DSLSettingsText.from(R.string.preferences__internal_clear_all_service_ids_description),
        onClick = {
          clearAllServiceIds()
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_clear_all_profile_keys),
        summary = DSLSettingsText.from(R.string.preferences__internal_clear_all_profile_keys_description),
        onClick = {
          clearAllProfileKeys()
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.ConversationListTabs__stories)

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_clear_onboarding_state),
        summary = DSLSettingsText.from(R.string.preferences__internal_clears_onboarding_flag_and_triggers_download_of_onboarding_stories),
        isEnabled = state.canClearOnboardingState,
        onClick = {
          viewModel.onClearOnboardingState()
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__internal_stories_dialog_launcher),
        onClick = {
          findNavController().safeNavigate(InternalSettingsFragmentDirections.actionInternalSettingsFragmentToStoryDialogsLauncherFragment())
        }
      )
    }
  }

  private fun copyPaymentsDataToClipboard() {
    MaterialAlertDialogBuilder(requireContext())
      .setMessage(
        """
    Local payments history will be copied to the clipboard.
    It may therefore compromise privacy.
    However, no private keys will be copied.
        """.trimIndent()
      )
      .setPositiveButton(
        "Copy"
      ) { _: DialogInterface?, _: Int ->
        val context: Context = ApplicationDependencies.getApplication()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        SimpleTask.run<Any?>(
          SignalExecutors.UNBOUNDED,
          {
            val tsv = DataExportUtil.createTsv()
            val clip = ClipData.newPlainText(context.getString(R.string.app_name), tsv)
            clipboard.setPrimaryClip(clip)
            null
          },
          {
            Toast.makeText(
              context,
              "Payments have been copied",
              Toast.LENGTH_SHORT
            ).show()
          }
        )
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun refreshAttributes() {
    ApplicationDependencies.getJobManager()
      .startChain(RefreshAttributesJob())
      .then(RefreshOwnProfileJob())
      .enqueue()
    Toast.makeText(context, "Scheduled attribute refresh", Toast.LENGTH_SHORT).show()
  }

  private fun refreshProfile() {
    ApplicationDependencies.getJobManager().add(RefreshOwnProfileJob())
    Toast.makeText(context, "Scheduled profile refresh", Toast.LENGTH_SHORT).show()
  }

  private fun rotateProfileKey() {
    ApplicationDependencies.getJobManager().add(RotateProfileKeyJob())
    Toast.makeText(context, "Scheduled profile key rotation", Toast.LENGTH_SHORT).show()
  }

  private fun refreshRemoteValues() {
    Toast.makeText(context, "Running remote config refresh, app will restart after completion.", Toast.LENGTH_LONG).show()
    SignalExecutors.BOUNDED.execute {
      val result: Optional<JobTracker.JobState> = ApplicationDependencies.getJobManager().runSynchronously(RemoteConfigRefreshJob(), TimeUnit.SECONDS.toMillis(10))

      if (result.isPresent && result.get() == JobTracker.JobState.SUCCESS) {
        AppUtil.restart(requireContext())
      } else {
        Toast.makeText(context, "Failed to refresh config remote config.", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun enqueueStorageServiceSync() {
    StorageSyncHelper.scheduleSyncForDataChange()
    Toast.makeText(context, "Scheduled routine storage sync", Toast.LENGTH_SHORT).show()
  }

  private fun enqueueStorageServiceForcePush() {
    ApplicationDependencies.getJobManager().add(StorageForcePushJob())
    Toast.makeText(context, "Scheduled storage force push", Toast.LENGTH_SHORT).show()
  }

  private fun deleteAllDynamicShortcuts() {
    ConversationUtil.clearAllShortcuts(requireContext())
    Toast.makeText(context, "Deleted all dynamic shortcuts.", Toast.LENGTH_SHORT).show()
  }

  private fun clearAllSenderKeyState() {
    SignalDatabase.senderKeys.deleteAll()
    SignalDatabase.senderKeyShared.deleteAll()
    Toast.makeText(context, "Deleted all sender key state.", Toast.LENGTH_SHORT).show()
  }

  private fun clearAllSenderKeySharedState() {
    SignalDatabase.senderKeyShared.deleteAll()
    Toast.makeText(context, "Deleted all sender key shared state.", Toast.LENGTH_SHORT).show()
  }

  private fun clearAllLocalMetricsState() {
    LocalMetricsDatabase.getInstance(ApplicationDependencies.getApplication()).clear()
    Toast.makeText(context, "Cleared all local metrics state.", Toast.LENGTH_SHORT).show()
  }

  private fun enqueueSubscriptionRedemption() {
    SubscriptionReceiptRequestResponseJob.createSubscriptionContinuationJobChain().enqueue()
  }

  private fun enqueueSubscriptionKeepAlive() {
    SubscriptionKeepAliveJob.enqueueAndTrackTime(System.currentTimeMillis())
  }

  private fun clearCdsHistory() {
    SignalDatabase.cds.clearAll()
    SignalStore.misc().cdsToken = null
    Toast.makeText(context, "Cleared all CDS history.", Toast.LENGTH_SHORT).show()
  }

  private fun clearAllServiceIds() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle("Clear all serviceIds?")
      .setMessage("Are you sure? Never do this on a non-test device.")
      .setPositiveButton(android.R.string.ok) { _, _ ->
        SignalDatabase.recipients.debugClearServiceIds()
        Toast.makeText(context, "Cleared all service IDs.", Toast.LENGTH_SHORT).show()
      }
      .setNegativeButton(android.R.string.cancel) { d, _ ->
        d.dismiss()
      }
      .show()
  }

  private fun clearAllProfileKeys() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle("Clear all profile keys?")
      .setMessage("Are you sure? Never do this on a non-test device.")
      .setPositiveButton(android.R.string.ok) { _, _ ->
        SignalDatabase.recipients.debugClearProfileData()
        Toast.makeText(context, "Cleared all profile keys.", Toast.LENGTH_SHORT).show()
      }
      .setNegativeButton(android.R.string.cancel) { d, _ ->
        d.dismiss()
      }
      .show()
  }

  private fun clearKeepLongerLogs() {
    SimpleTask.run({
      LogDatabase.getInstance(requireActivity().application).clearKeepLonger()
    }) {
      Toast.makeText(requireContext(), "Cleared keep longer logs", Toast.LENGTH_SHORT).show()
    }
  }
}
