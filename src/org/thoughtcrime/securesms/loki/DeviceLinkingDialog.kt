package org.thoughtcrime.securesms.loki

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.support.v7.app.AlertDialog
import android.util.AttributeSet
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_device_linking.view.*
import kotlinx.android.synthetic.main.view_qr_code.view.cancelButton
import network.loki.messenger.R

object DeviceLinkingDialog {

    fun show(context: Context) {
        val view = DeviceLinkingView(context)
        val dialog = AlertDialog.Builder(context).setView(view).show()
        view.onCancel = { dialog.dismiss() }
    }
}

class DeviceLinkingView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : LinearLayout(context, attrs, defStyleAttr) {
    var onCancel: (() -> Unit)? = null

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    init {
        inflate(context, R.layout.view_device_linking, this)
        spinner.indeterminateDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        cancelButton.setOnClickListener { onCancel?.invoke() }
    }
}