package org.thoughtcrime.securesms.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView.OnEditorActionListener
import android.widget.Toast
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityDisplayNameBinding
import org.session.libsession.utilities.SSKEnvironment.ProfileManagerProtocol
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.util.push
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo
import org.session.libsession.utilities.TextSecurePreferences

class DisplayNameActivity : BaseActionBarActivity() {
    private lateinit var binding: ActivityDisplayNameBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo()
        binding = ActivityDisplayNameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        with(binding) {
            displayNameEditText.imeOptions = displayNameEditText.imeOptions or 16777216 // Always use incognito keyboard
            displayNameEditText.setOnEditorActionListener(
                    OnEditorActionListener { _, actionID, event ->
                        if (actionID == EditorInfo.IME_ACTION_SEARCH ||
                            actionID == EditorInfo.IME_ACTION_DONE ||
                           (event.action == KeyEvent.ACTION_DOWN &&
                            event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                            register()
                            return@OnEditorActionListener true
                        }
                        false
                    })
            registerButton.setOnClickListener { register() }
        }
    }

    private fun register() {
        val displayName = binding.displayNameEditText.text.toString().trim()
        if (displayName.isEmpty()) {
            return Toast.makeText(this, R.string.activity_display_name_display_name_missing_error, Toast.LENGTH_SHORT).show()
        }
        if (displayName.toByteArray().size > ProfileManagerProtocol.Companion.NAME_PADDED_LENGTH) {
            return Toast.makeText(this, R.string.activity_display_name_display_name_too_long_error, Toast.LENGTH_SHORT).show()
        }
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.displayNameEditText.windowToken, 0)
        TextSecurePreferences.setProfileName(this, displayName)
        val intent = Intent(this, PNModeActivity::class.java)
        push(intent)
    }
}