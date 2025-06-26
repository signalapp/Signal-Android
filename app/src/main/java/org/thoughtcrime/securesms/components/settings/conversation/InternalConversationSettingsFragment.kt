package org.thoughtcrime.securesms.components.settings.conversation

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.isAbsent
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.core.util.roundedString
import org.signal.core.util.withinTransaction
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.UriAttachment
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.BitmapUtil
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.Util
import java.util.Objects
import kotlin.random.Random
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

/**
 * Shows internal details about a recipient that you can view from the conversation settings.
 */
@Stable
class InternalConversationSettingsFragment : ComposeFragment(), InternalConversationSettingsScreenCallbacks {

  companion object {
    val TAG = Log.tag(InternalConversationSettingsFragment::class.java)
  }

  private val viewModel: InternalViewModel by viewModels(
    factoryProducer = {
      val recipientId = InternalConversationSettingsFragmentArgs.fromBundle(requireArguments()).recipientId
      MyViewModelFactory(recipientId)
    }
  )

  @Composable
  override fun FragmentContent() {
    val state: InternalConversationSettingsState by viewModel.state.collectAsStateWithLifecycle()

    InternalConversationSettingsScreen(
      state = state,
      callbacks = this
    )
  }

  private fun makeDummyAttachment(): Attachment {
    val bitmapDimens = 1024
    val bitmap = Bitmap.createBitmap(
      IntArray(bitmapDimens * bitmapDimens) { Random.nextInt(0xFFFFFF) },
      0,
      bitmapDimens,
      bitmapDimens,
      bitmapDimens,
      Bitmap.Config.RGB_565
    )
    val stream = BitmapUtil.toCompressedJpeg(bitmap)
    val bytes = stream.readBytes()
    val uri = BlobProvider.getInstance().forData(bytes).createForSingleSessionOnDisk(requireContext())
    return UriAttachment(
      uri = uri,
      contentType = MediaUtil.IMAGE_JPEG,
      transferState = AttachmentTable.TRANSFER_PROGRESS_DONE,
      size = bytes.size.toLong(),
      fileName = null,
      voiceNote = false,
      borderless = false,
      videoGif = false,
      quote = false,
      caption = null,
      stickerLocator = null,
      blurHash = null,
      audioHash = null,
      transformProperties = null
    )
  }

  override fun onNavigationClick() {
    requireActivity().onBackPressedDispatcher.onBackPressed()
  }

  override fun copyToClipboard(data: String) {
    Util.copyToClipboard(requireContext(), data)
    Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
  }

  override fun triggerThreadUpdate(threadId: Long?) {
    val startTimeNanos = System.nanoTime()
    SignalDatabase.threads.update(threadId ?: -1L, true)
    val endTimeNanos = System.nanoTime()
    Toast.makeText(context, "Thread update took ${(endTimeNanos - startTimeNanos).nanoseconds.toDouble(DurationUnit.MILLISECONDS).roundedString(2)} ms", Toast.LENGTH_SHORT).show()
  }

  override fun disableProfileSharing(recipientId: RecipientId) {
    SignalDatabase.recipients.setProfileSharing(recipientId, false)
  }

  override fun deleteSessions(recipientId: RecipientId) {
    val recipient = Recipient.live(recipientId).get()
    val aci = recipient.aci.orNull()
    val pni = recipient.pni.orNull()

    if (aci != null) {
      SignalDatabase.sessions.deleteAllFor(serviceId = SignalStore.account.requireAci(), addressName = aci.toString())
    }
    if (pni != null) {
      SignalDatabase.sessions.deleteAllFor(serviceId = SignalStore.account.requireAci(), addressName = pni.toString())
    }
  }

  override fun archiveSessions(recipientId: RecipientId) {
    AppDependencies.protocolStore.aci().sessions().archiveSessions(recipientId)
  }

  override fun deleteAvatar(recipientId: RecipientId) {
    SignalDatabase.recipients.manuallyUpdateShowAvatar(recipientId, false)
    AvatarHelper.delete(requireContext(), recipientId)
  }

  override fun clearRecipientData(recipientId: RecipientId) {
    val recipient = Recipient.live(recipientId).get()
    SignalDatabase.threads.deleteConversation(SignalDatabase.threads.getThreadIdIfExistsFor(recipientId))

    if (recipient.hasServiceId) {
      SignalDatabase.recipients.debugClearServiceIds(recipientId)
      SignalDatabase.recipients.debugClearProfileData(recipientId)
    }

    if (recipient.hasAci) {
      SignalDatabase.sessions.deleteAllFor(serviceId = SignalStore.account.requireAci(), addressName = recipient.requireAci().toString())
      SignalDatabase.sessions.deleteAllFor(serviceId = SignalStore.account.requirePni(), addressName = recipient.requireAci().toString())
      AppDependencies.protocolStore.aci().identities().delete(recipient.requireAci().toString())
    }

    if (recipient.hasPni) {
      SignalDatabase.sessions.deleteAllFor(serviceId = SignalStore.account.requireAci(), addressName = recipient.requirePni().toString())
      SignalDatabase.sessions.deleteAllFor(serviceId = SignalStore.account.requirePni(), addressName = recipient.requirePni().toString())
      AppDependencies.protocolStore.aci().identities().delete(recipient.requirePni().toString())
    }

    startActivity(MainActivity.clearTop(requireContext()))
  }

  override fun add1000Messages(recipientId: RecipientId) {
    val recipient = Recipient.live(recipientId).get()
    val messageCount = 10
    val startTime = System.currentTimeMillis() - messageCount
    SignalDatabase.rawDatabase.withinTransaction {
      val targetThread = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
      for (i in 1..messageCount) {
        val time = startTime + i
        val attachment = makeDummyAttachment()
        val id = SignalDatabase.messages.insertMessageOutbox(
          message = OutgoingMessage(threadRecipient = recipient, sentTimeMillis = time, body = "Outgoing: $i", attachments = listOf(attachment)),
          threadId = targetThread
        )
        SignalDatabase.messages.markAsSent(id, true)
      }
    }

    Toast.makeText(context, "Done!", Toast.LENGTH_SHORT).show()
  }

  override fun add10Messages(recipientId: RecipientId) {
    val recipient = Recipient.live(recipientId).get()
    val messageCount = 10
    val startTime = System.currentTimeMillis() - messageCount
    SignalDatabase.rawDatabase.withinTransaction {
      val targetThread = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
      for (i in 1..messageCount) {
        val time = startTime + i
        val attachment = makeDummyAttachment()
        val id = SignalDatabase.messages.insertMessageOutbox(
          message = OutgoingMessage(threadRecipient = recipient, sentTimeMillis = time, body = "Outgoing: $i", attachments = listOf(attachment)),
          threadId = targetThread
        )
        SignalDatabase.messages.markAsSent(id, true)
      }
    }

    Toast.makeText(context, "Done!", Toast.LENGTH_SHORT).show()
  }

  override fun splitAndCreateThreads(recipientId: RecipientId) {
    val recipient = Recipient.live(recipientId).get()
    if (!recipient.hasE164) {
      Toast.makeText(context, "Recipient doesn't have an E164! Can't split.", Toast.LENGTH_SHORT).show()
      return
    }

    SignalDatabase.recipients.debugClearE164AndPni(recipient.id)

    val splitRecipientId: RecipientId = SignalDatabase.recipients.getAndPossiblyMergePnpVerified(null, recipient.pni.orElse(null), recipient.requireE164())
    val splitRecipient: Recipient = Recipient.resolved(splitRecipientId)
    val splitThreadId: Long = SignalDatabase.threads.getOrCreateThreadIdFor(splitRecipient)

    val messageId: Long = SignalDatabase.messages.insertMessageOutbox(
      OutgoingMessage.text(splitRecipient, "Test Message ${System.currentTimeMillis()}", 0),
      splitThreadId,
      false,
      null
    )
    SignalDatabase.messages.markAsSent(messageId, true)

    SignalDatabase.threads.update(splitThreadId, true)

    Toast.makeText(context, "Done! We split the E164/PNI from this contact into $splitRecipientId", Toast.LENGTH_SHORT).show()
  }

  override fun splitWithoutCreatingThreads(recipientId: RecipientId) {
    val recipient = Recipient.live(recipientId).get()
    if (recipient.pni.isAbsent()) {
      Toast.makeText(context, "Recipient doesn't have a PNI! Can't split.", Toast.LENGTH_SHORT).show()
    }

    if (recipient.serviceId.isAbsent()) {
      Toast.makeText(context, "Recipient doesn't have a serviceId! Can't split.", Toast.LENGTH_SHORT).show()
    }

    SignalDatabase.recipients.debugRemoveAci(recipient.id)

    val aciRecipientId: RecipientId = SignalDatabase.recipients.getAndPossiblyMergePnpVerified(recipient.requireAci(), null, null)

    recipient.profileKey?.let { profileKey ->
      SignalDatabase.recipients.setProfileKey(aciRecipientId, ProfileKey(profileKey))
    }

    SignalDatabase.recipients.debugClearProfileData(recipient.id)

    Toast.makeText(context, "Done! Split the ACI and profile key off into $aciRecipientId", Toast.LENGTH_SHORT).show()
  }

  override fun clearSenderKey(recipientId: RecipientId) {
    val group = SignalDatabase.groups.getGroup(recipientId).orNull()
    if (group == null) {
      Log.w(TAG, "Couldn't find group for recipientId: $recipientId")
      return
    }

    if (group.distributionId == null) {
      Log.w(TAG, "No distributionId for recipientId: $recipientId")
      return
    }

    SignalDatabase.senderKeyShared.deleteAllFor(group.distributionId)
  }

  class InternalViewModel(
    val recipientId: RecipientId
  ) : ViewModel(), RecipientForeverObserver {

    private val store = MutableStateFlow(
      InternalConversationSettingsState.create(
        recipient = Recipient.resolved(recipientId),
        threadId = null,
        groupId = null
      )
    )

    val state: StateFlow<InternalConversationSettingsState> = store
    val liveRecipient = Recipient.live(recipientId)

    init {
      liveRecipient.observeForever(this)

      SignalExecutors.BOUNDED.execute {
        val threadId: Long? = SignalDatabase.threads.getThreadIdFor(recipientId)
        val groupId: GroupId? = SignalDatabase.groups.getGroup(recipientId).map { it.id }.orElse(null)
        store.update { state -> state.copy(threadId = threadId, groupId = groupId) }
      }
    }

    override fun onRecipientChanged(recipient: Recipient) {
      store.update { state -> InternalConversationSettingsState.create(recipient, state.threadId, state.groupId) }
    }

    override fun onCleared() {
      liveRecipient.removeForeverObserver(this)
    }
  }

  class MyViewModelFactory(val recipientId: RecipientId) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return Objects.requireNonNull(modelClass.cast(InternalViewModel(recipientId)))
    }
  }
}
