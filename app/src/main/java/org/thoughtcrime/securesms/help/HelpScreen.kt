/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.help

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.CircularProgressWrapper
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiImage
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SupportEmailUtil

@Composable
fun HelpScreenContent(
  state: HelpScreenState,
  onEvent: (HelpScreenEvents) -> Unit,
  sideEffect: Flow<HelpScreenSideEffects>
) {
  val context = LocalContext.current
  val categories = stringArrayResource(R.array.HelpFragment__categories_6).toList()

  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val categoryShakeOffset = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    sideEffect.collect { sideEffect ->
      when (sideEffect) {
        is HelpScreenSideEffects.OpenEmail -> {
          CommunicationActions.openEmail(
            context,
            SupportEmailUtil.getSupportEmailAddress(context),
            sideEffect.subject,
            sideEffect.body
          )
        }

        is HelpScreenSideEffects.ShowSnackbar -> {
          snackbarHostState.showSnackbar(sideEffect.getMessage(context))
        }

        HelpScreenSideEffects.ShakeCategory -> {
          scope.launch {
            categoryShakeOffset.animateTo(
              targetValue = 0f,
              animationSpec = keyframes {
                durationMillis = 300
                0f at 0
                -8f at 50
                8f at 100
                -8f at 150
                8f at 200
                -8f at 250
                0f at 300
              }
            )
          }
        }
      }
    }
  }

  Scaffolds.Settings(
    snackbarHost = { Snackbars.Host(snackbarHostState = snackbarHostState) },
    title = stringResource(R.string.preferences__help),
    onNavigationClick = { onEvent(HelpScreenEvents.NavigationClick) },
    navigationIcon = SignalIcons.ArrowStart.imageVector
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .padding(paddingValues)
    ) {
      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .horizontalGutters()
      ) {
        item {
          Text(
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            text = stringResource(id = R.string.HelpFragment__contact_us),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        item {
          val isFieldError = state.displayValidationErrors && !state.isTextValid
          TextField(
            isError = isFieldError,
            supportingText = {
              if (isFieldError) {
                Text(text = pluralStringResource(R.plurals.HelpFragment__must_be_at_least_n_characters, HelpScreenState.MINIMUM_PROBLEM_CHARS, HelpScreenState.MINIMUM_PROBLEM_CHARS))
              }
            },
            value = state.problemText,
            onValueChange = { onEvent(HelpScreenEvents.ProblemTextChanged(it)) },
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
              .padding(bottom = 16.dp)
          )
        }

        item {
          Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = stringResource(id = R.string.HelpFragment__tell_us_why_youre_reaching_out),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        item {
          CategoryDropdown(
            modifier = Modifier
              .offset { IntOffset(x = categoryShakeOffset.value.dp.roundToPx(), y = 0) }
              .padding(bottom = 16.dp)
              .let { modifier ->
                if (state.displayValidationErrors && !state.isCategoryValid) {
                  modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(
                      topStart = 4.dp,
                      topEnd = 4.dp,
                      bottomStart = 0.dp,
                      bottomEnd = 0.dp
                    )
                  )
                } else {
                  modifier
                }
              },
            categories = categories,
            selectedIndex = state.categoryIndex,
            onCategorySelected = { onEvent(HelpScreenEvents.CategorySelected(it)) }
          )
        }

        item {
          Text(
            modifier = Modifier.padding(bottom = 12.dp),
            text = stringResource(id = R.string.HelpFragment__how_do_you_feel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        item {
          EmojiRatingRow(
            modifier = Modifier.padding(bottom = 12.dp),
            selectedFeeling = state.selectedFeeling,
            onFeelingSelected = { onEvent(HelpScreenEvents.FeelingSelected(it)) }
          )
        }

        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = state.includeDebugLog,
              onCheckedChange = { onEvent(HelpScreenEvents.DebugLogsToggled(it)) }
            )
            Text(
              text = stringResource(id = R.string.HelpFragment__include_debug_log),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { onEvent(HelpScreenEvents.WhatIsDebugLogClick) }) {
              Text(
                text = stringResource(id = R.string.HelpFragment__whats_this),
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }
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
                linkInteractionListener = { onEvent(HelpScreenEvents.FAQClick) },
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
            onClick = { onEvent(HelpScreenEvents.OnNextClick) },
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
  onCategorySelected: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    modifier = modifier,
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
  onFeelingSelected: (Feeling) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier.fillMaxWidth(),
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
    EmojiImage(
      emoji = feeling.emojiCode,
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
      onEvent = {},
      sideEffect = emptyFlow()
    )
  }
}
