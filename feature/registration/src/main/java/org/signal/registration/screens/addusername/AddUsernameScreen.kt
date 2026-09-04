/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.addusername

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.util.UsernameUtil
import org.signal.libsignal.usernames.Username
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.test.TestTags

/** Size of the avatar artwork, whose sphere fills its box edge to edge. */
private val AVATAR_SIZE = 72.dp

/** The discriminator field is sized to its content, but never narrower than this many digits. */
private const val DISCRIMINATOR_MIN_WIDTH_TEMPLATE = "00"

/** Extra room on the discriminator field so the caret isn't clipped at the end of the text. */
private val DISCRIMINATOR_CARET_ALLOWANCE = 2.dp

/**
 * Offers the user an optional username so people can reach them without a phone number.
 */
@Composable
fun AddUsernameScreen(
  state: AddUsernameState,
  onEvent: (AddUsernameScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val simpleError: Pair<String, AddUsernameScreenEvents>? = when {
    state.dialogs.networkError -> stringResource(R.string.VerificationCodeScreen__network_error) to AddUsernameScreenEvents.NetworkErrorDialogDismissed
    state.dialogs.usernameUnavailable -> stringResource(R.string.AddUsernameScreen__this_username_is_not_available) to AddUsernameScreenEvents.UsernameUnavailableDialogDismissed
    state.dialogs.reservationLapsed -> stringResource(R.string.AddUsernameScreen__your_username_reservation_expired) to AddUsernameScreenEvents.ReservationLapsedDialogDismissed
    state.dialogs.rateLimited -> stringResource(R.string.VerificationCodeScreen__too_many_attempts) to AddUsernameScreenEvents.RateLimitedDialogDismissed
    state.dialogs.unknownError -> stringResource(R.string.VerificationCodeScreen__an_unexpected_error_occurred) to AddUsernameScreenEvents.UnknownErrorDialogDismissed
    else -> null
  }

  simpleError?.let { (message, dismissedEvent) ->
    Dialogs.SimpleMessageDialog(
      message = message,
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onEvent(dismissedEvent) }
    )
  }

  if (state.dialogs.confirmSkip) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.AddUsernameScreen__are_you_sure),
      body = stringResource(R.string.AddUsernameScreen__without_a_username_people_wont_be_able_to_find_you),
      confirm = stringResource(R.string.AddUsernameScreen__continue),
      dismiss = stringResource(R.string.AddUsernameScreen__cancel),
      onConfirm = { onEvent(AddUsernameScreenEvents.SkipConfirmed) },
      onDismiss = { onEvent(AddUsernameScreenEvents.SkipDialogDismissed) }
    )
  }

  if (state.dialogs.learnMore) {
    Dialogs.SimpleMessageDialog(
      title = stringResource(R.string.AddUsernameScreen__what_is_this_number),
      message = stringResource(R.string.AddUsernameScreen__these_digits_help_keep),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onEvent(AddUsernameScreenEvents.LearnMoreDialogDismissed) }
    )
  }

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.ADD_USERNAME_SCREEN)
  ) {
    when (val params = RegistrationScaffold.rememberLayoutParams()) {
      is RegistrationScaffold.Params.OnePane -> OnePaneLayout(params, state, onEvent)
      is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(params, state, onEvent)
    }
  }
}

@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  state: AddUsernameState,
  onEvent: (AddUsernameScreenEvents) -> Unit
) {
  val scrollState = rememberScrollState()

  OnePaneRegistrationScaffold(
    params = params,
    content = { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(paddingValues)
      ) {
        Description()

        Spacer(modifier = Modifier.height(32.dp))

        UsernameEntry(state = state, onEvent = onEvent)
      }
    },
    footer = { Footer(params, state, scrollState.canScrollForward, onEvent) }
  )
}

@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: AddUsernameState,
  onEvent: (AddUsernameScreenEvents) -> Unit
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()

  TwoPaneRegistrationScaffold(
    params = params,
    firstPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues)
      ) {
        Description(twoPane = true)
      }
    },
    secondPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues),
        verticalArrangement = Arrangement.Center
      ) {
        UsernameEntry(state = state, onEvent = onEvent)
      }
    },
    footer = { Footer(params, state, firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward, onEvent) }
  )
}

@Composable
private fun Description(twoPane: Boolean = false) {
  Text(
    text = stringResource(R.string.AddUsernameScreen__add_an_optional_username),
    style = if (twoPane) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )

  Text(
    text = stringResource(R.string.AddUsernameScreen__people_can_message_you_by_username),
    style = if (twoPane) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 16.dp)
  )
}

@Composable
private fun ColumnScope.UsernameEntry(
  state: AddUsernameState,
  onEvent: (AddUsernameScreenEvents) -> Unit
) {
  val focusRequester = remember { FocusRequester() }
  val validationMessage: String? = state.validationError?.message()

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  UsernameAvatar(modifier = Modifier.align(Alignment.CenterHorizontally))

  Text(
    text = state.reservation?.username ?: stringResource(R.string.AddUsernameScreen__choose_your_username),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .padding(top = 12.dp)
      .fillMaxWidth()
  )

  Spacer(modifier = Modifier.height(20.dp))

  TextField(
    value = state.username,
    onValueChange = { onEvent(AddUsernameScreenEvents.UsernameChanged(it)) },
    label = { Text(stringResource(R.string.AddUsernameScreen__username)) },
    singleLine = true,
    enabled = !state.showSpinner,
    isError = state.validationError != null,
    supportingText = validationMessage?.let { message -> { Text(message) } },
    suffix = discriminatorSuffix(state, onEvent),
    keyboardOptions = KeyboardOptions(
      capitalization = KeyboardCapitalization.None,
      autoCorrectEnabled = false,
      imeAction = ImeAction.Done
    ),
    keyboardActions = KeyboardActions(
      onDone = {
        if (state.isSubmittable) {
          onEvent(AddUsernameScreenEvents.NextClicked)
        }
      }
    ),
    colors = TextFieldDefaults.colors(
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      errorContainerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    modifier = Modifier
      .fillMaxWidth()
      .focusRequester(focusRequester)
      .testTag(TestTags.ADD_USERNAME_FIELD)
  )

  Text(
    text = buildAnnotatedString {
      append(stringResource(R.string.AddUsernameScreen__usernames_are_always_paired_with_a_set_of_numbers))
      append(' ')

      withLink(
        LinkAnnotation.Clickable(
          tag = "learn-more",
          styles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary)),
          linkInteractionListener = { onEvent(AddUsernameScreenEvents.LearnMoreClicked) }
        )
      ) {
        append(stringResource(R.string.AddUsernameScreen__learn_more))
      }
    },
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.secondary,
    modifier = Modifier
      .padding(top = 8.dp)
      .testTag(TestTags.ADD_USERNAME_LEARN_MORE_LINK)
  )
}

/**
 * The trailing content of the username field: a spinner while a username is being reserved, and, behind a divider, the
 * discriminator. The discriminator is assigned by the service, but the user can overwrite it to claim a specific one.
 */
private fun discriminatorSuffix(state: AddUsernameState, onEvent: (AddUsernameScreenEvents) -> Unit): (@Composable () -> Unit)? {
  if (!state.isReserving && !state.showDiscriminator) {
    return null
  }

  return {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (state.isReserving) {
        CircularProgressIndicator(
          strokeWidth = 2.dp,
          modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))
      }

      if (state.showDiscriminator) {
        Dividers.Vertical(
          thickness = 1.dp,
          color = MaterialTheme.colorScheme.outline,
          modifier = Modifier.height(20.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        DiscriminatorField(state = state, onEvent = onEvent)
      }
    }
  }
}

/** The editable discriminator, sized to its content so it hugs the right edge of the username field. */
@Composable
private fun DiscriminatorField(
  state: AddUsernameState,
  onEvent: (AddUsernameScreenEvents) -> Unit
) {
  val textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface)
  val textMeasurer = rememberTextMeasurer()

  val width = with(LocalDensity.current) {
    val content = textMeasurer.measure(state.discriminator, textStyle).size.width
    val minimum = textMeasurer.measure(DISCRIMINATOR_MIN_WIDTH_TEMPLATE, textStyle).size.width
    maxOf(content, minimum).toDp() + DISCRIMINATOR_CARET_ALLOWANCE
  }

  BasicTextField(
    value = state.discriminator,
    onValueChange = { onEvent(AddUsernameScreenEvents.DiscriminatorChanged(it)) },
    textStyle = textStyle,
    singleLine = true,
    enabled = !state.showSpinner,
    cursorBrush = SolidColor(if (state.validationError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.Number,
      imeAction = ImeAction.Done
    ),
    keyboardActions = KeyboardActions(
      onDone = {
        if (state.isSubmittable) {
          onEvent(AddUsernameScreenEvents.NextClicked)
        }
      }
    ),
    modifier = Modifier
      .width(width)
      .testTag(TestTags.ADD_USERNAME_DISCRIMINATOR_FIELD)
  )
}

/**
 * The designed avatar sphere with the "@" glyph baked in.
 */
@Composable
private fun UsernameAvatar(modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(R.drawable.image_registration_usernames),
    contentDescription = null,
    modifier = modifier.size(AVATAR_SIZE)
  )
}

@Composable
private fun Footer(
  params: RegistrationScaffold.Params,
  state: AddUsernameState,
  isElevated: Boolean,
  onEvent: (AddUsernameScreenEvents) -> Unit
) {
  RegistrationScaffold.FooterSurface(isElevated = isElevated) {
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(params.footerPadding)
    ) {
      TextButton(
        onClick = { onEvent(AddUsernameScreenEvents.SkipClicked) },
        enabled = !state.showSpinner,
        modifier = Modifier.testTag(TestTags.ADD_USERNAME_SKIP_BUTTON)
      ) {
        Text(stringResource(R.string.AddUsernameScreen__skip))
      }

      Buttons.LargeTonal(
        onClick = { onEvent(AddUsernameScreenEvents.NextClicked) },
        enabled = state.isSubmittable,
        modifier = Modifier.testTag(TestTags.ADD_USERNAME_NEXT_BUTTON)
      ) {
        if (state.showSpinner) {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp)
          )
        } else {
          Text(stringResource(R.string.AddUsernameScreen__next))
        }
      }
    }
  }
}

@Composable
private fun AddUsernameState.ValidationError.message(): String = when (this) {
  AddUsernameState.ValidationError.TOO_SHORT -> stringResource(R.string.AddUsernameScreen__usernames_must_be_at_least_3_characters)
  AddUsernameState.ValidationError.TOO_LONG -> stringResource(R.string.AddUsernameScreen__usernames_must_be_at_most_32_characters)
  AddUsernameState.ValidationError.INVALID_CHARACTERS -> stringResource(R.string.AddUsernameScreen__usernames_can_only_contain)
  AddUsernameState.ValidationError.CANNOT_START_WITH_DIGIT -> stringResource(R.string.AddUsernameScreen__usernames_cannot_begin_with_a_number)
  AddUsernameState.ValidationError.NOT_AVAILABLE -> stringResource(R.string.AddUsernameScreen__this_username_is_not_available)
  AddUsernameState.ValidationError.DISCRIMINATOR_TOO_SHORT -> stringResource(R.string.AddUsernameScreen__enter_a_minimum_of_d_digits, UsernameUtil.MIN_DISCRIMINATOR_LENGTH)
  AddUsernameState.ValidationError.DISCRIMINATOR_TOO_LONG -> stringResource(R.string.AddUsernameScreen__enter_a_maximum_of_d_digits, UsernameUtil.MAX_DISCRIMINATOR_LENGTH)
  AddUsernameState.ValidationError.DISCRIMINATOR_INVALID_CHARACTERS -> stringResource(R.string.AddUsernameScreen__numbers_can_only_contain_digits)
  AddUsernameState.ValidationError.DISCRIMINATOR_CANNOT_BE_00 -> stringResource(R.string.AddUsernameScreen__this_number_cant_be_00)
  AddUsernameState.ValidationError.DISCRIMINATOR_CANNOT_START_WITH_ZERO -> stringResource(R.string.AddUsernameScreen__this_number_cant_start_with_0)
  AddUsernameState.ValidationError.DISCRIMINATOR_NOT_AVAILABLE -> stringResource(R.string.AddUsernameScreen__this_username_is_not_available_try_another_number)
}

@AllDevicePreviews
@Composable
private fun AddUsernameScreenPreview() {
  Previews.Preview {
    AddUsernameScreen(
      state = AddUsernameState(),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun AddUsernameScreenFilledPreview() {
  Previews.Preview {
    AddUsernameScreen(
      state = AddUsernameState(username = "alice", isReserving = true),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun AddUsernameScreenReservedPreview() {
  Previews.Preview {
    AddUsernameScreen(
      state = AddUsernameState(
        username = "alice",
        discriminator = "45",
        showDiscriminator = true,
        reservation = Username("alice.45")
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun AddUsernameScreenConfirmSkipPreview() {
  Previews.Preview {
    AddUsernameScreen(
      state = AddUsernameState(dialogs = AddUsernameState.Dialogs(confirmSkip = true)),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun AddUsernameScreenErrorPreview() {
  Previews.Preview {
    AddUsernameScreen(
      state = AddUsernameState(
        username = "al",
        validationError = AddUsernameState.ValidationError.TOO_SHORT
      ),
      onEvent = {}
    )
  }
}
