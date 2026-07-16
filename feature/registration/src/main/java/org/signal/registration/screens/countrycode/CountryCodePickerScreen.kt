/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.countrycode

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.IconButtons.IconButton
import org.signal.core.ui.compose.LargeFontPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.TextFields
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.test.TestTags

/**
 * Screen that allows someone to search and select a country code from a supported list of countries.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CountryCodePickerScreen(
  state: CountryCodeState,
  onEvent: (CountryCodePickerScreenEvents) -> Unit
) {
  when (val layoutParams = RegistrationScaffold.rememberLayoutParams()) {
    is RegistrationScaffold.Params.OnePane -> OnePaneLayout(layoutParams, state, onEvent)
    is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(layoutParams, state, onEvent)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnePaneLayout(
  layoutParams: RegistrationScaffold.Params.OnePane,
  state: CountryCodeState,
  onEvent: (CountryCodePickerScreenEvents) -> Unit
) {
  val topBarScrollBehavior = RegistrationScaffold.rememberTopBarScrollBehavior()

  OnePaneRegistrationScaffold(
    modifier = Modifier
      .fillMaxSize()
      .testTag(TestTags.COUNTRY_CODE_PICKER_SCREEN),
    params = layoutParams,
    topBar = {
      TopAppBar(
        title = stringResource(R.string.CountryCodeSelectScreen__your_country),
        scrollBehavior = topBarScrollBehavior,
        onCloseClick = { onEvent(CountryCodePickerScreenEvents.Dismissed) }
      )
    },
    content = { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
      ) {
        CountryList(
          state = state,
          onEvent = onEvent,
          contentPadding = paddingValues,
          modifier = Modifier.weight(1f)
        )
      }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: CountryCodeState,
  onEvent: (CountryCodePickerScreenEvents) -> Unit
) {
  val topBarScrollBehavior = RegistrationScaffold.rememberTopBarScrollBehavior()

  TwoPaneRegistrationScaffold(
    modifier = Modifier
      .fillMaxSize()
      .testTag(TestTags.COUNTRY_CODE_PICKER_SCREEN),
    params = params,
    topBar = {
      TopAppBar(
        scrollBehavior = topBarScrollBehavior,
        onCloseClick = { onEvent(CountryCodePickerScreenEvents.Dismissed) }
      )
    },
    firstPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .padding(paddingValues)
      ) {
        Text(
          stringResource(R.string.CountryCodeSelectScreen__your_country),
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.attachDebugLogHelper()
        )
      }
    },
    secondPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .padding(paddingValues)
      ) {
        CountryList(
          state = state,
          onEvent = onEvent
        )
      }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
  scrollBehavior: TopAppBarScrollBehavior,
  onCloseClick: () -> Unit,
  title: String = ""
) {
  Scaffolds.DefaultTopAppBar(
    title = title,
    titleContent = { _, titleText ->
      Text(
        text = titleText,
        style = MaterialTheme.typography.titleLarge
      )
    },
    navigationIconContent = {
      IconButton(
        onClick = onCloseClick,
        modifier = Modifier
          .padding(end = 16.dp)
          .testTag(TestTags.COUNTRY_CODE_CLOSE_BUTTON)
      ) {
        Icon(
          imageVector = SignalIcons.X.imageVector,
          contentDescription = stringResource(R.string.CountryCodeSelectScreen__close)
        )
      }
    },
    scrollBehavior = scrollBehavior
  )
}

@Composable
private fun CountryList(
  state: CountryCodeState,
  onEvent: (CountryCodePickerScreenEvents) -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues()
) {
  val listState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()

  LazyColumn(
    modifier = modifier,
    state = listState,
    contentPadding = PaddingValues(
      start = 24.dp,
      end = 24.dp,
      bottom = contentPadding.calculateBottomPadding()
    ),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    item {
      SearchBar(
        text = state.query,
        onSearch = { onEvent(CountryCodePickerScreenEvents.Search(it)) }
      )
    }

    if (state.countryList.isEmpty()) {
      item {
        CircularProgressIndicator(
          modifier = Modifier.size(56.dp)
        )
      }
    } else if (state.query.isEmpty()) {
      if (state.commonCountryList.isNotEmpty()) {
        items(state.commonCountryList) { country ->
          CountryItem(country, onEvent)
        }

        item {
          Dividers.Default()
        }
      }

      items(state.countryList) { country ->
        CountryItem(country, onEvent)
      }
    } else {
      items(state.filteredList) { country ->
        CountryItem(country, onEvent, state.query)
      }
    }
  }

  LaunchedEffect(state.startingIndex) {
    coroutineScope.launch {
      listState.scrollToItem(index = state.startingIndex)
    }
  }
}

@Composable
private fun CountryItem(
  country: Country,
  onEvent: (CountryCodePickerScreenEvents) -> Unit = {},
  query: String = ""
) {
  val emoji = country.emoji
  val name = country.name
  val code = "+${country.countryCode}"
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 56.dp)
      .clickable { onEvent(CountryCodePickerScreenEvents.CountrySelected(country)) }
  ) {
    Text(
      text = emoji,
      modifier = Modifier.size(24.dp)
    )

    if (query.isEmpty()) {
      Text(
        text = name.ifEmpty { stringResource(R.string.CountryCodeSelectScreen__unknown_country) },
        modifier = Modifier
          .padding(start = 24.dp)
          .weight(1f),
        style = MaterialTheme.typography.bodyLarge
      )
      Text(
        text = code,
        modifier = Modifier.padding(start = 24.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      val annotatedName = buildAnnotatedString {
        val startIndex = name.indexOf(query, ignoreCase = true)

        if (startIndex >= 0) {
          append(name.substring(0, startIndex))

          withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append(name.substring(startIndex, startIndex + query.length))
          }

          append(name.substring(startIndex + query.length))
        } else {
          append(name)
        }
      }

      val annotatedCode = buildAnnotatedString {
        val startIndex = code.indexOf(query, ignoreCase = true)

        if (startIndex >= 0) {
          append(code.substring(0, startIndex))

          withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append(code.substring(startIndex, startIndex + query.length))
          }

          append(code.substring(startIndex + query.length))
        } else {
          append(code)
        }
      }

      Text(
        text = annotatedName,
        modifier = Modifier
          .padding(start = 24.dp)
          .weight(1f),
        style = MaterialTheme.typography.bodyLarge
      )
      Text(
        text = annotatedCode,
        modifier = Modifier.padding(start = 24.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun SearchBar(
  text: String,
  modifier: Modifier = Modifier,
  hint: String = stringResource(R.string.CountryCodeSelectScreen__search_by),
  onSearch: (String) -> Unit = {}
) {
  val focusRequester = remember { FocusRequester() }
  var showKeyboard by remember { mutableStateOf(false) }

  Box(
    modifier = modifier.padding(vertical = 10.dp)
  ) {
    TextFields.TextField(
      value = text,
      onValueChange = { onSearch(it) },
      placeholder = { Text(hint) },
      trailingIcon = {
        if (text.isNotEmpty()) {
          IconButton(onClick = { onSearch("") }) {
            Icon(
              imageVector = SignalIcons.X.imageVector,
              contentDescription = null
            )
          }
        } else {
          IconButton(onClick = {
            showKeyboard = !showKeyboard
            focusRequester.requestFocus()
          }) {
            if (showKeyboard) {
              Icon(
                imageVector = SignalIcons.Keyboard.imageVector,
                contentDescription = null
              )
            } else {
              Icon(
                imageVector = ImageVector.vectorResource(R.drawable.symbol_number_pad_24),
                contentDescription = null
              )
            }
          }
        }
      },
      keyboardOptions = KeyboardOptions(
        keyboardType = if (showKeyboard) {
          KeyboardType.Number
        } else {
          KeyboardType.Text
        }
      ),
      singleLine = true,
      shape = RoundedCornerShape(32.dp),
      modifier = modifier
        .background(MaterialTheme.colorScheme.background)
        .fillMaxWidth()
        .defaultMinSize(minHeight = 44.dp)
        .padding(0.dp)
        .focusRequester(focusRequester)
        .testTag(TestTags.COUNTRY_CODE_SEARCH_FIELD),
      visualTransformation = VisualTransformation.None,
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      colors = TextFieldDefaults.colors(
        // TODO move to SignalTheme
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
      )
    )
  }
}

@AllDevicePreviews
@Composable
private fun ScreenPreview() {
  Previews.Preview {
    CountryCodePickerScreen(
      state = CountryCodeState(
        countryList = mutableListOf(
          Country("\uD83C\uDDFA\uD83C\uDDF8", "United States", 1, "US"),
          Country("\uD83C\uDDE8\uD83C\uDDE6", "Canada", 2, "CA"),
          Country("\uD83C\uDDF2\uD83C\uDDFD", "Mexico", 3, "MX")
        ),
        commonCountryList = mutableListOf(
          Country("\uD83C\uDDFA\uD83C\uDDF8", "United States", 4, "US"),
          Country("\uD83C\uDDE8\uD83C\uDDE6", "Canada", 5, "CA")
        )
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun LoadingScreenPreview() {
  Previews.Preview {
    CountryCodePickerScreen(
      state = CountryCodeState(
        countryList = emptyList()
      ),
      onEvent = {}
    )
  }
}

@LargeFontPreviews
@Composable
private fun LargeFontScreenPreview() {
  Previews.Preview {
    CountryCodePickerScreen(
      state = CountryCodeState(
        countryList = mutableListOf(
          Country("\uD83C\uDDFA\uD83C\uDDF8", "United States", 1, "US"),
          Country("\uD83C\uDDE8\uD83C\uDDE6", "Canada", 2, "CA"),
          Country("\uD83C\uDDF2\uD83C\uDDFD", "Mexico", 3, "MX")
        ),
        commonCountryList = mutableListOf(
          Country("\uD83C\uDDFA\uD83C\uDDF8", "United States", 4, "US"),
          Country("\uD83C\uDDE8\uD83C\uDDE6", "Canada", 5, "CA")
        )
      ),
      onEvent = {}
    )
  }
}
