package org.thoughtcrime.securesms.safety

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.LocalFragmentManager
import org.signal.core.ui.compose.Previews
import org.signal.core.util.or
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.rememberRecipientField
import org.thoughtcrime.securesms.verify.VerifyIdentityFragment

/**
 * Compose content for the safety number bottom sheet.
 *
 * @param state Current sheet state from [SafetyNumberBottomSheetViewModel].
 * @param initialUntrustedCount Original untrusted recipient count from [SafetyNumberBottomSheetArgs],
 *   used for the "You have X connections" subtitle when [SafetyNumberBottomSheetState.hasLargeNumberOfUntrustedRecipients] is true.
 * @param getIdentityRecord Suspending function that fetches the identity record for a recipient,
 *   used when the user chooses to verify a safety number.
 * @param emitter Callback for user-driven events.
 */
@Composable
fun SafetyNumberBottomSheetContent(
  state: SafetyNumberBottomSheetState,
  initialUntrustedCount: Int,
  getIdentityRecord: suspend (RecipientId) -> IdentityRecord?,
  emitter: (SafetyNumberBottomSheetEvent) -> Unit
) {
  val recipients = remember(state) {
    if (!state.hasLargeNumberOfUntrustedRecipients) {
      state.destinationToRecipientMap.values.flatten().distinct()
    } else {
      emptyList()
    }
  }

  val fragmentManager = LocalFragmentManager.current
  val scope = rememberCoroutineScope()
  val wrappedEmitter: (SafetyNumberBottomSheetEvent) -> Unit = remember(emitter, fragmentManager) {
    { event ->
      when (event) {
        is SafetyNumberBottomSheetEvent.VerifySafetyNumber -> scope.launch {
          val record = getIdentityRecord(event.recipientId) ?: return@launch
          val fm = fragmentManager ?: error("SafetyNumberBottomSheetContent requires a FragmentManager via LocalFragmentManager.")
          VerifyIdentityFragment.createDialog(
            recipientId = event.recipientId,
            remoteIdentity = IdentityKeyParcelable(record.identityKey),
            verified = false
          ).show(fm, null)
        }

        else -> emitter(event)
      }
    }
  }

  var showReviewConnections by remember { mutableStateOf(false) }

  SafetyNumberBottomSheetContentInternal(
    hasLargeList = state.hasLargeNumberOfUntrustedRecipients,
    isCheckupComplete = state.isCheckupComplete(),
    isEmpty = state.isEmpty(),
    sendAnywayFired = state.sendAnywayFired,
    recipients = recipients,
    initialUntrustedCount = initialUntrustedCount,
    onReviewConnectionsClick = {
      showReviewConnections = true
      emitter(SafetyNumberBottomSheetEvent.ReviewConnections)
    },
    emitter = wrappedEmitter
  )

  if (showReviewConnections) {
    Dialog(
      onDismissRequest = { showReviewConnections = false },
      properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      SafetyNumberReviewConnectionsScreen(
        state = state,
        onDoneClick = { showReviewConnections = false },
        emitter = wrappedEmitter
      )
    }
  }
}

@Composable
private fun SafetyNumberBottomSheetContentInternal(
  hasLargeList: Boolean,
  isCheckupComplete: Boolean,
  isEmpty: Boolean,
  sendAnywayFired: Boolean,
  recipients: List<SafetyNumberRecipient>,
  initialUntrustedCount: Int,
  onReviewConnectionsClick: () -> Unit,
  emitter: (SafetyNumberBottomSheetEvent) -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
  ) {
    BottomSheets.Handle()

    Spacer(modifier = Modifier.height(24.dp))

    Icon(
      imageVector = ImageVector.vectorResource(id = R.drawable.symbol_safety_number_24),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.size(56.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = stringResource(
        id = when {
          isCheckupComplete && hasLargeList -> R.string.SafetyNumberBottomSheetFragment__safety_number_checkup_complete
          hasLargeList -> R.string.SafetyNumberBottomSheetFragment__safety_number_checkup
          else -> R.string.SafetyNumberBottomSheetFragment__safety_number_changes
        }
      ),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 24.dp)
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = when {
        isCheckupComplete && hasLargeList -> stringResource(R.string.SafetyNumberBottomSheetFragment__all_connections_have_been_reviewed)
        hasLargeList -> pluralStringResource(R.plurals.SafetyNumberBottomSheetFragment__you_have_d_connections_plural, initialUntrustedCount, initialUntrustedCount)
        else -> stringResource(R.string.SafetyNumberBottomSheetFragment__the_following_people)
      },
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 24.dp)
    )

    if (isEmpty) {
      Spacer(modifier = Modifier.height(48.dp))
      Text(
        text = stringResource(R.string.SafetyNumberBottomSheetFragment__no_more_recipients_to_show),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp)
      )
      Spacer(modifier = Modifier.height(48.dp))
    } else if (!hasLargeList) {
      Spacer(modifier = Modifier.height(8.dp))
      recipients.forEach { safetyNumberRecipient ->
        SafetyNumberRecipientRow(safetyNumberRecipient = safetyNumberRecipient, emitter = emitter)
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
    ) {
      if (hasLargeList) {
        TextButton(onClick = onReviewConnectionsClick) {
          Text(text = stringResource(R.string.SafetyNumberBottomSheetFragment__review_connections))
        }
      }
      Spacer(modifier = Modifier.weight(1f))
      Buttons.MediumTonal(enabled = !sendAnywayFired, onClick = { emitter(SafetyNumberBottomSheetEvent.SendAnyway) }) {
        Text(
          text = stringResource(
            if (isCheckupComplete) R.string.conversation_activity__send
            else R.string.SafetyNumberBottomSheetFragment__send_anyway
          )
        )
      }
    }
  }
}

@Composable
fun SafetyNumberRecipientRow(
  safetyNumberRecipient: SafetyNumberRecipient,
  emitter: (SafetyNumberBottomSheetEvent) -> Unit
) {
  val context = LocalContext.current
  val menuController = remember { DropdownMenus.MenuController() }
  val displayName by rememberRecipientField(safetyNumberRecipient.recipient) { getDisplayName(context) }
  val identifier by rememberRecipientField(safetyNumberRecipient.recipient) { e164.or(username).orElse(null) }
  val isVerified = safetyNumberRecipient.identityRecord.verifiedStatus == IdentityTable.VerifiedStatus.VERIFIED
  val secondaryText = remember(identifier, isVerified) { buildSecondaryText(identifier, isVerified, context) }

  Box(modifier = Modifier.fillMaxWidth()) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .clickable { menuController.show() }
        .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
      AvatarImage(
        recipient = safetyNumberRecipient.recipient,
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
      )

      Spacer(modifier = Modifier.width(16.dp))

      Column {
        Text(
          text = displayName,
          style = MaterialTheme.typography.bodyLarge
        )
        if (!secondaryText.isNullOrBlank()) {
          Text(
            text = secondaryText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    DropdownMenus.Menu(controller = menuController) { controller ->
      DropdownMenus.ItemWithIcon(
        menuController = controller,
        drawableResId = R.drawable.ic_safety_number_24,
        stringResId = R.string.SafetyNumberBottomSheetFragment__verify_safety_number,
        onClick = { emitter(SafetyNumberBottomSheetEvent.VerifySafetyNumber(safetyNumberRecipient.recipient.id)) }
      )
      if (safetyNumberRecipient.distributionListMembershipCount > 0) {
        DropdownMenus.ItemWithIcon(
          menuController = controller,
          drawableResId = R.drawable.ic_circle_x_24,
          stringResId = R.string.SafetyNumberBottomSheetFragment__remove_from_story,
          onClick = { emitter(SafetyNumberBottomSheetEvent.RemoveFromStory(safetyNumberRecipient.recipient.id)) }
        )
      }
      if (safetyNumberRecipient.distributionListMembershipCount == 0 && safetyNumberRecipient.groupMembershipCount == 0) {
        DropdownMenus.ItemWithIcon(
          menuController = controller,
          drawableResId = R.drawable.ic_circle_x_24,
          stringResId = R.string.SafetyNumberReviewConnectionsFragment__remove,
          onClick = { emitter(SafetyNumberBottomSheetEvent.RemoveDestination(safetyNumberRecipient.recipient.id)) }
        )
      }
    }
  }
}

private fun buildSecondaryText(identifier: String?, isVerified: Boolean, context: Context): String? {
  return when {
    isVerified && identifier.isNullOrBlank() -> context.getString(R.string.SafetyNumberRecipientRowItem__verified)
    isVerified -> context.getString(R.string.SafetyNumberRecipientRowItem__s_dot_verified, identifier)
    else -> identifier
  }
}

@Suppress("UNCHECKED_CAST")
private fun <T> Any?.unsafeCast(): T = this as T

private fun previewSafetyRecipient(
  firstName: String,
  lastName: String = "",
  isVerified: Boolean = false,
  distributionListMembershipCount: Int = 0,
  groupMembershipCount: Int = 0
): SafetyNumberRecipient {
  return SafetyNumberRecipient(
    recipient = Recipient(profileName = ProfileName.fromParts(firstName, lastName)),
    identityRecord = IdentityRecord(
      recipientId = RecipientId.UNKNOWN,
      identityKey = FakeIdentityKey(0),
      verifiedStatus = if (isVerified) IdentityTable.VerifiedStatus.VERIFIED else IdentityTable.VerifiedStatus.DEFAULT,
      firstUse = false,
      timestamp = 0L,
      nonblockingApproval = false
    ),
    distributionListMembershipCount = distributionListMembershipCount,
    groupMembershipCount = groupMembershipCount
  )
}

@DayNightPreviews
@Composable
private fun PreviewSmallList() {
  Previews.BottomSheetContentPreview {
    SafetyNumberBottomSheetContent(
      state = SafetyNumberBottomSheetState(
        untrustedRecipientCount = 2,
        hasLargeNumberOfUntrustedRecipients = false,
        destinationToRecipientMap = mapOf(
          SafetyNumberBucket.ContactsBucket to listOf(
            previewSafetyRecipient("Alice", "Smith"),
            previewSafetyRecipient("Bob", "Chen", isVerified = true, distributionListMembershipCount = 1)
          )
        ),
        loadState = SafetyNumberBottomSheetState.LoadState.READY
      ),
      initialUntrustedCount = 2,
      getIdentityRecord = { null },
      emitter = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun PreviewLargeList() {
  Previews.BottomSheetContentPreview {
    SafetyNumberBottomSheetContent(
      state = SafetyNumberBottomSheetState(
        untrustedRecipientCount = 12,
        hasLargeNumberOfUntrustedRecipients = true,
        loadState = SafetyNumberBottomSheetState.LoadState.READY
      ),
      initialUntrustedCount = 12,
      getIdentityRecord = { null },
      emitter = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun PreviewCheckupComplete() {
  Previews.BottomSheetContentPreview {
    SafetyNumberBottomSheetContent(
      state = SafetyNumberBottomSheetState(
        untrustedRecipientCount = 12,
        hasLargeNumberOfUntrustedRecipients = true,
        loadState = SafetyNumberBottomSheetState.LoadState.DONE
      ),
      initialUntrustedCount = 12,
      getIdentityRecord = { null },
      emitter = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun PreviewEmpty() {
  Previews.BottomSheetContentPreview {
    SafetyNumberBottomSheetContent(
      state = SafetyNumberBottomSheetState(
        untrustedRecipientCount = 1,
        hasLargeNumberOfUntrustedRecipients = false,
        loadState = SafetyNumberBottomSheetState.LoadState.READY
      ),
      initialUntrustedCount = 1,
      getIdentityRecord = { null },
      emitter = {}
    )
  }
}

/**
 * Since ECPublicKey relies on native code that we don't have access to in
 * previews, this lets us create an 'IdentityKey' that doesn't break them.
 */
private class FakeIdentityKey(private val id: Int) : IdentityKey(null.unsafeCast<ECPublicKey>()) {
  override fun equals(other: Any?): Boolean = other is FakeIdentityKey && other.id == id
  override fun hashCode(): Int = id.hashCode()
}
