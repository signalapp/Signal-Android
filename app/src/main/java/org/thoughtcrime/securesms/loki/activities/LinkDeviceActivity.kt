package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_link_device.*
import kotlinx.android.synthetic.main.conversation_activity.*
import kotlinx.android.synthetic.main.fragment_recovery_phrase.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.KeyHelper
import org.session.libsignal.crypto.MnemonicCodec
import org.session.libsignal.utilities.hexEncodedPublicKey
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.loki.fragments.ScanQRCodeWrapperFragment
import org.thoughtcrime.securesms.loki.fragments.ScanQRCodeWrapperFragmentDelegate
import org.session.libsession.utilities.KeyPairUtilities
import org.thoughtcrime.securesms.loki.utilities.MnemonicUtilities
import org.thoughtcrime.securesms.loki.utilities.push
import org.thoughtcrime.securesms.loki.utilities.setUpActionBarSessionLogo

class LinkDeviceActivity : BaseActionBarActivity(), ScanQRCodeWrapperFragmentDelegate {
    private val adapter = LinkDeviceActivityAdapter(this)
    private var restoreJob: Job? = null

    override fun onBackPressed() {
        if (restoreJob?.isActive == true) return // Don't allow going back with a pending job
        super.onBackPressed()
    }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpActionBarSessionLogo()
        TextSecurePreferences.apply {
            setHasViewedSeed(this@LinkDeviceActivity, true)
            setConfigurationMessageSynced(this@LinkDeviceActivity, false)
            setRestorationTime(this@LinkDeviceActivity, System.currentTimeMillis())
            setLastProfileUpdateTime(this@LinkDeviceActivity, 0)
        }
        setContentView(R.layout.activity_link_device)
        viewPager.adapter = adapter
        tabLayout.setupWithViewPager(viewPager)
    }
    // endregion

    // region Interaction
    override fun handleQRCodeScanned(mnemonic: String) {
        try {
            val seed = Hex.fromStringCondensed(mnemonic)
            continueWithSeed(seed)
        } catch (e: Exception) {
            Log.e("Loki","Error getting seed from QR code", e)
            Toast.makeText(this, "An error occurred.", Toast.LENGTH_LONG).show()
        }
    }

    fun continueWithMnemonic(mnemonic: String) {
        val loadFileContents: (String) -> String = { fileName ->
            MnemonicUtilities.loadFileContents(this, fileName)
        }
        try {
            val hexEncodedSeed = MnemonicCodec(loadFileContents).decode(mnemonic)
            val seed = Hex.fromStringCondensed(hexEncodedSeed)
            continueWithSeed(seed)
        } catch (error: Exception) {
            val message = if (error is MnemonicCodec.DecodingError) {
                error.description
            } else {
                "An error occurred."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun continueWithSeed(seed: ByteArray) {

        // only have one sync job running at a time (prevent QR from trying to spawn a new job)
        if (restoreJob?.isActive == true) return

        restoreJob = lifecycleScope.launch {
            // RestoreActivity handles seed this way
            val keyPairGenerationResult = KeyPairUtilities.generate(seed)
            val x25519KeyPair = keyPairGenerationResult.x25519KeyPair
            KeyPairUtilities.store(this@LinkDeviceActivity, seed, keyPairGenerationResult.ed25519KeyPair, x25519KeyPair)
            val userHexEncodedPublicKey = x25519KeyPair.hexEncodedPublicKey
            val registrationID = KeyHelper.generateRegistrationId(false)
            TextSecurePreferences.setLocalRegistrationId(this@LinkDeviceActivity, registrationID)
            TextSecurePreferences.setLocalNumber(this@LinkDeviceActivity, userHexEncodedPublicKey)
            TextSecurePreferences.setRestorationTime(this@LinkDeviceActivity, System.currentTimeMillis())
            TextSecurePreferences.setHasViewedSeed(this@LinkDeviceActivity, true)

            loader.isVisible = true
            val snackBar = Snackbar.make(containerLayout, R.string.activity_link_device_skip_prompt,Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.registration_activity__skip) { register(true) }

            val skipJob = launch {
                delay(30_000L)
                snackBar.show()
                // show a dialog or something saying do you want to skip this bit?
            }
            // start polling and wait for updated message
            ApplicationContext.getInstance(this@LinkDeviceActivity).apply {
                startPollingIfNeeded()
            }
            TextSecurePreferences.events.filter { it == TextSecurePreferences.CONFIGURATION_SYNCED }.collect {
                // handle we've synced
                snackBar.dismiss()
                skipJob.cancel()
                register(false)
            }

            loader.isVisible = false
        }
    }

    private fun register(skipped: Boolean) {
        restoreJob?.cancel()
        loader.isVisible = false
        TextSecurePreferences.setLastConfigurationSyncTime(this, System.currentTimeMillis())
        val intent = Intent(this@LinkDeviceActivity, if (skipped) DisplayNameActivity::class.java else PNModeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        push(intent)
    }
    // endregion
}

// region Adapter
private class LinkDeviceActivityAdapter(private val activity: LinkDeviceActivity) : FragmentPagerAdapter(activity.supportFragmentManager) {
    val recoveryPhraseFragment = RecoveryPhraseFragment()

    override fun getCount(): Int {
        return 2
    }

    override fun getItem(index: Int): Fragment {
        return when (index) {
            0 -> recoveryPhraseFragment
            1 -> {
                val result = ScanQRCodeWrapperFragment()
                result.delegate = activity
                result.message = "Navigate to Settings → Recovery Phrase on your other device to show your QR code."
                result
            }
            else -> throw IllegalStateException()
        }
    }

    override fun getPageTitle(index: Int): CharSequence {
        return when (index) {
            0 -> "Recovery Phrase"
            1 -> "Scan QR Code"
            else -> throw IllegalStateException()
        }
    }
}
// endregion

// region Recovery Phrase Fragment
class RecoveryPhraseFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_recovery_phrase, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mnemonicEditText.imeOptions = EditorInfo.IME_ACTION_DONE or 16777216 // Always use incognito keyboard
        mnemonicEditText.setRawInputType(InputType.TYPE_CLASS_TEXT)
        mnemonicEditText.setOnEditorActionListener { v, actionID, _ ->
            if (actionID == EditorInfo.IME_ACTION_DONE) {
                val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                handleContinueButtonTapped()
                true
            } else {
                false
            }
        }
        continueButton.setOnClickListener { handleContinueButtonTapped() }
    }

    private fun handleContinueButtonTapped() {
        val mnemonic = mnemonicEditText.text?.trim().toString()
        (requireActivity() as LinkDeviceActivity).continueWithMnemonic(mnemonic)
    }
}
// endregion
