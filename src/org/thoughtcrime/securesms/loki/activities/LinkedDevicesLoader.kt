package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.devicelist.Device
import org.thoughtcrime.securesms.loki.utilities.MnemonicUtilities
import org.thoughtcrime.securesms.util.AsyncLoader
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import org.whispersystems.signalservice.loki.protocol.shelved.multidevice.MultiDeviceProtocol
import java.io.File

class LinkedDevicesLoader(context: Context) : AsyncLoader<List<Device>>(context) {

    private val mnemonicCodec by lazy {
        val loadFileContents: (String) -> String = { fileName ->
            MnemonicUtilities.loadFileContents(context, fileName)
        }
        MnemonicCodec(loadFileContents)
    }

    override fun loadInBackground(): List<Device>? {
        try {
            val userPublicKey = TextSecurePreferences.getLocalNumber(context)
            val slaveDevices = MultiDeviceProtocol.shared.getSlaveDevices(userPublicKey)
            return slaveDevices.map { device ->
                val shortID = MnemonicUtilities.getFirst3Words(mnemonicCodec, device)
                val name = DatabaseFactory.getLokiUserDatabase(context).getDisplayName(device)
                Device(device, shortID, name)
            }.sortedBy { it.name }
        } catch (e: Exception) {
            return null
        }
    }
}