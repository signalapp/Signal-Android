/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pincreation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.IconButtons.IconButton
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.registration.R
import org.signal.registration.screens.PinVisualTransformation
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.test.TestTags
import org.signal.core.ui.R as CoreR

private const val STEP_TRANSITION_DURATION = 250

/**
 * PIN creation screen for the registration flow.
 * Allows users to create a new PIN for their account, then confirm it.
 */
@Composable
fun PinCreationScreen(
  state: PinCreationState,
  onEvent: (PinCreationScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val activePin = remember { mutableStateOf("") }
  val canSubmitPin = activePin.value.length >= 4

  BackHandler(enabled = state.isConfirmEnabled) {
    onEvent(PinCreationScreenEvents.BackToPinEntry)
  }

  val errorDialog: Pair<String, PinCreationScreenEvents>? = when {
    state.dialogs.serviceError -> stringResource(R.string.PinCreationScreen__service_error) to PinCreationScreenEvents.ServiceErrorDialogDismissed
    state.dialogs.networkError != null -> {
      val retryAfter = state.dialogs.networkError.retryAfter
      val message = if (retryAfter != null) {
        stringResource(R.string.PinCreationScreen__network_error_try_again_in_s, retryAfter.toString())
      } else {
        stringResource(R.string.PinCreationScreen__network_error)
      }
      message to PinCreationScreenEvents.NetworkErrorDialogDismissed
    }
    else -> null
  }

  errorDialog?.let { (message, dismissedEvent) ->
    Dialogs.SimpleMessageDialog(
      message = message,
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onEvent(dismissedEvent) }
    )
  }

  when (val params = RegistrationScaffold.rememberLayoutParams()) {
    is RegistrationScaffold.Params.OnePane -> OnePaneLayout(
      params = params,
      state = state,
      activePin = activePin,
      canSubmitPin = canSubmitPin,
      onEvent = onEvent,
      modifier = modifier
    )

    is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(
      params = params,
      state = state,
      activePin = activePin,
      canSubmitPin = canSubmitPin,
      onEvent = onEvent,
      modifier = modifier
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  state: PinCreationState,
  activePin: MutableState<String>,
  canSubmitPin: Boolean,
  onEvent: (PinCreationScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()
  val topBarScrollBehavior = RegistrationScaffold.rememberTopBarScrollBehavior()

  RegistrationScaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.PIN_CREATION_SCREEN),
    topBar = {
      PinCreationTopBar(
        scrollBehavior = topBarScrollBehavior,
        onEvent = onEvent
      )
    },
    content = {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(scrollState)
          .padding(params.panePadding(hasHeader = true))
      ) {
        PinStepTransition(isConfirmEnabled = state.isConfirmEnabled) { isConfirm ->
          Column {
            PinDescription(
              isConfirmEnabled = isConfirm,
              onLearnMore = { onEvent(PinCreationScreenEvents.LearnMore) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            PinInputSection(
              state = state,
              isConfirm = isConfirm,
              activePin = activePin,
              onEvent = onEvent
            )
          }
        }
      }
    },
    footer = {
      NextButton(
        params = params,
        canSubmitPin = canSubmitPin,
        isElevated = scrollState.canScrollForward,
        loading = state.loading,
        onNext = { onEvent(PinCreationScreenEvents.PinSubmitted(activePin.value)) }
      )
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: PinCreationState,
  activePin: MutableState<String>,
  canSubmitPin: Boolean,
  onEvent: (PinCreationScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()
  val topBarScrollBehavior = RegistrationScaffold.rememberTopBarScrollBehavior()

  TwoPaneRegistrationScaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.PIN_CREATION_SCREEN),
    params = params,
    topBar = {
      PinCreationTopBar(
        scrollBehavior = topBarScrollBehavior,
        onEvent = onEvent
      )
    },
    firstPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues)
      ) {
        PinStepTransition(isConfirmEnabled = state.isConfirmEnabled) { isConfirm ->
          PinDescription(
            isConfirmEnabled = isConfirm,
            onLearnMore = { onEvent(PinCreationScreenEvents.LearnMore) }
          )
        }
      }
    },
    secondPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues)
      ) {
        PinStepTransition(isConfirmEnabled = state.isConfirmEnabled) { isConfirm ->
          PinInputSection(
            state = state,
            isConfirm = isConfirm,
            activePin = activePin,
            onEvent = onEvent
          )
        }
      }
    },
    footer = {
      NextButton(
        params = params,
        canSubmitPin = canSubmitPin,
        isElevated = firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward,
        loading = state.loading,
        onNext = { onEvent(PinCreationScreenEvents.PinSubmitted(activePin.value)) }
      )
    }
  )
}

/**
 * Animates between the create and confirm steps with a horizontal slide, as if they were separate screens.
 * Moving forward to the confirm step slides in from the right; returning to the create step slides in from the left.
 */
@Composable
private fun PinStepTransition(
  isConfirmEnabled: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable (isConfirm: Boolean) -> Unit
) {
  AnimatedContent(
    targetState = isConfirmEnabled,
    transitionSpec = {
      if (targetState) {
        (slideInHorizontally(animationSpec = tween(STEP_TRANSITION_DURATION)) { it } + fadeIn(tween(STEP_TRANSITION_DURATION))) togetherWith
          (slideOutHorizontally(animationSpec = tween(STEP_TRANSITION_DURATION)) { -it } + fadeOut(tween(STEP_TRANSITION_DURATION)))
      } else {
        (slideInHorizontally(animationSpec = tween(STEP_TRANSITION_DURATION)) { -it } + fadeIn(tween(STEP_TRANSITION_DURATION))) togetherWith
          (slideOutHorizontally(animationSpec = tween(STEP_TRANSITION_DURATION)) { it } + fadeOut(tween(STEP_TRANSITION_DURATION)))
      }
    },
    label = "PinCreationStep",
    modifier = modifier
  ) { isConfirm ->
    content(isConfirm)
  }
}

@Composable
private fun PinDescription(
  isConfirmEnabled: Boolean,
  onLearnMore: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    Text(
      text = when {
        isConfirmEnabled -> stringResource(R.string.PinCreationScreen__confirm_your_pin)
        else -> stringResource(R.string.PinCreationScreen__create_your_pin)
      },
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier
        .fillMaxWidth()
        .attachDebugLogHelper()
    )

    if (isConfirmEnabled) {
      Text(
        text = stringResource(R.string.PinCreationScreen__reenter_pin_description),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp)
      )
    } else {
      val descriptionText = buildAnnotatedString {
        append(stringResource(R.string.PinCreationScreen__pins_can_help))
        append(" ")
        pushStringAnnotation(tag = "LEARN_MORE", annotation = "learn_more")
        withStyle(
          style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline
          )
        ) {
          append(stringResource(R.string.PinCreationScreen__learn_more))
        }
        pop()
      }

      ClickableText(
        text = descriptionText,
        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 16.dp),
        onClick = { offset ->
          descriptionText.getStringAnnotations(tag = "LEARN_MORE", start = offset, end = offset)
            .firstOrNull()
            ?.let { onLearnMore() }
        }
      )
    }
  }
}

@Composable
private fun PinInputSection(
  state: PinCreationState,
  isConfirm: Boolean,
  activePin: MutableState<String>,
  onEvent: (PinCreationScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  var pin by remember { mutableStateOf("") }
  val canSubmitPin = pin.length >= 4
  val focusRequester = remember { FocusRequester() }

  // Keep the footer's submit action in sync with the step currently on screen.
  if (isConfirm == state.isConfirmEnabled) {
    SideEffect { activePin.value = pin }
  }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  Column(modifier = modifier) {
    PinInputField(
      isAlphanumericKeyboard = state.isAlphanumericKeyboard,
      pin = pin,
      canSubmitPin = canSubmitPin,
      focusRequester = focusRequester,
      onPinChanged = { pin = it },
      onSubmit = { onEvent(PinCreationScreenEvents.PinSubmitted(pin)) },
      testTag = if (isConfirm) TestTags.PIN_CREATION_CONFIRM_INPUT else TestTags.PIN_CREATION_INPUT,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))
    PinInputLabel(
      isConfirm = isConfirm,
      isAlphanumericKeyboard = state.isAlphanumericKeyboard,
      isMismatch = state.pinMismatch
    )
    Spacer(modifier = Modifier.height(16.dp))
    KeyboardToggleButton(
      isAlphanumericKeyboard = state.isAlphanumericKeyboard,
      onToggleKeyboard = { onEvent(PinCreationScreenEvents.ToggleKeyboard) }
    )
  }
}

@Composable
private fun PinInputField(
  isAlphanumericKeyboard: Boolean,
  pin: String,
  canSubmitPin: Boolean,
  focusRequester: FocusRequester,
  onPinChanged: (String) -> Unit,
  onSubmit: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier
) {
  TextField(
    value = pin,
    onValueChange = onPinChanged,
    modifier = modifier
      .testTag(testTag)
      .focusRequester(focusRequester),
    textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
    singleLine = true,
    keyboardOptions = KeyboardOptions(
      keyboardType = if (isAlphanumericKeyboard) KeyboardType.Text else KeyboardType.Number,
      imeAction = ImeAction.Done
    ),
    keyboardActions = KeyboardActions(onDone = { if (canSubmitPin) onSubmit() }),
    visualTransformation = PinVisualTransformation
  )
}

@Composable
private fun PinInputLabel(
  isConfirm: Boolean,
  isAlphanumericKeyboard: Boolean,
  isMismatch: Boolean,
  modifier: Modifier = Modifier
) {
  Text(
    text = when {
      isConfirm -> stringResource(R.string.PinCreationScreen__reenter_pin)
      isMismatch -> stringResource(R.string.PinCreationScreen__pins_dont_match)
      isAlphanumericKeyboard -> stringResource(R.string.PinCreationScreen__pin_at_least_4_characters)
      else -> stringResource(R.string.PinCreationScreen__pin_at_least_4_digits)
    },
    style = MaterialTheme.typography.bodyMedium,
    textAlign = TextAlign.Center,
    color = if (!isConfirm && isMismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.fillMaxWidth()
  )
}

@Composable
private fun KeyboardToggleButton(
  isAlphanumericKeyboard: Boolean,
  onToggleKeyboard: () -> Unit,
  modifier: Modifier = Modifier
) {
  TextButton(
    onClick = onToggleKeyboard,
    modifier = modifier
      .fillMaxWidth()
      .testTag(TestTags.PIN_CREATION_TOGGLE_KEYBOARD_BUTTON)
  ) {
    Icon(
      painter = SignalIcons.Keyboard.painter,
      contentDescription = null,
      modifier = Modifier.padding(end = 8.dp)
    )
    Text(
      text = if (isAlphanumericKeyboard) {
        stringResource(R.string.PinCreationScreen__switch_to_numeric)
      } else {
        stringResource(R.string.PinCreationScreen__switch_to_alphanumeric)
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinCreationTopBar(
  scrollBehavior: TopAppBarScrollBehavior,
  onEvent: (PinCreationScreenEvents) -> Unit
) {
  var showOptOutDialog by rememberSaveable { mutableStateOf(false) }

  if (showOptOutDialog) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.PinCreationScreen__warning),
      body = stringResource(R.string.PinCreationScreen__disable_pin_warning),
      confirm = stringResource(R.string.PinCreationScreen__disable_pin),
      dismiss = stringResource(R.string.PinCreationScreen__cancel),
      onConfirm = {
        showOptOutDialog = false
        onEvent(PinCreationScreenEvents.OptOut)
      },
      onDismiss = { showOptOutDialog = false }
    )
  }

  Scaffolds.DefaultTopAppBar(
    title = "",
    titleContent = { _, _ -> },
    onNavigationClick = { },
    navigationIcon = null,
    scrollBehavior = scrollBehavior,
    actions = {
      val menuController = remember { DropdownMenus.MenuController() }

      IconButton(
        onClick = { menuController.show() },
        modifier = Modifier
          .padding(horizontal = 8.dp)
          .testTag(TestTags.PIN_CREATION_MENU_BUTTON)
      ) {
        Icon(
          imageVector = ImageVector.vectorResource(CoreR.drawable.symbol_more_vertical_24),
          contentDescription = stringResource(R.string.RegistrationActivity_open_menu)
        )
      }

      DropdownMenus.Menu(
        controller = menuController,
        offsetX = 24.dp,
        offsetY = 0.dp
      ) {
        DropdownMenus.Item(
          text = { Text(text = stringResource(R.string.PinCreationScreen__learn_more_about_pins)) },
          onClick = {
            menuController.hide()
            onEvent(PinCreationScreenEvents.LearnMore)
          }
        )
        DropdownMenus.Item(
          text = { Text(text = stringResource(R.string.PinCreationScreen__disable_pin)) },
          onClick = {
            menuController.hide()
            showOptOutDialog = true
          },
          modifier = Modifier.testTag(TestTags.PIN_CREATION_DISABLE_PIN_MENU_ITEM)
        )
      }
    }
  )
}

@Composable
private fun NextButton(
  params: RegistrationScaffold.Params,
  canSubmitPin: Boolean,
  isElevated: Boolean,
  loading: Boolean,
  onNext: () -> Unit,
  modifier: Modifier = Modifier
) {
  RegistrationScaffold.FooterSurface(
    isElevated = isElevated,
    modifier = modifier
  ) {
    Row(
      horizontalArrangement = Arrangement.End,
      modifier = Modifier.fillMaxWidth()
    ) {
      Buttons.LargeTonal(
        onClick = onNext,
        enabled = canSubmitPin && !loading,
        modifier = Modifier
          .widthIn(max = params.maxButtonWidth)
          .padding(params.footerPadding)
          .testTag(TestTags.PIN_CREATION_NEXT_BUTTON)
      ) {
        if (loading) {
          CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary
          )
        } else {
          Text(stringResource(R.string.PinCreationScreen__next))
        }
      }
    }
  }
}

@AllDevicePreviews
@Composable
private fun PinCreationScreenNumericPreview() {
  Previews.Preview {
    PinCreationScreen(
      state = PinCreationState(
        isAlphanumericKeyboard = false
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun PinCreationScreenAlphanumericPreview() {
  Previews.Preview {
    PinCreationScreen(
      state = PinCreationState(
        isAlphanumericKeyboard = true
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun PinCreationScreenConfirmPreview() {
  Previews.Preview {
    PinCreationScreen(
      state = PinCreationState(isConfirmEnabled = true),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun PinCreationScreenMismatchPreview() {
  Previews.Preview {
    PinCreationScreen(
      state = PinCreationState(pinMismatch = true),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun PinCreationScreenLoadingPreview() {
  Previews.Preview {
    PinCreationScreen(
      state = PinCreationState(isConfirmEnabled = true, loading = true),
      onEvent = {}
    )
  }
}
