package org.thoughtcrime.securesms.mediasend

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.mediasend.ProofConstants.IS_PROOF_ENABLED
import org.thoughtcrime.securesms.mediasend.ProofConstants.IS_PROOF_LOCATION_ENABLED_GLOBAL
import org.thoughtcrime.securesms.mediasend.ProofConstants.IS_PROOF_LOCATION_ENABLED_LOCAL
import org.thoughtcrime.securesms.mediasend.ProofConstants.IS_PROOF_NETWORK_ENABLED_GLOBAL
import org.thoughtcrime.securesms.mediasend.ProofConstants.IS_PROOF_NETWORK_ENABLED_LOCAL
import org.thoughtcrime.securesms.mediasend.ProofConstants.IS_PROOF_NOTARY_ENABLED_GLOBAL
import org.thoughtcrime.securesms.mediasend.ProofConstants.IS_PROOF_NOTARY_ENABLED_LOCAL
import org.thoughtcrime.securesms.mediasend.ProofConstants.IS_PROOF_PHONE_ENABLED_GLOBAL
import org.thoughtcrime.securesms.mediasend.ProofConstants.IS_PROOF_PHONE_ENABLED_LOCAL
import org.witness.proofmode.ProofMode
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ProofModeUtil {

  fun setProofSettingsGlobal(
    context: Context,
    proofDeviceIds: Boolean? = null,
    proofLocation: Boolean? = null,
    proofNetwork: Boolean? = null,
    proofNotary: Boolean? = null
  ) {
    proofDeviceIds?.let {
      PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(IS_PROOF_PHONE_ENABLED_GLOBAL, it).apply()
    }
    proofLocation?.let {
      PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(IS_PROOF_LOCATION_ENABLED_GLOBAL, it).apply()
    }
    proofNetwork?.let {
      PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(IS_PROOF_NETWORK_ENABLED_GLOBAL, it).apply()
    }
    proofNotary?.let {
      PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(IS_PROOF_NOTARY_ENABLED_GLOBAL, it).apply()
    }
  }

  fun setProofSettingsLocal(
    context: Context,
    proofDeviceIds: Boolean? = null,
    proofLocation: Boolean? = null,
    proofNetwork: Boolean? = null,
    proofNotary: Boolean? = null
  ) {
    proofDeviceIds?.let {
      PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(IS_PROOF_PHONE_ENABLED_LOCAL, it).apply()
    }
    proofLocation?.let {
      PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(IS_PROOF_LOCATION_ENABLED_LOCAL, it).apply()
    }
    proofNetwork?.let {
      PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(IS_PROOF_NETWORK_ENABLED_LOCAL, it).apply()
    }
    proofNotary?.let {
      PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(IS_PROOF_NOTARY_ENABLED_LOCAL, it).apply()
    }
  }

  fun clearLocalSettings(context: Context) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(IS_PROOF_NOTARY_ENABLED_LOCAL, false).apply()
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(IS_PROOF_LOCATION_ENABLED_LOCAL, false).apply()
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(IS_PROOF_PHONE_ENABLED_LOCAL, false).apply()
    PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(IS_PROOF_NETWORK_ENABLED_LOCAL, false).apply()
  }

  fun getProofHash(context: Context, uri: Uri, byteArray: ByteArray, mimeType: String): String {
    val isEnabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(IS_PROOF_ENABLED, true)
    return if (isEnabled) {
      val proofHash = ProofMode.generateProof(context, uri, byteArray, mimeType)
      ProofMode.getProofDir(context, proofHash)

      proofHash
    } else {
      ""
    }
  }

  fun setProofPoints(
    context: Context,
    proofDeviceIds: Boolean = true,
    proofLocation: Boolean = true,
    proofNetwork: Boolean = true,
    proofNotary: Boolean = true
  ) {
    ProofMode.setProofPoints(context, proofDeviceIds, proofLocation, proofNetwork, proofNotary)
  }

  private fun settingsSetter(context: Context) {
    val notaryGlobal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(IS_PROOF_NOTARY_ENABLED_GLOBAL, true)
    val locationGlobal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(IS_PROOF_LOCATION_ENABLED_GLOBAL, true)
    val phoneGlobal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(IS_PROOF_PHONE_ENABLED_GLOBAL, true)
    val networkGlobal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(IS_PROOF_NETWORK_ENABLED_GLOBAL, true)
    val notaryLocal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(IS_PROOF_NOTARY_ENABLED_LOCAL, true)
    val locationLocal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(IS_PROOF_LOCATION_ENABLED_LOCAL, true)
    val phoneLocal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(IS_PROOF_PHONE_ENABLED_LOCAL, true)
    val networkLocal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(IS_PROOF_NETWORK_ENABLED_LOCAL, true)
    val resultNotary = if (notaryGlobal == notaryLocal) notaryGlobal else notaryLocal
    val resultLocation = if (locationGlobal == locationLocal) locationGlobal else locationLocal
    val resultPhone = if (phoneGlobal == phoneLocal) phoneGlobal else phoneLocal
    val resultNetwork = if (networkGlobal == networkLocal) networkGlobal else networkLocal

    setProofPoints(
      context = context,
      proofDeviceIds = resultPhone,
      proofLocation = resultLocation,
      proofNetwork = resultNetwork,
      proofNotary = resultNotary
    )
  }

  fun createZipProof(proofHash: String, context: Context): File {
    settingsSetter(context)

    var proofDir = ProofMode.getProofDir(context, proofHash)
    var fileZip = makeProofZip(proofDir.absoluteFile, context)

    Log.e("ZIP PATH", "zip path: $fileZip")

    return fileZip

  }

  private fun makeProofZip(proofDirPath: File, context: Context): File {
    val outputZipFile = File(proofDirPath.path, proofDirPath.name + ".zip")
    ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
      proofDirPath.walkTopDown().forEach { file ->
        val zipFileName = file.absolutePath.removePrefix(proofDirPath.absolutePath).removePrefix("/")
        val entry = ZipEntry("$zipFileName${(if (file.isDirectory) "/" else "")}")
        zos.putNextEntry(entry)
        if (file.isFile) {
          file.inputStream().copyTo(zos)
        }
      }

      val keyEntry = ZipEntry("pubkey.asc");
      zos.putNextEntry(keyEntry);
      var publicKey = ProofMode.getPublicKey(context)
      zos.write(publicKey.toByteArray())

      return outputZipFile
    }
  }
}