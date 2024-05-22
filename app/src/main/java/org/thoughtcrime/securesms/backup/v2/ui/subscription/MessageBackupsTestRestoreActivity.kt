/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dividers
import org.signal.core.util.getLength
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.ProfileUploadJob
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.RegistrationUtil

class MessageBackupsTestRestoreActivity : BaseActivity() {
  companion object {
    fun getIntent(context: Context): Intent {
      return Intent(context, MessageBackupsTestRestoreActivity::class.java)
    }
  }

  private val viewModel: MessageBackupsTestRestoreViewModel by viewModels()
  private lateinit var importFileLauncher: ActivityResultLauncher<Intent>

  private fun onPlaintextClicked() {
    viewModel.onPlaintextToggled()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    importFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        result.data?.data?.let { uri ->
          contentResolver.getLength(uri)?.let { length ->
            viewModel.import(length) { contentResolver.openInputStream(uri)!! }
          }
        } ?: Toast.makeText(this, "No URI selected", Toast.LENGTH_SHORT).show()
      }
    }

    setContent {
      val state by viewModel.state
      Surface {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
        ) {
          Buttons.LargePrimary(
            onClick = this@MessageBackupsTestRestoreActivity::restoreFromServer,
            enabled = !state.importState.inProgress
          ) {
            Text("Restore")
          }

          Spacer(modifier = Modifier.height(8.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically
          ) {
            StateLabel(text = "Plaintext?")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
              checked = state.plaintext,
              onCheckedChange = { onPlaintextClicked() }
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          Buttons.LargePrimary(
            onClick = {
              val intent = Intent().apply {
                action = Intent.ACTION_GET_CONTENT
                type = "application/octet-stream"
                addCategory(Intent.CATEGORY_OPENABLE)
              }

              importFileLauncher.launch(intent)
            },
            enabled = !state.importState.inProgress
          ) {
            Text("Import from file")
          }

          Spacer(modifier = Modifier.height(8.dp))

          Dividers.Default()

          Buttons.LargeTonal(
            onClick = { continueRegistration() },
            enabled = !state.importState.inProgress
          ) {
            Text("Continue Reg Flow")
          }
        }
      }
      if (state.importState == MessageBackupsTestRestoreViewModel.ImportState.RESTORED) {
        SideEffect {
          RegistrationUtil.maybeMarkRegistrationComplete()
          AppDependencies.jobManager.add(ProfileUploadJob())
          startActivity(MainActivity.clearTop(this))
        }
      }
    }
  }

  private fun restoreFromServer() {
    viewModel.restore()
  }

  private fun continueRegistration() {
    if (Recipient.self().profileName.isEmpty || !AvatarHelper.hasAvatar(this, Recipient.self().id)) {
      val main = MainActivity.clearTop(this)
      val profile = CreateProfileActivity.getIntentForUserProfile(this)
      profile.putExtra("next_intent", main)
      startActivity(profile)
    } else {
      RegistrationUtil.maybeMarkRegistrationComplete()
      AppDependencies.jobManager.add(ProfileUploadJob())
      startActivity(MainActivity.clearTop(this))
    }
    finish()
  }

  @Composable
  private fun StateLabel(text: String) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      textAlign = TextAlign.Center
    )
  }
}
