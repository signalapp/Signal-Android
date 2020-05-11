package org.thoughtcrime.securesms.loki.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_device.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.devicelist.Device
import org.thoughtcrime.securesms.loki.toPx

class DeviceView : LinearLayout {
    var device: Device? = null

    // region Lifecycle
    constructor(context: Context) : super(context) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        setUpViewHierarchy()
    }

    private fun setUpViewHierarchy() {
        val inflater = context.applicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val contentView = inflater.inflate(R.layout.view_device, null)
        addView(contentView)
    }
    // endregion

    // region Updating
    fun bind(device: Device) {
        titleTextView.text = if (!device.name.isNullOrBlank()) device.name else "Unnamed Device"
        // FIXME: Hacky way of getting the view to be screen width
        val titleTextViewLayoutParams = titleTextView.layoutParams
        titleTextViewLayoutParams.width = resources.displayMetrics.widthPixels - toPx(32, resources)
        titleTextView.layoutParams = titleTextViewLayoutParams
        subtitleTextView.text = device.shortId
    }
    // endregion
}