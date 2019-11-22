package org.thoughtcrime.securesms.loki

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Handler
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.View
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_device_linking.view.*
import kotlinx.android.synthetic.main.view_device_linking.view.cancelButton
import kotlinx.android.synthetic.main.view_device_linking.view.explanationTextView
import kotlinx.android.synthetic.main.view_device_linking.view.titleTextView
import network.loki.messenger.R
import org.thoughtcrime.securesms.qr.QrCode
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.api.PairingAuthorisation
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import java.io.File

class DeviceLinkingView private constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, private val mode: Mode, private var delegate: DeviceLinkingDelegate) : LinearLayout(context, attrs, defStyleAttr) {
    private val languageFileDirectory: File = MnemonicUtilities.getLanguageFileDirectory(context)
    var dismiss: (() -> Unit)? = null
    var pairingAuthorisation: PairingAuthorisation? = null
        private set

    // region Types
    enum class Mode { Master, Slave }
    // endregion

    // region Lifecycle
    constructor(context: Context, mode: Mode, delegate: DeviceLinkingDelegate) : this(context, null, 0, mode, delegate)
    private constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0, Mode.Master, object : DeviceLinkingDelegate { }) // Just pass in a dummy mode
    private constructor(context: Context) : this(context, null)

    init {
        setUpViewHierarchy()
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
            val hexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context)
            mnemonicTextView.text = MnemonicUtilities.getFirst3Words(MnemonicCodec(languageFileDirectory), hexEncodedPublicKey)
        }
        authorizeButton.visibility = View.GONE
        authorizeButton.setOnClickListener { authorizePairing() }

        // QR Code
        spinner.visibility = if (mode == Mode.Master) View.GONE else View.VISIBLE
        qrCodeImageView.visibility = if (mode == Mode.Master) View.VISIBLE else View.GONE
        if (mode == Mode.Master) {
            val hexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context)
            val displayMetrics = DisplayMetrics()
            ServiceUtil.getWindowManager(context).defaultDisplay.getMetrics(displayMetrics)
            val size = displayMetrics.widthPixels - 2 * toPx(96, resources)
            val qrCode = QrCode.create(hexEncodedPublicKey, size)
            qrCodeImageView.setImageBitmap(qrCode)
        }

        cancelButton.setOnClickListener { cancel() }
    }
    // endregion

    // region Device Linking
    fun requestUserAuthorization(pairingAuthorisation: PairingAuthorisation) {
        if (mode != Mode.Master || pairingAuthorisation.type != PairingAuthorisation.Type.REQUEST || this.pairingAuthorisation != null) { return }
        this.pairingAuthorisation = pairingAuthorisation
        spinner.visibility = View.GONE
        qrCodeImageView.visibility = View.GONE
        val titleTextViewLayoutParams = titleTextView.layoutParams as LayoutParams
        titleTextViewLayoutParams.topMargin = toPx(16, resources)
        titleTextView.layoutParams = titleTextViewLayoutParams
        titleTextView.text = resources.getString(R.string.view_device_linking_title_3)
        explanationTextView.text = resources.getString(R.string.view_device_linking_explanation_2)
        mnemonicTextView.visibility = View.VISIBLE
        mnemonicTextView.text = MnemonicUtilities.getFirst3Words(MnemonicCodec(languageFileDirectory), pairingAuthorisation.secondaryDevicePublicKey)
        authorizeButton.visibility = View.VISIBLE
    }

    fun onDeviceLinkAuthorized(pairingAuthorisation: PairingAuthorisation) {
        if (mode != Mode.Slave || pairingAuthorisation.type != PairingAuthorisation.Type.GRANT || this.pairingAuthorisation != null) { return }
        this.pairingAuthorisation = pairingAuthorisation
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
        cancelButton.visibility = View.GONE
        Handler().postDelayed({
            delegate.handleDeviceLinkAuthorized(pairingAuthorisation)
            dismiss?.invoke()
        }, 4000)
    }
    // endregion

    // region Interaction
    private fun authorizePairing() {
        val pairingAuthorisation = this.pairingAuthorisation
        if (mode != Mode.Master || pairingAuthorisation == null) { return; }
        delegate.sendPairingAuthorizedMessage(pairingAuthorisation)
        delegate.handleDeviceLinkAuthorized(pairingAuthorisation)
        dismiss?.invoke()
    }

    private fun cancel() {
        delegate.handleDeviceLinkingDialogDismissed()
        dismiss?.invoke()
    }
    // endregion
}