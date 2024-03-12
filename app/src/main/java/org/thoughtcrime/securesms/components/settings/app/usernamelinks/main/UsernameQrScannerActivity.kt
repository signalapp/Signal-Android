/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalPermissionsApi::class)

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.signal.core.ui.Dialogs
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.permissions.PermissionCompat
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.DynamicTheme

/**
 * Prompts the user to scan a username QR code. Uses the activity result to communicate the recipient that was found, or null if no valid usernames were scanned.
 * See [Contract].
 */
class UsernameQrScannerActivity : AppCompatActivity() {

  companion object {
    private const val KEY_RECIPIENT_ID = "recipient_id"
  }

  private val viewModel: UsernameQrScannerViewModel by viewModels()
  private val disposables = LifecycleDisposable()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    disposables.bindTo(this)

    val galleryLauncher = registerForActivityResult(UsernameQrImageSelectionActivity.Contract()) { uri ->
      if (uri != null) {
        viewModel.onQrImageSelected(this, uri)
      }
    }

    setContent {
      val galleryPermissionState: MultiplePermissionsState = rememberMultiplePermissionsState(permissions = PermissionCompat.forImages().toList()) { grants ->
        if (grants.values.all { it }) {
          galleryLauncher.launch(Unit)
        } else {
          Toast.makeText(this, R.string.ChatWallpaperPreviewActivity__viewing_your_gallery_requires_the_storage_permission, Toast.LENGTH_SHORT).show()
        }
      }

      val state by viewModel.state

      SignalTheme(isDarkMode = DynamicTheme.isDarkTheme(LocalContext.current)) {
        Content(
          lifecycleOwner = this,
          diposables = disposables.disposables,
          state = state,
          galleryPermissionsState = galleryPermissionState,
          onQrScanned = { url -> viewModel.onQrScanned(url) },
          onQrResultHandled = {
            finish()
          },
          onOpenGalleryClicked = {
            if (galleryPermissionState.allPermissionsGranted) {
              galleryLauncher.launch(Unit)
            } else {
              galleryPermissionState.launchMultiplePermissionRequest()
            }
          },
          onRecipientFound = { recipient ->
            val intent = Intent().apply {
              putExtra(KEY_RECIPIENT_ID, recipient.id)
            }
            setResult(RESULT_OK, intent)
            finish()
          },
          onBackNavigationPressed = {
            finish()
          }
        )
      }
    }
  }

  class Contract : ActivityResultContract<Unit, RecipientId?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
      return Intent(context, UsernameQrScannerActivity::class.java)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): RecipientId? {
      return intent?.getParcelableExtraCompat(KEY_RECIPIENT_ID, RecipientId::class.java)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Content(
  lifecycleOwner: LifecycleOwner,
  diposables: CompositeDisposable,
  state: UsernameQrScannerViewModel.ScannerState,
  galleryPermissionsState: MultiplePermissionsState,
  onQrScanned: (String) -> Unit,
  onQrResultHandled: () -> Unit,
  onOpenGalleryClicked: () -> Unit,
  onRecipientFound: (Recipient) -> Unit,
  onBackNavigationPressed: () -> Unit
) {
  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = {},
        navigationIcon = {
          IconButton(
            onClick = onBackNavigationPressed
          ) {
            Icon(
              painter = painterResource(R.drawable.symbol_x_24),
              contentDescription = stringResource(android.R.string.cancel)
            )
          }
        }
      )
    }
  ) { contentPadding ->
    UsernameQrScanScreen(
      lifecycleOwner = lifecycleOwner,
      disposables = diposables,
      qrScanResult = state.qrScanResult,
      onQrCodeScanned = onQrScanned,
      onQrResultHandled = onQrResultHandled,
      onOpenGalleryClicked = onOpenGalleryClicked,
      onRecipientFound = onRecipientFound,
      modifier = Modifier.padding(contentPadding)
    )

    if (state.indeterminateProgress) {
      Dialogs.IndeterminateProgressDialog()
    }
  }
}
