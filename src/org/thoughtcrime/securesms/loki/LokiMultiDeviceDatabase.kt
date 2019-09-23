package org.thoughtcrime.securesms.loki

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.Cursor
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.util.Base64
import org.whispersystems.signalservice.loki.api.LokiPairingAuthorisation
import org.whispersystems.signalservice.loki.api.LokiStorageAPIDatabaseProtocol
import java.util.*

class LokiMultiDeviceDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), LokiStorageAPIDatabaseProtocol {

  companion object {
    // Authorisation
    private val authorisation_table = "loki_multi_device_authorisation"
    private val primaryDevice = "primary_device"
    private val secondaryDevice = "secondary_device"
    private val requestSignature = "request_signature"
    private val grantSignature = "grant_signature"
    @JvmStatic
    val createTableCommand = "CREATE TABLE $authorisation_table(_id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "$primaryDevice TEXT," +
            "$secondaryDevice TEXT," +
            "$requestSignature TEXT NULLABLE" +
            "$grantSignature TEXT NULLABLE" +
            ");"
  }

  override fun insertOrUpdatePairingAuthorisation(authorisation: LokiPairingAuthorisation) {
    val database = databaseHelper.writableDatabase
    val values = ContentValues()
    values.put(primaryDevice, authorisation.primaryDevicePubKey)
    values.put(secondaryDevice, authorisation.secondaryDevicePubKey)
    if (authorisation.requestSignature != null) { values.put(requestSignature, Base64.encodeBytes(authorisation.requestSignature)) }
    if (authorisation.grantSignature != null) { values.put(grantSignature, Base64.encodeBytes(authorisation.grantSignature)) }
    database.insertOrUpdate(authorisation_table, values, "$primaryDevice = ? AND $secondaryDevice = ?", arrayOf(authorisation.primaryDevicePubKey, authorisation.secondaryDevicePubKey))
  }

  override fun removePairingAuthorisations(pubKey: String) {
    val database = databaseHelper.readableDatabase
    database.delete(authorisation_table, "$primaryDevice = ? OR $secondaryDevice = ?", arrayOf(pubKey, pubKey))
  }

  fun getAuthorisationForSecondaryDevice(pubKey: String): LokiPairingAuthorisation? {
    val database = databaseHelper.readableDatabase
    return database.get(authorisation_table, "$secondaryDevice = ?", arrayOf(pubKey)) { cursor ->
      val primaryDevicePubKey = cursor.getString(primaryDevice)
      val secondaryDevicePubKey = cursor.getString(secondaryDevice)
      val requestSignature: ByteArray? = if (cursor.isNull(cursor.getColumnIndexOrThrow(requestSignature))) null else cursor.getBase64EncodedData(requestSignature)
      val grantSignature: ByteArray? = if (cursor.isNull(cursor.getColumnIndexOrThrow(grantSignature))) null else cursor.getBase64EncodedData(grantSignature)
      LokiPairingAuthorisation(primaryDevicePubKey, secondaryDevicePubKey, requestSignature, grantSignature)
    }
  }

  override fun getSecondaryDevices(primaryDevicePubKey: String): List<String> {
    val database = databaseHelper.readableDatabase

    var cursor: Cursor? = null
    val results = LinkedList<String>()

    try {
      cursor = database.query(authorisation_table, arrayOf(secondaryDevice), "$primaryDevice = ?", arrayOf(primaryDevicePubKey), null, null, null)
      if (cursor != null && cursor.moveToNext()) {
        results.add(cursor.getString(secondaryDevice))
      }
    } catch (e: Exception) {
      // Do nothing
    } finally {
      cursor?.close()
    }

    return results
  }

  override fun getPrimaryDevice(secondaryDevicePubKey: String): String? {
    val database = databaseHelper.readableDatabase
    return database.get(authorisation_table, "$secondaryDevice = ?", arrayOf(secondaryDevicePubKey)) { cursor ->
      cursor.getString(primaryDevice)
    }
  }
}