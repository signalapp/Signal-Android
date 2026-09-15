/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalFoundationApi::class)

package org.thoughtcrime.securesms.contactshare.screens.details

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.glide.compose.GlideImage
import org.signal.glide.decryptableuri.DecryptableUri
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatar
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatarImage
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.ContactAction
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.DetailAction
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.DetailKind
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.DetailRow
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.recipients.RecipientId
import org.signal.core.ui.R as CoreUiR

/** Matches the share screen, so icons and text line up across both. */
private val LEADING_COLUMN_WIDTH = 72.dp

private val ROW_HIGHLIGHT_INSET_HORIZONTAL = 10.dp
private val ROW_HIGHLIGHT_INSET_VERTICAL = 2.dp
private val ROW_HIGHLIGHT_CORNER_RADIUS = 16.dp
private val HORIZONTAL_PADDING = 24.dp
private val AVATAR_SIZE = 80.dp

@Composable
fun SharedContactDetailsScreen(
  state: SharedContactDetailsState,
  onEvent: (SharedContactDetailsEvent) -> Unit
) {
  Scaffolds.Default(
    title = null,
    onNavigationClick = { onEvent(SharedContactDetailsEvent.BackClicked) },
    navigationIconRes = CoreUiR.drawable.symbol_arrow_start_24,
    navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description)
  ) { contentPadding ->
    if (state.isLoading) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .padding(contentPadding)
          .fillMaxSize()
      ) {
        CircularProgressIndicator()
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .padding(contentPadding)
          .fillMaxSize()
          .testTag(SharedContactDetailsTestTags.CONTENT)
      ) {
        item {
          Header(state = state, onEvent = onEvent)
        }

        if (state.actions.isNotEmpty()) {
          item { Dividers.Default() }

          items(state.actions, key = { it.name }) { action ->
            ActionRow(
              action = action,
              onClick = { onEvent(SharedContactDetailsEvent.ActionClicked(action)) }
            )
          }
        }

        if (state.details.isNotEmpty()) {
          item { Dividers.Default() }

          items(state.details, key = { it.id }) { detail ->
            DetailRowItem(
              detail = detail,
              contextMenu = state.contextMenu?.takeIf { it.detailId == detail.id },
              onPress = { onEvent(SharedContactDetailsEvent.DetailPressed(detail.id)) },
              onMenuAction = { onEvent(SharedContactDetailsEvent.DetailActionClicked(it)) },
              onMenuDismiss = { onEvent(SharedContactDetailsEvent.ContextMenuDismissed) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun Header(
  state: SharedContactDetailsState,
  onEvent: (SharedContactDetailsEvent) -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = HORIZONTAL_PADDING)
      .padding(bottom = 24.dp)
  ) {
    Avatar(photoUri = state.photoUri, displayName = state.displayName, hasPersonalName = state.hasPersonalName)

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = state.displayName,
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center
    )

    if (state.organization != null) {
      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = state.organization,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
    }

    if (state.showCallButtons) {
      Spacer(modifier = Modifier.height(24.dp))

      Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        CallButton(
          iconRes = R.drawable.symbol_chat_24,
          labelRes = R.string.SharedContactDetailsScreen__message,
          testTag = SharedContactDetailsTestTags.MESSAGE_BUTTON,
          onClick = { onEvent(SharedContactDetailsEvent.MessageClicked) }
        )
        CallButton(
          iconRes = R.drawable.symbol_video_24,
          labelRes = R.string.SharedContactDetailsScreen__video,
          testTag = SharedContactDetailsTestTags.VIDEO_CALL_BUTTON,
          onClick = { onEvent(SharedContactDetailsEvent.VideoCallClicked) }
        )
        CallButton(
          iconRes = CoreUiR.drawable.symbol_phone_24,
          labelRes = R.string.SharedContactDetailsScreen__audio,
          testTag = SharedContactDetailsTestTags.AUDIO_CALL_BUTTON,
          onClick = { onEvent(SharedContactDetailsEvent.AudioCallClicked) }
        )
      }
    }
  }
}

@Composable
private fun Avatar(photoUri: String?, displayName: String, hasPersonalName: Boolean) {
  val modifier = Modifier.size(AVATAR_SIZE).clip(CircleShape)

  if (photoUri == null) {
    val fallback = if (hasPersonalName) {
      FallbackAvatar.forTextOrDefault(displayName, AvatarColor.A100)
    } else {
      FallbackAvatar.Resource.Person(AvatarColor.A100)
    }

    FallbackAvatarImage(fallbackAvatar = fallback, modifier = Modifier.size(AVATAR_SIZE))
    return
  }

  if (LocalInspectionMode.current) {
    Image(
      painter = painterResource(R.drawable.ic_avatar_abstract_02),
      contentDescription = null,
      modifier = modifier
    )
  } else {
    GlideImage(
      model = DecryptableUri(photoUri.toUri()),
      contentScale = ContentScale.Crop,
      modifier = modifier
    )
  }
}

@Composable
private fun CallButton(iconRes: Int, labelRes: Int, testTag: String, onClick: () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(56.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(MaterialTheme.colorScheme.secondaryContainer)
        .clickable(onClick = onClick)
        .testTag(testTag)
    ) {
      Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSecondaryContainer
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(labelRes),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun ActionRow(action: ContactAction, onClick: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 16.dp)
      .testTag(SharedContactDetailsTestTags.actionRow(action))
  ) {
    LeadingIcon(iconRes = action.iconRes())

    Text(
      text = stringResource(action.labelRes()),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun DetailRowItem(
  detail: DetailRow,
  contextMenu: SharedContactDetailsState.ContextMenu?,
  onPress: () -> Unit,
  onMenuAction: (DetailAction) -> Unit,
  onMenuDismiss: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val menuActions = rememberMenuActions(contextMenu)

  val highlightColor = SignalTheme.colors.colorSurface5
  val highlight by animateColorAsState(
    // copy(alpha = 0f) rather than Color.Transparent, which would fade through grey.
    targetValue = if (isPressed || contextMenu != null) highlightColor else highlightColor.copy(alpha = 0f),
    label = "row-highlight"
  )

  Box {
    Box(
      modifier = Modifier
        .matchParentSize()
        .padding(horizontal = ROW_HIGHLIGHT_INSET_HORIZONTAL, vertical = ROW_HIGHLIGHT_INSET_VERTICAL)
        .background(highlight, RoundedCornerShape(ROW_HIGHLIGHT_CORNER_RADIUS))
    )

    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .testTag(SharedContactDetailsTestTags.detailRow(detail.id))
        .combinedClickable(
          interactionSource = interactionSource,
          indication = null,
          onLongClick = onPress,
          onClick = onPress
        )
        .padding(vertical = 12.dp)
    ) {
      LeadingIcon(iconRes = detail.kind.iconRes())

      Column(modifier = Modifier.padding(end = HORIZONTAL_PADDING)) {
        detail.lines.forEach { line ->
          Text(
            text = line,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        if (detail.label.isNotBlank()) {
          Text(
            text = detail.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    DropdownMenus.Menu(
      expanded = contextMenu != null,
      onDismissRequest = onMenuDismiss,
      offsetX = LEADING_COLUMN_WIDTH
    ) {
      menuActions.forEach { action ->
        DropdownMenus.Item(
          leadingIconResId = action.iconRes(),
          text = { Text(text = stringResource(action.labelRes())) },
          onClick = { onMenuAction(action) },
          modifier = Modifier.testTag(SharedContactDetailsTestTags.menuItem(action))
        )
      }
    }
  }
}

/** The popup outlives the state that opened it, so its content must survive the exit animation. */
@Composable
private fun rememberMenuActions(contextMenu: SharedContactDetailsState.ContextMenu?): List<DetailAction> {
  val retained = remember { RetainedActions() }

  if (contextMenu != null) {
    retained.value = contextMenu.actions
  }

  return retained.value
}

@Composable
private fun LeadingIcon(iconRes: Int) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.width(LEADING_COLUMN_WIDTH)
  ) {
    Icon(
      painter = painterResource(iconRes),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurface
    )
  }
}

private fun ContactAction.iconRes(): Int = when (this) {
  ContactAction.INVITE_TO_SIGNAL -> R.drawable.symbol_invite_24
  ContactAction.ADD_TO_PHONE_CONTACTS -> CoreUiR.drawable.symbol_person_circle_24
  ContactAction.ADD_TO_GROUP -> R.drawable.symbol_plus_circle_24
}

private fun ContactAction.labelRes(): Int = when (this) {
  ContactAction.INVITE_TO_SIGNAL -> R.string.SharedContactDetailsScreen__invite_to_signal
  ContactAction.ADD_TO_PHONE_CONTACTS -> R.string.SharedContactDetailsScreen__add_to_phone_contacts
  ContactAction.ADD_TO_GROUP -> R.string.SharedContactDetailsScreen__add_to_a_group
}

private fun DetailKind.iconRes(): Int = when (this) {
  DetailKind.PHONE -> CoreUiR.drawable.symbol_phone_24
  DetailKind.NICKNAME -> CoreUiR.drawable.symbol_edit_24
  DetailKind.NOTE -> R.drawable.symbol_note_24
  DetailKind.EMAIL -> R.drawable.symbol_invite_24
  DetailKind.ADDRESS -> R.drawable.symbol_pin_24
}

private fun DetailAction.iconRes(): Int = when (this) {
  DetailAction.MESSAGE -> R.drawable.symbol_chat_24
  DetailAction.VIDEO_CALL -> R.drawable.symbol_video_24
  DetailAction.AUDIO_CALL -> CoreUiR.drawable.symbol_phone_24
  DetailAction.OPEN_IN_MAPS -> R.drawable.symbol_open_24
  DetailAction.COPY -> CoreUiR.drawable.symbol_copy_android_24
}

private fun DetailAction.labelRes(): Int = when (this) {
  DetailAction.MESSAGE -> R.string.SharedContactDetailsScreen__message
  DetailAction.VIDEO_CALL -> R.string.SharedContactDetailsScreen__video_call
  DetailAction.AUDIO_CALL -> R.string.SharedContactDetailsScreen__audio_call
  DetailAction.OPEN_IN_MAPS -> R.string.SharedContactDetailsScreen__open_in_maps
  DetailAction.COPY -> R.string.SharedContactDetailsScreen__copy
}

private class RetainedActions(var value: List<DetailAction> = emptyList())

@DayNightPreviews
@Composable
private fun SharedContactDetailsOnSignalPreview() {
  Previews.Preview {
    SharedContactDetailsScreen(state = previewState(), onEvent = {})
  }
}

@DayNightPreviews
@Composable
private fun SharedContactDetailsWithNicknameAndNotePreview() {
  Previews.Preview {
    SharedContactDetailsScreen(state = previewState(withNicknameAndNote = true), onEvent = {})
  }
}

@DayNightPreviews
@Composable
private fun SharedContactDetailsNotOnSignalPreview() {
  Previews.Preview {
    SharedContactDetailsScreen(state = previewState(isOnSignal = false), onEvent = {})
  }
}

@DayNightPreviews
@Composable
private fun SharedContactDetailsMinimalPreview() {
  Previews.Preview {
    SharedContactDetailsScreen(state = previewState(withDetails = false), onEvent = {})
  }
}

@DayNightPreviews
@Composable
private fun SharedContactDetailsWithOrganizationPreview() {
  Previews.Preview {
    SharedContactDetailsScreen(
      state = previewState().copy(organization = "Signal Messenger"),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun SharedContactDetailsAddressOnlyBusinessPreview() {
  Previews.Preview {
    SharedContactDetailsScreen(
      state = SharedContactDetailsState(
        displayName = "Pacific Plumbing",
        photoUri = null,
        actions = emptyList(),
        details = listOf(
          DetailRow("address:0", listOf("123 Beach Drive", "San Francisco CA", "United States"), "Home", DetailKind.ADDRESS)
        )
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun SharedContactDetailsContextMenuPreview() {
  Previews.Preview {
    SharedContactDetailsScreen(
      state = previewState().copy(
        contextMenu = SharedContactDetailsState.ContextMenu(
          detailId = "phone:0",
          actions = listOf(DetailAction.MESSAGE, DetailAction.VIDEO_CALL, DetailAction.AUDIO_CALL, DetailAction.COPY)
        )
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun SharedContactDetailsAddressContextMenuPreview() {
  Previews.Preview {
    SharedContactDetailsScreen(
      state = previewState().copy(
        contextMenu = SharedContactDetailsState.ContextMenu(
          detailId = "address:0",
          actions = listOf(DetailAction.OPEN_IN_MAPS, DetailAction.COPY)
        )
      ),
      onEvent = {}
    )
  }
}

private fun previewState(
  isOnSignal: Boolean = true,
  withNicknameAndNote: Boolean = false,
  withDetails: Boolean = true
): SharedContactDetailsState {
  val details = if (!withDetails) {
    emptyList()
  } else {
    buildList {
      add(DetailRow("phone:0", listOf("+1 555-555-4567"), "Mobile", DetailKind.PHONE))
      if (withNicknameAndNote) {
        add(DetailRow("nickname", listOf("Paige"), "Nickname", DetailKind.NICKNAME))
        add(DetailRow("note", listOf("Met in 2017"), "Notes", DetailKind.NOTE))
      }
      add(DetailRow("email:0", listOf("paigehall@example.com"), "Home", DetailKind.EMAIL))
      add(DetailRow("address:0", listOf("123 Beach Drive", "San Francisco CA", "United States"), "Home", DetailKind.ADDRESS))
    }
  }

  return SharedContactDetailsState(
    displayName = "Paige Hall",
    photoUri = "",
    signalRecipientId = if (isOnSignal) RecipientId.from(1L) else null,
    actions = SharedContactDetailsViewModel.contactActionsFor(
      isOnSignal = isOnSignal,
      hasInviteTarget = withDetails,
      hasAnythingToSave = withDetails
    ),
    details = details
  )
}
