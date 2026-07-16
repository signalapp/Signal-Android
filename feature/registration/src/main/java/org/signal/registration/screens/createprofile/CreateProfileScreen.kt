/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.createprofile

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.registration.R
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.test.TestTags

/**
 * Profile creation screen for the registration flow. Captures the user's given name, family name,
 * avatar, and phone-number discoverability before completing registration.
 *
 * Dispatches to a per-[WindowBreakpoint] layout following the pattern in `WelcomeScreen`. All three
 * breakpoints currently share the [CompactLayout] body — Medium/Large variants can be split out
 * later without changing this entry point.
 */
@Composable
fun CreateProfileScreen(
  state: CreateProfileState,
  onEvent: (CreateProfileScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val pickAvatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) {
      val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
      }.getOrNull()
      if (bytes != null) {
        onEvent(CreateProfileScreenEvents.AvatarSelected(bytes))
      }
    }
  }
  val onAvatarClick = {
    pickAvatarLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
  }

  if (state.showUploadFailedDialog) {
    Dialogs.SimpleMessageDialog(
      message = stringResource(R.string.VerificationCodeScreen__an_unexpected_error_occurred),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onEvent(CreateProfileScreenEvents.UploadFailedDialogDismissed) }
    )
  }

  if (state.isLoading) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  when (rememberWindowBreakpoint()) {
    is WindowBreakpoint.Small -> CompactLayout(state, onEvent, onAvatarClick, modifier)
    is WindowBreakpoint.Medium -> MediumLayout(state, onEvent, onAvatarClick, modifier)
    is WindowBreakpoint.Large -> LargeLayout(state, onEvent, onAvatarClick, modifier)
  }
}

@Composable
private fun CompactLayout(
  state: CreateProfileState,
  onEvent: (CreateProfileScreenEvents) -> Unit,
  onAvatarClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  RegistrationScaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.CREATE_PROFILE_SCREEN),
    content = {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
          text = stringResource(R.string.CreateProfileScreen__set_up_your_profile),
          style = MaterialTheme.typography.headlineMedium,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = stringResource(R.string.CreateProfileScreen__your_profile_is_end_to_end_encrypted),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Avatar(avatarBytes = state.avatar, onClick = onAvatarClick)

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
          value = state.givenName,
          onValueChange = { onEvent(CreateProfileScreenEvents.GivenNameChanged(it)) },
          label = { Text(stringResource(R.string.CreateProfileScreen__first_name_required)) },
          singleLine = true,
          enabled = !state.isSubmitting,
          keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.CREATE_PROFILE_GIVEN_NAME_FIELD)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
          value = state.familyName,
          onValueChange = { onEvent(CreateProfileScreenEvents.FamilyNameChanged(it)) },
          label = { Text(stringResource(R.string.CreateProfileScreen__last_name_optional)) },
          singleLine = true,
          enabled = !state.isSubmitting,
          keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.CREATE_PROFILE_FAMILY_NAME_FIELD)
        )

        Spacer(modifier = Modifier.height(16.dp))

        WhoCanFindMeRow(
          discoverable = state.discoverableByPhoneNumber,
          enabled = !state.isSubmitting,
          onClick = { onEvent(CreateProfileScreenEvents.WhoCanFindMeClicked) }
        )
      }
    },
    footer = {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
      ) {
        Buttons.LargeTonal(
          onClick = { onEvent(CreateProfileScreenEvents.NextClicked) },
          enabled = state.isFormValid && !state.isSubmitting,
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 320.dp)
            .testTag(TestTags.CREATE_PROFILE_NEXT_BUTTON)
        ) {
          if (state.isSubmitting) {
            CircularProgressIndicator(
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              strokeWidth = 2.dp,
              modifier = Modifier.size(20.dp)
            )
          } else {
            Text(stringResource(R.string.CreateProfileScreen__next))
          }
        }
      }
    }
  )
}

@Composable
private fun MediumLayout(
  state: CreateProfileState,
  onEvent: (CreateProfileScreenEvents) -> Unit,
  onAvatarClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  // TODO [registration] dedicated medium-width layout. For now, reuse the compact body.
  CompactLayout(state = state, onEvent = onEvent, onAvatarClick = onAvatarClick, modifier = modifier)
}

@Composable
private fun LargeLayout(
  state: CreateProfileState,
  onEvent: (CreateProfileScreenEvents) -> Unit,
  onAvatarClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  // TODO [registration] dedicated large-width layout. For now, reuse the compact body.
  CompactLayout(state = state, onEvent = onEvent, onAvatarClick = onAvatarClick, modifier = modifier)
}

@Composable
private fun WhoCanFindMeRow(
  discoverable: Boolean,
  enabled: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = enabled, onClick = onClick)
      .padding(vertical = 12.dp)
      .testTag(TestTags.CREATE_PROFILE_WHO_CAN_FIND_ME_ROW),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      painter = if (discoverable) painterResource(R.drawable.symbol_group_24) else SignalIcons.Lock.painter,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(24.dp)
    )
    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.WhoCanSeeMyPhoneNumberFragment__who_can_find_me_by_number),
        style = MaterialTheme.typography.bodyLarge
      )
      Text(
        text = stringResource(
          if (discoverable) R.string.PhoneNumberPrivacy_everyone else R.string.PhoneNumberPrivacy_nobody
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Icon(
      painter = SignalIcons.ChevronRight.painter,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun Avatar(
  avatarBytes: ByteArray?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val bitmap = remember(avatarBytes) {
    avatarBytes?.let {
      runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
    }
  }

  Box(modifier = modifier.size(112.dp)) {
    Box(
      modifier = Modifier
        .size(112.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable(onClick = onClick),
      contentAlignment = Alignment.Center
    ) {
      if (bitmap != null) {
        androidx.compose.foundation.Image(
          bitmap = bitmap.asImageBitmap(),
          contentDescription = stringResource(R.string.CreateProfileScreen__set_avatar_description),
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        Icon(
          painter = SignalIcons.Camera.painter,
          contentDescription = stringResource(R.string.CreateProfileScreen__set_avatar_description),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(40.dp)
        )
      }
    }

    Box(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .offset(x = 4.dp, y = 4.dp)
        .size(36.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primary)
        .clickable(onClick = onClick),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        painter = SignalIcons.Camera.painter,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(20.dp)
      )
    }
  }
}

@AllDevicePreviews
@Composable
private fun CreateProfileScreenLoadingPreview() {
  Previews.Preview {
    CreateProfileScreen(
      state = CreateProfileState(isLoading = true),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun CreateProfileScreenEmptyPreview() {
  Previews.Preview {
    CreateProfileScreen(
      state = CreateProfileState(isLoading = false),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun CreateProfileScreenWithNamePreview() {
  Previews.Preview {
    CreateProfileScreen(
      state = CreateProfileState(
        givenName = "Alice",
        familyName = "Anderson",
        isLoading = false
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun CreateProfileScreenNobodyPreview() {
  Previews.Preview {
    CreateProfileScreen(
      state = CreateProfileState(
        givenName = "Alice",
        familyName = "Anderson",
        discoverableByPhoneNumber = false,
        isLoading = false
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun CreateProfileScreenSubmittingPreview() {
  Previews.Preview {
    CreateProfileScreen(
      state = CreateProfileState(
        givenName = "Alice",
        familyName = "Anderson",
        isLoading = false,
        isSubmitting = true
      ),
      onEvent = {}
    )
  }
}
