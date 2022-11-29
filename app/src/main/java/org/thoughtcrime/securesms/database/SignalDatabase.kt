package org.thoughtcrime.securesms.database

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.AttachmentSecret
import org.thoughtcrime.securesms.crypto.DatabaseSecret
import org.thoughtcrime.securesms.crypto.MasterSecret
import org.thoughtcrime.securesms.database.helpers.ClassicOpenHelper
import org.thoughtcrime.securesms.database.helpers.PreKeyMigrationHelper
import org.thoughtcrime.securesms.database.helpers.SQLCipherMigrationHelper
import org.thoughtcrime.securesms.database.helpers.SessionStoreMigrationHelper
import org.thoughtcrime.securesms.database.helpers.SignalDatabaseMigrations
import org.thoughtcrime.securesms.database.helpers.SignalDatabaseMigrations.migrate
import org.thoughtcrime.securesms.database.helpers.SignalDatabaseMigrations.migratePostTransaction
import org.thoughtcrime.securesms.database.model.AvatarPickerDatabase
import org.thoughtcrime.securesms.jobs.PreKeysSyncJob
import org.thoughtcrime.securesms.migrations.LegacyMigrationJob
import org.thoughtcrime.securesms.migrations.LegacyMigrationJob.DatabaseUpgradeListener
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.io.File

open class SignalDatabase(private val context: Application, databaseSecret: DatabaseSecret, attachmentSecret: AttachmentSecret) :
  SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    databaseSecret.asString(),
    null,
    SignalDatabaseMigrations.DATABASE_VERSION,
    0,
    SqlCipherErrorHandler(DATABASE_NAME),
    SqlCipherDatabaseHook()
  ),
  SignalDatabaseOpenHelper {

  val sms: SmsTable = SmsTable(context, this)
  val mms: MmsTable = MmsTable(context, this)
  val attachments: AttachmentTable = AttachmentTable(context, this, attachmentSecret)
  val media: MediaTable = MediaTable(context, this)
  val thread: ThreadTable = ThreadTable(context, this)
  val mmsSmsTable: MmsSmsTable = MmsSmsTable(context, this)
  val identityTable: IdentityTable = IdentityTable(context, this)
  val draftTable: DraftTable = DraftTable(context, this)
  val pushTable: PushTable = PushTable(context, this)
  val groupTable: GroupTable = GroupTable(context, this)
  val recipientTable: RecipientTable = RecipientTable(context, this)
  val groupReceiptTable: GroupReceiptTable = GroupReceiptTable(context, this)
  val preKeyDatabase: OneTimePreKeyTable = OneTimePreKeyTable(context, this)
  val signedPreKeyTable: SignedPreKeyTable = SignedPreKeyTable(context, this)
  val sessionTable: SessionTable = SessionTable(context, this)
  val senderKeyTable: SenderKeyTable = SenderKeyTable(context, this)
  val senderKeySharedTable: SenderKeySharedTable = SenderKeySharedTable(context, this)
  val pendingRetryReceiptTable: PendingRetryReceiptTable = PendingRetryReceiptTable(context, this)
  val searchTable: SearchTable = SearchTable(context, this)
  val stickerTable: StickerTable = StickerTable(context, this, attachmentSecret)
  val storageIdDatabase: UnknownStorageIdTable = UnknownStorageIdTable(context, this)
  val remappedRecordTables: RemappedRecordTables = RemappedRecordTables(context, this)
  val mentionTable: MentionTable = MentionTable(context, this)
  val paymentTable: PaymentTable = PaymentTable(context, this)
  val chatColorsTable: ChatColorsTable = ChatColorsTable(context, this)
  val emojiSearchTable: EmojiSearchTable = EmojiSearchTable(context, this)
  val messageSendLogTables: MessageSendLogTables = MessageSendLogTables(context, this)
  val avatarPickerDatabase: AvatarPickerDatabase = AvatarPickerDatabase(context, this)
  val groupCallRingTable: GroupCallRingTable = GroupCallRingTable(context, this)
  val reactionTable: ReactionTable = ReactionTable(context, this)
  val notificationProfileDatabase: NotificationProfileDatabase = NotificationProfileDatabase(context, this)
  val donationReceiptTable: DonationReceiptTable = DonationReceiptTable(context, this)
  val distributionListTables: DistributionListTables = DistributionListTables(context, this)
  val storySendTable: StorySendTable = StorySendTable(context, this)
  val cdsTable: CdsTable = CdsTable(context, this)
  val remoteMegaphoneTable: RemoteMegaphoneTable = RemoteMegaphoneTable(context, this)
  val pendingPniSignatureMessageTable: PendingPniSignatureMessageTable = PendingPniSignatureMessageTable(context, this)

  override fun onOpen(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
    db.setForeignKeyConstraintsEnabled(true)
  }

  override fun onCreate(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
    db.execSQL(SmsTable.CREATE_TABLE)
    db.execSQL(MmsTable.CREATE_TABLE)
    db.execSQL(AttachmentTable.CREATE_TABLE)
    db.execSQL(ThreadTable.CREATE_TABLE)
    db.execSQL(IdentityTable.CREATE_TABLE)
    db.execSQL(DraftTable.CREATE_TABLE)
    db.execSQL(PushTable.CREATE_TABLE)
    db.execSQL(GroupTable.CREATE_TABLE)
    db.execSQL(RecipientTable.CREATE_TABLE)
    db.execSQL(GroupReceiptTable.CREATE_TABLE)
    db.execSQL(OneTimePreKeyTable.CREATE_TABLE)
    db.execSQL(SignedPreKeyTable.CREATE_TABLE)
    db.execSQL(SessionTable.CREATE_TABLE)
    db.execSQL(SenderKeyTable.CREATE_TABLE)
    db.execSQL(SenderKeySharedTable.CREATE_TABLE)
    db.execSQL(PendingRetryReceiptTable.CREATE_TABLE)
    db.execSQL(StickerTable.CREATE_TABLE)
    db.execSQL(UnknownStorageIdTable.CREATE_TABLE)
    db.execSQL(MentionTable.CREATE_TABLE)
    db.execSQL(PaymentTable.CREATE_TABLE)
    db.execSQL(ChatColorsTable.CREATE_TABLE)
    db.execSQL(EmojiSearchTable.CREATE_TABLE)
    db.execSQL(AvatarPickerDatabase.CREATE_TABLE)
    db.execSQL(GroupCallRingTable.CREATE_TABLE)
    db.execSQL(ReactionTable.CREATE_TABLE)
    db.execSQL(DonationReceiptTable.CREATE_TABLE)
    db.execSQL(StorySendTable.CREATE_TABLE)
    db.execSQL(CdsTable.CREATE_TABLE)
    db.execSQL(RemoteMegaphoneTable.CREATE_TABLE)
    db.execSQL(PendingPniSignatureMessageTable.CREATE_TABLE)
    executeStatements(db, SearchTable.CREATE_TABLE)
    executeStatements(db, RemappedRecordTables.CREATE_TABLE)
    executeStatements(db, MessageSendLogTables.CREATE_TABLE)
    executeStatements(db, NotificationProfileDatabase.CREATE_TABLE)
    executeStatements(db, DistributionListTables.CREATE_TABLE)

    executeStatements(db, RecipientTable.CREATE_INDEXS)
    executeStatements(db, SmsTable.CREATE_INDEXS)
    executeStatements(db, MmsTable.CREATE_INDEXS)
    executeStatements(db, AttachmentTable.CREATE_INDEXS)
    executeStatements(db, ThreadTable.CREATE_INDEXS)
    executeStatements(db, DraftTable.CREATE_INDEXS)
    executeStatements(db, GroupTable.CREATE_INDEXS)
    executeStatements(db, GroupReceiptTable.CREATE_INDEXES)
    executeStatements(db, StickerTable.CREATE_INDEXES)
    executeStatements(db, UnknownStorageIdTable.CREATE_INDEXES)
    executeStatements(db, MentionTable.CREATE_INDEXES)
    executeStatements(db, PaymentTable.CREATE_INDEXES)
    executeStatements(db, MessageSendLogTables.CREATE_INDEXES)
    executeStatements(db, GroupCallRingTable.CREATE_INDEXES)
    executeStatements(db, NotificationProfileDatabase.CREATE_INDEXES)
    executeStatements(db, DonationReceiptTable.CREATE_INDEXS)
    executeStatements(db, StorySendTable.CREATE_INDEXS)
    executeStatements(db, DistributionListTables.CREATE_INDEXES)
    executeStatements(db, PendingPniSignatureMessageTable.CREATE_INDEXES)

    executeStatements(db, MessageSendLogTables.CREATE_TRIGGERS)
    executeStatements(db, ReactionTable.CREATE_TRIGGERS)

    DistributionListTables.insertInitialDistributionListAtCreationTime(db)

    if (context.getDatabasePath(ClassicOpenHelper.NAME).exists()) {
      val legacyHelper = ClassicOpenHelper(context)
      val legacyDb = legacyHelper.writableDatabase
      SQLCipherMigrationHelper.migratePlaintext(context, legacyDb, db)
      val masterSecret = KeyCachingService.getMasterSecret(context)
      if (masterSecret != null) SQLCipherMigrationHelper.migrateCiphertext(context, masterSecret, legacyDb, db, null) else TextSecurePreferences.setNeedsSqlCipherMigration(context, true)
      if (!PreKeyMigrationHelper.migratePreKeys(context, db)) {
        PreKeysSyncJob.enqueue()
      }
      SessionStoreMigrationHelper.migrateSessions(context, db)
      PreKeyMigrationHelper.cleanUpPreKeys(context)
    }
  }

  override fun onUpgrade(db: net.zetetic.database.sqlcipher.SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    Log.i(TAG, "Upgrading database: $oldVersion, $newVersion")
    val startTime = System.currentTimeMillis()
    db.beginTransaction()
    try {
      migrate(context, db, oldVersion, newVersion)
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    migratePostTransaction(context, oldVersion)
    Log.i(TAG, "Upgrade complete. Took " + (System.currentTimeMillis() - startTime) + " ms.")
  }

  override fun getReadableDatabase(): net.zetetic.database.sqlcipher.SQLiteDatabase {
    throw UnsupportedOperationException("Call getSignalReadableDatabase() instead!")
  }

  override fun getWritableDatabase(): net.zetetic.database.sqlcipher.SQLiteDatabase {
    throw UnsupportedOperationException("Call getSignalWritableDatabase() instead!")
  }

  open val rawReadableDatabase: net.zetetic.database.sqlcipher.SQLiteDatabase
    get() = super.getReadableDatabase()

  open val rawWritableDatabase: net.zetetic.database.sqlcipher.SQLiteDatabase
    get() = super.getWritableDatabase()

  open val signalReadableDatabase: SQLiteDatabase
    get() = SQLiteDatabase(super.getReadableDatabase())

  open val signalWritableDatabase: SQLiteDatabase
    get() = SQLiteDatabase(super.getWritableDatabase())

  override fun getSqlCipherDatabase(): net.zetetic.database.sqlcipher.SQLiteDatabase {
    return super.getWritableDatabase()
  }

  open fun markCurrent(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
    db.version = SignalDatabaseMigrations.DATABASE_VERSION
  }

  private fun executeStatements(db: net.zetetic.database.sqlcipher.SQLiteDatabase, statements: Array<String>) {
    for (statement in statements) db.execSQL(statement)
  }

  companion object {
    private val TAG = Log.tag(SignalDatabase::class.java)
    private const val DATABASE_NAME = "signal.db"

    @JvmStatic
    @Volatile
    var instance: SignalDatabase? = null
      private set

    @JvmStatic
    fun init(application: Application, databaseSecret: DatabaseSecret, attachmentSecret: AttachmentSecret) {
      if (instance == null) {
        synchronized(SignalDatabase::class.java) {
          if (instance == null) {
            instance = SignalDatabase(application, databaseSecret, attachmentSecret)
            instance!!.setWriteAheadLoggingEnabled(true)
          }
        }
      }
    }

    @JvmStatic
    @VisibleForTesting
    fun setSignalDatabaseInstanceForTesting(signalDatabase: SignalDatabase) {
      this.instance = signalDatabase
    }

    @JvmStatic
    val rawDatabase: net.zetetic.database.sqlcipher.SQLiteDatabase
      get() = instance!!.rawWritableDatabase

    @JvmStatic
    val backupDatabase: net.zetetic.database.sqlcipher.SQLiteDatabase
      get() = instance!!.rawReadableDatabase

    @JvmStatic
    @get:JvmName("inTransaction")
    val inTransaction: Boolean
      get() = instance!!.rawWritableDatabase.inTransaction()

    @JvmStatic
    fun runPostSuccessfulTransaction(dedupeKey: String, task: Runnable) {
      instance!!.signalReadableDatabase.runPostSuccessfulTransaction(dedupeKey, task)
    }

    @JvmStatic
    fun runPostSuccessfulTransaction(task: Runnable) {
      instance!!.signalReadableDatabase.runPostSuccessfulTransaction(task)
    }

    @JvmStatic
    fun databaseFileExists(context: Context): Boolean {
      return context.getDatabasePath(DATABASE_NAME).exists()
    }

    @JvmStatic
    fun getDatabaseFile(context: Context): File {
      return context.getDatabasePath(DATABASE_NAME)
    }

    @JvmStatic
    fun upgradeRestored(database: net.zetetic.database.sqlcipher.SQLiteDatabase) {
      synchronized(SignalDatabase::class.java) {
        instance!!.onUpgrade(database, database.getVersion(), -1)
        instance!!.markCurrent(database)
        instance!!.sms.deleteAbandonedMessages()
        instance!!.mms.deleteAbandonedMessages()
        instance!!.mms.trimEntriesForExpiredMessages()
        instance!!.reactionTable.deleteAbandonedReactions()
        instance!!.rawWritableDatabase.execSQL("DROP TABLE IF EXISTS key_value")
        instance!!.rawWritableDatabase.execSQL("DROP TABLE IF EXISTS megaphone")
        instance!!.rawWritableDatabase.execSQL("DROP TABLE IF EXISTS job_spec")
        instance!!.rawWritableDatabase.execSQL("DROP TABLE IF EXISTS constraint_spec")
        instance!!.rawWritableDatabase.execSQL("DROP TABLE IF EXISTS dependency_spec")

        instance!!.rawWritableDatabase.close()
        triggerDatabaseAccess()
      }
    }

    @JvmStatic
    fun hasTable(table: String): Boolean {
      return SqlUtil.tableExists(instance!!.rawReadableDatabase, table)
    }

    @JvmStatic
    fun triggerDatabaseAccess() {
      instance!!.signalWritableDatabase
    }

    @Deprecated("Only used for a legacy migration.")
    @JvmStatic
    fun onApplicationLevelUpgrade(
      context: Context,
      masterSecret: MasterSecret,
      fromVersion: Int,
      listener: DatabaseUpgradeListener?
    ) {
      instance!!.signalWritableDatabase
      var legacyOpenHelper: ClassicOpenHelper? = null
      if (fromVersion < LegacyMigrationJob.ASYMMETRIC_MASTER_SECRET_FIX_VERSION) {
        legacyOpenHelper = ClassicOpenHelper(context)
        legacyOpenHelper.onApplicationLevelUpgrade(context, masterSecret, fromVersion, listener)
      }

      if (fromVersion < LegacyMigrationJob.SQLCIPHER && TextSecurePreferences.getNeedsSqlCipherMigration(context)) {
        if (legacyOpenHelper == null) {
          legacyOpenHelper = ClassicOpenHelper(context)
        }

        SQLCipherMigrationHelper.migrateCiphertext(
          context,
          masterSecret,
          legacyOpenHelper.writableDatabase,
          instance!!.rawWritableDatabase,
          listener
        )
      }
    }

    @JvmStatic
    fun runInTransaction(operation: Runnable) {
      instance!!.signalWritableDatabase.beginTransaction()
      try {
        operation.run()
        instance!!.signalWritableDatabase.setTransactionSuccessful()
      } finally {
        instance!!.signalWritableDatabase.endTransaction()
      }
    }

    @get:JvmStatic
    @get:JvmName("attachments")
    val attachments: AttachmentTable
      get() = instance!!.attachments

    @get:JvmStatic
    @get:JvmName("avatarPicker")
    val avatarPicker: AvatarPickerDatabase
      get() = instance!!.avatarPickerDatabase

    @get:JvmStatic
    @get:JvmName("cds")
    val cds: CdsTable
      get() = instance!!.cdsTable

    @get:JvmStatic
    @get:JvmName("chatColors")
    val chatColors: ChatColorsTable
      get() = instance!!.chatColorsTable

    @get:JvmStatic
    @get:JvmName("distributionLists")
    val distributionLists: DistributionListTables
      get() = instance!!.distributionListTables

    @get:JvmStatic
    @get:JvmName("donationReceipts")
    val donationReceipts: DonationReceiptTable
      get() = instance!!.donationReceiptTable

    @get:JvmStatic
    @get:JvmName("drafts")
    val drafts: DraftTable
      get() = instance!!.draftTable

    @get:JvmStatic
    @get:JvmName("emojiSearch")
    val emojiSearch: EmojiSearchTable
      get() = instance!!.emojiSearchTable

    @get:JvmStatic
    @get:JvmName("groupCallRings")
    val groupCallRings: GroupCallRingTable
      get() = instance!!.groupCallRingTable

    @get:JvmStatic
    @get:JvmName("groupReceipts")
    val groupReceipts: GroupReceiptTable
      get() = instance!!.groupReceiptTable

    @get:JvmStatic
    @get:JvmName("groups")
    val groups: GroupTable
      get() = instance!!.groupTable

    @get:JvmStatic
    @get:JvmName("identities")
    val identities: IdentityTable
      get() = instance!!.identityTable

    @get:JvmStatic
    @get:JvmName("media")
    val media: MediaTable
      get() = instance!!.media

    @get:JvmStatic
    @get:JvmName("mentions")
    val mentions: MentionTable
      get() = instance!!.mentionTable

    @get:JvmStatic
    @get:JvmName("messageSearch")
    val messageSearch: SearchTable
      get() = instance!!.searchTable

    @get:JvmStatic
    @get:JvmName("messageLog")
    val messageLog: MessageSendLogTables
      get() = instance!!.messageSendLogTables

    @get:JvmStatic
    @get:JvmName("mms")
    val mms: MmsTable
      get() = instance!!.mms

    @get:JvmStatic
    @get:JvmName("mmsSms")
    val mmsSms: MmsSmsTable
      get() = instance!!.mmsSmsTable

    @get:JvmStatic
    @get:JvmName("notificationProfiles")
    val notificationProfiles: NotificationProfileDatabase
      get() = instance!!.notificationProfileDatabase

    @get:JvmStatic
    @get:JvmName("payments")
    val payments: PaymentTable
      get() = instance!!.paymentTable

    @get:JvmStatic
    @get:JvmName("pendingRetryReceipts")
    val pendingRetryReceipts: PendingRetryReceiptTable
      get() = instance!!.pendingRetryReceiptTable

    @get:JvmStatic
    @get:JvmName("oneTimePreKeys")
    val oneTimePreKeys: OneTimePreKeyTable
      get() = instance!!.preKeyDatabase

    @get:Deprecated("This only exists to migrate from legacy storage. There shouldn't be any new usages.")
    @get:JvmStatic
    @get:JvmName("push")
    val push: PushTable
      get() = instance!!.pushTable

    @get:JvmStatic
    @get:JvmName("recipients")
    val recipients: RecipientTable
      get() = instance!!.recipientTable

    @get:JvmStatic
    @get:JvmName("signedPreKeys")
    val signedPreKeys: SignedPreKeyTable
      get() = instance!!.signedPreKeyTable

    @get:JvmStatic
    @get:JvmName("sms")
    val sms: SmsTable
      get() = instance!!.sms

    @get:JvmStatic
    @get:JvmName("threads")
    val threads: ThreadTable
      get() = instance!!.thread

    @get:JvmStatic
    @get:JvmName("reactions")
    val reactions: ReactionTable
      get() = instance!!.reactionTable

    @get:JvmStatic
    @get:JvmName("remappedRecords")
    val remappedRecords: RemappedRecordTables
      get() = instance!!.remappedRecordTables

    @get:JvmStatic
    @get:JvmName("senderKeys")
    val senderKeys: SenderKeyTable
      get() = instance!!.senderKeyTable

    @get:JvmStatic
    @get:JvmName("senderKeyShared")
    val senderKeyShared: SenderKeySharedTable
      get() = instance!!.senderKeySharedTable

    @get:JvmStatic
    @get:JvmName("sessions")
    val sessions: SessionTable
      get() = instance!!.sessionTable

    @get:JvmStatic
    @get:JvmName("stickers")
    val stickers: StickerTable
      get() = instance!!.stickerTable

    @get:JvmStatic
    @get:JvmName("storySends")
    val storySends: StorySendTable
      get() = instance!!.storySendTable

    @get:JvmStatic
    @get:JvmName("unknownStorageIds")
    val unknownStorageIds: UnknownStorageIdTable
      get() = instance!!.storageIdDatabase

    @get:JvmStatic
    @get:JvmName("remoteMegaphones")
    val remoteMegaphones: RemoteMegaphoneTable
      get() = instance!!.remoteMegaphoneTable

    @get:JvmStatic
    @get:JvmName("pendingPniSignatureMessages")
    val pendingPniSignatureMessages: PendingPniSignatureMessageTable
      get() = instance!!.pendingPniSignatureMessageTable
  }
}
