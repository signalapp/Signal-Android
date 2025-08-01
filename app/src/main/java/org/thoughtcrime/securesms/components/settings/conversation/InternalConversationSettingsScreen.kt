/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.Texts
import org.signal.core.util.Hex.fromStringCondensed
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.RecipientId

private enum class Dialog {
  NONE,
  DISABLE_PROFILE_SHARING,
  CLEAR_SENDER_KEY,
  DELETE_SESSIONS,
  ARCHIVE_SESSIONS,
  DELETE_AVATAR,
  CLEAR_RECIPIENT_DATA,
  ADD_1000_MESSAGES,
  ADD_10_MESSAGES,
  SPLIT_AND_CREATE_THREADS,
  SPLIT_WITHOUT_CREATING_THREADS
}

/**
 * Shows internal details about a recipient that you can view from the conversation settings.
 */
@Composable
fun InternalConversationSettingsScreen(
  state: InternalConversationSettingsState,
  callbacks: InternalConversationSettingsScreenCallbacks
) {
  var dialog by remember { mutableStateOf(Dialog.NONE) }

  Scaffolds.Settings(
    title = stringResource(R.string.ConversationSettingsFragment__internal_details),
    onNavigationClick = callbacks::onNavigationClick,
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
    navigationContentDescription = stringResource(R.string.CallScreenTopBar__go_back)
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier.padding(paddingValues)
    ) {
      item {
        Texts.SectionHeader(
          text = "Data"
        )
      }

      item {
        Rows.TextRow(
          text = "RecipientId",
          label = state.recipientId.toString()
        )
      }

      if (!state.isGroup) {
        item {
          LongClickToCopy(
            text = "E164",
            label = state.e164,
            callback = callbacks
          )
        }

        item {
          LongClickToCopy(
            text = "ACI",
            label = state.aci,
            callback = callbacks
          )
        }

        item {
          LongClickToCopy(
            text = "PNI",
            label = state.pni,
            callback = callbacks
          )
        }
      }

      if (state.groupIdString != null) {
        item {
          LongClickToCopy(
            text = "GroupId",
            label = state.groupIdString,
            callback = callbacks
          )
        }
      }

      item {
        LongClickToCopy(
          text = "ThreadId",
          label = state.threadIdString,
          callback = callbacks
        )
      }

      if (!state.isGroup) {
        item {
          Rows.TextRow(
            text = "Profile Name",
            label = state.profileName
          )
        }

        item {
          LongClickToCopy(
            text = "Profile Key (Base64)",
            label = state.profileKeyBase64,
            callback = callbacks
          )
        }

        item {
          LongClickToCopy(
            text = "Profile Key (Hex)",
            label = state.profileKeyHex,
            callback = callbacks
          )
        }

        item {
          Rows.TextRow(
            text = "Sealed Sender Mode",
            label = state.sealedSenderAccessMode
          )
        }

        item {
          Rows.TextRow(
            text = "Phone Number Sharing",
            label = state.phoneNumberSharing
          )
        }

        item {
          Rows.TextRow(
            text = "Phone Number Discoverability",
            label = state.phoneNumberDiscoverability
          )
        }
      }

      item {
        Rows.TextRow(
          text = "Profile Sharing (AKA \"Whitelisted\")",
          label = state.profileSharing
        )
      }

      if (!state.isGroup) {
        item {
          Rows.TextRow(
            text = remember { AnnotatedString("Capabilities") },
            label = state.capabilities
          )
        }
      }

      item {
        val onClick = remember(state.threadId) {
          { callbacks.triggerThreadUpdate(state.threadId) }
        }

        Rows.TextRow(
          text = "Trigger Thread Update",
          label = "Triggers a thread update. Useful for testing perf.",
          onClick = onClick
        )
      }

      item {
        Texts.SectionHeader(text = "Actions")
      }

      if (state.isGroup) {
        item {
          Rows.TextRow(
            text = "Clear sender key",
            label = "Resets any sender key state, meaning the next message will require re-distributing sender key material.",
            onClick = {
              dialog = Dialog.CLEAR_SENDER_KEY
            }
          )
        }
      } else {
        item {
          Rows.TextRow(
            text = "Disable Profile Sharing",
            label = "Clears profile sharing/whitelisted status, which should cause the Message Request UI to show.",
            onClick = {
              dialog = Dialog.DISABLE_PROFILE_SHARING
            }
          )
        }

        item {
          Rows.TextRow(
            text = "Delete Sessions",
            label = "Deletes all sessions with this recipient, essentially guaranteeing an encryption error if they send you a message.",
            onClick = {
              dialog = Dialog.DELETE_SESSIONS
            }
          )
        }

        item {
          Rows.TextRow(
            text = "Archive Sessions",
            label = "Archives all sessions associated with this recipient, causing you to create a new session the next time you send a message (while not causing decryption errors).",
            onClick = {
              dialog = Dialog.ARCHIVE_SESSIONS
            }
          )
        }
      }

      item {
        Rows.TextRow(
          text = "Delete Avatar",
          label = "Deletes the avatar file and clears manually showing the avatar, resulting in a blurred gradient (assuming no profile sharing, no group in common, etc.)",
          onClick = {
            dialog = Dialog.DELETE_AVATAR
          }
        )
      }

      item {
        Rows.TextRow(
          text = "Clear recipient data",
          label = "Clears service id, profile data, sessions, identities, and thread.",
          onClick = {
            dialog = Dialog.CLEAR_RECIPIENT_DATA
          }
        )
      }

      if (!state.isGroup) {
        item {
          Rows.TextRow(
            text = "Add 1,000 dummy messages",
            label = "Just adds 1,000 random messages to the chat. Text-only, nothing complicated.",
            onClick = {
              dialog = Dialog.ADD_1000_MESSAGES
            }
          )
        }

        item {
          Rows.TextRow(
            text = "Add 10 dummy messages with attachments",
            label = "Adds 10 random messages to the chat with attachments of a random image. Attachments are not uploaded.",
            onClick = {
              dialog = Dialog.ADD_10_MESSAGES
            }
          )
        }
      }

      if (state.isSelf) {
        item {
          Texts.SectionHeader(text = "Donations")
        }

        item {
          val onLongClick = remember(state.subscriberId) {
            { callbacks.copyToClipboard(state.subscriberId) }
          }

          Rows.TextRow(
            text = "Subscriber ID",
            label = state.subscriberId,
            onLongClick = onLongClick
          )
        }
      }

      item {
        Texts.SectionHeader(
          text = "PNP"
        )
      }

      item {
        Rows.TextRow(
          text = "Split and create threads",
          label = "Splits this contact into two recipients and two threads so that you can test merging them together. This will remain the 'primary' recipient.",
          onClick = {
            dialog = Dialog.SPLIT_AND_CREATE_THREADS
          }
        )

        Rows.TextRow(
          text = "Split without creating threads",
          label = "Splits this contact into two recipients so you can test merging them together. This will become the PNI-based recipient. Another recipient will be made with this ACI and profile key. Doing a CDS refresh should allow you to see a Session Switchover Event, as long as you had a session with this PNI.",
          onClick = {
            dialog = Dialog.SPLIT_WITHOUT_CREATING_THREADS
          }
        )
      }
    }
  }

  if (dialog != Dialog.NONE) {
    val onConfirm = rememberOnConfirm(state, callbacks, dialog)

    AreYouSureDialog(onConfirm) {
      dialog = Dialog.NONE
    }
  }
}

@Composable
private fun rememberOnConfirm(
  state: InternalConversationSettingsState,
  callbacks: InternalConversationSettingsScreenCallbacks,
  dialog: Dialog
): () -> Unit {
  return remember(dialog, callbacks, state.threadId, state.recipientId) {
    when (dialog) {
      Dialog.NONE -> {
        {}
      }
      Dialog.DISABLE_PROFILE_SHARING -> {
        { callbacks.disableProfileSharing(state.recipientId) }
      }
      Dialog.DELETE_SESSIONS -> {
        { callbacks.deleteSessions(state.recipientId) }
      }
      Dialog.ARCHIVE_SESSIONS -> {
        { callbacks.archiveSessions(state.recipientId) }
      }
      Dialog.DELETE_AVATAR -> {
        { callbacks.deleteAvatar(state.recipientId) }
      }
      Dialog.CLEAR_RECIPIENT_DATA -> {
        { callbacks.clearRecipientData(state.recipientId) }
      }
      Dialog.ADD_1000_MESSAGES -> {
        { callbacks.add1000Messages(state.recipientId) }
      }
      Dialog.ADD_10_MESSAGES -> {
        { callbacks.add10Messages(state.recipientId) }
      }
      Dialog.SPLIT_AND_CREATE_THREADS -> {
        { callbacks.splitAndCreateThreads(state.recipientId) }
      }
      Dialog.SPLIT_WITHOUT_CREATING_THREADS -> {
        { callbacks.splitWithoutCreatingThreads(state.recipientId) }
      }
      Dialog.CLEAR_SENDER_KEY -> {
        { callbacks.clearSenderKey(state.recipientId) }
      }
    }
  }
}

@Composable
private fun LongClickToCopy(
  text: String,
  label: String,
  callback: InternalConversationSettingsScreenCallbacks
) {
  val onLongClick = remember(label, callback) {
    { callback.copyToClipboard(label) }
  }

  Rows.TextRow(
    text = text,
    label = label,
    onLongClick = onLongClick
  )
}

@Composable
private fun AreYouSureDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = "",
    body = "Are you sure?",
    confirm = stringResource(android.R.string.ok),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = onConfirm,
    onDismiss = onDismiss
  )
}

@SignalPreview
@Composable
fun InternalConversationSettingsScreenPreview() {
  Previews.Preview {
    InternalConversationSettingsScreen(
      state = createState(),
      callbacks = InternalConversationSettingsScreenCallbacks.Empty
    )
  }
}

@SignalPreview
@Composable
fun InternalConversationSettingsScreenGroupPreview() {
  Previews.Preview {
    InternalConversationSettingsScreen(
      state = createState(GroupId.v2(GroupMasterKey(fromStringCondensed("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")))),
      callbacks = InternalConversationSettingsScreenCallbacks.Empty
    )
  }
}

private fun createState(
  groupId: GroupId? = null
): InternalConversationSettingsState {
  return InternalConversationSettingsState(
    recipientId = RecipientId.from(1),
    isGroup = groupId != null,
    e164 = "+11111111111",
    aci = "TEST-ACI",
    pni = "TEST-PNI",
    groupId = groupId,
    threadId = 12,
    profileName = "Miles Morales",
    profileKeyBase64 = "profile64",
    profileKeyHex = "profileHex",
    sealedSenderAccessMode = "SealedSenderAccessMode",
    phoneNumberSharing = "PhoneNumberSharing",
    phoneNumberDiscoverability = "PhoneNumberDiscoverability",
    profileSharing = "ProfileSharing",
    capabilities = AnnotatedString("CapabilitiesString"),
    hasServiceId = true,
    isSelf = groupId == null,
    subscriberId = "SubscriberId"
  )
}

@Stable
interface InternalConversationSettingsScreenCallbacks {
  fun onNavigationClick() = Unit
  fun copyToClipboard(data: String) = Unit
  fun triggerThreadUpdate(threadId: Long?) = Unit
  fun disableProfileSharing(recipientId: RecipientId) = Unit
  fun deleteSessions(recipientId: RecipientId) = Unit
  fun archiveSessions(recipientId: RecipientId) = Unit
  fun deleteAvatar(recipientId: RecipientId) = Unit
  fun clearRecipientData(recipientId: RecipientId) = Unit
  fun add1000Messages(recipientId: RecipientId) = Unit
  fun add10Messages(recipientId: RecipientId) = Unit
  fun splitAndCreateThreads(recipientId: RecipientId) = Unit
  fun splitWithoutCreatingThreads(recipientId: RecipientId) = Unit
  fun clearSenderKey(recipientId: RecipientId) = Unit

  object Empty : InternalConversationSettingsScreenCallbacks
}
