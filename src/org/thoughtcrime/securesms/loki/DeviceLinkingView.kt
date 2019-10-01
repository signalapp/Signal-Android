package org.thoughtcrime.securesms.loki

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_device_linking.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.loki.api.LokiDeviceLinkingSession
import org.whispersystems.signalservice.loki.api.LokiPairingAuthorisation
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import org.whispersystems.signalservice.loki.utilities.removing05PrefixIfNeeded
import java.io.File
import java.io.FileOutputStream

class DeviceLinkingView private constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, private val mode: Mode, private var delegate: DeviceLinkingViewDelegate) : LinearLayout(context, attrs, defStyleAttr) {
    private lateinit var languageFileDirectory: File
    var dismiss: (() -> Unit)? = null
    var pairingAuthorisation: LokiPairingAuthorisation? = null
        private set

    // region Types
    enum class Mode { Master, Slave }
    // endregion

    // region Lifecycle
    constructor(context: Context, mode: Mode, delegate: DeviceLinkingViewDelegate) : this(context, null, 0, mode, delegate)
    private constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0, Mode.Master, object : DeviceLinkingViewDelegate {}) // Just pass in a dummy mode
    private constructor(context: Context) : this(context, null)

    init {
        setUpLanguageFileDirectory()
        setUpViewHierarchy()
    }

    private fun setUpLanguageFileDirectory() {
        val languages = listOf( "english", "japanese", "portuguese", "spanish" )
        val directory = File(context.applicationInfo.dataDir)
        for (language in languages) {
            val fileName = "$language.txt"
            if (directory.list().contains(fileName)) { continue }
            val inputStream = context.assets.open("mnemonic/$fileName")
            val file = File(directory, fileName)
            val outputStream = FileOutputStream(file)
            val buffer = ByteArray(1024)
            while (true) {
                val count = inputStream.read(buffer)
                if (count < 0) { break }
                outputStream.write(buffer, 0, count)
            }
            inputStream.close()
            outputStream.close()
        }
        languageFileDirectory = directory
    }

    private fun setUpViewHierarchy() {
        inflate(context, R.layout.view_device_linking, this)
        spinner.indeterminateDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        val titleID = when (mode) {
            Mode.Master -> R.string.view_device_linking_title_1
            Mode.Slave -> R.string.view_device_linking_title_2
        }
        titleTextView.text = resources.getString(titleID)
        val explanationID = when (mode) {
            Mode.Master -> R.string.view_device_linking_explanation_1
            Mode.Slave -> R.string.view_device_linking_explanation_2
        }
        explanationTextView.text = resources.getString(explanationID)
        mnemonicTextView.visibility = if (mode == Mode.Master) View.GONE else View.VISIBLE
        if (mode == Mode.Slave) {
            val hexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context).removing05PrefixIfNeeded()
            mnemonicTextView.text = MnemonicCodec(languageFileDirectory).encode(hexEncodedPublicKey).split(" ").slice(0 until 3).joinToString(" ")
        }
        authorizeButton.visibility = View.GONE
        authorizeButton.setOnClickListener { authorize() }
        cancelButton.setOnClickListener { cancel() }
    }
    // endregion

    // region Device Linking
    fun requestUserAuthorization(authorisation: LokiPairingAuthorisation) {
        // To be called when a linking request has been received
        if (mode != Mode.Master) {
            Log.w("Loki", "Received request for pairing authorisation on a slave device")
            return
        }

        if (authorisation.type != LokiPairingAuthorisation.Type.REQUEST) {
            Log.w("Loki", "Received request for GRANT pairing authorisation! It shouldn't be possible!!")
            return
        }

        if (this.pairingAuthorisation != null) {
            Log.e("Loki", "Received request for another pairing authorisation when one was active")
            return
        }

        this.pairingAuthorisation = authorisation

        spinner.visibility = View.GONE
        val titleTextViewLayoutParams = titleTextView.layoutParams as LayoutParams
        titleTextViewLayoutParams.topMargin = toPx(16, resources)
        titleTextView.layoutParams = titleTextViewLayoutParams
        titleTextView.text = resources.getString(R.string.view_device_linking_title_3)
        explanationTextView.text = resources.getString(R.string.view_device_linking_explanation_2)
        mnemonicTextView.visibility = View.VISIBLE
        val hexEncodedPublicKey = authorisation.secondaryDevicePubKey.removing05PrefixIfNeeded()
        mnemonicTextView.text = MnemonicCodec(languageFileDirectory).encode(hexEncodedPublicKey).split(" ").slice(0 until 3).joinToString(" ")
        authorizeButton.visibility = View.VISIBLE
    }

    private fun authorize() {
        if (pairingAuthorisation == null || mode != Mode.Master ) { return; }

        // Pass authorisation to delegate and only dismiss if it succeeded
        if (delegate.authorise(pairingAuthorisation!!)) {
            delegate.handleDeviceLinkAuthorized()
            dismiss?.invoke()
        }
    }

    fun onDeviceLinkAuthorized(authorisation: LokiPairingAuthorisation) {
        // To be called when a device link was accepted by the primary device
        if (mode == Mode.Master || authorisation != pairingAuthorisation) { return }

        spinner.visibility = View.GONE
        val titleTextViewLayoutParams = titleTextView.layoutParams as LayoutParams
        titleTextViewLayoutParams.topMargin = toPx(8, resources)
        titleTextView.layoutParams = titleTextViewLayoutParams
        titleTextView.text = resources.getString(R.string.view_device_linking_title_4)
        val explanationTextViewLayoutParams = explanationTextView.layoutParams as LayoutParams
        explanationTextViewLayoutParams.bottomMargin = toPx(12, resources)
        explanationTextView.layoutParams = explanationTextViewLayoutParams
        explanationTextView.text = resources.getString(R.string.view_device_linking_explanation_3)
        titleTextView.text = resources.getString(R.string.view_device_linking_title_4)
        mnemonicTextView.visibility = View.GONE
        buttonContainer.visibility = View.GONE

        Handler().postDelayed({
            delegate.handleDeviceLinkAuthorized()
            dismiss?.invoke()
        }, 4000)
    }
    // endregion

    // region Interaction
    private fun cancel() {
        delegate.handleDeviceLinkingDialogDismissed()
        dismiss?.invoke()
    }
    // endregion
}