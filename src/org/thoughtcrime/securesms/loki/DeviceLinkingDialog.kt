package org.thoughtcrime.securesms.loki

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Handler
import android.support.v7.app.AlertDialog
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_device_linking.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import org.whispersystems.signalservice.loki.utilities.removing05PrefixIfNeeded
import java.io.File
import java.io.FileOutputStream

object DeviceLinkingDialog {

    fun show(context: Context, mode: DeviceLinkingView.Mode) {
        val view = DeviceLinkingView(context, mode)
        val dialog = AlertDialog.Builder(context).setView(view).show()
        view.dismiss = { dialog.dismiss() }
    }
}

class DeviceLinkingView private constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, private val mode: Mode) : LinearLayout(context, attrs, defStyleAttr) {
    private var delegate: DeviceLinkingDialogDelegate? = null
    private lateinit var languageFileDirectory: File
    var dismiss: (() -> Unit)? = null

    // region Types
    enum class Mode { Master, Slave }
    // endregion

    // region Lifecycle
    constructor(context: Context, mode: Mode) : this(context, null, 0, mode)
    private constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0, Mode.Master) // Just pass in a dummy mode
    private constructor(context: Context) : this(context, null)

    init {
        if (mode == Mode.Slave) {
            if (delegate == null) { throw IllegalStateException("Missing delegate for device linking dialog in slave mode.") }
        }
        setUpLanguageFileDirectory()
        setUpViewHierarchy()
        when (mode) {
            Mode.Master -> Log.d("Loki", "TODO: DeviceLinkingSession.startListeningForLinkingRequests(this)")
            Mode.Slave -> Log.d("Loki", "TODO: DeviceLinkingSession.startListeningForAuthorization(this)")
        }
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
        cancelButton.setOnClickListener { cancel() }
    }
    // endregion

    // region Device Linking
    private fun requestUserAuthorization() { // TODO: deviceLink parameter
        // To be called by DeviceLinkingSession when a linking request has been received
        // TODO: this.deviceLink = deviceLink
        spinner.visibility = View.GONE
        val titleTextViewLayoutParams = titleTextView.layoutParams as LayoutParams
        titleTextViewLayoutParams.topMargin = toPx(16, resources)
        titleTextView.layoutParams = titleTextViewLayoutParams
        titleTextView.text = resources.getString(R.string.view_device_linking_title_3)
        explanationTextView.text = resources.getString(R.string.view_device_linking_explanation_2)
        mnemonicTextView.visibility = View.VISIBLE
        val hexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context).removing05PrefixIfNeeded() // TODO: deviceLink.slave.hexEncodedPublicKey.removing05PrefixIfNeeded()
        mnemonicTextView.text = MnemonicCodec(languageFileDirectory).encode(hexEncodedPublicKey).split(" ").slice(0 until 3).joinToString(" ")
        authorizeButton.visibility = View.VISIBLE
    }

    private fun authorizeDeviceLink() {
        // TODO: val deviceLink = this.deviceLink!!
        // TODO: val linkingAuthorizationMessage = DeviceLinkingUtilities.getLinkingAuthorizationMessage(deviceLink)
        // TODO: Send the linking authorization message
        // TODO: val session = DeviceLinkingSession.current!!
        // TODO: session.stopListeningForLinkingRequests()
        // TODO: session.markLinkingRequestAsProcessed()
        dismiss?.invoke()
        // TODO: val master = DeviceLink.Device(deviceLink.master.hexEncodedPublicKey, linkingAuthorizationMessage.masterSignature)
        // TODO: val signedDeviceLink = DeviceLink(master, deviceLink.slave)
        // TODO: LokiStorageAPI.addDeviceLink(signedDeviceLink).fail { error ->
        // TODO:     Log.d("Loki", "Failed to add device link due to error: $error.")
        // TODO: }
    }

    private fun handleDeviceLinkAuthorized() { // TODO: deviceLink parameter
        // To be called by DeviceLinkingSession when a device link has been authorized
        // TODO: val session = DeviceLinkingSession.current!!
        // TODO: session.stopListeningForLinkingAuthorization()
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
        // TODO: LokiStorageAPI.addDeviceLink(signedDeviceLink).fail { error ->
        // TODO:     Log.d("Loki", "Failed to add device link due to error: $error.")
        // TODO: }
        Handler().postDelayed({
            delegate?.handleDeviceLinkAuthorized()
            dismiss?.invoke()
        }, 4000)
    }
    // endregion

    // region Interaction
    private fun cancel() {
        // TODO: val session = DeviceLinkingSession.current!!
        // TODO: session.stopListeningForLinkingRequests()
        // TODO: session.markLinkingRequestAsProcessed() // Only relevant in master mode
        delegate?.handleDeviceLinkingDialogDismissed() // Only relevant in slave mode
        dismiss?.invoke()
    }
    // endregion
}