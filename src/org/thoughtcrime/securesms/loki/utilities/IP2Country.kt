package org.thoughtcrime.securesms.loki.utilities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import com.opencsv.CSVReader
import org.whispersystems.signalservice.loki.api.onionrequests.OnionRequestAPI
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader

class IP2Country private constructor(private val context: Context) {
    private val pathsBuiltEventReceiver: BroadcastReceiver
    val countryNamesCache = mutableMapOf<String, String>()

    private val ipv4Table by lazy {
        loadFile("geolite2_country_blocks_ipv4.csv")
    }

    private val countryNamesTable by lazy {
        loadFile("geolite2_country_locations_english.csv")
    }

    // region Initialization
    companion object {

        public lateinit var shared: IP2Country

        public fun configureIfNeeded(context: Context) {
            if (::shared.isInitialized) { return; }
            shared = IP2Country(context)
        }
    }

    init {
        populateCacheIfNeeded()
        pathsBuiltEventReceiver = object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) {
                populateCacheIfNeeded()
            }
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(pathsBuiltEventReceiver, IntentFilter("pathsBuilt"))
    }

    // TODO: Deinit?
    // endregion

    // region Implementation
    private fun loadFile(fileName: String): File {
        val directory = File(context.applicationInfo.dataDir)
        val file = File(directory, fileName)
        if (directory.list().contains(fileName)) { return file }
        val inputStream = context.assets.open("csv/$fileName")
        val outputStream = FileOutputStream(file)
        val buffer = ByteArray(1024)
        while (true) {
            val count = inputStream.read(buffer)
            if (count < 0) { break }
            outputStream.write(buffer, 0, count)
        }
        inputStream.close()
        outputStream.close()
        return file
    }

    private fun cacheCountryForIP(ip: String): String {
        var truncatedIP = ip
        fun getCountryInternal(): String {
            val country = countryNamesCache[ip]
            if (country != null) { return country }
            val ipv4TableReader = CSVReader(FileReader(ipv4Table.absoluteFile))
            val countryNamesTableReader = CSVReader(FileReader(countryNamesTable.absoluteFile))
            var ipv4TableLine = ipv4TableReader.readNext()
            while (ipv4TableLine != null) {
                if (!ipv4TableLine[0].startsWith(truncatedIP)) {
                    ipv4TableLine = ipv4TableReader.readNext()
                    continue
                }
                val countryID = ipv4TableLine[1]
                var countryNamesTableLine = countryNamesTableReader.readNext()
                while (countryNamesTableLine != null) {
                    if (countryNamesTableLine[0] != countryID) {
                        countryNamesTableLine = countryNamesTableReader.readNext()
                        continue
                    }
                    @Suppress("NAME_SHADOWING") val country = countryNamesTableLine[5]
                    countryNamesCache[ip] = country
                    return country
                }
            }
            if (truncatedIP.contains(".") && !truncatedIP.endsWith(".")) { // The fuzziest we want to go is xxx.x
                truncatedIP = truncatedIP.dropLast(1)
                if (truncatedIP.endsWith(".")) { truncatedIP = truncatedIP.dropLast(1) }
                return getCountryInternal()
            } else {
                return "Unknown Country"
            }
        }
        return getCountryInternal()
    }

    private fun populateCacheIfNeeded() {
        Thread {
            val path = OnionRequestAPI.paths.firstOrNull() ?: return@Thread
            path.forEach { snode ->
                cacheCountryForIP(snode.ip) // Preload if needed
            }
            Broadcaster(context).broadcast("onionRequestPathCountriesLoaded")
            Log.d("Loki", "Finished preloading onion request path countries.")
        }.start()
    }
    // endregion
}