/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.help

import android.widget.ImageView
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.CircularProgressWrapper
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.Snackbars
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiImageView
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SupportEmailUtil

@Composable
fun HelpScreen(
  viewModel: HelpViewModel,
  startCategoryIndex: Int = 0,
  onNavigationClick: () -> Unit,
  onWhatIsDebugLogClick: () -> Unit,
  onFaqClick: () -> Unit
) {
  val activity = LocalActivity.current
  val context = LocalContext.current
  val categories = stringArrayResource(R.array.HelpFragment__categories_6).toList()

  val state by viewModel.state.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(startCategoryIndex) {
    viewModel.onCategorySelected(startCategoryIndex)
  }

  LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
      when (event) {
        is HelpScreenEvents.OpenEmail -> {
          CommunicationActions.openEmail(
            context,
            SupportEmailUtil.getSupportEmailAddress(context),
            event.subject,
            event.body
          )
        }
        is HelpScreenEvents.ShowSnackbar -> {
          snackbarHostState.showSnackbar(context.getString(event.messageRes))
        }
      }
    }
  }

  DisposableEffect(Unit) {
    activity?.window?.let {
      WindowCompat.setDecorFitsSystemWindows(it, false)
    }
    onDispose {
      activity?.window?.let {
        WindowCompat.setDecorFitsSystemWindows(it, true)
      }
    }
  }

  HelpScreenContent(
    state = state,
    categories = categories,
    snackbarHostState = snackbarHostState,
    onNavigationClick = { onNavigationClick() },
    onWhatIsDebugLogClick = { onWhatIsDebugLogClick() },
    onFaqClick = { onFaqClick() },
    onProblemTextChanged = viewModel::onProblemChanged,
    onCategorySelected = viewModel::onCategorySelected,
    onFeelingSelected = viewModel::onFeelingSelected,
    onDebugLogsToggled = viewModel::onDebugLogsToggled,
    onNextClick = viewModel::onNextClick
  )
}

@Composable
private fun HelpScreenContent(
  state: HelpScreenState,
  categories: List<String>,
  snackbarHostState: SnackbarHostState,
  onNavigationClick: () -> Unit,
  onWhatIsDebugLogClick: () -> Unit,
  onFaqClick: () -> Unit,
  onProblemTextChanged: (String) -> Unit,
  onCategorySelected: (Int) -> Unit,
  onFeelingSelected: (Feeling) -> Unit,
  onDebugLogsToggled: (Boolean) -> Unit,
  onNextClick: () -> Unit
) {
  Scaffolds.Settings(
    snackbarHost = { Snackbars.Host(snackbarHostState = snackbarHostState) },
    title = stringResource(R.string.preferences__help),
    onNavigationClick = onNavigationClick,
    navigationIcon = SignalIcons.ArrowStart.imageVector
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp)
      ) {
        Text(
          modifier = Modifier.padding(top = 8.dp),
          text = stringResource(id = R.string.HelpFragment__contact_us),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        TextField(
          value = state.problemText,
          onValueChange = { onProblemTextChanged(it) },
          placeholder = {
            Text(text = stringResource(id = R.string.HelpFragment__tell_us_whats_going_on))
          },
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Sentences
          ),
          maxLines = Int.MAX_VALUE,
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 144.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = stringResource(id = R.string.HelpFragment__tell_us_why_youre_reaching_out),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        CategoryDropdown(
          categories = categories,
          selectedIndex = state.categoryIndex,
          onCategorySelected = onCategorySelected
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = stringResource(id = R.string.HelpFragment__how_do_you_feel),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        EmojiRatingRow(
          selectedFeeling = state.selectedFeeling,
          onFeelingSelected = onFeelingSelected
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Checkbox(
            checked = state.includeDebugLog,
            onCheckedChange = { onDebugLogsToggled(it) }
          )
          Text(
            text = stringResource(id = R.string.HelpFragment__include_debug_log),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          TextButton(onClick = onWhatIsDebugLogClick) {
            Text(
              text = stringResource(id = R.string.HelpFragment__whats_this),
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp, start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          modifier = Modifier
            .weight(1f),
          text = buildAnnotatedString {
            withLink(
              link = LinkAnnotation.Clickable(
                "view-faq",
                linkInteractionListener = { onFaqClick() },
                styles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary))
              )
            ) {
              append(stringResource(R.string.HelpFragment__have_you_read_our_faq_yet))
            }
          }
        )

        CircularProgressWrapper(
          isLoading = state.isSubmitting
        ) {
          Buttons.LargeTonal(
            modifier = Modifier.padding(end = 16.dp),
            onClick = onNextClick,
            enabled = !state.isSubmitting
          ) {
            Text(stringResource(R.string.HelpFragment__next))
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
  categories: List<String>,
  selectedIndex: Int,
  onCategorySelected: (Int) -> Unit
) {
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded }
  ) {
    TextField(
      modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
      value = categories.getOrElse(selectedIndex) { "" },
      onValueChange = {},
      readOnly = true,
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      categories.forEachIndexed { index, category ->
        DropdownMenuItem(
          text = { Text(category) },
          onClick = {
            onCategorySelected(index)
            expanded = false
          }
        )
      }
    }
  }
}

@Composable
private fun EmojiRatingRow(
  selectedFeeling: Feeling?,
  onFeelingSelected: (Feeling) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Feeling.entries.forEach { feeling ->
      EmojiButton(
        feeling = feeling,
        isSelected = feeling == selectedFeeling,
        onClick = { onFeelingSelected(feeling) }
      )
    }
  }
}

@Composable
private fun EmojiButton(
  feeling: Feeling,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val isDark = isSystemInDarkTheme()

  val backgroundColor = if (isSelected) {
    if (isDark) Color(0xFF6191f3) else Color(0xFF2C6BED)
  } else {
    if (isDark) Color(0xFF3b3b3b) else Color(0xFFE9E9E9)
  }

  Box(
    modifier = Modifier
      .size(48.dp)
      .background(backgroundColor, shape = CircleShape)
      .padding(4.dp)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    AndroidView(
      factory = { context ->
        EmojiImageView(context).apply {
          scaleType = ImageView.ScaleType.FIT_CENTER
        }
      },
      update = { view ->
        view.setImageEmoji(feeling.emojiCode)
      },
      modifier = Modifier.fillMaxSize()
    )
  }
}

enum class Feeling(val emojiCode: String, val labelRes: Int) {
  ECSTATIC(emojiCode = "\ud83d\ude00", labelRes = R.string.HelpFragment__emoji_5),
  HAPPY(emojiCode = "\ud83d\ude42", labelRes = R.string.HelpFragment__emoji_4),
  AMBIVALENT(emojiCode = "\ud83d\ude10", labelRes = R.string.HelpFragment__emoji_3),
  UNHAPPY(emojiCode = "\ud83d\ude41", labelRes = R.string.HelpFragment__emoji_2),
  ANGRY(emojiCode = "\ud83d\ude20", labelRes = R.string.HelpFragment__emoji_1)
}

@DayNightPreviews
@Composable
private fun HelpScreenPreview() {
  Previews.Preview {
    HelpScreenContent(
      state = HelpScreenState(),
      categories = emptyList(),
      snackbarHostState = SnackbarHostState(),
      onNavigationClick = {},
      onWhatIsDebugLogClick = {},
      onFaqClick = {},
      onProblemTextChanged = {},
      onCategorySelected = {},
      onFeelingSelected = {},
      onDebugLogsToggled = {},
      onNextClick = {}
    )
  }
}
