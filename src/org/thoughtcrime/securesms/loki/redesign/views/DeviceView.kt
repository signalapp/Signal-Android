package org.thoughtcrime.securesms.loki.redesign.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_device.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.devicelist.Device

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
        subtitleTextView.text = device.shortId
    }
    // endregion
}