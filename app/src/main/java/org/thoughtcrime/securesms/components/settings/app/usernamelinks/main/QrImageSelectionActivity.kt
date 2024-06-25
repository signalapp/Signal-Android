/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.v2.gallery.MediaGalleryFragment

/**
 * Select qr code from gallery instead of using camera. Used in usernames and when linking devices
 */
class QrImageSelectionActivity : AppCompatActivity(), MediaGalleryFragment.Callbacks {

  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.attachBaseContext(newBase)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
    setContentView(R.layout.username_qr_image_selection_activity)
  }

  @SuppressLint("LogTagInlined")
  override fun onMediaSelected(media: Media) {
    setResult(RESULT_OK, Intent().setData(media.uri))
    finish()
  }

  override fun onToolbarNavigationClicked() {
    setResult(RESULT_CANCELED)
    finish()
  }

  override fun isCameraEnabled() = false
  override fun isMultiselectEnabled() = false

  class Contract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
      return Intent(context, QrImageSelectionActivity::class.java)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
      return if (resultCode == RESULT_OK) {
        intent?.data
      } else {
        null
      }
    }
  }
}
