/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package org.thoughtcrime.securesms.components.settings.app.internal.search

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import java.util.UUID

class InternalSearchFragment : ComposeFragment() {

  val viewModel: InternalSearchViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val results: ImmutableList<InternalSearchResult> by viewModel.results
    val query: String by viewModel.query

    InternalSearchFragmentScreen(
      query = query,
      results = results,
      onSearchUpdated = { viewModel.onQueryChanged(it) }
    )
  }
}

@Composable
fun InternalSearchFragmentScreen(query: String, results: ImmutableList<InternalSearchResult>, onSearchUpdated: (String) -> Unit, modifier: Modifier = Modifier) {
  val backgroundColor = MaterialTheme.colorScheme.surface

  CompositionLocalProvider(LocalContentColor provides contentColorFor(backgroundColor = backgroundColor)) {
    LazyColumn(
      modifier = modifier
        .fillMaxWidth()
        .background(backgroundColor)
    ) {
      item(key = -1) {
        SearchBar(query, onSearchUpdated)
      }
      results.forEach { recipient ->
        item(key = recipient.id) {
          ResultItem(recipient)
        }
      }
    }
  }
}

@Composable
fun SearchBar(query: String, onSearchUpdated: (String) -> Unit, modifier: Modifier = Modifier) {
  TextField(
    value = query,
    onValueChange = onSearchUpdated,
    placeholder = { Text(text = "Search by ID, ACI, or PNI") },
    modifier = modifier.fillMaxWidth()
  )
}

@Composable
fun ResultItem(result: InternalSearchResult, modifier: Modifier = Modifier) {
  val activity = LocalContext.current as? AppCompatActivity

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clickable {
        if (activity != null) {
          RecipientBottomSheetDialogFragment
            .show(activity.supportFragmentManager, result.id, result.groupId)
        }
      }
      .padding(8.dp)
  ) {
    Text(text = result.name, style = MaterialTheme.typography.titleSmall)
    Text(text = "ID: ${result.id}")
    Text(text = "ACI: ${result.aci ?: "null"}")
    Text(text = "PNI: ${result.pni ?: "null"}")
  }
}

@Preview
@Composable
fun InternalSearchScreenPreviewLightTheme() {
  SignalTheme(isDarkMode = false) {
    InternalSearchScreenPreview()
  }
}

@Preview
@Composable
fun InternalSearchScreenPreviewDarkTheme() {
  SignalTheme(isDarkMode = true) {
    InternalSearchScreenPreview()
  }
}

@Composable
fun InternalSearchScreenPreview() {
  InternalSearchFragmentScreen(
    query = "",
    results = persistentListOf(
      InternalSearchResult(
        name = "Peter Parker",
        id = RecipientId.from(1),
        aci = UUID.randomUUID().toString(),
        pni = UUID.randomUUID().toString()
      ),
      InternalSearchResult(
        name = "Mary Jane",
        id = RecipientId.from(2),
        aci = UUID.randomUUID().toString(),
        pni = null
      )
    ),
    onSearchUpdated = {}
  )
}
