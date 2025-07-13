
package org.thoughtcrime.securesms.conversation

import android.os.Bundle
import android.widget.Toast
import org.thoughtcrime.securesms.ConversationActivity
import org.thoughtcrime.securesms.crypto.FipsBridge
import org.thoughtcrime.securesms.crypto.fips.FipsHandshakeFailedDialogFragment
import org.thoughtcrime.securesms.util.MdmPolicyManager

/**
 * FIPS-aware conversation activity that ensures secure messaging compliance
 * with FIPS 140-2 requirements and enterprise policies.
 */
class FipsConversationActivity : ConversationActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check FIPS compliance before allowing conversation
        if (!validateFipsCompliance()) {
            showFipsComplianceError()
            return
        }

        // Additional FIPS-specific initialization
        initializeFipsMode()
    }

    private fun validateFipsCompliance(): Boolean {
        // Check if FIPS mode is required by MDM policy
        val mdmManager = MdmPolicyManager.getInstance(this)
        if (mdmManager.isFipsRequired() && !FipsBridge.isFipsMode()) {
            return false
        }

        // Verify FIPS cryptographic modules are available
        if (!FipsBridge.areFipsModulesAvailable()) {
            return false
        }

        return true
    }

    private fun showFipsComplianceError() {
        Toast.makeText(
            this,
            "FIPS compliance required for secure messaging",
            Toast.LENGTH_LONG
        ).show()
        
        // Show detailed FIPS error dialog
        val dialog = FipsHandshakeFailedDialogFragment()
        dialog.show(supportFragmentManager, "fips_error")
        
        // Close activity after showing error
        finish()
    }

    private fun initializeFipsMode() {
        // Initialize FIPS-specific conversation features
        if (FipsBridge.isFipsMode()) {
            // Enable additional FIPS logging and monitoring
            FipsBridge.enableFipsLogging(true)
            
            // Verify ongoing FIPS compliance
            FipsBridge.verifyFipsIntegrity()
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Re-validate FIPS compliance on resume
        if (!validateFipsCompliance()) {
            showFipsComplianceError()
        }
    }
}
