package org.thoughtcrime.securesms

import android.content.ContentValues
import android.os.Build
import org.signal.spinner.Spinner
import org.signal.spinner.Spinner.DatabaseConfig
import org.thoughtcrime.securesms.database.DatabaseMonitor
import org.thoughtcrime.securesms.database.GV2Transformer
import org.thoughtcrime.securesms.database.GV2UpdateTransformer
import org.thoughtcrime.securesms.database.IsStoryTransformer
import org.thoughtcrime.securesms.database.JobDatabase
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.database.KyberKeyTransformer
import org.thoughtcrime.securesms.database.LocalMetricsDatabase
import org.thoughtcrime.securesms.database.LogDatabase
import org.thoughtcrime.securesms.database.MegaphoneDatabase
import org.thoughtcrime.securesms.database.MessageBitmaskColumnTransformer
import org.thoughtcrime.securesms.database.MessageRangesTransformer
import org.thoughtcrime.securesms.database.ProfileKeyCredentialTransformer
import org.thoughtcrime.securesms.database.QueryMonitor
import org.thoughtcrime.securesms.database.RecipientTransformer
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.TimestampTransformer
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.AppSignatureUtil
import java.util.Locale

class SpinnerApplicationContext : ApplicationContext() {
  override fun onCreate() {
    super.onCreate()

    try {
      Class.forName("dalvik.system.CloseGuard")
        .getMethod("setEnabled", Boolean::class.javaPrimitiveType)
        .invoke(null, true)
    } catch (e: ReflectiveOperationException) {
      throw RuntimeException(e)
    }

    Spinner.init(
      this,
      mapOf(
        "Device" to { "${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})" },
        "Package" to { "$packageName (${AppSignatureUtil.getAppSignature(this)})" },
        "App Version" to { "${BuildConfig.VERSION_NAME} (${BuildConfig.CANONICAL_VERSION_CODE}, ${BuildConfig.GIT_HASH})" },
        "Profile Name" to { (if (SignalStore.account().isRegistered) Recipient.self().profileName.toString() else "none") },
        "E164" to { SignalStore.account().e164 ?: "none" },
        "ACI" to { SignalStore.account().aci?.toString() ?: "none" },
        "PNI" to { SignalStore.account().pni?.toString() ?: "none" },
        Spinner.KEY_ENVIRONMENT to { BuildConfig.FLAVOR_environment.uppercase(Locale.US) }
      ),
      linkedMapOf(
        "signal" to DatabaseConfig(
          db = { SignalDatabase.rawDatabase },
          columnTransformers = listOf(MessageBitmaskColumnTransformer, GV2Transformer, GV2UpdateTransformer, IsStoryTransformer, TimestampTransformer, ProfileKeyCredentialTransformer, MessageRangesTransformer, KyberKeyTransformer, RecipientTransformer)
        ),
        "jobmanager" to DatabaseConfig(db = { JobDatabase.getInstance(this).sqlCipherDatabase }),
        "keyvalue" to DatabaseConfig(db = { KeyValueDatabase.getInstance(this).sqlCipherDatabase }),
        "megaphones" to DatabaseConfig(db = { MegaphoneDatabase.getInstance(this).sqlCipherDatabase }),
        "localmetrics" to DatabaseConfig(db = { LocalMetricsDatabase.getInstance(this).sqlCipherDatabase }),
        "logs" to DatabaseConfig(db = { LogDatabase.getInstance(this).sqlCipherDatabase })
      ),
      linkedMapOf(
        StorageServicePlugin.PATH to StorageServicePlugin()
      )
    )

    DatabaseMonitor.initialize(object : QueryMonitor {
      override fun onSql(sql: String, args: Array<Any>?) {
        Spinner.onSql("signal", sql, args)
      }

      override fun onQuery(distinct: Boolean, table: String, projection: Array<String>?, selection: String?, args: Array<Any>?, groupBy: String?, having: String?, orderBy: String?, limit: String?) {
        Spinner.onQuery("signal", distinct, table, projection, selection, args, groupBy, having, orderBy, limit)
      }

      override fun onDelete(table: String, selection: String?, args: Array<Any>?) {
        Spinner.onDelete("signal", table, selection, args)
      }

      override fun onUpdate(table: String, values: ContentValues, selection: String?, args: Array<Any>?) {
        Spinner.onUpdate("signal", table, values, selection, args)
      }
    })
  }
}
