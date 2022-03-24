package org.signal.contactstest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.signal.core.util.logging.Log

class MainActivity : AppCompatActivity() {

  companion object {
    private val TAG = Log.tag(MainActivity::class.java)
    private const val PERMISSION_CODE = 7
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_main)

    if (hasPermission(Manifest.permission.READ_CONTACTS) && hasPermission(Manifest.permission.WRITE_CONTACTS)) {
      Log.i(TAG, "Already have permission.")
      startActivity(Intent(this, ContactsActivity::class.java))
      finish()
      return
    }

    findViewById<Button>(R.id.permission_button).setOnClickListener { v ->
      requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS), PERMISSION_CODE)
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    if (requestCode == PERMISSION_CODE) {
      if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
        startActivity(Intent(this, ContactsActivity::class.java))
        finish()
      } else {
        Toast.makeText(this, "You must provide permissions to continue.", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
  }
}
