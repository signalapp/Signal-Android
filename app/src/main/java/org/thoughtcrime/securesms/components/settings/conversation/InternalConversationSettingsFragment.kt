package org.thoughtcrime.securesms.components.settings.conversation

import android.graphics.Color
import android.text.TextUtils
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.Hex
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.livedata.Store
import java.util.Objects
import java.util.UUID

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

      val uuid = recipient.uuid.transform(UUID::toString).or("null")
      longClickPref(
        title = DSLSettingsText.from("UUID"),
        summary = DSLSettingsText.from(uuid),
        onLongClick = { copyToClipboard(uuid) }
      )

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

      textPref(
        title = DSLSettingsText.from("Profile Sharing (AKA \"Whitelisted\")"),
        summary = DSLSettingsText.from(recipient.isProfileSharing.toString())
      )

      textPref(
        title = DSLSettingsText.from("Capabilities"),
        summary = DSLSettingsText.from(buildCapabilitySpan(recipient))
      )

      sectionHeaderPref(DSLSettingsText.from("Actions"))

      clickPref(
        title = DSLSettingsText.from("Disable Profile Sharing"),
        summary = DSLSettingsText.from("Clears profile sharing/whitelisted status, which should cause the Message Request UI to show."),
        onClick = {
          MaterialAlertDialogBuilder(requireContext())
            .setTitle("Are you sure?")
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .setPositiveButton(android.R.string.ok) { _, _ -> DatabaseFactory.getRecipientDatabase(requireContext()).setProfileSharing(recipient.id, false) }
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
              if (recipient.hasUuid()) {
                DatabaseFactory.getSessionDatabase(context).deleteAllFor(recipient.requireUuid().toString())
              }
              if (recipient.hasE164()) {
                DatabaseFactory.getSessionDatabase(context).deleteAllFor(recipient.requireE164())
              }
            }
            .show()
        }
      )
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

    private val store = Store(InternalState(Recipient.resolved(recipientId)))

    val state = store.stateLiveData
    val liveRecipient = Recipient.live(recipientId)

    init {
      liveRecipient.observeForever(this)
    }

    override fun onRecipientChanged(recipient: Recipient) {
      store.update { InternalState(recipient) }
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
    val recipient: Recipient
  )
}
