package org.thoughtcrime.securesms.conversation.v2

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.copied.androidx.compose.DragAndDropEvent
import org.signal.core.ui.compose.copied.androidx.compose.DraggableItem
import org.signal.core.ui.compose.copied.androidx.compose.dragContainer
import org.signal.core.ui.compose.copied.androidx.compose.rememberDragDropState
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeDialogFragment
import org.thoughtcrime.securesms.polls.Poll
import org.thoughtcrime.securesms.util.ViewUtil
import kotlin.time.Duration.Companion.milliseconds

/**
 * Fragment to create a poll
 */
class CreatePollFragment : ComposeDialogFragment() {

  companion object {
    private val TAG = Log.tag(CreatePollFragment::class)

    const val MAX_CHARACTER_LENGTH = 100
    const val MAX_OPTIONS = 10
    const val MIN_OPTIONS = 2
    const val CHARACTER_COUNTDOWN_THRESHOLD = 20
    const val REQUEST_KEY = "CreatePollFragment"

    fun show(fragmentManager: FragmentManager) {
      return CreatePollFragment().show(fragmentManager, null)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen_Poll) // TODO(michelle): Finalize animation
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState)
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    return dialog
  }

  @Composable
  override fun DialogContent() {
    Scaffolds.Settings(
      title = stringResource(R.string.CreatePollFragment__new_poll),
      onNavigationClick = {
        dismissAllowingStateLoss()
      },
      navigationIcon = ImageVector.vectorResource(R.drawable.symbol_x_24),
      navigationContentDescription = stringResource(R.string.Material3SearchToolbar__close)
    ) { paddingValues ->
      CreatePollScreen(
        paddingValues = paddingValues,
        onSend = { question, allowMultiple, options ->
          ViewUtil.hideKeyboard(requireContext(), requireView())
          setFragmentResult(REQUEST_KEY, Poll(question, allowMultiple, options).toBundle())
          dismissAllowingStateLoss()
        },
        onShowErrorSnackbar = { hasQuestion, hasOptions ->
          if (!hasQuestion && !hasOptions) {
            Snackbar.make(requireView(), R.string.CreatePollFragment__add_question_option, Snackbar.LENGTH_LONG).show()
          } else if (!hasQuestion) {
            Snackbar.make(requireView(), R.string.CreatePollFragment__add_question, Snackbar.LENGTH_LONG).show()
          } else {
            Snackbar.make(requireView(), R.string.CreatePollFragment__add_option, Snackbar.LENGTH_LONG).show()
          }
        }
      )
    }
  }
}

@OptIn(FlowPreview::class)
@Composable
private fun CreatePollScreen(
  paddingValues: PaddingValues,
  onSend: (String, Boolean, List<String>) -> Unit = { _, _, _ -> },
  onShowErrorSnackbar: (Boolean, Boolean) -> Unit = { _, _ -> }
) {
  // Parts of poll
  var question by remember { mutableStateOf("") }
  val options = remember { mutableStateListOf("", "") }
  var allowMultiple by remember { mutableStateOf(false) }

  var hasMinimumOptions by remember { mutableStateOf(false) }
  val isEnabled = question.isNotBlank() && hasMinimumOptions
  var focusedOption by remember { mutableStateOf(-1) }

  // Drag and drop
  val screenWidth = LocalConfiguration.current.screenWidthDp.dp
  val isRtl = ViewUtil.isRtl(LocalContext.current)
  val listState = rememberLazyListState()
  val dragDropState = rememberDragDropState(listState, includeHeader = true, includeFooter = true, onEvent = { event ->
    when (event) {
      is DragAndDropEvent.OnItemMove -> {
        val oldIndex = options[event.fromIndex]
        options[event.fromIndex] = options[event.toIndex]
        options[event.toIndex] = oldIndex
      }
      is DragAndDropEvent.OnItemDrop, is DragAndDropEvent.OnDragCancel -> Unit
    }
  })

  LaunchedEffect(Unit) {
    snapshotFlow { options.toList() }
      .debounce(100.milliseconds)
      .collect { currentOptions ->
        val count = currentOptions.count { it.isNotBlank() }
        if (count == currentOptions.size && currentOptions.size < CreatePollFragment.MAX_OPTIONS) {
          options.add("")
        }
        hasMinimumOptions = count >= CreatePollFragment.MIN_OPTIONS
      }
  }

  LaunchedEffect(focusedOption) {
    val count = options.count { it.isNotBlank() }
    if (count >= CreatePollFragment.MIN_OPTIONS) {
      if (options.removeIf { it.isEmpty() }) {
        options.add("")
      }
    }
  }

  Box(
    modifier = Modifier
      .padding(paddingValues)
      .fillMaxSize()
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxHeight()
        .imePadding()
        .dragContainer(
          dragDropState = dragDropState,
          leftDpOffset = if (isRtl) 0.dp else screenWidth - 56.dp,
          rightDpOffset = if (isRtl) 56.dp else screenWidth
        ),
      state = listState
    ) {
      item {
        DraggableItem(dragDropState, 0) {
          Text(
            text = stringResource(R.string.CreatePollFragment__question),
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp),
            style = MaterialTheme.typography.titleSmall
          )

          TextFieldWithCountdown(
            value = question,
            label = { Text(text = stringResource(R.string.CreatePollFragment__ask_a_question)) },
            onValueChange = { question = it.substring(0, minOf(it.length, CreatePollFragment.MAX_CHARACTER_LENGTH)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = TextFieldDefaults.colors(
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
              .fillMaxWidth()
              .onFocusChanged { focusState -> if (focusState.isFocused) focusedOption = -1 },
            countdownThreshold = CreatePollFragment.CHARACTER_COUNTDOWN_THRESHOLD
          )

          Spacer(modifier = Modifier.size(32.dp))

          Text(
            text = stringResource(R.string.CreatePollFragment__options),
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp),
            style = MaterialTheme.typography.titleSmall
          )
        }
      }

      itemsIndexed(options) { index, option ->
        DraggableItem(dragDropState, 1 + index) {
          TextFieldWithCountdown(
            value = option,
            label = { Text(text = stringResource(R.string.CreatePollFragment__option_n, index + 1)) },
            onValueChange = { options[index] = it.substring(0, minOf(it.length, CreatePollFragment.MAX_CHARACTER_LENGTH)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = TextFieldDefaults.colors(
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
              .fillMaxWidth()
              .onFocusChanged { focusState -> if (focusState.isFocused) focusedOption = index },
            trailingIcon = {
              Icon(
                imageVector = ImageVector.vectorResource(R.drawable.drag_handle),
                contentDescription = stringResource(R.string.CreatePollFragment__drag_handle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            },
            countdownThreshold = CreatePollFragment.CHARACTER_COUNTDOWN_THRESHOLD
          )
        }
      }

      item {
        DraggableItem(dragDropState, 1 + options.size) {
          Dividers.Default()
          Rows.ToggleRow(checked = allowMultiple, text = stringResource(R.string.CreatePollFragment__allow_multiple_votes), onCheckChanged = { allowMultiple = it })
          Spacer(modifier = Modifier.size(60.dp))
        }
      }
    }

    Buttons.MediumTonal(
      colors = ButtonDefaults.filledTonalButtonColors(
        contentColor = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        containerColor = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
      ),
      onClick = {
        if (isEnabled) {
          onSend(question, allowMultiple, options.filter { it.isNotBlank() })
        } else {
          onShowErrorSnackbar(question.isNotBlank(), hasMinimumOptions)
        }
      },
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(bottom = 16.dp, end = 24.dp)
        .imePadding()
    ) {
      Text(text = stringResource(R.string.conversation_activity__send))
    }
  }
}

/**
 * Text field with a character countdown. Once [charactersRemaining] has hit [countdownThreshold],
 * the remaining character count will show within the text field.
 */
@Composable
private fun TextFieldWithCountdown(
  value: String,
  label: @Composable () -> Unit,
  onValueChange: (String) -> Unit,
  keyboardOptions: KeyboardOptions,
  colors: TextFieldColors,
  modifier: Modifier,
  trailingIcon: @Composable () -> Unit = {},
  countdownThreshold: Int
) {
  val charactersRemaining = CreatePollFragment.MAX_CHARACTER_LENGTH - value.length
  val displayCountdown = charactersRemaining <= countdownThreshold

  Box(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
    TextField(
      value = value,
      label = label,
      onValueChange = onValueChange,
      keyboardOptions = keyboardOptions,
      colors = colors,
      modifier = modifier,
      trailingIcon = trailingIcon
    )

    AnimatedVisibility(
      visible = displayCountdown,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(bottom = 6.dp, end = 12.dp)
    ) {
      Text(
        text = "$charactersRemaining",
        style = MaterialTheme.typography.bodySmall,
        color = if (charactersRemaining <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
      )
    }
  }
}

@DayNightPreviews
@Composable
fun CreatePollPreview() {
  Previews.Preview {
    CreatePollScreen(PaddingValues(0.dp))
  }
}
