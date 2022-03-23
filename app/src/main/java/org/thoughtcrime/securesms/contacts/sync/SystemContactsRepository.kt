package org.thoughtcrime.securesms.contacts.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.content.OperationApplicationException
import android.net.Uri
import android.os.RemoteException
import android.provider.BaseColumns
import android.provider.ContactsContract
import org.signal.core.util.ListUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.requireInt
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.util.Util
import java.util.ArrayList
import java.util.HashMap

/**
 * A way to retrieve and update data in the Android system contacts.
 */
object SystemContactsRepository {

  private val TAG = Log.tag(SystemContactsRepository::class.java)
  private const val CONTACT_MIMETYPE = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact"
  private const val CALL_MIMETYPE = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.call"
  private const val SYNC = "__TS"

  @JvmStatic
  fun getOrCreateSystemAccount(context: Context): Account? {
    val accountManager: AccountManager = AccountManager.get(context)
    val accounts: Array<Account> = accountManager.getAccountsByType(BuildConfig.APPLICATION_ID)
    var account: Account? = if (accounts.isNotEmpty()) accounts[0] else null

    if (account == null) {
      Log.i(TAG, "Attempting to create a new account...")
      val newAccount = Account(context.getString(R.string.app_name), BuildConfig.APPLICATION_ID)

      if (accountManager.addAccountExplicitly(newAccount, null, null)) {
        Log.i(TAG, "Successfully created a new account.")
        ContentResolver.setIsSyncable(newAccount, ContactsContract.AUTHORITY, 1)
        account = newAccount
      } else {
        Log.w(TAG, "Failed to create a new account!")
      }
    }

    if (account != null && !ContentResolver.getSyncAutomatically(account, ContactsContract.AUTHORITY)) {
      Log.i(TAG, "Updated account to sync automatically.")
      ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)
    }

    return account
  }

  @JvmStatic
  @Synchronized
  fun removeDeletedRawContacts(context: Context, account: Account) {
    val currentContactsUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
      .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
      .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
      .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
      .build()

    val projection = arrayOf(BaseColumns._ID, ContactsContract.RawContacts.SYNC1)

    context.contentResolver.query(currentContactsUri, projection, "${ContactsContract.RawContacts.DELETED} = ?", SqlUtil.buildArgs(1), null)?.use { cursor ->
      while (cursor.moveToNext()) {
        val rawContactId = cursor.getLong(0)

        Log.i(TAG, """Deleting raw contact: ${cursor.getString(1)}, $rawContactId""")
        context.contentResolver.delete(currentContactsUri, "${ContactsContract.RawContacts._ID} = ?", arrayOf(rawContactId.toString()))
      }
    }
  }

  @JvmStatic
  @Synchronized
  @Throws(RemoteException::class, OperationApplicationException::class)
  fun setRegisteredUsers(
    context: Context,
    account: Account,
    registeredAddressList: List<String>,
    remove: Boolean
  ) {
    val registeredAddressSet: Set<String> = registeredAddressList.toSet()
    val operations: ArrayList<ContentProviderOperation> = ArrayList()
    val currentContacts: Map<String, SignalContact> = getSignalRawContacts(context, account)

    val registeredChunks: List<List<String>> = ListUtil.chunk(registeredAddressList, 50)
    for (registeredChunk in registeredChunks) {
      for (registeredAddress in registeredChunk) {
        if (!currentContacts.containsKey(registeredAddress)) {
          val systemContactInfo: SystemContactInfo? = getSystemContactInfo(context, registeredAddress)
          if (systemContactInfo != null) {
            Log.i(TAG, "Adding number: $registeredAddress")
            addTextSecureRawContact(
              context = context,
              operations = operations,
              account = account,
              e164number = systemContactInfo.number,
              displayName = systemContactInfo.name,
              aggregateId = systemContactInfo.id
            )
          }
        }
      }

      if (operations.isNotEmpty()) {
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
        operations.clear()
      }
    }

    for ((key, value) in currentContacts) {
      if (!registeredAddressSet.contains(key)) {
        if (remove) {
          Log.i(TAG, "Removing number: $key")
          removeTextSecureRawContact(operations, account, value.id)
        }
      } else if (!value.isVoiceSupported()) {
        Log.i(TAG, "Adding voice support: $key")
        addContactVoiceSupport(context, operations, key, value.id)
      } else if (!Util.isStringEquals(value.rawDisplayName, value.aggregateDisplayName)) {
        Log.i(TAG, "Updating display name: $key")
        updateDisplayName(operations, value.aggregateDisplayName, value.id, value.displayNameSource)
      }
    }

    if (operations.isNotEmpty()) {
      applyOperationsInBatches(context.contentResolver, ContactsContract.AUTHORITY, operations, 50)
    }
  }

  @JvmStatic
  fun getNameDetails(context: Context, contactId: Long): NameDetails? {
    val projection = arrayOf(
      ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
      ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
      ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
      ContactsContract.CommonDataKinds.StructuredName.PREFIX,
      ContactsContract.CommonDataKinds.StructuredName.SUFFIX,
      ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME
    )
    val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val args = SqlUtil.buildArgs(contactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)

    return context.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        NameDetails(
          displayName = cursor.requireString(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME),
          givenName = cursor.requireString(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME),
          familyName = cursor.requireString(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME),
          prefix = cursor.requireString(ContactsContract.CommonDataKinds.StructuredName.PREFIX),
          suffix = cursor.requireString(ContactsContract.CommonDataKinds.StructuredName.SUFFIX),
          middleName = cursor.requireString(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME)
        )
      } else {
        null
      }
    }
  }

  @JvmStatic
  fun getOrganizationName(context: Context, contactId: Long): String? {
    val projection = arrayOf(ContactsContract.CommonDataKinds.Organization.COMPANY)
    val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val args = SqlUtil.buildArgs(contactId, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)

    context.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        return cursor.getString(0)
      }
    }

    return null
  }

  @JvmStatic
  fun getPhoneDetails(context: Context, contactId: Long): List<PhoneDetails> {
    val projection = arrayOf(
      ContactsContract.CommonDataKinds.Phone.NUMBER,
      ContactsContract.CommonDataKinds.Phone.TYPE,
      ContactsContract.CommonDataKinds.Phone.LABEL
    )
    val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val args = SqlUtil.buildArgs(contactId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)

    val phoneDetails: MutableList<PhoneDetails> = mutableListOf()

    context.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
      while (cursor.moveToNext()) {
        phoneDetails += PhoneDetails(
          number = cursor.requireString(ContactsContract.CommonDataKinds.Phone.NUMBER),
          type = cursor.requireInt(ContactsContract.CommonDataKinds.Phone.TYPE),
          label = cursor.requireString(ContactsContract.CommonDataKinds.Phone.LABEL)
        )
      }
    }

    return phoneDetails
  }

  @JvmStatic
  fun getEmailDetails(context: Context, contactId: Long): List<EmailDetails> {
    val projection = arrayOf(
      ContactsContract.CommonDataKinds.Email.ADDRESS,
      ContactsContract.CommonDataKinds.Email.TYPE,
      ContactsContract.CommonDataKinds.Email.LABEL
    )
    val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val args = SqlUtil.buildArgs(contactId, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)

    val emailDetails: MutableList<EmailDetails> = mutableListOf()
    context.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
      while (cursor.moveToNext()) {
        emailDetails += EmailDetails(
          address = cursor.requireString(ContactsContract.CommonDataKinds.Email.ADDRESS),
          type = cursor.requireInt(ContactsContract.CommonDataKinds.Email.TYPE),
          label = cursor.requireString(ContactsContract.CommonDataKinds.Email.LABEL)
        )
      }
    }

    return emailDetails
  }

  @JvmStatic
  fun getPostalAddressDetails(context: Context, contactId: Long): List<PostalAddressDetails> {
    val projection = arrayOf(
      ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
      ContactsContract.CommonDataKinds.StructuredPostal.LABEL,
      ContactsContract.CommonDataKinds.StructuredPostal.STREET,
      ContactsContract.CommonDataKinds.StructuredPostal.POBOX,
      ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD,
      ContactsContract.CommonDataKinds.StructuredPostal.CITY,
      ContactsContract.CommonDataKinds.StructuredPostal.REGION,
      ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,
      ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY
    )
    val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val args = SqlUtil.buildArgs(contactId, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)

    val postalDetails: MutableList<PostalAddressDetails> = mutableListOf()

    context.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
      while (cursor.moveToNext()) {
        postalDetails += PostalAddressDetails(
          type = cursor.requireInt(ContactsContract.CommonDataKinds.StructuredPostal.TYPE),
          label = cursor.requireString(ContactsContract.CommonDataKinds.StructuredPostal.LABEL),
          street = cursor.requireString(ContactsContract.CommonDataKinds.StructuredPostal.STREET),
          poBox = cursor.requireString(ContactsContract.CommonDataKinds.StructuredPostal.POBOX),
          neighborhood = cursor.requireString(ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD),
          city = cursor.requireString(ContactsContract.CommonDataKinds.StructuredPostal.CITY),
          region = cursor.requireString(ContactsContract.CommonDataKinds.StructuredPostal.REGION),
          postal = cursor.requireString(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE),
          country = cursor.requireString(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY)
        )
      }
    }

    return postalDetails
  }

  @JvmStatic
  fun getAvatarUri(context: Context, contactId: Long): Uri? {
    val projection = arrayOf(ContactsContract.CommonDataKinds.Photo.PHOTO_URI)
    val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val args = SqlUtil.buildArgs(contactId, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)

    context.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val uri = cursor.getString(0)
        if (uri != null) {
          return Uri.parse(uri)
        }
      }
    }

    return null
  }

  private fun addContactVoiceSupport(context: Context, operations: MutableList<ContentProviderOperation>, address: String, rawContactId: Long) {
    operations.add(
      ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI)
        .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawContactId.toString()))
        .withValue(ContactsContract.RawContacts.SYNC4, "true")
        .build()
    )

    operations.add(
      ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
        .withValue(ContactsContract.Data.MIMETYPE, CALL_MIMETYPE)
        .withValue(ContactsContract.Data.DATA1, address)
        .withValue(ContactsContract.Data.DATA2, context.getString(R.string.app_name))
        .withValue(ContactsContract.Data.DATA3, context.getString(R.string.ContactsDatabase_signal_call_s, address))
        .withYieldAllowed(true)
        .build()
    )
  }

  private fun updateDisplayName(operations: MutableList<ContentProviderOperation>, displayName: String?, rawContactId: Long, displayNameSource: Int) {
    val dataUri = ContactsContract.Data.CONTENT_URI.buildUpon()
      .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
      .build()

    if (displayNameSource != ContactsContract.DisplayNameSources.STRUCTURED_NAME) {
      operations.add(
        ContentProviderOperation.newInsert(dataUri)
          .withValue(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, rawContactId)
          .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
          .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
          .build()
      )
    } else {
      operations.add(
        ContentProviderOperation.newUpdate(dataUri)
          .withSelection("${ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?", SqlUtil.buildArgs(rawContactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE))
          .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
          .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
          .build()
      )
    }
  }

  private fun addTextSecureRawContact(
    context: Context,
    operations: MutableList<ContentProviderOperation>,
    account: Account,
    e164number: String,
    displayName: String,
    aggregateId: Long
  ) {
    val index = operations.size
    val dataUri = ContactsContract.Data.CONTENT_URI.buildUpon()
      .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
      .build()

    operations.add(
      ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
        .withValue(ContactsContract.RawContacts.SYNC1, e164number)
        .withValue(ContactsContract.RawContacts.SYNC4, true.toString())
        .build()
    )
    operations.add(
      ContentProviderOperation.newInsert(dataUri)
        .withValueBackReference(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, index)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        .build()
    )
    operations.add(
      ContentProviderOperation.newInsert(dataUri)
        .withValueBackReference(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID, index)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, e164number)
        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_OTHER)
        .withValue(ContactsContract.Data.SYNC2, SYNC)
        .build()
    )
    operations.add(
      ContentProviderOperation.newInsert(dataUri)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
        .withValue(ContactsContract.Data.MIMETYPE, CONTACT_MIMETYPE)
        .withValue(ContactsContract.Data.DATA1, e164number)
        .withValue(ContactsContract.Data.DATA2, context.getString(R.string.app_name))
        .withValue(ContactsContract.Data.DATA3, context.getString(R.string.ContactsDatabase_message_s, e164number))
        .withYieldAllowed(true)
        .build()
    )
    operations.add(
      ContentProviderOperation.newInsert(dataUri)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
        .withValue(ContactsContract.Data.MIMETYPE, CALL_MIMETYPE)
        .withValue(ContactsContract.Data.DATA1, e164number)
        .withValue(ContactsContract.Data.DATA2, context.getString(R.string.app_name))
        .withValue(ContactsContract.Data.DATA3, context.getString(R.string.ContactsDatabase_signal_call_s, e164number))
        .withYieldAllowed(true)
        .build()
    )
    operations.add(
      ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
        .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, aggregateId)
        .withValueBackReference(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, index)
        .withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
        .build()
    )
  }

  private fun removeTextSecureRawContact(operations: MutableList<ContentProviderOperation>, account: Account, rowId: Long) {
    operations.add(
      ContentProviderOperation.newDelete(
        ContactsContract.RawContacts.CONTENT_URI.buildUpon()
          .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
          .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
          .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build()
      )
        .withYieldAllowed(true)
        .withSelection("${BaseColumns._ID} = ?", SqlUtil.buildArgs(rowId))
        .build()
    )
  }

  private fun getSignalRawContacts(context: Context, account: Account): Map<String, SignalContact> {
    val currentContactsUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
      .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
      .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type).build()
    val projection = arrayOf(BaseColumns._ID, ContactsContract.RawContacts.SYNC1, ContactsContract.RawContacts.SYNC4, ContactsContract.RawContacts.CONTACT_ID, ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY, ContactsContract.RawContacts.DISPLAY_NAME_SOURCE)

    val signalContacts: MutableMap<String, SignalContact> = HashMap()

    context.contentResolver.query(currentContactsUri, projection, null, null, null)?.use { cursor ->
      while (cursor.moveToNext()) {
        val currentAddress = PhoneNumberFormatter.get(context).format(cursor.getString(1))

        signalContacts[currentAddress] = SignalContact(
          id = cursor.getLong(0),
          supportsVoice = cursor.getString(2),
          rawDisplayName = cursor.getString(4),
          aggregateDisplayName = getDisplayName(context, cursor.getLong(3)),
          displayNameSource = cursor.getInt(5)
        )
      }
    }

    return signalContacts
  }

  private fun getSystemContactInfo(context: Context, address: String): SystemContactInfo? {
    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
    val projection = arrayOf(
      ContactsContract.PhoneLookup.NUMBER,
      ContactsContract.PhoneLookup._ID,
      ContactsContract.PhoneLookup.DISPLAY_NAME
    )

    context.contentResolver.query(uri, projection, null, null, null)?.use { numberCursor ->
      while (numberCursor.moveToNext()) {
        val systemNumber = numberCursor.getString(0)
        val systemAddress = PhoneNumberFormatter.get(context).format(systemNumber)
        if (systemAddress == address) {
          context.contentResolver.query(ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID), "${ContactsContract.RawContacts.CONTACT_ID} = ? ", SqlUtil.buildArgs(numberCursor.getLong(1)), null)?.use { idCursor ->
            if (idCursor.moveToNext()) {
              return SystemContactInfo(
                name = numberCursor.getString(2),
                number = numberCursor.getString(0),
                id = idCursor.getLong(0)
              )
            }
          }
        }
      }
    }

    return null
  }

  private fun getDisplayName(context: Context, contactId: Long): String? {
    val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
    val selection = "${ContactsContract.Contacts._ID} = ?"
    val args = SqlUtil.buildArgs(contactId)

    context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        return cursor.getString(0)
      }
    }

    return null
  }

  @Throws(OperationApplicationException::class, RemoteException::class)
  private fun applyOperationsInBatches(
    contentResolver: ContentResolver,
    authority: String,
    operations: List<ContentProviderOperation>,
    batchSize: Int
  ) {
    val batches = ListUtil.chunk(operations, batchSize)
    for (batch in batches) {
      contentResolver.applyBatch(authority, ArrayList(batch))
    }
  }

  private data class SystemContactInfo(val name: String, val number: String, val id: Long)

  private data class SignalContact(
    val id: Long,
    val supportsVoice: String?,
    val rawDisplayName: String?,
    val aggregateDisplayName: String?,
    val displayNameSource: Int
  ) {
    fun isVoiceSupported(): Boolean {
      return "true" == supportsVoice
    }
  }

  data class NameDetails(
    val displayName: String?,
    val givenName: String?,
    val familyName: String?,
    val prefix: String?,
    val suffix: String?,
    val middleName: String?
  )

  data class PhoneDetails(
    val number: String?,
    val type: Int,
    val label: String?
  )

  data class EmailDetails(
    val address: String?,
    val type: Int,
    val label: String?
  )

  data class PostalAddressDetails(
    val type: Int,
    val label: String?,
    val street: String?,
    val poBox: String?,
    val neighborhood: String?,
    val city: String?,
    val region: String?,
    val postal: String?,
    val country: String?
  )
}
