package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.devicelist.Device
import org.thoughtcrime.securesms.loki.MnemonicUtilities
import org.thoughtcrime.securesms.util.AsyncLoader
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.api.LokiStorageAPI
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec
import java.io.File

class LinkedDevicesLoader(context: Context) : AsyncLoader<List<Device>>(context) {

    private val mnemonicCodec by lazy {
        val languageFileDirectory = File(context.applicationInfo.dataDir)
        MnemonicCodec(languageFileDirectory)
    }

    override fun loadInBackground(): List<Device>? {
        try {
            val userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context)
            val slaveDeviceHexEncodedPublicKeys = LokiStorageAPI.shared.getSecondaryDevicePublicKeys(userHexEncodedPublicKey).get()
            return slaveDeviceHexEncodedPublicKeys.map { hexEncodedPublicKey ->
                val shortID = MnemonicUtilities.getFirst3Words(mnemonicCodec, hexEncodedPublicKey)
                val name = DatabaseFactory.getLokiUserDatabase(context).getDisplayName(hexEncodedPublicKey)
                Device(hexEncodedPublicKey, shortID, name)
            }.sortedBy { it.name }
        } catch (e: Exception) {
            return null
        }
    }
}