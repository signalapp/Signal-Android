package org.thoughtcrime.securesms.loki

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.support.v7.app.AlertDialog
import android.util.AttributeSet
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_device_linking.view.*
import network.loki.messenger.R

object DeviceLinkingDialog {

    fun show(context: Context, mode: DeviceLinkingView.Mode) {
        val view = DeviceLinkingView(context, mode)
        val dialog = AlertDialog.Builder(context).setView(view).show()
        view.onCancel = { dialog.dismiss() }
    }
}

class DeviceLinkingView private constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : LinearLayout(context, attrs, defStyleAttr) {
    private lateinit var mode: Mode
    private var delegate: DeviceLinkingDialogDelegate? = null
    var onCancel: (() -> Unit)? = null

    // region Types
    enum class Mode { Master, Slave }
    // endregion

    // region Lifecycle
    constructor(context: Context, mode: Mode) : this(context, null, 0) {
        this.mode = mode
    }
    private constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    private constructor(context: Context) : this(context, null)

    init {
        if (mode == Mode.Slave) {
            if (delegate == null) { throw IllegalStateException("Missing delegate for device linking dialog in slave mode.") }
        }
        setUpViewHierarchy()
        when (mode) {
            Mode.Master -> throw IllegalStateException() // DeviceLinkingSession.startListeningForLinkingRequests(this)
            Mode.Slave -> throw IllegalStateException() // DeviceLinkingSession.startListeningForAuthorization(this)
        }
    }

    private fun setUpViewHierarchy() {
        inflate(context, R.layout.view_device_linking, this)
        spinner.indeterminateDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        cancelButton.setOnClickListener { onCancel?.invoke() }
    }
    // endregion

    // region Device Linking
    private fun requestUserAuthorization() { // TODO: Device link
        // Called by DeviceLinkingSession when a linking request has been received
    }

    private fun authorizeDeviceLink() {

    }

    private fun handleDeviceLinkAuthorized() { // TODO: Device link
        // Called by DeviceLinkingSession when a device link has been authorized
    }
    // endregion
}