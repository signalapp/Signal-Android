package org.signal.contacts

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.content.OperationApplicationException
import android.database.Cursor
import android.net.Uri
import android.os.RemoteException
import android.provider.BaseColumns
import android.provider.ContactsContract
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import java.io.Closeable
import java.util.Objects

/**
 * A way to retrieve and update data in the Android system contacts.
 *
 * Contacts in Android are miserable, but they're reasonably well-documented here:
 * https://developer.android.com/guide/topics/providers/contacts-provider
 *
 * But here's a summary of how contacts are stored.
 *
 * There's three main entities:
 * - Contacts
 * - RawContacts
 * - ContactData
 *
 * Each Contact can have multiple RawContacts associated with it, and each RawContact can have multiple ContactDatas associated with it.
 *
 *       ┌───────Contact────────┐
 *       │          │           │
 *       ▼          ▼           ▼
 *   RawContact  RawContact  RawContact
 *     │           │           │
 *     ├─►Data     ├─►Data     ├─►Data
 *     │           │           │
 *     ├─►Data     ├─►Data     ├─►Data
 *     │           │           │
 *     └─►Data     └─►Data     └─►Data
 *
 * (Shortened ContactData -> Data for space)
 *
 * How are they linked together?
 * - Each RawContact has a [ContactsContract.RawContacts.CONTACT_ID] that links to a [ContactsContract.Contacts._ID]
 * - Each ContactData has a [ContactsContract.Data.RAW_CONTACT_ID] column that links to a [ContactsContract.RawContacts._ID]
 * - Each ContactData has a [ContactsContract.Data.CONTACT_ID] column that links to a [ContactsContract.Contacts._ID]
 * - Each ContactData has a [ContactsContract.Data.LOOKUP_KEY] column that links to a [ContactsContract.Contacts.LOOKUP_KEY]
 *   - The lookup key is a way to link back to a Contact in a more stable way. Apparently linking using the CONTACT_ID can lead to unstable results if a sync
 *     is happening or data is otherwise corrupted.
 *
 * What type of stuff are stored in each?
 * - Contact only really has metadata about the contact. Basically the stuff you see at the top of the contact entry in the contacts app, like:
 *   - Photo
 *   - Display name (*not* structured name)
 *   - Whether or not it's starred
 * - RawContact also only really has metadata, largely about which account it's bound to
 * - ContactData is where all the actual contact details are, stuff like:
 *   - Phone
 *   - Email
 *   - Structured name
 *   - Address
 * - ContactData has a [ContactsContract.Data.MIMETYPE] that will tell you what kind of data is it. Common ones are [ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE]
 *   and [ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE]
 * - You can imagine that it's tricky to come up with a schema that can store arbitrary contact data -- that's why a lot of the columns in ContactData are just
 *   generic things, like [ContactsContract.Data.DATA1]. Thankfully aliases have been provided for common types, like [ContactsContract.CommonDataKinds.Phone.NUMBER],
 *   which is an alias for [ContactsContract.Data.DATA1].
 *
 *
 */
object SystemContactsRepository {

  private val TAG = Log.tag(SystemContactsRepository::class.java)

  private const val FIELD_DISPLAY_PHONE = ContactsContract.RawContacts.SYNC1
  private const val FIELD_TAG = ContactsContract.Data.SYNC2
  private const val FIELD_SUPPORTS_VOICE = ContactsContract.RawContacts.SYNC4

  /**
   * Gets and returns an iterator over data for all contacts, containing both phone number data and structured name data.
   *
   * In order to get all of this in one query, we have to query all of the ContactData items with the appropriate mimetypes, and then group it together by
   * lookup key.
   */
  @JvmStatic
  fun getAllSystemContacts(context: Context, e164Formatter: (String) -> String?): ContactIterator {
    val uri = ContactsContract.Data.CONTENT_URI
    val projection = arrayOf(
      ContactsContract.Data.MIMETYPE,
      ContactsContract.CommonDataKinds.Phone.NUMBER,
      ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
      ContactsContract.CommonDataKinds.Phone.LABEL,
      ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
      ContactsContract.CommonDataKinds.Phone._ID,
      ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
      ContactsContract.CommonDataKinds.Phone.TYPE,
      ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
      ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME
    )
    val where = "${ContactsContract.Data.MIMETYPE} IN (?, ?)"
    val args = SqlUtil.buildArgs(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
    val orderBy = "${ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY} ASC, ${ContactsContract.Data.MIMETYPE} DESC, ${ContactsContract.CommonDataKinds.Phone._ID} DESC"

    val cursor: Cursor = context.contentResolver.query(uri, projection, where, args, orderBy) ?: return EmptyContactIterator()

    return CursorContactIterator(cursor, e164Formatter)
  }

  @JvmStatic
  fun getContactDetailsByQueries(context: Context, queries: List<String>, e164Formatter: (String) -> String?): ContactIterator {
    val lookupKeys: MutableSet<String> = mutableSetOf()

    for (query in queries) {
      val lookupKeyUri: Uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
      context.contentResolver.query(lookupKeyUri, arrayOf(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY), null, null, null).use { cursor ->
        while (cursor != null && cursor.moveToNext()) {
          val lookup: String? = cursor.requireString(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
          if (lookup != null) {
            lookupKeys += lookup
          }
        }
      }
    }

    if (lookupKeys.isEmpty()) {
      return EmptyContactIterator()
    }

    val uri = ContactsContract.Data.CONTENT_URI
    val projection = arrayOf(
      ContactsContract.Data.MIMETYPE,
      ContactsContract.CommonDataKinds.Phone.NUMBER,
      ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
      ContactsContract.CommonDataKinds.Phone.LABEL,
      ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
      ContactsContract.CommonDataKinds.Phone._ID,
      ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
      ContactsContract.CommonDataKinds.Phone.TYPE,
      ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
      ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME
    )

    val lookupPlaceholder = lookupKeys.map { "?" }.joinToString(separator = ",")

    val where = "${ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY} IN ($lookupPlaceholder) AND ${ContactsContract.Data.MIMETYPE} IN (?, ?)"
    val args = lookupKeys.toTypedArray() + SqlUtil.buildArgs(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
    val orderBy = "${ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY} ASC, ${ContactsContract.Data.MIMETYPE} DESC, ${ContactsContract.CommonDataKinds.Phone._ID} DESC"

    val cursor: Cursor = context.contentResolver.query(uri, projection, where, args, orderBy) ?: return EmptyContactIterator()
    return CursorContactIterator(cursor, e164Formatter)
  }

  /**
   * Retrieves all unique display numbers in the system contacts. (By display, we mean not-E164-formatted)
   */
  @JvmStatic
  fun getAllDisplayNumbers(context: Context): Set<String> {
    val results: MutableSet<String> = mutableSetOf()

    context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null).use { cursor ->
      while (cursor != null && cursor.moveToNext()) {
        val formattedPhone: String? = cursor.requireString(ContactsContract.CommonDataKinds.Phone.NUMBER)
        if (formattedPhone != null && formattedPhone.isNotEmpty()) {
          results.add(formattedPhone)
        }
      }
    }
    return results
  }

  /**
   * Retrieves a system account for the provided applicationId, creating one if necessary.
   */
  @JvmStatic
  fun getOrCreateSystemAccount(context: Context, applicationId: String, accountDisplayName: String): Account? {
    val accountManager: AccountManager = AccountManager.get(context)
    val accounts: Array<Account> = accountManager.getAccountsByType(applicationId)
    var account: Account? = if (accounts.isNotEmpty()) accounts[0] else null

    if (account == null) {
      try {
        Log.i(TAG, "Attempting to create a new account...")
        val newAccount = Account(accountDisplayName, applicationId)

        if (accountManager.addAccountExplicitly(newAccount, null, null)) {
          Log.i(TAG, "Successfully created a new account.")
          ContentResolver.setIsSyncable(newAccount, ContactsContract.AUTHORITY, 1)
          account = newAccount
        } else {
          Log.w(TAG, "Failed to create a new account!")
        }
      } catch (e: SecurityException) {
        Log.w(TAG, "Failed to add an account.", e)
      }
    }

    if (account != null && !ContentResolver.getSyncAutomatically(account, ContactsContract.AUTHORITY)) {
      Log.i(TAG, "Updated account to sync automatically.")
      ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)
    }

    return account
  }

  /**
   * Deletes all raw contacts the specified account that are flagged as deleted.
   */
  @JvmStatic
  @Synchronized
  fun removeDeletedRawContactsForAccount(context: Context, account: Account) {
    val currentContactsUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
      .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
      .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
      .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
      .build()

    val projection = arrayOf(BaseColumns._ID, FIELD_DISPLAY_PHONE)

    // TODO Could we write this as a single delete(DELETED = true)?
    context.contentResolver.query(currentContactsUri, projection, "${ContactsContract.RawContacts.DELETED} = ?", SqlUtil.buildArgs(1), null)?.use { cursor ->
      while (cursor.moveToNext()) {
        val rawContactId = cursor.requireLong(BaseColumns._ID)

        Log.i(TAG, "Deleting raw contact: ${cursor.requireString(FIELD_DISPLAY_PHONE)}, $rawContactId")
        context.contentResolver.delete(currentContactsUri, "${ContactsContract.RawContacts._ID} = ?", SqlUtil.buildArgs(rawContactId))
      }
    }
  }

  /**
   * Adds links to message and call using your app to the system contacts.
   * [config] Your configuration object.
   * [targetE164s] A list of E164s whose contact entries you would like to add links to.
   * [removeIfMissing] If true, links will be removed from all contacts not in the [targetE164s].
   */
  @JvmStatic
  @Synchronized
  @Throws(RemoteException::class, OperationApplicationException::class)
  fun addMessageAndCallLinksToContacts(
    context: Context,
    config: ContactLinkConfiguration,
    targetE164s: Set<String>,
    removeIfMissing: Boolean
  ) {
    val operations: ArrayList<ContentProviderOperation> = ArrayList()
    val currentLinkedContacts: Map<String, LinkedContactDetails> = getLinkedContactsByE164(context, config.account, config.e164Formatter)

    val targetChunks: List<List<String>> = targetE164s.chunked(50).toList()
    for (targetChunk in targetChunks) {
      for (target in targetChunk) {
        if (!currentLinkedContacts.containsKey(target)) {
          val systemContactInfo: SystemContactInfo? = getSystemContactInfo(context, target, config.e164Formatter)
          if (systemContactInfo != null) {
            Log.i(TAG, "Adding number: $target")
            operations += buildAddRawContactOperations(
              operationIndex = operations.size,
              linkConfig = config,
              systemContactInfo = systemContactInfo
            )
          }
        }
      }

      if (operations.isNotEmpty()) {
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
        operations.clear()
      }
    }

    for ((e164, details) in currentLinkedContacts) {
      if (!targetE164s.contains(e164)) {
        if (removeIfMissing) {
          Log.i(TAG, "Removing number: $e164")
          removeLinkedContact(operations, config.account, details.id)
        }
      } else if (!Objects.equals(details.rawDisplayName, details.aggregateDisplayName)) {
        Log.i(TAG, "Updating display name: $e164")
        operations += buildUpdateDisplayNameOperations(details.aggregateDisplayName, details.id, details.displayNameSource)
      }
    }

    if (operations.isNotEmpty()) {
      operations
        .chunked(50)
        .forEach { batch ->
          context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(batch))
        }
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

  private fun buildUpdateDisplayNameOperations(
    displayName: String?,
    rawContactId: Long,
    displayNameSource: Int
  ): ContentProviderOperation {
    val dataUri = ContactsContract.Data.CONTENT_URI.buildUpon()
      .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
      .build()

    return if (displayNameSource != ContactsContract.DisplayNameSources.STRUCTURED_NAME) {
      ContentProviderOperation.newInsert(dataUri)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, rawContactId)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        .build()
    } else {
      ContentProviderOperation.newUpdate(dataUri)
        .withSelection("${ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?", SqlUtil.buildArgs(rawContactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE))
        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        .build()
    }
  }

  private fun buildAddRawContactOperations(
    operationIndex: Int,
    linkConfig: ContactLinkConfiguration,
    systemContactInfo: SystemContactInfo
  ): List<ContentProviderOperation> {
    val dataUri = ContactsContract.Data.CONTENT_URI.buildUpon()
      .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
      .build()

    return listOf(
      // RawContact entry
      ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, linkConfig.account.name)
        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, linkConfig.account.type)
        .withValue(FIELD_DISPLAY_PHONE, systemContactInfo.displayPhone)
        .withValue(FIELD_SUPPORTS_VOICE, true.toString())
        .build(),

      // Data entry for name
      ContentProviderOperation.newInsert(dataUri)
        .withValueBackReference(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, operationIndex)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, systemContactInfo.name.displayName)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, systemContactInfo.name.givenName)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, systemContactInfo.name.familyName)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.PREFIX, systemContactInfo.name.prefix)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, systemContactInfo.name.suffix)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, systemContactInfo.name.middleName)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        .build(),

      // Data entry for number (Note: This may not be necessary)
      ContentProviderOperation.newInsert(dataUri)
        .withValueBackReference(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID, operationIndex)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, systemContactInfo.displayPhone)
        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, systemContactInfo.type)
        .withValue(FIELD_TAG, linkConfig.syncTag)
        .build(),

      // Data entry for sending a message
      ContentProviderOperation.newInsert(dataUri)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, operationIndex)
        .withValue(ContactsContract.Data.MIMETYPE, linkConfig.messageMimetype)
        .withValue(ContactsContract.Data.DATA1, systemContactInfo.displayPhone)
        .withValue(ContactsContract.Data.DATA2, linkConfig.appName)
        .withValue(ContactsContract.Data.DATA3, linkConfig.messagePrompt(systemContactInfo.displayPhone))
        .withYieldAllowed(true)
        .build(),

      // Data entry for making a call
      ContentProviderOperation.newInsert(dataUri)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, operationIndex)
        .withValue(ContactsContract.Data.MIMETYPE, linkConfig.callMimetype)
        .withValue(ContactsContract.Data.DATA1, systemContactInfo.displayPhone)
        .withValue(ContactsContract.Data.DATA2, linkConfig.appName)
        .withValue(ContactsContract.Data.DATA3, linkConfig.callPrompt(systemContactInfo.displayPhone))
        .withYieldAllowed(true)
        .build(),

      // Data entry for making a video call
      ContentProviderOperation.newInsert(dataUri)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, operationIndex)
        .withValue(ContactsContract.Data.MIMETYPE, linkConfig.videoCallMimetype)
        .withValue(ContactsContract.Data.DATA1, systemContactInfo.displayPhone)
        .withValue(ContactsContract.Data.DATA2, linkConfig.appName)
        .withValue(ContactsContract.Data.DATA3, linkConfig.videoCallPrompt(systemContactInfo.displayPhone))
        .withYieldAllowed(true)
        .build(),

      // Ensures that this RawContact entry is shown next to another RawContact entry we found for this contact
      ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
        .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, systemContactInfo.siblingRawContactId)
        .withValueBackReference(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, operationIndex)
        .withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
        .build()
    )
  }

  private fun removeLinkedContact(operations: MutableList<ContentProviderOperation>, account: Account, rowId: Long) {
    operations.add(
      ContentProviderOperation.newDelete(
        ContactsContract.RawContacts.CONTENT_URI.buildUpon()
          .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
          .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
          .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
          .build()
      )
        .withYieldAllowed(true)
        .withSelection("${BaseColumns._ID} = ?", SqlUtil.buildArgs(rowId))
        .build()
    )
  }

  private fun getLinkedContactsByE164(context: Context, account: Account, e164Formatter: (String) -> String?): Map<String, LinkedContactDetails> {
    val currentContactsUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
      .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
      .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type).build()
    val projection = arrayOf(
      BaseColumns._ID,
      FIELD_DISPLAY_PHONE,
      FIELD_SUPPORTS_VOICE,
      ContactsContract.RawContacts.CONTACT_ID,
      ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY,
      ContactsContract.RawContacts.DISPLAY_NAME_SOURCE
    )

    val contactsDetails: MutableMap<String, LinkedContactDetails> = HashMap()

    context.contentResolver.query(currentContactsUri, projection, null, null, null)?.use { cursor ->
      while (cursor.moveToNext()) {
        val displayPhone = cursor.requireString(FIELD_DISPLAY_PHONE)

        if (displayPhone != null) {
          val e164 = e164Formatter(displayPhone) ?: continue

          contactsDetails[e164] = LinkedContactDetails(
            id = cursor.requireLong(BaseColumns._ID),
            supportsVoice = cursor.requireString(FIELD_SUPPORTS_VOICE),
            rawDisplayName = cursor.requireString(ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY),
            aggregateDisplayName = getDisplayName(context, cursor.requireLong(ContactsContract.RawContacts.CONTACT_ID)),
            displayNameSource = cursor.requireInt(ContactsContract.RawContacts.DISPLAY_NAME_SOURCE)
          )
        }
      }
    }

    return contactsDetails
  }

  private fun getSystemContactInfo(context: Context, e164: String, e164Formatter: (String) -> String?): SystemContactInfo? {
    ContactsContract.RawContactsEntity.RAW_CONTACT_ID
    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(e164))
    val projection = arrayOf(
      ContactsContract.PhoneLookup.NUMBER,
      ContactsContract.PhoneLookup._ID,
      ContactsContract.PhoneLookup.DISPLAY_NAME,
      ContactsContract.PhoneLookup.TYPE
    )

    context.contentResolver.query(uri, projection, null, null, null)?.use { contactCursor ->
      while (contactCursor.moveToNext()) {
        val systemNumber: String? = contactCursor.requireString(ContactsContract.PhoneLookup.NUMBER)
        if (systemNumber != null && e164Formatter(systemNumber) == e164) {
          val phoneLookupId = contactCursor.requireLong(ContactsContract.PhoneLookup._ID)
          val idProjection = arrayOf(ContactsContract.RawContacts._ID)
          val idSelection = "${ContactsContract.RawContacts.CONTACT_ID} = ? "
          val idArgs = SqlUtil.buildArgs(phoneLookupId)

          context.contentResolver.query(ContactsContract.RawContacts.CONTENT_URI, idProjection, idSelection, idArgs, null)?.use { idCursor ->
            if (idCursor.moveToNext()) {
              val rawContactId = idCursor.requireLong(ContactsContract.RawContacts._ID)
              val nameProjection = arrayOf(
                ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                ContactsContract.CommonDataKinds.StructuredName.PREFIX,
                ContactsContract.CommonDataKinds.StructuredName.SUFFIX,
                ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME
              )
              val nameSelection = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
              val nameArgs = SqlUtil.buildArgs(rawContactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)

              context.contentResolver.query(ContactsContract.Data.CONTENT_URI, nameProjection, nameSelection, nameArgs, null)?.use { nameCursor ->
                if (nameCursor.moveToNext()) {
                  return SystemContactInfo(
                    name = NameDetails(
                      displayName = nameCursor.requireString(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME),
                      givenName = nameCursor.requireString(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME),
                      familyName = nameCursor.requireString(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME),
                      prefix = nameCursor.requireString(ContactsContract.CommonDataKinds.StructuredName.PREFIX),
                      suffix = nameCursor.requireString(ContactsContract.CommonDataKinds.StructuredName.SUFFIX),
                      middleName = nameCursor.requireString(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME)
                    ),
                    displayPhone = systemNumber,
                    siblingRawContactId = rawContactId,
                    type = contactCursor.requireInt(ContactsContract.PhoneLookup.TYPE)
                  )
                }
              }
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

  interface ContactIterator : Iterator<ContactDetails>, Closeable {
    @Throws
    override fun close() {
    }
  }

  private class EmptyContactIterator : ContactIterator {
    override fun close() {}
    override fun hasNext(): Boolean = false
    override fun next(): ContactDetails = throw NoSuchElementException()
  }

  /**
   * Remember cursor rows are ordered by the following params:
   *  1. Contact Lookup Key ASC
   *  1. Mimetype ASC
   *  1. id DESC
   *
   * The lookup key is a fixed value that allows you to verify two rows in the database actually
   * belong to the same contact, since the contact uri can be unstable (if a sync fails, say.)
   *
   * We order by id explicitly here for the same contact sync failure error, which could result in
   * multiple structured name rows for the same user. By ordering by id DESC, we ensure that the
   * latest name is first in the cursor.
   *
   * What this results in is a cursor that looks like:
   *
   * Alice phone 2
   * Alice phone 1
   * Alice structured name 2
   * Alice structured name 1
   * Bob phone 1
   * ... etc.
   *
   * The general idea of how this is implemented:
   * - Assume you're already on the correct row at the start of [next].
   * - Store the lookup key from the first row.
   * - Read all phone entries for that lookup key and store them.
   * - Read the first name entry for that lookup key and store it.
   * - Skip all other rows for that lookup key. This will ensure that you're on the correct row for the next call to [next]
   */
  private class CursorContactIterator(
    private val cursor: Cursor,
    private val e164Formatter: (String) -> String?
  ) : ContactIterator {

    init {
      cursor.moveToFirst()
    }

    override fun hasNext(): Boolean {
      return !cursor.isAfterLast && cursor.position >= 0
    }

    override fun next(): ContactDetails {
      if (cursor.isAfterLast || cursor.position < 0) {
        throw NoSuchElementException()
      }

      val lookupKey: String = cursor.getLookupKey()
      val phoneDetails: List<ContactPhoneDetails> = readAllPhones(cursor, lookupKey)
      val structuredName: StructuredName? = readStructuredName(cursor, lookupKey)

      while (!cursor.isAfterLast && cursor.position >= 0 && cursor.getLookupKey() == lookupKey) {
        cursor.moveToNext()
      }

      return ContactDetails(
        givenName = structuredName?.givenName,
        familyName = structuredName?.familyName,
        numbers = phoneDetails
      )
    }

    override fun close() {
      cursor.close()
    }

    fun readAllPhones(cursor: Cursor, lookupKey: String): List<ContactPhoneDetails> {
      val phoneDetails: MutableList<ContactPhoneDetails> = mutableListOf()

      while (!cursor.isAfterLast && lookupKey == cursor.getLookupKey() && cursor.isPhoneMimeType()) {
        val displayNumber: String? = cursor.requireString(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val formattedNumber: String? = displayNumber?.let { e164Formatter(it) }

        if (!displayNumber.isNullOrEmpty() && !formattedNumber.isNullOrEmpty()) {
          phoneDetails += ContactPhoneDetails(
            contactUri = ContactsContract.Contacts.getLookupUri(cursor.requireLong(ContactsContract.CommonDataKinds.Phone._ID), lookupKey),
            displayName = cursor.requireString(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            photoUri = cursor.requireString(ContactsContract.CommonDataKinds.Phone.PHOTO_URI),
            number = formattedNumber,
            type = cursor.requireInt(ContactsContract.CommonDataKinds.Phone.TYPE),
            label = cursor.requireString(ContactsContract.CommonDataKinds.Phone.LABEL)
          )
        } else {
          Log.w(TAG, "Skipping phone entry with invalid number!")
        }

        cursor.moveToNext()
      }

      // You may get duplicates of the same phone number with different types.
      // This dedupes by taking the entry with the lowest phone type.
      return phoneDetails
        .groupBy { it.number }
        .mapValues { entry ->
          entry.value.minByOrNull { it.type }!!
        }
        .values
        .toList()
    }

    fun readStructuredName(cursor: Cursor, lookupKey: String): StructuredName? {
      return if (!cursor.isAfterLast && cursor.getLookupKey() == lookupKey && cursor.isNameMimeType()) {
        StructuredName(
          givenName = cursor.requireString(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME),
          familyName = cursor.requireString(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)
        )
      } else {
        null
      }
    }

    fun Cursor.getLookupKey(): String {
      return requireNonNullString(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
    }

    fun Cursor.isPhoneMimeType(): Boolean {
      return requireString(ContactsContract.Data.MIMETYPE) == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
    }

    fun Cursor.isNameMimeType(): Boolean {
      return requireString(ContactsContract.Data.MIMETYPE) == ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
    }

    fun firstNonEmpty(s1: String?, s2: String): String {
      return if (s1 != null && s1.isNotEmpty()) s1 else s2
    }
  }

  data class ContactDetails(
    val givenName: String?,
    val familyName: String?,
    val numbers: List<ContactPhoneDetails>
  )

  data class ContactPhoneDetails(
    val contactUri: Uri,
    val displayName: String?,
    val photoUri: String?,
    val number: String,
    val type: Int,
    val label: String?
  )

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

  private data class LinkedContactDetails(
    val id: Long,
    val supportsVoice: String?,
    val rawDisplayName: String?,
    val aggregateDisplayName: String?,
    val displayNameSource: Int
  )

  private data class SystemContactInfo(
    val name: NameDetails,
    val displayPhone: String,
    val siblingRawContactId: Long,
    val type: Int
  )

  private data class StructuredName(val givenName: String?, val familyName: String?)
}
