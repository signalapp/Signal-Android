package org.thoughtcrime.securesms.conversation

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R // Assuming R file is available for resources
import org.thoughtcrime.securesms.database.model.SessionRecord // Placeholder
import org.thoughtcrime.securesms.recipients.Recipient // Placeholder

/**
 * An Activity for displaying a conversation with a contact.
 *
 * This class is a critical part of the UI implementation for the FIPS-compliant
 * client. It demonstrates how to create a dynamic user interface that changes its
 * appearance based on the cryptographic mode of the session.
 *
 * Key Features:
 * - Observes the `SessionMode` of the current conversation.
 * - Dynamically updates the Toolbar color to provide a persistent visual cue.
 * - Displays a "FIPS SECURE" icon and label in the header for FIPS-compliant chats.
 * - Provides a clear and unambiguous user experience, fulfilling the requirements
 * of the MDM-enabled architecture plan.
 */
class FipsConversationActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var fipsStatusView: TextView
    private lateinit var recipientNameView: TextView

    // In a real app, this would be determined by the recipientId passed in the Intent
    // and loaded from a ViewModel or database.
    private lateinit var currentSession: SessionRecord
    private lateinit var currentRecipient: Recipient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fips_conversation) // Assume a layout file exists

        toolbar = findViewById(R.id.toolbar)
        fipsStatusView = findViewById(R.id.fips_status_label)
        recipientNameView = findViewById(R.id.recipient_name_view)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // --- Load Session and Recipient Data (Placeholder) ---
        // In a real app, you would get the RecipientId from the Intent's extras
        // and use it to load the corresponding data.
        loadDummyData()

        // Update the UI based on the loaded session's mode.
        updateUiForSessionMode(currentSession.getSessionMode())
    }

    /**
     * Updates the conversation screen's UI elements based on the session's security mode.
     *
     * @param mode The current SessionMode (STANDARD or FIPS).
     */
    private fun updateUiForSessionMode(mode: SessionRecord.SessionMode) {
        when (mode) {
            SessionRecord.SessionMode.FIPS -> {
                // Apply the "FIPS Secure" theme
                toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.fips_secure_blue))
                window.statusBarColor = ContextCompat.getColor(this, R.color.fips_secure_blue_dark)

                recipientNameView.setTextColor(ContextCompat.getColor(this, R.color.fips_text_color_light))
                
                fipsStatusView.visibility = View.VISIBLE
                fipsStatusView.text = "🛡️ FIPS SECURE" // In a real app, use string resources
                fipsStatusView.setTextColor(ContextCompat.getColor(this, R.color.fips_text_color_light))
            }
            SessionRecord.SessionMode.STANDARD, SessionRecord.SessionMode.FIPS_NEGOTIATING -> {
                // Apply the default "Standard" theme
                toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.standard_primary))
                window.statusBarColor = ContextCompat.getColor(this, R.color.standard_primary_dark)
                
                recipientNameView.setTextColor(ContextCompat.getColor(this, R.color.standard_text_color))

                fipsStatusView.visibility = View.GONE
            }
        }
    }

    /**
     * Loads placeholder data to simulate having a real session and recipient.
     */
    private fun loadDummyData() {
        val recipientId = intent.getLongExtra("RECIPIENT_ID", 1L)
        
        // Simulate loading a session record. In a real app, this would come from the database.
        // We'll alternate based on the dummy ID for demonstration purposes.
        val mode = if (recipientId % 2 == 0L) SessionRecord.SessionMode.FIPS else SessionRecord.SessionMode.STANDARD
        
        currentSession = SessionRecord().apply {
            setSessionMode(mode)
        }
        
        currentRecipient = Recipient().apply {
            name = "Alice" // Dummy name
        }
        
        recipientNameView.text = currentRecipient.name
    }

    // --- Dummy Classes for Compilation ---
    // These would be replaced by the actual classes in the Signal project.
    
    // In a real app, these would be in their own files.
    // We assume R.layout.activity_fips_conversation, R.id.*, and R.color.* exist.
    object R {
        object layout { const val activity_fips_conversation = 0 }
        object id {
            const val toolbar = 0
            const val fips_status_label = 0
            const val recipient_name_view = 0
        }
        object color {
            const val fips_secure_blue = 0
            const val fips_secure_blue_dark = 0
            const val fips_text_color_light = 0
            const val standard_primary = 0
            const val standard_primary_dark = 0
            const val standard_text_color = 0
        }
    }

    // Placeholder for the real Recipient class
    class Recipient {
        var name: String = ""
    }
}

// Placeholder for the real SessionRecord class
class SessionRecord {
    enum class SessionMode { STANDARD, FIPS_NEGOTIATING, FIPS }
    private var mode: SessionMode = SessionMode.STANDARD
    
    fun getSessionMode(): SessionMode = this.mode
    fun setSessionMode(mode: SessionMode) {
        this.mode = mode
    }
}
