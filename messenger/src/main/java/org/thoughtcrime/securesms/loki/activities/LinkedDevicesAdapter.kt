package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import org.thoughtcrime.securesms.devicelist.Device
import org.thoughtcrime.securesms.loki.views.DeviceView

class LinkedDevicesAdapter(private val context: Context) : RecyclerView.Adapter<LinkedDevicesAdapter.ViewHolder>() {
    var devices = listOf<Device>()
        set(value) { field = value; notifyDataSetChanged() }
    var deviceClickListener: DeviceClickListener? = null

    class ViewHolder(val view: DeviceView) : RecyclerView.ViewHolder(view)

    override fun getItemCount(): Int {
        return devices.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = DeviceView(context)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val device = devices[position]
        viewHolder.view.setOnClickListener { deviceClickListener?.onDeviceClick(device) }
        viewHolder.view.bind(device)
    }
}

interface DeviceClickListener {

    fun onDeviceClick(device: Device)
}
