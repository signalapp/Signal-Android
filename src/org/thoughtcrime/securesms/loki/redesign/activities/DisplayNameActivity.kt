package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView.OnEditorActionListener
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_display_name.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.loki.redesign.utilities.push
import org.thoughtcrime.securesms.loki.redesign.utilities.setUpActionBarSessionLogo
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.crypto.ProfileCipher

class DisplayNameActivity : BaseActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo()
        setContentView(R.layout.activity_display_name)
        displayNameEditText.imeOptions = displayNameEditText.imeOptions or 16777216 // Always use incognito keyboard
        displayNameEditText.setOnEditorActionListener(
                OnEditorActionListener { _, actionID, event ->
                    if (actionID == EditorInfo.IME_ACTION_SEARCH ||
                        actionID == EditorInfo.IME_ACTION_DONE ||
                       (event.action == KeyEvent.ACTION_DOWN &&
                        event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                        this.register()
                        return@OnEditorActionListener true
                    }
                    false
                })
        registerButton.setOnClickListener { register() }
    }

    private fun register() {
        val displayName = displayNameEditText.text.toString().trim()
        if (displayName.isEmpty()) {
            return Toast.makeText(this, "Please pick a display name", Toast.LENGTH_SHORT).show()
        }
        if (!displayName.matches(Regex("[a-zA-Z0-9_]+"))) {
            return Toast.makeText(this, "Please pick a display name that consists of only a-z, A-Z, 0-9 and _ characters", Toast.LENGTH_SHORT).show()
        }
        if (displayName.toByteArray().size > ProfileCipher.NAME_PADDED_LENGTH) {
            return Toast.makeText(this, "Please pick a shorter display name", Toast.LENGTH_SHORT).show()
        }
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(displayNameEditText.windowToken, 0)
        TextSecurePreferences.setProfileName(this, displayName)
        val intent = Intent(this, PNModeActivity::class.java)
        push(intent)
    }
}