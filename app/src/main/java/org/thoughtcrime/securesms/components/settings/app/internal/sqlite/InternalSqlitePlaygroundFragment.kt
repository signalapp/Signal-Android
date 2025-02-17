/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.sqlite

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.signal.libsignal.protocol.util.Hex
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.internal.sqlite.InternalSqlitePlaygroundViewModel.QueryResult
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.util.Util

class InternalSqlitePlaygroundFragment : ComposeFragment() {

  val viewModel by viewModels<InternalSqlitePlaygroundViewModel>()

  @Composable
  override fun FragmentContent() {
    val queryResults by viewModel.queryResults

    Screen(
      onBackPressed = { findNavController().popBackStack() },
      queryResults = queryResults,
      onQuerySubmitted = { viewModel.onQuerySubmitted(it) }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(
  onBackPressed: () -> Unit = {},
  queryResults: QueryResult?,
  onQuerySubmitted: (String) -> Unit = {}
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("SQLite Playground") },
        navigationIcon = {
          IconButton(onClick = onBackPressed) {
            Icon(
              painter = painterResource(R.drawable.symbol_arrow_left_24),
              tint = MaterialTheme.colorScheme.onSurface,
              contentDescription = null
            )
          }
        }
      )
    }
  ) { contentPadding ->
    Surface(modifier = Modifier.padding(contentPadding)) {
      Column(modifier = Modifier.padding(8.dp)) {
        Text("Warning! This allows you to run arbitrary queries. Only use this if you know what you're doing!", color = Color.Red)
        Spacer(Modifier.height(8.dp))
        QueryBox(onQuerySubmitted)
        Spacer(Modifier.height(8.dp))
        QueryResults(queryResults)
      }
    }
  }
}

@Composable
private fun QueryBox(onQuerySubmitted: (String) -> Unit = {}) {
  var queryText: String by remember { mutableStateOf("") }
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.End
  ) {
    OutlinedTextField(
      value = queryText,
      onValueChange = { queryText = it },
      modifier = Modifier.fillMaxWidth(),
      minLines = 3,
      textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    )
    Spacer(Modifier.height(2.dp))
    Buttons.LargePrimary(onClick = { onQuerySubmitted(queryText) }) {
      Text("Execute")
    }
  }
}

@Composable
private fun QueryResults(results: QueryResult?) {
  val columnWidth = LocalConfiguration.current.screenWidthDp.dp / 2
  val horizontalScrollState = rememberScrollState()
  val context = LocalContext.current

  if (results == null) {
    Text("Waiting on query results.")
    return
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text("${results.rows.size} rows in ${results.totalTimeString} ms", modifier = Modifier.padding(4.dp).weight(1f))
    Buttons.Small(onClick = { Util.copyToClipboard(context, results.toCopyString()) }) {
      Text("Copy results")
    }
  }
  QueryRow(data = results.columns, columnWidth = columnWidth, scrollState = horizontalScrollState, fontWeight = FontWeight.Bold)

  LazyColumn {
    items(results.rows) { row ->
      QueryRow(data = row, columnWidth = columnWidth, scrollState = horizontalScrollState)
    }
  }
}

@Composable
private fun QueryRow(data: List<Any?>, columnWidth: Dp, scrollState: ScrollState, fontWeight: FontWeight = FontWeight.Normal) {
  val context = LocalContext.current

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(scrollState)
  ) {
    data.forEach {
      Text(
        text = it.toDisplayString(),
        fontWeight = fontWeight,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        modifier = Modifier.width(columnWidth)
          .padding(4.dp)
          .clickable {
            MaterialAlertDialogBuilder(context)
              .setMessage(it.toDisplayString())
              .setPositiveButton("Ok", null)
              .show()
          }
      )
    }
  }
}

private fun Any?.toDisplayString(): String {
  return when (this) {
    is ByteArray -> "Blob { ${Hex.toStringCondensed(this)} }"
    else -> this.toString()
  }
}

private fun QueryResult.toCopyString(): String {
  val builder = StringBuilder()

  builder.append(this.columns.toCsv()).append("\n")

  for (row in this.rows) {
    builder.append(row.toCsv()).append("\n")
  }

  return builder.toString()
}

private fun List<Any?>.toCsv(): String {
  return this.joinToString(
    separator = ",",
    transform = { input ->
      input
        .toDisplayString()
        .replace("\"", "\"\"")
        .let { if (it.isNotEmpty()) "\"$it\"" else "" }
    }
  )
}

@SignalPreview
@Composable
private fun ScreenPreview() {
  Previews.Preview {
    Screen(
      queryResults = QueryResult(
        columns = listOf("column1", "column2", "column3"),
        rows = listOf(
          listOf("a", 1, ByteArray(16) { 0 }),
          listOf("b", 2, ByteArray(16) { 1 }),
          listOf("c", 3, ByteArray(16) { 2 })
        ),
        totalTimeString = "3.42"
      )
    )
  }
}

@SignalPreview
@Composable
private fun ScreenPreviewNoResults() {
  Previews.Preview {
    Screen(
      queryResults = null
    )
  }
}
