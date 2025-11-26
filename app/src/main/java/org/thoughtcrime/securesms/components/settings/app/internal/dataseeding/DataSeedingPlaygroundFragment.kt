/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.dataseeding

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.database.model.ThreadRecord

class DataSeedingPlaygroundFragment : ComposeFragment() {

  private val viewModel: DataSeedingPlaygroundViewModel by viewModels()
  private lateinit var selectFolderLauncher: ActivityResultLauncher<Intent>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    selectFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        result.data?.data?.let { uri ->
          viewModel.selectFolder(uri)
        } ?: Toast.makeText(requireContext(), "No folder selected", Toast.LENGTH_SHORT).show()
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
      viewModel.loadThreads()
    }

    Screen(
      state = state,
      onBack = { findNavController().popBackStack() },
      onSelectFolderClicked = {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        selectFolderLauncher.launch(intent)
      },
      onThreadSelectionChanged = { threadId, isSelected ->
        viewModel.toggleThreadSelection(threadId, isSelected)
      },
      onSeedDataClicked = {
        viewModel.seedData(
          onComplete = {
            Toast.makeText(context, "Data seeding completed!", Toast.LENGTH_SHORT).show()
          },
          onError = { error ->
            Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
          }
        )
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
  state: DataSeedingPlaygroundState,
  onBack: () -> Unit = {},
  onSelectFolderClicked: () -> Unit = {},
  onThreadSelectionChanged: (Long, Boolean) -> Unit = { _, _ -> },
  onSeedDataClicked: () -> Unit = {}
) {
  var showConfirmDialog by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text("Data Seeding Playground")
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              painter = painterResource(R.drawable.symbol_arrow_start_24),
              tint = MaterialTheme.colorScheme.onSurface,
              contentDescription = null
            )
          }
        }
      )
    }
  ) { paddingValues ->
    Surface(modifier = Modifier.padding(paddingValues)) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Folder selection section
        Card(
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp)
          ) {
            Text(
              text = "Media Folder",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (state.selectedFolderPath.isNotEmpty()) {
              Text(
                text = "Selected: ${state.selectedFolderPath}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "Media files found: ${state.mediaFiles.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            } else {
              Text(
                text = "No folder selected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Rows.TextRow(
              text = "Select Media Folder",
              label = "Choose a folder containing photos and videos to seed into conversations.",
              onClick = onSelectFolderClicked
            )
          }
        }

        // Thread selection section
        Card(
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp)
          ) {
            Text(
              text = "Conversation Threads (${state.selectedThreads.size} selected)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
              modifier = Modifier.height(300.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              items(state.threads) { thread ->
                ThreadSelectionRow(
                  thread = thread,
                  isSelected = state.selectedThreads.contains(thread.threadId),
                  onSelectionChanged = { isSelected ->
                    onThreadSelectionChanged(thread.threadId, isSelected)
                  }
                )
              }
            }
          }
        }

        // Action section
        if (state.mediaFiles.isNotEmpty() && state.selectedThreads.isNotEmpty()) {
          Card(
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(16.dp)
            ) {
              Text(
                text = "Ready to Seed Data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Spacer(modifier = Modifier.height(8.dp))

              Text(
                text = "This will send ${state.mediaFiles.size} media files to ${state.selectedThreads.size} conversations in a round-robin fashion.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )

              Spacer(modifier = Modifier.height(8.dp))

              Rows.TextRow(
                text = "Seed Data",
                label = "Send the selected media files to the selected conversations.",
                onClick = {
                  showConfirmDialog = true
                }
              )
            }
          }
        }
      }
    }
  }

  // Confirmation dialog
  if (showConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showConfirmDialog = false },
      title = { Text("Confirm Data Seeding") },
      text = {
        Text("Are you sure you want to send ${state.mediaFiles.size} media files to ${state.selectedThreads.size} conversations? This action cannot be undone.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            showConfirmDialog = false
            onSeedDataClicked()
          }
        ) {
          Text("Seed Data")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showConfirmDialog = false }
        ) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun ThreadSelectionRow(
  thread: ThreadRecord,
  isSelected: Boolean,
  onSelectionChanged: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Checkbox(
      checked = isSelected,
      onCheckedChange = onSelectionChanged
    )

    Column(
      modifier = Modifier
        .weight(1f)
        .padding(start = 8.dp)
    ) {
      Text(
        text = thread.recipient.getDisplayName(LocalContext.current),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
      )
      if (thread.body.isNotEmpty()) {
        Text(
          text = thread.body,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1
        )
      }
    }
  }
}

@DayNightPreviews
@Composable
fun PreviewScreen() {
  Previews.Preview {
    Screen(
      state = DataSeedingPlaygroundState(
        threads = emptyList(),
        selectedThreads = emptySet(),
        mediaFiles = emptyList(),
        selectedFolderPath = "/storage/emulated/0/Pictures"
      )
    )
  }
}

@DayNightPreviews
@Composable
fun PreviewScreenWithData() {
  Previews.Preview {
    Screen(
      state = DataSeedingPlaygroundState(
        threads = emptyList(),
        selectedThreads = setOf(1L, 2L),
        mediaFiles = listOf("photo1.jpg", "video1.mp4", "photo2.jpg"),
        selectedFolderPath = "/storage/emulated/0/Pictures"
      )
    )
  }
}
