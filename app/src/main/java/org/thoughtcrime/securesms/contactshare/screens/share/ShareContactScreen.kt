/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package org.thoughtcrime.securesms.contactshare.screens.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.LocalChatColorProvider
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.glide.compose.GlideImage
import org.signal.glide.decryptableuri.DecryptableUri
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatar
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatarImage
import org.thoughtcrime.securesms.contactshare.PHOTO_ID_ADDRESS_BOOK
import org.thoughtcrime.securesms.contactshare.PHOTO_ID_NONE
import org.thoughtcrime.securesms.contactshare.PHOTO_ID_SIGNAL_PROFILE
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactState.ContactPhoto
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactState.DetailLabel
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactState.DetailSelection
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.recipients.RecipientId
import org.signal.core.ui.R as CoreUiR

/** Width of the leading selection column, so every row's content shares one text margin. */
private val SELECTION_COLUMN_WIDTH = 72.dp
private val HORIZONTAL_PADDING = 24.dp
private val AVATAR_SIZE = 72.dp
private val EDIT_BADGE_SIZE = 32.dp

/** The badge sits off the avatar's corner rather than tucked inside it. */
private val EDIT_BADGE_OVERHANG = 4.dp
private val PICKER_PHOTO_SIZE = 96.dp
private val PICKER_PHOTO_SPACING = 24.dp

@Composable
fun ShareContactScreen(
  state: ShareContactState,
  onEvent: (ShareContactEvent) -> Unit
) {
  Scaffolds.Default(
    title = stringResource(R.string.ShareContactScreen__share_contact),
    onNavigationClick = { onEvent(ShareContactEvent.BackClicked) },
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
      Column(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f).testTag(ShareContactTestTags.CONTENT)) {
          if (state.avatar != null) {
            item {
              AvatarRow(
                avatar = state.avatar,
                displayName = state.name?.displayName.orEmpty(),
                onToggle = { onEvent(ShareContactEvent.AvatarToggled) },
                onEditClick = { onEvent(ShareContactEvent.EditPhotoClicked) }
              )
            }
          }

          if (state.name != null) {
            item {
              NameRow(
                name = state.name,
                onToggle = { onEvent(ShareContactEvent.NameToggled) },
                onEditClick = { onEvent(ShareContactEvent.EditNameClicked) }
              )
            }
          }

          items(state.details, key = { it.id }) { detail ->
            DetailRow(
              detail = detail,
              onToggle = { onEvent(ShareContactEvent.DetailToggled(detail.id)) }
            )
          }
        }

        Footer(
          sendingTo = state.sendingTo,
          recipientId = state.recipientId,
          canSend = state.canSend,
          isSending = state.isSending,
          onSendClick = { onEvent(ShareContactEvent.SendClicked) }
        )
      }
    }
  }

  if (state.photoPicker != null) {
    PhotoPickerSheet(
      picker = state.photoPicker,
      displayName = state.name?.displayName.orEmpty(),
      onPhotoSelected = { onEvent(ShareContactEvent.PhotoSelected(it)) },
      onConfirm = { onEvent(ShareContactEvent.PhotoPickerConfirmed) },
      onDismiss = { onEvent(ShareContactEvent.PhotoPickerDismissed) }
    )
  }
}

@Composable
private fun AvatarRow(
  avatar: ShareContactState.AvatarSelection,
  displayName: String,
  onToggle: () -> Unit,
  onEditClick: () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .testTag(ShareContactTestTags.AVATAR_ROW)
      .clickable(onClick = onToggle)
      .padding(vertical = 12.dp)
  ) {
    SelectionColumn(isSelected = avatar.isSelected)

    Box {
      ContactPhotoImage(
        photo = avatar.photo,
        displayName = displayName,
        modifier = Modifier.size(AVATAR_SIZE)
      )

      if (avatar.isEditable) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = EDIT_BADGE_OVERHANG, y = EDIT_BADGE_OVERHANG)
            .size(EDIT_BADGE_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(ShareContactTestTags.EDIT_PHOTO_BUTTON)
            .clickable(onClick = onEditClick)
        ) {
          Icon(
            painter = painterResource(CoreUiR.drawable.symbol_edit_24),
            contentDescription = stringResource(R.string.ShareContactScreen__change_photo),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun NameRow(
  name: ShareContactState.NameSelection,
  onToggle: () -> Unit,
  onEditClick: () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .testTag(ShareContactTestTags.NAME_ROW)
      .then(if (name.isToggleable) Modifier.clickable(onClick = onToggle) else Modifier)
      .padding(vertical = 16.dp)
  ) {
    SelectionColumn(isSelected = name.isSelected)

    Text(
      text = name.displayName,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f)
    )

    if (name.isEditable) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .testTag(ShareContactTestTags.EDIT_NAME_BUTTON)
          .clickable(onClick = onEditClick)
      ) {
        Icon(
          painter = painterResource(CoreUiR.drawable.symbol_edit_24),
          contentDescription = stringResource(R.string.ShareContactScreen__edit_name),
          tint = MaterialTheme.colorScheme.onSurface
        )
      }
    }

    Spacer(modifier = Modifier.width(HORIZONTAL_PADDING))
  }
}

@Composable
private fun DetailRow(detail: DetailSelection, onToggle: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .testTag(ShareContactTestTags.detailRow(detail.id))
      .clickable(onClick = onToggle)
      .padding(vertical = 12.dp)
  ) {
    SelectionColumn(isSelected = detail.isSelected)

    Column(modifier = Modifier.padding(end = HORIZONTAL_PADDING)) {
      detail.lines.forEach { line ->
        Text(
          text = line,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      Text(
        text = detail.label.resolve(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

/**
 * Leading column holding the selection control. Fixed width so that every row aligns its text to the
 * same margin regardless of row height.
 */
@Composable
private fun SelectionColumn(isSelected: Boolean) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.width(SELECTION_COLUMN_WIDTH)
  ) {
    SelectionCheck(isSelected = isSelected)
  }
}

/**
 * Filled circle with a check when selected, hollow outlined circle when not. The unselected state
 * keeps a surface fill so that it stays visible when drawn over a photo.
 */
@Composable
private fun SelectionCheck(isSelected: Boolean, modifier: Modifier = Modifier) {
  if (isSelected) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = modifier
        .size(24.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primary)
    ) {
      Icon(
        painter = painterResource(CoreUiR.drawable.symbol_check_24),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(16.dp)
      )
    }
  } else {
    Box(
      modifier = modifier
        .size(24.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surface)
        .border(width = 2.dp, color = MaterialTheme.colorScheme.outline, shape = CircleShape)
    )
  }
}

@Composable
private fun Footer(
  sendingTo: String,
  recipientId: RecipientId?,
  canSend: Boolean,
  isSending: Boolean,
  onSendClick: () -> Unit
) {
  val chatColor: Color? = recipientId?.let { LocalChatColorProvider.current(it.toLong()).value }

  val sendBackground = chatColor ?: colorResource(CoreUiR.color.signal_light_colorPrimary)
  val sendForeground = colorResource(R.color.conversation_send_button_tint)

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = HORIZONTAL_PADDING, vertical = 12.dp)
  ) {
    Text(
      text = sendingTo,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f)
    )

    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(if (canSend) sendBackground else MaterialTheme.colorScheme.surfaceVariant)
        .testTag(ShareContactTestTags.SEND_BUTTON)
        .clickable(enabled = canSend, onClick = onSendClick)
    ) {
      if (isSending) {
        CircularProgressIndicator(
          color = sendForeground,
          modifier = Modifier.size(20.dp)
        )
      } else {
        Icon(
          painter = painterResource(CoreUiR.drawable.symbol_send_fill_24),
          contentDescription = stringResource(R.string.ShareContactScreen__send),
          tint = if (canSend) sendForeground else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun PhotoPickerSheet(
  picker: ShareContactState.PhotoPicker,
  displayName: String,
  onPhotoSelected: (String) -> Unit,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  BottomSheets.BottomSheet(onDismissRequest = onDismiss) {
    Spacer(modifier = Modifier.height(22.dp))

    Text(
      text = stringResource(R.string.ShareContactScreen__select_a_photo_to_share),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth()
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val photoSize = pickerPhotoSize(available = maxWidth, count = picker.options.size)

      Row(
        horizontalArrangement = Arrangement.spacedBy(PICKER_PHOTO_SPACING, Alignment.CenterHorizontally),
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 24.dp)
      ) {
        picker.options.forEach { option ->
          Box(
            modifier = Modifier.clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = ripple(bounded = false),
              onClick = { onPhotoSelected(option.id) }
            )
          ) {
            ContactPhotoImage(
              photo = option.photo,
              displayName = displayName,
              modifier = Modifier.size(photoSize)
            )

            SelectionCheck(
              isSelected = option.id == picker.selectedId,
              modifier = Modifier.align(Alignment.BottomEnd)
            )
          }
        }
      }
    }

    Buttons.MediumTonal(
      onClick = onConfirm,
      modifier = Modifier
        .align(Alignment.End)
        .padding(horizontal = HORIZONTAL_PADDING)
        .padding(bottom = 24.dp)
    ) {
      Text(text = stringResource(R.string.ShareContactScreen__done))
    }
  }
}

/**
 * Shrinks the photos on a narrow screen so that the row still fits, never growing them past the size
 * the design calls for. The gaps hold their width, so only the circles give.
 */
private fun pickerPhotoSize(available: Dp, count: Int): Dp {
  if (count <= 0) {
    return PICKER_PHOTO_SIZE
  }

  val forPhotos = available - (HORIZONTAL_PADDING * 2) - (PICKER_PHOTO_SPACING * (count - 1))

  return minOf(PICKER_PHOTO_SIZE, forPhotos / count)
}

/** A null [photo] is the "no photo" choice, drawn the way the receiver will draw it. */
@Composable
private fun ContactPhotoImage(photo: ContactPhoto?, displayName: String, modifier: Modifier = Modifier) {
  if (photo == null) {
    FallbackAvatarImage(
      fallbackAvatar = FallbackAvatar.forTextOrDefault(displayName, AvatarColor.A100),
      modifier = modifier
    )
  } else if (LocalInspectionMode.current) {
    Image(
      painter = painterResource(R.drawable.ic_avatar_abstract_02),
      contentDescription = null,
      modifier = modifier.clip(CircleShape)
    )
  } else {
    GlideImage(
      model = DecryptableUri(photo.uri.toUri()),
      contentScale = ContentScale.Crop,
      modifier = modifier.clip(CircleShape)
    )
  }
}

@Composable
private fun DetailLabel.resolve(): String {
  return when (this) {
    DetailLabel.Nickname -> stringResource(R.string.ShareContactScreen__nickname)
    DetailLabel.Note -> stringResource(R.string.ShareContactScreen__notes)
    is DetailLabel.Text -> value
  }
}

private fun previewState(photoPicker: ShareContactState.PhotoPicker? = null): ShareContactState {
  val addressBookPhoto = ContactPhoto(uri = "", isProfile = false)

  return ShareContactState(
    sendingTo = "Maya Johnson",
    recipientId = RecipientId.from(2),
    avatar = ShareContactState.AvatarSelection(isSelected = true, photo = addressBookPhoto, isEditable = true),
    name = ShareContactState.NameSelection(displayName = "Paige Hall", isSelected = true, isEditable = true),
    details = listOf(
      DetailSelection("phone", listOf("+1 510-123-4567"), DetailLabel.Text("Phone"), isSelected = true),
      DetailSelection("nickname", listOf("Paige"), DetailLabel.Nickname, isSelected = false),
      DetailSelection("note", listOf("Met in 2017"), DetailLabel.Note, isSelected = false),
      DetailSelection("email", listOf("paigehall@example.com"), DetailLabel.Text("Home"), isSelected = false),
      DetailSelection("address", listOf("123 Beach Drive", "San Francisco CA", "United States"), DetailLabel.Text("Home"), isSelected = false)
    ),
    photoPicker = photoPicker
  )
}

@DayNightPreviews
@Composable
private fun ShareContactScreenPreview() {
  Previews.Preview {
    ShareContactScreen(state = previewState(), onEvent = {})
  }
}

@DayNightPreviews
@Composable
private fun ShareContactScreenAciContactPreview() {
  Previews.Preview {
    ShareContactScreen(
      state = previewState().copy(
        avatar = ShareContactState.AvatarSelection(
          isSelected = true,
          photo = ContactPhoto(uri = "", isProfile = true),
          isEditable = false
        ),
        name = ShareContactState.NameSelection(displayName = "Paige Hall", isSelected = true, isEditable = false),
        details = previewState().details.filterNot { it.id == "phone" }
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun ShareContactScreenLockedNamePreview() {
  Previews.Preview {
    ShareContactScreen(
      state = previewState().let {
        it.copy(name = it.name?.copy(isToggleable = false))
      },
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun ShareContactScreenPhotoPickerPreview() {
  Previews.Preview {
    ShareContactScreen(state = previewState(photoPicker = previewPicker()), onEvent = {})
  }
}

@DayNightPreviews
@Composable
private fun ShareContactScreenNoPhotoPickedPreview() {
  Previews.Preview {
    ShareContactScreen(state = previewState(photoPicker = previewPicker(selectedId = PHOTO_ID_NONE)), onEvent = {})
  }
}

/** What the row looks like once the no photo choice is committed. */
@DayNightPreviews
@Composable
private fun ShareContactScreenNoPhotoPreview() {
  Previews.Preview {
    ShareContactScreen(
      state = previewState().let { it.copy(avatar = it.avatar?.copy(photo = null)) },
      onEvent = {}
    )
  }
}

/** Narrow enough that the photos have to give up width to fit. */
@Preview(widthDp = 320)
@Composable
private fun ShareContactScreenNarrowPhotoPickerPreview() {
  Previews.Preview {
    ShareContactScreen(state = previewState(photoPicker = previewPicker()), onEvent = {})
  }
}

private fun previewPicker(selectedId: String = PHOTO_ID_ADDRESS_BOOK): ShareContactState.PhotoPicker {
  return ShareContactState.PhotoPicker(
    options = listOf(
      ShareContactState.PhotoOption(PHOTO_ID_ADDRESS_BOOK, ContactPhoto(uri = "", isProfile = false)),
      ShareContactState.PhotoOption(PHOTO_ID_SIGNAL_PROFILE, ContactPhoto(uri = "", isProfile = true)),
      ShareContactState.PhotoOption(PHOTO_ID_NONE, null)
    ),
    selectedId = selectedId
  )
}
