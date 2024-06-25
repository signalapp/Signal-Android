package org.signal.contactstest

import android.Manifest
import android.accounts.Account
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.PhoneNumberUtils
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.signal.contacts.ContactLinkConfiguration
import org.signal.contacts.SystemContactsRepository
import org.signal.core.util.concurrent.SimpleTask
import org.signal.core.util.logging.Log

class MainActivity : AppCompatActivity() {

  companion object {
    private val TAG = Log.tag(MainActivity::class.java)
    private const val PERMISSION_CODE = 7
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_main)

    findViewById<Button>(R.id.contact_list_button).setOnClickListener { v ->
      if (hasPermission(Manifest.permission.READ_CONTACTS) && hasPermission(Manifest.permission.WRITE_CONTACTS)) {
        startActivity(Intent(this, ContactListActivity::class.java))
      } else {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS), PERMISSION_CODE)
      }
    }

    findViewById<Button>(R.id.contact_lookup_button).setOnClickListener { v ->
      if (hasPermission(Manifest.permission.READ_CONTACTS) && hasPermission(Manifest.permission.WRITE_CONTACTS)) {
        startActivity(Intent(this, ContactLookupActivity::class.java))
      } else {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS), PERMISSION_CODE)
      }
    }

    findViewById<Button>(R.id.link_contacts_button).setOnClickListener { v ->
      val startTime = System.currentTimeMillis()
      if (hasPermission(Manifest.permission.READ_CONTACTS) && hasPermission(Manifest.permission.WRITE_CONTACTS)) {
        SimpleTask.run({
          val allE164s: Set<String> = SystemContactsRepository.getAllDisplayNumbers(this).map { PhoneNumberUtils.formatNumberToE164(it, "US") }.toSet()
          val account: Account = SystemContactsRepository.getOrCreateSystemAccount(this, BuildConfig.APPLICATION_ID, "Contact Test") ?: return@run false

          SystemContactsRepository.addMessageAndCallLinksToContacts(
            context = this,
            config = buildLinkConfig(account),
            targetE164s = allE164s,
            removeIfMissing = true
          )

          return@run true
        }, { success ->
          if (success) {
            Toast.makeText(this, "Success! Took ${System.currentTimeMillis() - startTime} ms", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(this, "Failed to create account!", Toast.LENGTH_SHORT).show()
          }
        })
      } else {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS), PERMISSION_CODE)
      }
    }

    findViewById<Button>(R.id.unlink_contact_button).setOnClickListener { v ->
      val startTime = System.currentTimeMillis()
      if (hasPermission(Manifest.permission.READ_CONTACTS) && hasPermission(Manifest.permission.WRITE_CONTACTS)) {
        SimpleTask.run({
          val account: Account = SystemContactsRepository.getOrCreateSystemAccount(this, BuildConfig.APPLICATION_ID, "Contact Test") ?: return@run false

          SystemContactsRepository.addMessageAndCallLinksToContacts(
            context = this,
            config = buildLinkConfig(account),
            targetE164s = emptySet(),
            removeIfMissing = true
          )

          return@run true
        }, { success ->
          if (success) {
            Toast.makeText(this, "Success! Took ${System.currentTimeMillis() - startTime} ms", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(this, "Failed to create account!", Toast.LENGTH_SHORT).show()
          }
        })
      } else {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS), PERMISSION_CODE)
      }
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    if (requestCode == PERMISSION_CODE) {
      if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
        startActivity(Intent(this, ContactListActivity::class.java))
      } else {
        Toast.makeText(this, "You must provide permissions to continue.", Toast.LENGTH_SHORT).show()
      }
    }

    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
  }

  private fun hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
  }

  private fun buildLinkConfig(account: Account): ContactLinkConfiguration {
    return ContactLinkConfiguration(
      account = account,
      appName = "Contact Test",
      messagePrompt = { "(Test) Message $it" },
      callPrompt = { "(Test) Call $it" },
      e164Formatter = { PhoneNumberUtils.formatNumberToE164(it, "US") },
      messageMimetype = "vnd.android.cursor.item/vnd.org.signal.contacts.test.message",
      callMimetype = "vnd.android.cursor.item/vnd.org.signal.contacts.test.call",
      syncTag = "__TEST",
      videoCallMimetype = "",
      videoCallPrompt = { "" }
    )
  }
}
