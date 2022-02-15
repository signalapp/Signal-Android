package org.thoughtcrime.securesms.database

import android.app.Application
import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.contacts.ContactsDatabase
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
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.RefreshPreKeysJob
import org.thoughtcrime.securesms.migrations.LegacyMigrationJob
import org.thoughtcrime.securesms.migrations.LegacyMigrationJob.DatabaseUpgradeListener
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.SqlUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.io.File
import java.lang.UnsupportedOperationException

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

  val sms: SmsDatabase = SmsDatabase(context, this)
  val mms: MmsDatabase = MmsDatabase(context, this)
  val attachments: AttachmentDatabase = AttachmentDatabase(context, this, attachmentSecret)
  val media: MediaDatabase = MediaDatabase(context, this)
  val thread: ThreadDatabase = ThreadDatabase(context, this)
  val mmsSmsDatabase: MmsSmsDatabase = MmsSmsDatabase(context, this)
  val identityDatabase: IdentityDatabase = IdentityDatabase(context, this)
  val draftDatabase: DraftDatabase = DraftDatabase(context, this)
  val pushDatabase: PushDatabase = PushDatabase(context, this)
  val groupDatabase: GroupDatabase = GroupDatabase(context, this)
  val recipientDatabase: RecipientDatabase = RecipientDatabase(context, this)
  val contactsDatabase: ContactsDatabase = ContactsDatabase(context)
  val groupReceiptDatabase: GroupReceiptDatabase = GroupReceiptDatabase(context, this)
  val preKeyDatabase: OneTimePreKeyDatabase = OneTimePreKeyDatabase(context, this)
  val signedPreKeyDatabase: SignedPreKeyDatabase = SignedPreKeyDatabase(context, this)
  val sessionDatabase: SessionDatabase = SessionDatabase(context, this)
  val senderKeyDatabase: SenderKeyDatabase = SenderKeyDatabase(context, this)
  val senderKeySharedDatabase: SenderKeySharedDatabase = SenderKeySharedDatabase(context, this)
  val pendingRetryReceiptDatabase: PendingRetryReceiptDatabase = PendingRetryReceiptDatabase(context, this)
  val searchDatabase: SearchDatabase = SearchDatabase(context, this)
  val stickerDatabase: StickerDatabase = StickerDatabase(context, this, attachmentSecret)
  val storageIdDatabase: UnknownStorageIdDatabase = UnknownStorageIdDatabase(context, this)
  val remappedRecordsDatabase: RemappedRecordsDatabase = RemappedRecordsDatabase(context, this)
  val mentionDatabase: MentionDatabase = MentionDatabase(context, this)
  val paymentDatabase: PaymentDatabase = PaymentDatabase(context, this)
  val chatColorsDatabase: ChatColorsDatabase = ChatColorsDatabase(context, this)
  val emojiSearchDatabase: EmojiSearchDatabase = EmojiSearchDatabase(context, this)
  val messageSendLogDatabase: MessageSendLogDatabase = MessageSendLogDatabase(context, this)
  val avatarPickerDatabase: AvatarPickerDatabase = AvatarPickerDatabase(context, this)
  val groupCallRingDatabase: GroupCallRingDatabase = GroupCallRingDatabase(context, this)
  val reactionDatabase: ReactionDatabase = ReactionDatabase(context, this)
  val notificationProfileDatabase: NotificationProfileDatabase = NotificationProfileDatabase(context, this)

  override fun onOpen(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
    db.enableWriteAheadLogging()
    db.setForeignKeyConstraintsEnabled(true)
  }

  override fun onCreate(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
    db.execSQL(SmsDatabase.CREATE_TABLE)
    db.execSQL(MmsDatabase.CREATE_TABLE)
    db.execSQL(AttachmentDatabase.CREATE_TABLE)
    db.execSQL(ThreadDatabase.CREATE_TABLE)
    db.execSQL(IdentityDatabase.CREATE_TABLE)
    db.execSQL(DraftDatabase.CREATE_TABLE)
    db.execSQL(PushDatabase.CREATE_TABLE)
    db.execSQL(GroupDatabase.CREATE_TABLE)
    db.execSQL(RecipientDatabase.CREATE_TABLE)
    db.execSQL(GroupReceiptDatabase.CREATE_TABLE)
    db.execSQL(OneTimePreKeyDatabase.CREATE_TABLE)
    db.execSQL(SignedPreKeyDatabase.CREATE_TABLE)
    db.execSQL(SessionDatabase.CREATE_TABLE)
    db.execSQL(SenderKeyDatabase.CREATE_TABLE)
    db.execSQL(SenderKeySharedDatabase.CREATE_TABLE)
    db.execSQL(PendingRetryReceiptDatabase.CREATE_TABLE)
    db.execSQL(StickerDatabase.CREATE_TABLE)
    db.execSQL(UnknownStorageIdDatabase.CREATE_TABLE)
    db.execSQL(MentionDatabase.CREATE_TABLE)
    db.execSQL(PaymentDatabase.CREATE_TABLE)
    db.execSQL(ChatColorsDatabase.CREATE_TABLE)
    db.execSQL(EmojiSearchDatabase.CREATE_TABLE)
    db.execSQL(AvatarPickerDatabase.CREATE_TABLE)
    db.execSQL(GroupCallRingDatabase.CREATE_TABLE)
    db.execSQL(ReactionDatabase.CREATE_TABLE)
    executeStatements(db, SearchDatabase.CREATE_TABLE)
    executeStatements(db, RemappedRecordsDatabase.CREATE_TABLE)
    executeStatements(db, MessageSendLogDatabase.CREATE_TABLE)
    executeStatements(db, NotificationProfileDatabase.CREATE_TABLE)

    executeStatements(db, RecipientDatabase.CREATE_INDEXS)
    executeStatements(db, SmsDatabase.CREATE_INDEXS)
    executeStatements(db, MmsDatabase.CREATE_INDEXS)
    executeStatements(db, AttachmentDatabase.CREATE_INDEXS)
    executeStatements(db, ThreadDatabase.CREATE_INDEXS)
    executeStatements(db, DraftDatabase.CREATE_INDEXS)
    executeStatements(db, GroupDatabase.CREATE_INDEXS)
    executeStatements(db, GroupReceiptDatabase.CREATE_INDEXES)
    executeStatements(db, StickerDatabase.CREATE_INDEXES)
    executeStatements(db, UnknownStorageIdDatabase.CREATE_INDEXES)
    executeStatements(db, MentionDatabase.CREATE_INDEXES)
    executeStatements(db, PaymentDatabase.CREATE_INDEXES)
    executeStatements(db, MessageSendLogDatabase.CREATE_INDEXES)
    executeStatements(db, GroupCallRingDatabase.CREATE_INDEXES)
    executeStatements(db, NotificationProfileDatabase.CREATE_INDEXES)

    executeStatements(db, MessageSendLogDatabase.CREATE_TRIGGERS)
    executeStatements(db, ReactionDatabase.CREATE_TRIGGERS)

    if (context.getDatabasePath(ClassicOpenHelper.NAME).exists()) {
      val legacyHelper = ClassicOpenHelper(context)
      val legacyDb = legacyHelper.writableDatabase
      SQLCipherMigrationHelper.migratePlaintext(context, legacyDb, db)
      val masterSecret = KeyCachingService.getMasterSecret(context)
      if (masterSecret != null) SQLCipherMigrationHelper.migrateCiphertext(context, masterSecret, legacyDb, db, null) else TextSecurePreferences.setNeedsSqlCipherMigration(context, true)
      if (!PreKeyMigrationHelper.migratePreKeys(context, db)) {
        ApplicationDependencies.getJobManager().add(RefreshPreKeysJob())
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
          }
        }
      }
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
        instance!!.reactionDatabase.deleteAbandonedReactions()
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
    val attachments: AttachmentDatabase
      get() = instance!!.attachments

    @get:JvmStatic
    @get:JvmName("avatarPicker")
    val avatarPicker: AvatarPickerDatabase
      get() = instance!!.avatarPickerDatabase

    @get:JvmStatic
    @get:JvmName("chatColors")
    val chatColors: ChatColorsDatabase
      get() = instance!!.chatColorsDatabase

    @get:JvmStatic
    @get:JvmName("contacts")
    val contacts: ContactsDatabase
      get() = instance!!.contactsDatabase

    @get:JvmStatic
    @get:JvmName("drafts")
    val drafts: DraftDatabase
      get() = instance!!.draftDatabase

    @get:JvmStatic
    @get:JvmName("emojiSearch")
    val emojiSearch: EmojiSearchDatabase
      get() = instance!!.emojiSearchDatabase

    @get:JvmStatic
    @get:JvmName("groupCallRings")
    val groupCallRings: GroupCallRingDatabase
      get() = instance!!.groupCallRingDatabase

    @get:JvmStatic
    @get:JvmName("groupReceipts")
    val groupReceipts: GroupReceiptDatabase
      get() = instance!!.groupReceiptDatabase

    @get:JvmStatic
    @get:JvmName("groups")
    val groups: GroupDatabase
      get() = instance!!.groupDatabase

    @get:JvmStatic
    @get:JvmName("identities")
    val identities: IdentityDatabase
      get() = instance!!.identityDatabase

    @get:JvmStatic
    @get:JvmName("media")
    val media: MediaDatabase
      get() = instance!!.media

    @get:JvmStatic
    @get:JvmName("mentions")
    val mentions: MentionDatabase
      get() = instance!!.mentionDatabase

    @get:JvmStatic
    @get:JvmName("messageSearch")
    val messageSearch: SearchDatabase
      get() = instance!!.searchDatabase

    @get:JvmStatic
    @get:JvmName("messageLog")
    val messageLog: MessageSendLogDatabase
      get() = instance!!.messageSendLogDatabase

    @get:JvmStatic
    @get:JvmName("mms")
    val mms: MmsDatabase
      get() = instance!!.mms

    @get:JvmStatic
    @get:JvmName("mmsSms")
    val mmsSms: MmsSmsDatabase
      get() = instance!!.mmsSmsDatabase

    @get:JvmStatic
    @get:JvmName("payments")
    val payments: PaymentDatabase
      get() = instance!!.paymentDatabase

    @get:JvmStatic
    @get:JvmName("pendingRetryReceipts")
    val pendingRetryReceipts: PendingRetryReceiptDatabase
      get() = instance!!.pendingRetryReceiptDatabase

    @get:JvmStatic
    @get:JvmName("preKeys")
    val preKeys: OneTimePreKeyDatabase
      get() = instance!!.preKeyDatabase

    @get:Deprecated("This only exists to migrate from legacy storage. There shouldn't be any new usages.")
    @get:JvmStatic
    @get:JvmName("push")
    val push: PushDatabase
      get() = instance!!.pushDatabase

    @get:JvmStatic
    @get:JvmName("recipients")
    val recipients: RecipientDatabase
      get() = instance!!.recipientDatabase

    @get:JvmStatic
    @get:JvmName("signedPreKeys")
    val signedPreKeys: SignedPreKeyDatabase
      get() = instance!!.signedPreKeyDatabase

    @get:JvmStatic
    @get:JvmName("sms")
    val sms: SmsDatabase
      get() = instance!!.sms

    @get:JvmStatic
    @get:JvmName("threads")
    val threads: ThreadDatabase
      get() = instance!!.thread

    @get:JvmStatic
    @get:JvmName("reactions")
    val reactions: ReactionDatabase
      get() = instance!!.reactionDatabase

    @get:JvmStatic
    @get:JvmName("remappedRecords")
    val remappedRecords: RemappedRecordsDatabase
      get() = instance!!.remappedRecordsDatabase

    @get:JvmStatic
    @get:JvmName("senderKeys")
    val senderKeys: SenderKeyDatabase
      get() = instance!!.senderKeyDatabase

    @get:JvmStatic
    @get:JvmName("senderKeyShared")
    val senderKeyShared: SenderKeySharedDatabase
      get() = instance!!.senderKeySharedDatabase

    @get:JvmStatic
    @get:JvmName("sessions")
    val sessions: SessionDatabase
      get() = instance!!.sessionDatabase

    @get:JvmStatic
    @get:JvmName("stickers")
    val stickers: StickerDatabase
      get() = instance!!.stickerDatabase

    @get:JvmStatic
    @get:JvmName("unknownStorageIds")
    val unknownStorageIds: UnknownStorageIdDatabase
      get() = instance!!.storageIdDatabase

    @get:JvmStatic
    @get:JvmName("notificationProfiles")
    val notificationProfiles: NotificationProfileDatabase
      get() = instance!!.notificationProfileDatabase
  }
}
