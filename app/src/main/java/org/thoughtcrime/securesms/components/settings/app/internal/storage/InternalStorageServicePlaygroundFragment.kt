/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.storage

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Rows.TextAndLabel
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.util.Hex
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.internal.storage.InternalStorageServicePlaygroundViewModel.OneOffEvent
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.StorageForcePushJob
import org.thoughtcrime.securesms.jobs.StorageSyncJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.storage.RecordIkm
import org.whispersystems.signalservice.api.storage.SignalStorageManifest
import org.whispersystems.signalservice.api.storage.SignalStorageRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord

class InternalStorageServicePlaygroundFragment : ComposeFragment() {

  val viewModel: InternalStorageServicePlaygroundViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val manifest by viewModel.manifest
    val storageRecords by viewModel.storageRecords
    val oneOffEvent by viewModel.oneOffEvents
    var forceSsreToggled by remember { mutableStateOf(SignalStore.internal.forceSsre2Capability) }

    Screen(
      onBackPressed = { findNavController().popBackStack() },
      manifest = manifest,
      storageRecords = storageRecords,
      oneOffEvent = oneOffEvent,
      forceSsreCapability = forceSsreToggled,
      onForceSsreToggled = { checked ->
        SignalStore.internal.forceSsre2Capability = checked
        forceSsreToggled = checked
      },
      onViewTabSelected = { viewModel.onViewTabSelected() }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
  manifest: SignalStorageManifest,
  storageRecords: List<SignalStorageRecord>,
  forceSsreCapability: Boolean,
  oneOffEvent: OneOffEvent,
  onForceSsreToggled: (Boolean) -> Unit = {},
  onViewTabSelected: () -> Unit = {},
  onBackPressed: () -> Unit = {}
) {
  var tabIndex by remember { mutableIntStateOf(0) }
  val tabs = listOf("Tools", "View")

  Scaffold(
    topBar = {
      Column {
        TopAppBar(
          title = { Text("Storage Service Playground") },
          navigationIcon = {
            IconButton(onClick = onBackPressed) {
              Icon(
                painter = painterResource(R.drawable.symbol_arrow_start_24),
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = null
              )
            }
          }
        )
        TabRow(selectedTabIndex = tabIndex) {
          tabs.forEachIndexed { index, tab ->
            Tab(
              text = { Text(tab) },
              selected = index == tabIndex,
              onClick = {
                tabIndex = index
                if (tabIndex == 1) {
                  onViewTabSelected()
                }
              }
            )
          }
        }
      }
    }
  ) { contentPadding ->
    Surface(modifier = Modifier.padding(contentPadding)) {
      when (tabIndex) {
        0 -> ToolScreen(
          forceSsreCapability = forceSsreCapability,
          onForceSsreToggled = onForceSsreToggled
        )
        1 -> ViewScreen(
          manifest = manifest,
          storageRecords = storageRecords,
          oneOffEvent = oneOffEvent
        )
      }
    }
  }
}

@Composable
fun ToolScreen(
  forceSsreCapability: Boolean,
  onForceSsreToggled: (Boolean) -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
  ) {
    ActionRow("Enqueue StorageSyncJob", "Just a normal syncing operation.") {
      AppDependencies.jobManager.add(StorageSyncJob())
    }

    ActionRow("Enqueue StorageForcePushJob", "Forces your local state over the remote.") {
      AppDependencies.jobManager.add(StorageForcePushJob())
    }

    ActionRow("Reset local manifest", "Makes us think we're not at the latest version (and erases RecordIkm).") {
      SignalStore.storageService.manifest = SignalStorageManifest.EMPTY
    }

    ActionRow("Set initial master key", "Initializes it to something random. Will cause a decryption failure.") {
      SignalStore.svr.masterKeyForInitialDataRestore = MasterKey(Util.getSecretBytes(32))
    }

    ActionRow("Clear initial master key", "Sets it to null.") {
      SignalStore.svr.masterKeyForInitialDataRestore = null
    }

    Rows.ToggleRow(
      text = "Force SSRE2 Capability",
      checked = forceSsreCapability,
      onCheckChanged = onForceSsreToggled
    )
  }
}

@Composable
fun ViewScreen(
  manifest: SignalStorageManifest,
  storageRecords: List<SignalStorageRecord>,
  oneOffEvent: OneOffEvent
) {
  val context = LocalContext.current

  LaunchedEffect(oneOffEvent) {
    when (oneOffEvent) {
      OneOffEvent.None -> Unit
      OneOffEvent.ManifestDecryptionError -> {
        Toast.makeText(context, "Failed to decrypt manifest!", Toast.LENGTH_SHORT).show()
      }
      OneOffEvent.StorageRecordDecryptionError -> {
        Toast.makeText(context, "Failed to decrypt storage records!", Toast.LENGTH_SHORT).show()
      }
      OneOffEvent.ManifestNotFoundError -> {
        Toast.makeText(context, "Manifest not found!", Toast.LENGTH_SHORT).show()
      }
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxHeight().padding(16.dp)
  ) {
    item(key = "manifest") {
      ManifestRow(manifest)
      Dividers.Default()
    }
    storageRecords.forEach { record ->
      item(key = Hex.toStringCondensed(record.id.raw)) {
        StorageRecordRow(record)
        Dividers.Default()
      }
    }
  }
}

@Composable
private fun ManifestRow(manifest: SignalStorageManifest) {
  Column {
    ManifestItemRow("Version", manifest.versionString)
    ManifestItemRow("RecordIkm", manifest.recordIkm?.value?.let { Hex.toStringCondensed(it) } ?: "null")
    ManifestItemRow("Total ID count", manifest.storageIds.size.toString())
    ManifestItemRow("Unknown ID count", manifest.storageIds.filter { it.isUnknown }.size.toString())
  }
}

@Composable
private fun ManifestItemRow(title: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth()) {
    Text(title + ":", fontWeight = FontWeight.Bold)
    Spacer(Modifier.width(6.dp))
    Text(value)
  }
}

@Composable
private fun StorageRecordRow(record: SignalStorageRecord) {
  Row(modifier = Modifier.fillMaxWidth()) {
    when {
      record.proto.account != null -> {
        Column {
          Text("Account", fontWeight = FontWeight.Bold)
          ManifestItemRow("ID", Hex.toStringCondensed(record.id.raw))
        }
      }
      record.proto.contact != null -> {
        Column {
          Text("Contact", fontWeight = FontWeight.Bold)
          ManifestItemRow("ID", Hex.toStringCondensed(record.id.raw))
        }
      }
      record.proto.groupV1 != null -> {
        Column {
          Text("GV1", fontWeight = FontWeight.Bold)
          ManifestItemRow("ID", Hex.toStringCondensed(record.id.raw))
        }
      }
      record.proto.groupV2 != null -> {
        Column {
          Text("GV2", fontWeight = FontWeight.Bold)
          ManifestItemRow("ID", Hex.toStringCondensed(record.id.raw))
        }
      }
      record.proto.callLink != null -> {
        Column {
          Text("Call Link", fontWeight = FontWeight.Bold)
          ManifestItemRow("ID", Hex.toStringCondensed(record.id.raw))
        }
      }
      record.proto.storyDistributionList != null -> {
        Column {
          Text("Distribution List", fontWeight = FontWeight.Bold)
          ManifestItemRow("ID", Hex.toStringCondensed(record.id.raw))
        }
      }
      record.proto.chatFolder != null -> {
        Column {
          Text("Chat Folder", fontWeight = FontWeight.Bold)
          ManifestItemRow("ID", Hex.toStringCondensed(record.id.raw))
        }
      }
      else -> {
        Column {
          Text("Unknown!")
          ManifestItemRow("ID", Hex.toStringCondensed(record.id.raw))
        }
      }
    }
  }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .padding(Rows.defaultPadding())
      .fillMaxWidth()
  ) {
    TextAndLabel(text = title, label = subtitle)
    Spacer(Modifier.width(8.dp))
    RunButton { onClick() }
  }
}

@Composable
private fun RunButton(onClick: () -> Unit) {
  Buttons.LargeTonal(onClick = onClick) {
    Text("Run")
  }
}

@SignalPreview
@Composable
fun ScreenPreview() {
  Previews.Preview {
    Screen(
      forceSsreCapability = true,
      manifest = SignalStorageManifest.EMPTY,
      storageRecords = emptyList(),
      oneOffEvent = OneOffEvent.None
    )
  }
}

@SignalPreview
@Composable
fun ViewScreenPreview() {
  val storageRecords = listOf(
    SignalStorageRecord(
      id = StorageId.forContact(byteArrayOf(1)),
      proto = StorageRecord(
        contact = ContactRecord()
      )
    ),
    SignalStorageRecord(
      id = StorageId.forContact(byteArrayOf(2)),
      proto = StorageRecord(
        contact = ContactRecord()
      )
    )
  )

  Previews.Preview {
    ViewScreen(
      manifest = SignalStorageManifest(
        version = 43,
        sourceDeviceId = 2,
        recordIkm = RecordIkm(ByteArray(32) { 1 }),
        storageIds = storageRecords.map { it.id }
      ),
      storageRecords = storageRecords,
      oneOffEvent = OneOffEvent.None
    )
  }
}
