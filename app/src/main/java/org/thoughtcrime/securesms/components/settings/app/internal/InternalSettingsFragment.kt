package org.thoughtcrime.securesms.components.settings.app.internal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.AppUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.concurrent.SimpleTask
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.signal.ringrtc.CallManager
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.database.JobDatabase
import org.thoughtcrime.securesms.database.LocalMetricsDatabase
import org.thoughtcrime.securesms.database.LogDatabase
import org.thoughtcrime.securesms.database.MegaphoneDatabase
import org.thoughtcrime.securesms.database.OneTimePreKeyTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.TerminalDonationQueue
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.jobs.DownloadLatestEmojiDataJob
import org.thoughtcrime.securesms.jobs.EmojiSearchIndexDownloadJob
import org.thoughtcrime.securesms.jobs.PnpInitializeDevicesJob
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
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import java.util.Optional
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.seconds

class InternalSettingsFragment : DSLSettingsFragment(R.string.preferences__internal_preferences) {

  companion object {
    private val TAG = Log.tag(InternalSettingsFragment::class.java)
  }

  private lateinit var viewModel: InternalSettingsViewModel

  private var scrollToPosition: Int = 0
  private val layoutManager: LinearLayoutManager?
    get() = recyclerView?.layoutManager as? LinearLayoutManager

  override fun onPause() {
    super.onPause()
    val firstVisiblePosition: Int? = layoutManager?.findFirstVisibleItemPosition()
    if (firstVisiblePosition != null) {
      SignalStore.internalValues().lastScrollPosition = firstVisiblePosition
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    scrollToPosition = SignalStore.internalValues().lastScrollPosition
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    val repository = InternalSettingsRepository(requireContext())
    val factory = InternalSettingsViewModel.Factory(repository)
    viewModel = ViewModelProvider(this, factory)[InternalSettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) {
      adapter.submitList(getConfiguration(it).toMappingModelList()) {
        if (scrollToPosition != 0) {
          layoutManager?.scrollToPositionWithOffset(scrollToPosition, 0)
          scrollToPosition = 0
        }
      }
    }
  }

  private fun getConfiguration(state: InternalSettingsState): DSLConfiguration {
    return configure {
      sectionHeaderPref(DSLSettingsText.from("Account"))

      clickPref(
        title = DSLSettingsText.from("Refresh attributes"),
        summary = DSLSettingsText.from("Forces a write of capabilities on to the server followed by a read."),
        onClick = {
          refreshAttributes()
        }
      )

      clickPref(
        title = DSLSettingsText.from("Refresh profile"),
        summary = DSLSettingsText.from("Forces a refresh of your own profile."),
        onClick = {
          refreshProfile()
        }
      )

      clickPref(
        title = DSLSettingsText.from("Rotate profile key"),
        summary = DSLSettingsText.from("Creates a new versioned profile, and triggers an update of any GV2 group you belong to."),
        onClick = {
          rotateProfileKey()
        }
      )

      clickPref(
        title = DSLSettingsText.from("Refresh remote config"),
        summary = DSLSettingsText.from("Forces a refresh of the remote config locally instead of waiting for the elapsed time."),
        onClick = {
          refreshRemoteValues()
        }
      )

      dividerPref()

      sectionHeaderPref(DSLSettingsText.from("Miscellaneous"))

      clickPref(
        title = DSLSettingsText.from("Search for a recipient"),
        summary = DSLSettingsText.from("Search by ID, ACI, or PNI."),
        onClick = {
          findNavController().safeNavigate(InternalSettingsFragmentDirections.actionInternalSettingsFragmentToInternalSearchFragment())
        }
      )

      clickPref(
        title = DSLSettingsText.from("SVR Playground"),
        summary = DSLSettingsText.from("Quickly test various SVR options and error conditions."),
        onClick = {
          findNavController().safeNavigate(InternalSettingsFragmentDirections.actionInternalSettingsFragmentToInternalSvrPlaygroundFragment())
        }
      )

      clickPref(
        title = DSLSettingsText.from("Backup Playground"),
        summary = DSLSettingsText.from("Test backup import/export."),
        onClick = {
          findNavController().safeNavigate(InternalSettingsFragmentDirections.actionInternalSettingsFragmentToInternalBackupPlaygroundFragment())
        }
      )

      switchPref(
        title = DSLSettingsText.from("'Internal Details' button"),
        summary = DSLSettingsText.from("Show a button in conversation settings that lets you see more information about a user."),
        isChecked = state.seeMoreUserDetails,
        onClick = {
          viewModel.setSeeMoreUserDetails(!state.seeMoreUserDetails)
        }
      )

      switchPref(
        title = DSLSettingsText.from("Shake to Report"),
        summary = DSLSettingsText.from("Shake your phone to easily submit and share a debug log."),
        isChecked = state.shakeToReport,
        onClick = {
          viewModel.setShakeToReport(!state.shakeToReport)
        }
      )

      clickPref(
        title = DSLSettingsText.from("Clear all logs"),
        onClick = {
          SimpleTask.run({
            LogDatabase.getInstance(requireActivity().application).logs.clearAll()
          }) {
            Toast.makeText(requireContext(), "Cleared all logs", Toast.LENGTH_SHORT).show()
          }
        }
      )

      clickPref(
        title = DSLSettingsText.from("Clear keep longer logs"),
        onClick = {
          clearKeepLongerLogs()
        }
      )

      clickPref(
        title = DSLSettingsText.from("Clear all crashes"),
        onClick = {
          SimpleTask.run({
            LogDatabase.getInstance(requireActivity().application).crashes.clear()
          }) {
            Toast.makeText(requireContext(), "Cleared crashes", Toast.LENGTH_SHORT).show()
          }
        }
      )

      clickPref(
        title = DSLSettingsText.from("Clear all ANRs"),
        onClick = {
          SimpleTask.run({
            LogDatabase.getInstance(requireActivity().application).anrs.clear()
          }) {
            Toast.makeText(requireContext(), "Cleared ANRs", Toast.LENGTH_SHORT).show()
          }
        }
      )

      clickPref(
        title = DSLSettingsText.from("Log dump PreKey ServiceId-KeyIds"),
        onClick = {
          logPreKeyIds()
        }
      )

      clickPref(
        title = DSLSettingsText.from("Retry all jobs now"),
        summary = DSLSettingsText.from("Clear backoff intervals, app will restart"),
        onClick = {
          SimpleTask.run({
            JobDatabase.getInstance(ApplicationDependencies.getApplication()).debugResetBackoffInterval()
          }) {
            AppUtil.restart(requireContext())
          }
        }
      )

      dividerPref()

      sectionHeaderPref(DSLSettingsText.from("Payments"))

      clickPref(
        title = DSLSettingsText.from("Copy payments data"),
        summary = DSLSettingsText.from("Copy all payment records to clipboard."),
        onClick = {
          copyPaymentsDataToClipboard()
        }
      )

      dividerPref()

      sectionHeaderPref(DSLSettingsText.from("Storage Service"))

      switchPref(
        title = DSLSettingsText.from("Disable syncing"),
        summary = DSLSettingsText.from("Prevent syncing any data to/from storage service."),
        isChecked = state.disableStorageService,
        onClick = {
          viewModel.setDisableStorageService(!state.disableStorageService)
        }
      )

      clickPref(
        title = DSLSettingsText.from("Sync now"),
        summary = DSLSettingsText.from("Enqueue a normal storage service sync."),
        onClick = {
          enqueueStorageServiceSync()
        }
      )

      clickPref(
        title = DSLSettingsText.from("Overwrite remote data"),
        summary = DSLSettingsText.from("Forces remote storage to match the local device state."),
        onClick = {
          enqueueStorageServiceForcePush()
        }
      )

      dividerPref()

      sectionHeaderPref(DSLSettingsText.from("Groups V2"))

      switchPref(
        title = DSLSettingsText.from("Force invites"),
        summary = DSLSettingsText.from("Members will not be added directly to a GV2 even if they could be."),
        isChecked = state.gv2forceInvites,
        onClick = {
          viewModel.setGv2ForceInvites(!state.gv2forceInvites)
        }
      )

      switchPref(
        title = DSLSettingsText.from("Ignore server changes"),
        summary = DSLSettingsText.from("Changes in server's response will be ignored, causing passive voice update messages if P2P is also ignored."),
        isChecked = state.gv2ignoreServerChanges,
        onClick = {
          viewModel.setGv2IgnoreServerChanges(!state.gv2ignoreServerChanges)
        }
      )

      switchPref(
        title = DSLSettingsText.from("Ignore P2P changes"),
        summary = DSLSettingsText.from("Changes sent P2P will be ignored. In conjunction with ignoring server changes, will cause passive voice."),
        isChecked = state.gv2ignoreP2PChanges,
        onClick = {
          viewModel.setGv2IgnoreP2PChanges(!state.gv2ignoreP2PChanges)
        }
      )

      dividerPref()

      sectionHeaderPref(DSLSettingsText.from("Network"))

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
        title = DSLSettingsText.from("Allow censorship circumvention toggle"),
        summary = DSLSettingsText.from("Allow changing the censorship circumvention toggle regardless of network connectivity."),
        isChecked = state.allowCensorshipSetting,
        onClick = {
          viewModel.setAllowCensorshipSetting(!state.allowCensorshipSetting)
        }
      )

      dividerPref()

      sectionHeaderPref(DSLSettingsText.from("Conversations and Shortcuts"))

      clickPref(
        title = DSLSettingsText.from("Delete all dynamic shortcuts"),
        summary = DSLSettingsText.from("Click to delete all dynamic shortcuts"),
        onClick = {
          deleteAllDynamicShortcuts()
        }
      )

      dividerPref()

      sectionHeaderPref(DSLSettingsText.from("Emoji"))

      val emojiSummary = if (state.emojiVersion == null) {
        "Use built-in emoji set"
      } else {
        "Current version: ${state.emojiVersion.version} at density ${state.emojiVersion.density}"
      }

      switchPref(
        title = DSLSettingsText.from("Use built-in emoji set"),
        summary = DSLSettingsText.from(emojiSummary),
        isChecked = state.useBuiltInEmojiSet,
        onClick = {
          viewModel.setUseBuiltInEmoji(!state.useBuiltInEmojiSet)
        }
      )

      clickPref(
        title = DSLSettingsText.from("Force emoji download"),
        summary = DSLSettingsText.from("Download the latest emoji set if it\\'s newer than what we have."),
        onClick = {
          ApplicationDependencies.getJobManager().add(DownloadLatestEmojiDataJob(true))
        }
      )

      clickPref(
        title = DSLSettingsText.from("Force search index download"),
        summary = DSLSettingsText.from("Download the latest emoji search index if it\\'s newer than what we have."),
        onClick = {
          EmojiSearchIndexDownloadJob.scheduleImmediately()
        }
      )

      dividerPref()

      sectionHeaderPref(DSLSettingsText.from("Sender Key"))

      clickPref(
        title = DSLSettingsText.from("Clear all state"),
        summary = DSLSettingsText.from("Click to delete all sender key state"),
        onClick = {
          clearAllSenderKeyState()
        }
      )

      clickPref(
        title = DSLSettingsText.from("Clear shared state"),
        summary = DSLSettingsText.from("Click to delete all sharing state"),
        onClick = {
          clearAllSenderKeySharedState()
        }
      )

      switchPref(
        title = DSLSettingsText.from("Remove 2 person minimum"),
        summary = DSLSettingsText.from("Remove the requirement that you  need at least 2 recipients to use sender key."),
        isChecked = state.removeSenderKeyMinimium,
        onClick = {
          viewModel.setRemoveSenderKeyMinimum(!state.removeSenderKeyMinimium)
        }
      )

      switchPref(
        title = DSLSettingsText.from("Delay resends"),
        summary = DSLSettingsText.from("Delay resending messages in response to retry receipts by 10 seconds."),
        isChecked = state.delayResends,
        onClick = {
          viewModel.setDelayResends(!state.delayResends)
        }
      )

      dividerPref()

      sectionHeaderPref(DSLSettingsText.from("Local Metrics"))

      clickPref(
        title = DSLSettingsText.from("Clear local metrics"),
        summary = DSLSettingsText.from("Click to clear all local metrics state."),
        onClick = {
          clearAllLocalMetricsState()
        }
      )

      dividerPref()

      sectionHeaderPref(DSLSettingsText.from("Group call server"))

      radioPref(
        title = DSLSettingsText.from("Production server"),
        summary = DSLSettingsText.from(BuildConfig.SIGNAL_SFU_URL),
        isChecked = state.callingServer == BuildConfig.SIGNAL_SFU_URL,
        onClick = {
          viewModel.setInternalGroupCallingServer(BuildConfig.SIGNAL_SFU_URL)
        }
      )

      BuildConfig.SIGNAL_SFU_INTERNAL_NAMES.zip(BuildConfig.SIGNAL_SFU_INTERNAL_URLS)
        .forEach { (name, server) ->
          radioPref(
            title = DSLSettingsText.from("$name server"),
            summary = DSLSettingsText.from(server),
            isChecked = state.callingServer == server,
            onClick = {
              viewModel.setInternalGroupCallingServer(server)
            }
          )
        }

      sectionHeaderPref(DSLSettingsText.from("Calling options"))

      radioListPref(
        title = DSLSettingsText.from("Audio processing method"),
        listItems = CallManager.AudioProcessingMethod.values().map { it.name }.toTypedArray(),
        selected = CallManager.AudioProcessingMethod.values().indexOf(state.callingAudioProcessingMethod),
        onSelected = {
          viewModel.setInternalCallingAudioProcessingMethod(CallManager.AudioProcessingMethod.values()[it])
        }
      )

      radioListPref(
        title = DSLSettingsText.from("Bandwidth mode"),
        listItems = CallManager.DataMode.values().map { it.name }.toTypedArray(),
        selected = CallManager.DataMode.values().indexOf(state.callingDataMode),
        onSelected = {
          viewModel.setInternalCallingDataMode(CallManager.DataMode.values()[it])
        }
      )

      switchPref(
        title = DSLSettingsText.from("Disable Telecom integration"),
        isChecked = state.callingDisableTelecom,
        onClick = {
          viewModel.setInternalCallingDisableTelecom(!state.callingDisableTelecom)
        }
      )

      switchPref(
        title = DSLSettingsText.from("Disable LBRed"),
        isChecked = state.callingDisableLBRed,
        onClick = {
          viewModel.setInternalCallingDisableLBRed(!state.callingDisableLBRed)
        }
      )

      dividerPref()

      if (SignalStore.donationsValues().getSubscriber() != null) {
        sectionHeaderPref(DSLSettingsText.from("Badges"))

        clickPref(
          title = DSLSettingsText.from("Enqueue redemption."),
          onClick = {
            enqueueSubscriptionRedemption()
          }
        )

        clickPref(
          title = DSLSettingsText.from("Enqueue keep-alive."),
          onClick = {
            enqueueSubscriptionKeepAlive()
          }
        )

        clickPref(
          title = DSLSettingsText.from("Set error state."),
          onClick = {
            findNavController().safeNavigate(InternalSettingsFragmentDirections.actionInternalSettingsFragmentToDonorErrorConfigurationFragment())
          }
        )

        clickPref(
          title = DSLSettingsText.from("Clear keep-alive timestamps"),
          onClick = {
            SignalStore.donationsValues().subscriptionEndOfPeriodRedemptionStarted = 0L
            SignalStore.donationsValues().subscriptionEndOfPeriodConversionStarted = 0L
            SignalStore.donationsValues().setLastEndOfPeriod(0L)
            Toast.makeText(context, "Cleared", Toast.LENGTH_SHORT).show()
          }
        )

        dividerPref()
      }

      if (state.hasPendingOneTimeDonation) {
        clickPref(
          title = DSLSettingsText.from("Clear pending one-time donation."),
          onClick = {
            SignalStore.donationsValues().setPendingOneTimeDonation(null)
          }
        )
      } else {
        clickPref(
          title = DSLSettingsText.from("Set pending one-time donation."),
          onClick = {
            findNavController().safeNavigate(InternalSettingsFragmentDirections.actionInternalSettingsFragmentToOneTimeDonationConfigurationFragment())
          }
        )
      }

      clickPref(
        title = DSLSettingsText.from("Enqueue terminal donation"),
        onClick = {
          findNavController().safeNavigate(InternalSettingsFragmentDirections.actionInternalSettingsFragmentToTerminalDonationConfigurationFragment())
        }
      )

      dividerPref()

      sectionHeaderPref(DSLSettingsText.from("Release channel"))

      clickPref(
        title = DSLSettingsText.from("Set last version seen back 10 versions"),
        onClick = {
          SignalStore.releaseChannelValues().highestVersionNoteReceived = max(SignalStore.releaseChannelValues().highestVersionNoteReceived - 10, 0)
        }
      )

      clickPref(
        title = DSLSettingsText.from("Reset donation megaphone"),
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
        title = DSLSettingsText.from("Fetch release channel"),
        onClick = {
          SignalStore.releaseChannelValues().previousManifestMd5 = ByteArray(0)
          RetrieveRemoteAnnouncementsJob.enqueue(force = true)
        }
      )

      clickPref(
        title = DSLSettingsText.from("Add sample note"),
        onClick = {
          viewModel.addSampleReleaseNote()
        }
      )

      clickPref(
        title = DSLSettingsText.from("Add remote donate megaphone"),
        onClick = {
          viewModel.addRemoteDonateMegaphone()
        }
      )

      clickPref(
        title = DSLSettingsText.from("Add donate_friend remote megaphone"),
        onClick = {
          viewModel.addRemoteDonateFriendMegaphone()
        }
      )

      dividerPref()

      sectionHeaderPref(DSLSettingsText.from("CDS"))

      clickPref(
        title = DSLSettingsText.from("Clear history"),
        summary = DSLSettingsText.from("Clears all CDS history, meaning the next sync will consider all numbers to be new."),
        onClick = {
          clearCdsHistory()
        }
      )

      clickPref(
        title = DSLSettingsText.from("Clear all service IDs"),
        summary = DSLSettingsText.from("Clears all known service IDs (except your own) for people that have phone numbers. Do not use on your personal device!"),
        onClick = {
          clearAllServiceIds()
        }
      )

      clickPref(
        title = DSLSettingsText.from("Clear all profile keys"),
        summary = DSLSettingsText.from("Clears all known profile keys (except your own). Do not use on your personal device!"),
        onClick = {
          clearAllProfileKeys()
        }
      )

      dividerPref()

      sectionHeaderPref(DSLSettingsText.from("Stories"))

      clickPref(
        title = DSLSettingsText.from("Clear onboarding state"),
        summary = DSLSettingsText.from("Clears onboarding flag and triggers download of onboarding stories."),
        isEnabled = state.canClearOnboardingState,
        onClick = {
          viewModel.onClearOnboardingState()
        }
      )

      clickPref(
        title = DSLSettingsText.from("Clear choose initial my story privacy state"),
        isEnabled = true,
        onClick = {
          SignalStore.storyValues().userHasBeenNotifiedAboutStories = false
        }
      )

      clickPref(
        title = DSLSettingsText.from("Clear first time navigation state"),
        isEnabled = true,
        onClick = {
          SignalStore.storyValues().userHasSeenFirstNavView = false
        }
      )

      clickPref(
        title = DSLSettingsText.from("Stories dialog launcher"),
        onClick = {
          findNavController().safeNavigate(InternalSettingsFragmentDirections.actionInternalSettingsFragmentToStoryDialogsLauncherFragment())
        }
      )

      dividerPref()

      sectionHeaderPref(DSLSettingsText.from("PNP"))

      clickPref(
        title = DSLSettingsText.from("Trigger 'Hello World' event"),
        isEnabled = true,
        onClick = {
          SimpleTask.run(viewLifecycleOwner.lifecycle, {
            ApplicationDependencies.getJobManager().runSynchronously(PnpInitializeDevicesJob(), 10.seconds.inWholeMilliseconds)
          }, { state ->
            if (state.isPresent) {
              Toast.makeText(context, "Job finished with result: ${state.get()}!", Toast.LENGTH_SHORT).show()
              viewModel.refresh()
            } else {
              Toast.makeText(context, "Job timed out after 10 seconds!", Toast.LENGTH_SHORT).show()
            }
          })
        }
      )

      clickPref(
        title = DSLSettingsText.from("Reset 'PNP initialized' state"),
        summary = DSLSettingsText.from("Current initialized state: ${state.pnpInitialized}"),
        isEnabled = state.pnpInitialized,
        onClick = {
          viewModel.resetPnpInitializedState()
        }
      )

      clickPref(
        title = DSLSettingsText.from("Corrupt username"),
        summary = DSLSettingsText.from("Changes our local username without telling the server so it falls out of sync. Refresh profile afterwards to trigger corruption."),
        onClick = {
          MaterialAlertDialogBuilder(requireContext())
            .setTitle("Corrupt your username?")
            .setMessage("Are you sure? You might not be able to get your original username back.")
            .setPositiveButton(android.R.string.ok) { _, _ ->
              val random = "${(1..5).map { ('a'..'z').random() }.joinToString(separator = "") }.${Random.nextInt(10, 100)}"

              SignalStore.account().username = random
              SignalDatabase.recipients.setUsername(Recipient.self().id, random)
              StorageSyncHelper.scheduleSyncForDataChange()

              Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .show()
        }
      )

      clickPref(
        title = DSLSettingsText.from("Corrupt username link"),
        summary = DSLSettingsText.from("Changes our local username link without telling the server so it falls out of sync. Refresh profile afterwards to trigger corruption."),
        onClick = {
          MaterialAlertDialogBuilder(requireContext())
            .setTitle("Corrupt your username link?")
            .setMessage("Are you sure? You'll have to reset your link.")
            .setPositiveButton(android.R.string.ok) { _, _ ->
              SignalStore.account().usernameLink = UsernameLinkComponents(
                entropy = Util.getSecretBytes(32),
                serverId = SignalStore.account().usernameLink?.serverId ?: UUID.randomUUID()
              )
              StorageSyncHelper.scheduleSyncForDataChange()
              Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .show()
        }
      )

      dividerPref()
      sectionHeaderPref(DSLSettingsText.from("Chat Filters"))
      clickPref(
        title = DSLSettingsText.from("Reset pull to refresh tip count"),
        onClick = {
          SignalStore.uiHints().resetNeverDisplayPullToRefreshCount()
        }
      )

      dividerPref()
      clickPref(
        title = DSLSettingsText.from("Launch Conversation Test Springboard "),
        onClick = {
          findNavController().safeNavigate(InternalSettingsFragmentDirections.actionInternalSettingsFragmentToInternalConversationSpringboardFragment())
        }
      )

      switchPref(
        title = DSLSettingsText.from("Use V2 ConversationItem for Media"),
        isChecked = state.useConversationItemV2ForMedia,
        onClick = {
          viewModel.setUseConversationItemV2Media(!state.useConversationItemV2ForMedia)
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
    SubscriptionReceiptRequestResponseJob.createSubscriptionContinuationJobChain(
      -1L,
      TerminalDonationQueue.TerminalDonation(
        level = 1000
      )
    ).enqueue()
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
      LogDatabase.getInstance(requireActivity().application).logs.clearKeepLonger()
    }) {
      Toast.makeText(requireContext(), "Cleared keep longer logs", Toast.LENGTH_SHORT).show()
    }
  }

  private fun logPreKeyIds() {
    SimpleTask.run({
      val oneTimePreKeys = SignalDatabase.rawDatabase
        .query("SELECT * FROM ${OneTimePreKeyTable.TABLE_NAME}")
        .readToList { c ->
          c.requireString(OneTimePreKeyTable.ACCOUNT_ID) to c.requireLong(OneTimePreKeyTable.KEY_ID)
        }
        .joinToString()

      Log.i(TAG, "One-Time Prekeys\n$oneTimePreKeys")
    }) {
      Toast.makeText(requireContext(), "Dumped to logs", Toast.LENGTH_SHORT).show()
    }
  }
}
