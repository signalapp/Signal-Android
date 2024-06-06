/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalPermissionsApi::class)

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import android.Manifest
import android.annotation.SuppressLint
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
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.signal.core.ui.Dialogs
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.main.UsernameQrScannerActivity.Contract
import org.thoughtcrime.securesms.permissions.PermissionCompat
import org.thoughtcrime.securesms.permissions.Permissions
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

  @SuppressLint("MissingSuperCall")
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    disposables.bindTo(this)

    val galleryLauncher = registerForActivityResult(QrImageSelectionActivity.Contract()) { uri ->
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

      val cameraPermissionState: PermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
      val state by viewModel.state

      SignalTheme(isDarkMode = DynamicTheme.isDarkTheme(LocalContext.current)) {
        Content(
          lifecycleOwner = this,
          diposables = disposables.disposables,
          state = state,
          galleryPermissionsState = galleryPermissionState,
          cameraPermissionState = cameraPermissionState,
          onQrScanned = { url -> viewModel.onQrScanned(url) },
          onQrResultHandled = {
            finish()
          },
          onOpenCameraClicked = { askCameraPermissions() },
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

  private fun askCameraPermissions() {
    Permissions.with(this)
      .request(Manifest.permission.CAMERA)
      .ifNecessary()
      .withPermanentDenialDialog(getString(R.string.CameraXFragment_signal_needs_camera_access_scan_qr_code), null, R.string.CameraXFragment_allow_access_camera, R.string.CameraXFragment_to_scan_qr_codes, supportFragmentManager)
      .onAnyDenied { Toast.makeText(this, R.string.CameraXFragment_signal_needs_camera_access_scan_qr_code, Toast.LENGTH_LONG).show() }
      .execute()
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
  cameraPermissionState: PermissionState,
  onQrScanned: (String) -> Unit,
  onQrResultHandled: () -> Unit,
  onOpenCameraClicked: () -> Unit,
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
      onOpenCameraClicked = onOpenCameraClicked,
      onOpenGalleryClicked = onOpenGalleryClicked,
      onRecipientFound = onRecipientFound,
      hasCameraPermission = cameraPermissionState.status.isGranted,
      modifier = Modifier.padding(contentPadding)
    )

    if (state.indeterminateProgress) {
      Dialogs.IndeterminateProgressDialog()
    }
  }
}
