/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.IconButtons.IconButton
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.ui.RecipientPicker.KeyboardType

/**
 * A search input field for finding recipients.
 *
 * Replaces [org.thoughtcrime.securesms.components.ContactFilterView].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipientSearchBar(
  hint: String = stringResource(R.string.RecipientSearchBar__search_name_or_number),
  query: String,
  onQueryChange: (String) -> Unit,
  onSearch: (String) -> Unit,
  modifier: Modifier = Modifier,
  enabledKeyboardTypes: List<KeyboardType> = listOf(KeyboardType.Text, KeyboardType.Phone)
) {
  val state = rememberSearchBarState()
  var keyboardType by remember(enabledKeyboardTypes) { mutableStateOf(enabledKeyboardTypes.first()) }
  val keyboardOptions = remember(keyboardType) {
    KeyboardOptions(
      keyboardType = keyboardType.wrappedType,
      imeAction = ImeAction.Search
    )
  }

  SearchBar(
    state = state,
    inputField = {
      TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(hint) },
        singleLine = true,
        shape = SearchBarDefaults.inputFieldShape,
        colors = TextFieldDefaults.colors(
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
          focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
          disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
          focusedIndicatorColor = Color.Transparent,
          disabledIndicatorColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(
          onSearch = { onSearch(query) }
        ),
        trailingIcon = {
          val modifier = Modifier.padding(end = 4.dp)
          if (query.isNotEmpty()) {
            ClearQueryButton(
              onClearQuery = { onQueryChange("") },
              modifier = modifier
            )
          } else if (enabledKeyboardTypes.size > 1) {
            KeyboardToggleButton(
              keyboardType = keyboardType,
              enabledKeyboardTypes = enabledKeyboardTypes,
              onKeyboardTypeChange = { keyboardType = it },
              modifier = modifier
            )
          }
        }
      )
    },
    modifier = modifier
  )
}

@Composable
private fun KeyboardToggleButton(
  keyboardType: KeyboardType,
  enabledKeyboardTypes: List<KeyboardType>,
  onKeyboardTypeChange: (KeyboardType) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val nextTypeMap = remember(enabledKeyboardTypes) {
    enabledKeyboardTypes.mapIndexed { index, type ->
      val nextIndex = (index + 1) % enabledKeyboardTypes.size
      type to enabledKeyboardTypes[nextIndex]
    }.toMap()
  }

  IconButton(
    onClick = {
      onKeyboardTypeChange(nextTypeMap.getValue(keyboardType))
    },
    modifier = modifier
  ) {
    when (keyboardType) {
      KeyboardType.Text -> Icon(
        imageVector = ImageVector.vectorResource(R.drawable.ic_number_pad_conversation_filter_24),
        tint = MaterialTheme.colorScheme.onSurface,
        contentDescription = stringResource(R.string.RecipientSearchBar_accessibility_switch_to_numeric_keyboard)
      )

      KeyboardType.Phone -> Icon(
        imageVector = ImageVector.vectorResource(R.drawable.ic_keyboard_24),
        tint = MaterialTheme.colorScheme.onSurface,
        contentDescription = stringResource(R.string.RecipientSearchBar_accessibility_switch_to_alphanumeric_keyboard)
      )
    }
  }
}

@Composable
private fun ClearQueryButton(
  onClearQuery: () -> Unit,
  modifier: Modifier = Modifier
) {
  IconButton(
    onClick = { onClearQuery() },
    modifier = modifier
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.ic_x_conversation_filter_24),
      tint = MaterialTheme.colorScheme.onSurface,
      contentDescription = stringResource(R.string.RecipientSearchBar_accessibility_clear_search)
    )
  }
}

@Composable
@DayNightPreviews
private fun RecipientSearchBarPreview() = Previews.Preview {
  RecipientSearchBar(
    query = "",
    onQueryChange = {},
    onSearch = {}
  )
}
