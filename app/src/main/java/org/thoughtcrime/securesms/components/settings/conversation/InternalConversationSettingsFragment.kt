package org.thoughtcrime.securesms.components.settings.conversation

import android.graphics.Color
import android.text.TextUtils
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.subscription.Subscriber
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.Hex
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.livedata.Store
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.PNI
import java.util.Objects

/**
 * Shows internal details about a recipient that you can view from the conversation settings.
 */
class InternalConversationSettingsFragment : DSLSettingsFragment(
  titleId = R.string.ConversationSettingsFragment__internal_details
) {

  private val viewModel: InternalViewModel by viewModels(
    factoryProducer = {
      val recipientId = InternalConversationSettingsFragmentArgs.fromBundle(requireArguments()).recipientId
      MyViewModelFactory(recipientId)
    }
  )

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: InternalState): DSLConfiguration {
    val recipient = state.recipient
    return configure {
      sectionHeaderPref(DSLSettingsText.from("Data"))

      textPref(
        title = DSLSettingsText.from("RecipientId"),
        summary = DSLSettingsText.from(recipient.id.serialize())
      )

      if (!recipient.isGroup) {
        val aci = recipient.aci.transform(ACI::toString).or("null")
        longClickPref(
          title = DSLSettingsText.from("ACI"),
          summary = DSLSettingsText.from(aci),
          onLongClick = { copyToClipboard(aci) }
        )

        val pni = recipient.pni.transform(PNI::toString).or("null")
        longClickPref(
          title = DSLSettingsText.from("PNI"),
          summary = DSLSettingsText.from(pni),
          onLongClick = { copyToClipboard(pni) }
        )
      }

      if (state.groupId != null) {
        val groupId: String = state.groupId.toString()
        longClickPref(
          title = DSLSettingsText.from("GroupId"),
          summary = DSLSettingsText.from(groupId),
          onLongClick = { copyToClipboard(groupId) }
        )
      }

      val threadId: String = if (state.threadId != null) state.threadId.toString() else "N/A"
      longClickPref(
        title = DSLSettingsText.from("ThreadId"),
        summary = DSLSettingsText.from(threadId),
        onLongClick = { copyToClipboard(threadId) }
      )

      if (!recipient.isGroup) {
        textPref(
          title = DSLSettingsText.from("Profile Name"),
          summary = DSLSettingsText.from("[${recipient.profileName.givenName}] [${state.recipient.profileName.familyName}]")
        )

        val profileKeyBase64 = recipient.profileKey?.let(Base64::encodeBytes) ?: "None"
        longClickPref(
          title = DSLSettingsText.from("Profile Key (Base64)"),
          summary = DSLSettingsText.from(profileKeyBase64),
          onLongClick = { copyToClipboard(profileKeyBase64) }
        )

        val profileKeyHex = recipient.profileKey?.let(Hex::toStringCondensed) ?: ""
        longClickPref(
          title = DSLSettingsText.from("Profile Key (Hex)"),
          summary = DSLSettingsText.from(profileKeyHex),
          onLongClick = { copyToClipboard(profileKeyHex) }
        )

        textPref(
          title = DSLSettingsText.from("Sealed Sender Mode"),
          summary = DSLSettingsText.from(recipient.unidentifiedAccessMode.toString())
        )
      }

      textPref(
        title = DSLSettingsText.from("Profile Sharing (AKA \"Whitelisted\")"),
        summary = DSLSettingsText.from(recipient.isProfileSharing.toString())
      )

      if (!recipient.isGroup) {
        textPref(
          title = DSLSettingsText.from("Capabilities"),
          summary = DSLSettingsText.from(buildCapabilitySpan(recipient))
        )
      }

      if (!recipient.isGroup) {
        sectionHeaderPref(DSLSettingsText.from("Actions"))

        clickPref(
          title = DSLSettingsText.from("Disable Profile Sharing"),
          summary = DSLSettingsText.from("Clears profile sharing/whitelisted status, which should cause the Message Request UI to show."),
          onClick = {
            MaterialAlertDialogBuilder(requireContext())
              .setTitle("Are you sure?")
              .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
              .setPositiveButton(android.R.string.ok) { _, _ -> SignalDatabase.recipients.setProfileSharing(recipient.id, false) }
              .show()
          }
        )

        clickPref(
          title = DSLSettingsText.from("Delete Session"),
          summary = DSLSettingsText.from("Deletes the session, essentially guaranteeing an encryption error if they send you a message."),
          onClick = {
            MaterialAlertDialogBuilder(requireContext())
              .setTitle("Are you sure?")
              .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
              .setPositiveButton(android.R.string.ok) { _, _ ->
                if (recipient.hasAci()) {
                  SignalDatabase.sessions.deleteAllFor(recipient.requireAci().toString())
                }
                if (recipient.hasE164()) {
                  SignalDatabase.sessions.deleteAllFor(recipient.requireE164())
                }
              }
              .show()
          }
        )
      }

      if (recipient.isSelf) {
        sectionHeaderPref(DSLSettingsText.from("Donations"))

        val subscriber: Subscriber? = SignalStore.donationsValues().getSubscriber()
        val summary = if (subscriber != null) {
          """currency code: ${subscriber.currencyCode}
            |subscriber id: ${subscriber.subscriberId.serialize()}
          """.trimMargin()
        } else {
          "None"
        }

        longClickPref(
          title = DSLSettingsText.from("Subscriber ID"),
          summary = DSLSettingsText.from(summary),
          onLongClick = {
            if (subscriber != null) {
              copyToClipboard(subscriber.subscriberId.serialize())
            }
          }
        )
      }
    }
  }

  private fun copyToClipboard(text: String) {
    Util.copyToClipboard(requireContext(), text)
    Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
  }

  private fun buildCapabilitySpan(recipient: Recipient): CharSequence {
    return TextUtils.concat(
      colorize("GV2", recipient.groupsV2Capability),
      ", ",
      colorize("GV1Migration", recipient.groupsV1MigrationCapability),
      ", ",
      colorize("AnnouncementGroup", recipient.announcementGroupCapability),
      ", ",
      colorize("SenderKey", recipient.senderKeyCapability),
      ", ",
      colorize("ChangeNumber", recipient.changeNumberCapability),
    )
  }

  private fun colorize(name: String, support: Recipient.Capability): CharSequence {
    return when (support) {
      Recipient.Capability.SUPPORTED -> SpanUtil.color(Color.rgb(0, 150, 0), name)
      Recipient.Capability.NOT_SUPPORTED -> SpanUtil.color(Color.RED, name)
      Recipient.Capability.UNKNOWN -> SpanUtil.italic(name)
    }
  }

  class InternalViewModel(
    val recipientId: RecipientId
  ) : ViewModel(), RecipientForeverObserver {

    private val store = Store(
      InternalState(
        recipient = Recipient.resolved(recipientId),
        threadId = null,
        groupId = null
      )
    )

    val state = store.stateLiveData
    val liveRecipient = Recipient.live(recipientId)

    init {
      liveRecipient.observeForever(this)

      SignalExecutors.BOUNDED.execute {
        val threadId: Long? = SignalDatabase.threads.getThreadIdFor(recipientId)
        val groupId: GroupId? = SignalDatabase.groups.getGroup(recipientId).transform { it.id }.orNull()
        store.update { state -> state.copy(threadId = threadId, groupId = groupId) }
      }
    }

    override fun onRecipientChanged(recipient: Recipient) {
      store.update { state -> state.copy(recipient = recipient) }
    }

    override fun onCleared() {
      liveRecipient.removeForeverObserver(this)
    }
  }

  class MyViewModelFactory(val recipientId: RecipientId) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return Objects.requireNonNull(modelClass.cast(InternalViewModel(recipientId)))
    }
  }

  data class InternalState(
    val recipient: Recipient,
    val threadId: Long?,
    val groupId: GroupId?
  )
}
