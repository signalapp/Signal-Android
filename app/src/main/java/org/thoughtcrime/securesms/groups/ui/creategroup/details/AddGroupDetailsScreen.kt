/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup.details

import android.graphics.BitmapFactory
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.CircularProgressWrapper
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.TextFields
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.util.ExpirationUtil
import org.signal.emoji.EmojiText
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Screen for user to add details to a new group
 */
@Composable
fun AddGroupDetailsScreen(
  state: AddGroupDetailsState,
  onEvent: (AddGroupDetailsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    color = MaterialTheme.colorScheme.background,
    modifier = modifier
  ) {
    Scaffolds.TitlePane(
      title = stringResource(R.string.AddGroupDetailsFragment__name_this_group),
      onNavigateUpClick = { onEvent(AddGroupDetailsEvent.NavigateBackClicked) },
      topAppBarActions = {},
      snackbarHostState = remember { SnackbarHostState() },
      primaryContent = {
        AddGroupDetailsContent(state = state, onEvent = onEvent)
      },
      floatingActionButton = {
        CreateButton(state = state, onEvent = onEvent)
      }
    )
  }

  when (val dialog = state.dialog) {
    AddGroupDetailsState.Dialog.None -> Unit
    is AddGroupDetailsState.Dialog.ConfirmRemoveMember -> RemoveMemberDialog(dialog = dialog, onEvent = onEvent)
    is AddGroupDetailsState.Dialog.ConfirmDiscard -> DiscardDialog(dialog = dialog, onEvent = onEvent)
  }
}

@Composable
private fun AddGroupDetailsContent(
  state: AddGroupDetailsState,
  onEvent: (AddGroupDetailsEvent) -> Unit
) {
  val focusRequester = remember { FocusRequester() }
  val isInspecting = LocalInspectionMode.current

  LaunchedEffect(Unit) {
    if (!isInspecting) {
      focusRequester.requestFocus()
    }
  }

  val header: @Composable () -> Unit = {
    Column {
      NameAndAvatar(state = state, focusRequester = focusRequester, onEvent = onEvent)
      DisappearingMessagesRow(timer = state.disappearingMessagesTimer, onEvent = onEvent)
      Dividers.Default()

      if (state.sameGroups.isNotEmpty()) {
        SameGroups(groups = state.sameGroups, onEvent = onEvent)
      }

      Texts.SectionHeader(
        text = stringResource(R.string.AddGroupDetailsFragment__members)
      )
    }
  }

  MemberList(state = state, onEvent = onEvent, header = header, modifier = Modifier.fillMaxSize())
}

@Composable
private fun MemberList(
  state: AddGroupDetailsState,
  onEvent: (AddGroupDetailsEvent) -> Unit,
  header: @Composable () -> Unit,
  modifier: Modifier = Modifier
) {
  LazyColumn(
    contentPadding = PaddingValues(bottom = 96.dp),
    modifier = modifier
      .fillMaxWidth()
      .consumeWindowInsets(WindowInsets.systemBars)
      .imePadding()
      .testTag(AddGroupDetailsTestTags.SCROLLER)
  ) {
    item(key = "header") {
      header()
    }

    if (state.members.isEmpty()) {
      item(key = "add-later") {
        Text(
          text = stringResource(R.string.AddGroupDetailsFragment__you_can_add_or_invite_friends_after_creating_this_group),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
            .padding(horizontal = 24.dp)
            .testTag(AddGroupDetailsTestTags.ADD_LATER)
        )
      }
    }

    items(state.members, key = { it.id.toLong() }) { member ->
      MemberRow(
        member = member,
        onClick = { onEvent(AddGroupDetailsEvent.MemberClicked(member.id)) },
        modifier = Modifier.testTag(AddGroupDetailsTestTags.MEMBER_ROW + member.id.toLong())
      )
    }
  }
}

@Composable
private fun NameAndAvatar(
  state: AddGroupDetailsState,
  focusRequester: FocusRequester,
  onEvent: (AddGroupDetailsEvent) -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 16.dp)
  ) {
    GroupAvatar(
      avatar = state.avatar,
      onClick = { onEvent(AddGroupDetailsEvent.AvatarClicked) }
    )

    Spacer(modifier = Modifier.width(16.dp))

    TextFields.TextField(
      value = state.name,
      onValueChange = { onEvent(AddGroupDetailsEvent.NameChanged(it)) },
      placeholder = {
        Text(text = stringResource(R.string.AddGroupDetailsFragment__group_name_required))
      },
      singleLine = true,
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
      modifier = Modifier
        .weight(1f)
        .focusRequester(focusRequester)
        .testTag(AddGroupDetailsTestTags.NAME_FIELD)
    )
  }
}

@Composable
private fun GroupAvatar(
  avatar: ByteArray?,
  onClick: () -> Unit
) {
  val bitmap = produceState<ImageBitmap?>(initialValue = null, avatar) {
    value = avatar?.let { withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
  }.value

  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .size(64.dp)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .clickable(onClick = onClick)
      .testTag(AddGroupDetailsTestTags.AVATAR)
  ) {
    if (bitmap != null) {
      Image(
        bitmap = bitmap,
        contentDescription = stringResource(R.string.AddAProfilePhotoMegaphone__add_photo),
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    } else {
      Icon(
        imageVector = ImageVector.vectorResource(R.drawable.ic_camera_outline_24),
        contentDescription = stringResource(R.string.AddAProfilePhotoMegaphone__add_photo),
        tint = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}

@Composable
private fun DisappearingMessagesRow(
  timer: Int,
  onEvent: (AddGroupDetailsEvent) -> Unit
) {
  val context = LocalContext.current

  Rows.TextRow(
    text = stringResource(R.string.PrivacySettingsFragment__disappearing_messages),
    label = ExpirationUtil.getExpirationDisplayValue(context, timer),
    icon = painterResource(R.drawable.symbol_timer_24),
    onClick = { onEvent(AddGroupDetailsEvent.DisappearingMessagesClicked) },
    modifier = Modifier.testTag(AddGroupDetailsTestTags.DISAPPEARING_MESSAGES_ROW)
  )
}

@Composable
private fun MemberRow(
  member: Recipient,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val about = member.combinedAboutAndEmoji

  Rows.TextRow(
    text = {
      Column(modifier = Modifier.weight(1f)) {
        EmojiText(
          text = member.getDisplayName(context),
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 1
        )

        if (!about.isNullOrBlank()) {
          EmojiText(
            text = about,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
          )
        }
      }
    },
    icon = {
      AvatarImage(
        recipient = member,
        modifier = Modifier.size(40.dp)
      )
    },
    onClick = onClick,
    modifier = modifier
  )
}

@Composable
private fun SameGroups(
  groups: List<Recipient>,
  onEvent: (AddGroupDetailsEvent) -> Unit
) {
  val context = LocalContext.current

  Texts.SectionHeader(
    text = pluralStringResource(R.plurals.AddGroupDetailsFragment__d_groups_with_same_members, groups.size, groups.size)
  )

  LazyRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 8.dp)
  ) {
    items(groups, key = { it.id.toLong() }) { group ->
      AssistChip(
        onClick = { onEvent(AddGroupDetailsEvent.SameGroupClicked(group.id)) },
        label = { Text(text = group.getDisplayName(context), maxLines = 1) },
        leadingIcon = {
          AvatarImage(
            recipient = group,
            modifier = Modifier.size(24.dp)
          )
        },
        trailingIcon = {
          Icon(
            imageVector = ImageVector.vectorResource(R.drawable.symbol_chevron_right_compact_bold_16),
            contentDescription = null,
            modifier = Modifier.size(14.dp)
          )
        },
        modifier = Modifier.testTag(AddGroupDetailsTestTags.SAME_GROUP_CHIP + group.id.toLong())
      )
    }
  }
}

@Composable
private fun CreateButton(
  state: AddGroupDetailsState,
  onEvent: (AddGroupDetailsEvent) -> Unit
) {
  CircularProgressWrapper(isLoading = state.isCreating) {
    Buttons.LargeTonal(
      onClick = { onEvent(AddGroupDetailsEvent.CreateClicked) },
      enabled = state.canCreate,
      modifier = Modifier.testTag(AddGroupDetailsTestTags.CREATE_BUTTON)
    ) {
      Text(text = stringResource(R.string.AddGroupDetailsFragment__create))
    }
  }
}

@Composable
private fun RemoveMemberDialog(
  dialog: AddGroupDetailsState.Dialog.ConfirmRemoveMember,
  onEvent: (AddGroupDetailsEvent) -> Unit
) {
  val context = LocalContext.current

  Dialogs.SimpleAlertDialog(
    title = "",
    body = stringResource(R.string.AddGroupDetailsFragment__remove_s_from_this_group, dialog.member.getDisplayName(context)),
    confirm = stringResource(R.string.AddGroupDetailsFragment__remove),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = { onEvent(AddGroupDetailsEvent.RemoveMemberConfirmed) },
    onDeny = { onEvent(AddGroupDetailsEvent.DialogDismissed) },
    onDismissRequest = { onEvent(AddGroupDetailsEvent.DialogDismissed) },
    modifier = Modifier.testTag(AddGroupDetailsTestTags.DIALOG_REMOVE_MEMBER)
  )
}

@Composable
private fun DiscardDialog(
  dialog: AddGroupDetailsState.Dialog.ConfirmDiscard,
  onEvent: (AddGroupDetailsEvent) -> Unit
) {
  val context = LocalContext.current

  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.AddGroupDetailsFragment__discard_group),
    body = stringResource(R.string.AddGroupDetailsFragment__if_you_continue_to_the_group_s_your_changes_wont_be_saved, dialog.group.getDisplayName(context)),
    confirm = stringResource(R.string.AddGroupDetailsFragment__discard),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = { onEvent(AddGroupDetailsEvent.DiscardConfirmed) },
    onDeny = { onEvent(AddGroupDetailsEvent.DialogDismissed) },
    onDismissRequest = { onEvent(AddGroupDetailsEvent.DialogDismissed) },
    modifier = Modifier.testTag(AddGroupDetailsTestTags.DIALOG_DISCARD)
  )
}

@VisibleForTesting
internal object AddGroupDetailsPreviewData {
  val members = listOf(
    previewRecipient(1, "Alex", "Rodriguez-Montgomery"),
    previewRecipient(2, "Bao", "Nguyen"),
    previewRecipient(3, "Christopher Alexander", "Featherstonehaugh-Worthington")
  )

  val sameGroups = listOf(
    Recipient(id = RecipientId.from(10), isResolving = false, groupName = "Weekend climbing crew and friends of friends"),
    Recipient(id = RecipientId.from(11), isResolving = false, groupName = "Book club")
  )

  fun previewRecipient(id: Long, given: String, family: String): Recipient {
    return Recipient(
      id = RecipientId.from(id),
      isResolving = false,
      registeredValue = RecipientTable.RegisteredState.REGISTERED,
      profileName = ProfileName.fromParts(given, family)
    )
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@AllDevicePreviews
@Composable
private fun AddGroupDetailsScreenEmptyPreview() {
  AddGroupDetailsScreen(state = AddGroupDetailsState(), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AddGroupDetailsScreenPopulatedPreview() {
  AddGroupDetailsScreen(
    state = AddGroupDetailsState(
      members = AddGroupDetailsPreviewData.members,
      name = "Saturday hiking",
      disappearingMessagesTimer = 604800,
      sameGroups = AddGroupDetailsPreviewData.sameGroups
    ),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AddGroupDetailsScreenCreatingPreview() {
  AddGroupDetailsScreen(
    state = AddGroupDetailsState(members = AddGroupDetailsPreviewData.members, name = "Saturday hiking", isCreating = true),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AddGroupDetailsScreenRemoveMemberPreview() {
  AddGroupDetailsScreen(
    state = AddGroupDetailsState(
      members = AddGroupDetailsPreviewData.members,
      dialog = AddGroupDetailsState.Dialog.ConfirmRemoveMember(AddGroupDetailsPreviewData.members.first())
    ),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AddGroupDetailsScreenDiscardPreview() {
  AddGroupDetailsScreen(
    state = AddGroupDetailsState(
      members = AddGroupDetailsPreviewData.members,
      sameGroups = AddGroupDetailsPreviewData.sameGroups,
      dialog = AddGroupDetailsState.Dialog.ConfirmDiscard(AddGroupDetailsPreviewData.sameGroups.first())
    ),
    onEvent = {}
  )
}
