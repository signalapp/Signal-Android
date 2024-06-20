package org.thoughtcrime.securesms.profiles.manage

import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.Base64
import org.signal.core.util.Result
import org.signal.core.util.Result.Companion.failure
import org.signal.core.util.Result.Companion.success
import org.signal.core.util.logging.Log
import org.signal.libsignal.usernames.BaseUsernameException
import org.signal.libsignal.usernames.Username
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.main.UsernameLinkResetResult
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.NetworkUtil
import org.thoughtcrime.securesms.util.UsernameUtil
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException
import org.whispersystems.signalservice.api.push.exceptions.UsernameIsNotAssociatedWithAnAccountException
import org.whispersystems.signalservice.api.push.exceptions.UsernameIsNotReservedException
import org.whispersystems.signalservice.api.push.exceptions.UsernameMalformedException
import org.whispersystems.signalservice.api.push.exceptions.UsernameTakenException
import org.whispersystems.signalservice.api.util.Usernames
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.api.util.toByteArray
import java.io.IOException
import java.util.UUID

/**
 * Performs various actions around usernames and username links.
 *
 * Usernames and username links are more complicated than you may think. This is because we want the following properties:
 * - We want usernames to be assigned a random numerical discriminator to avoid land grabs
 * - We don't want to store plaintext usernames on the service
 * - We don't want plaintext usernames in username links
 * - We want username links to be revocable and rotatable without changing your username
 * - We want users to be able to turn a link into a displayable username in the app
 *
 * As a result, the process of reserving them, creating links, and parsing those links is more complex.
 *
 * # Setting a username
 *
 * To start, let's define a username as being composed of two parts: a nickname and a discriminator. The nickname is the user-chosen part of the username, and
 * the discriminator is a random set of digits that we bolt onto the end so that people can choose whatever nickname they want. So a username ends up looking
 * like this: mynickname.123
 *
 * Setting a username is a multi-step process.
 *
 * 1. The user chooses a nickname.
 * 2. We take that nickname and pair it with a bunch of possible discriminators of different lengths, turning them into a list of possible usernames.
 * 3. We hash those possible usernames and submit them to the service. It will reserve the first one that's available, returning it in the response.
 * 4. We present the (nickname, discriminator) combo to the user, and they can choose to confirm it.
 * 5. If the user confirms it, we tell the service the final username hash, and it saves it as the final username.
 *
 * # Username links
 *
 * There's three main components to username links:
 * - An encrypted username blob
 * - A serverId (which is a UUID)
 * - "entropy" (some random bytes used to encrypt the username)
 *
 * The service basically stores a map of (serverId -> encrypted username blob). We can ask the service for the encrypted username blob for a given serverId,
 * and then decrypt it with the entropy. Simple enough.
 *
 * How are those pieces shared? Well, the link looks like this:
 * https://signal.me/#eu/<32 bytes of entropy><16 bytes of serverId uuid>
 *
 * So, when we get a link, we parse out the entropy and serverId. We then use the serverId to get the encrypted username, and then decrypt it with the entropy.
 *
 * This gives us everything we want:
 * - We can rotate our link without changing our username by just picking new (serverId, entropy) and storing a new blob on the service.
 * - When the user decrypts the username, they see it displayed exactly how the user uploaded it.
 * - The service has no idea what links correspond to what usernames -- it's just storing encrypted blobs.
 */
object UsernameRepository {
  private val TAG = Log.tag(UsernameRepository::class.java)

  private val URL_REGEX = """(https://)?signal.me/?#eu/([a-zA-Z0-9+\-_/]+)""".toRegex()

  private const val BASE_URL = "https://signal.me/#eu/"
  private const val USERNAME_SYNC_ERROR_THRESHOLD = 3

  private val accountManager: SignalServiceAccountManager get() = AppDependencies.signalServiceAccountManager

  /**
   * Given a nickname, this will temporarily reserve a matching discriminator that can later be confirmed via [confirmUsernameAndCreateNewLink].
   */
  fun reserveUsername(nickname: String, discriminator: String?): Single<Result<UsernameState.Reserved, UsernameSetResult>> {
    return Single
      .fromCallable { reserveUsernameInternal(nickname, discriminator) }
      .subscribeOn(Schedulers.io())
  }

  /**
   * This changes the encrypted username associated with your current username link.
   * The intent of this is to allow users to change the casing of their username without changing the link,
   * since usernames are case-insensitive.
   */
  fun updateUsernameDisplayForCurrentLink(updatedUsername: Username): Single<UsernameSetResult> {
    return Single
      .fromCallable { updateUsernameDisplayForCurrentLinkInternal(updatedUsername) }
      .subscribeOn(Schedulers.io())
  }

  /**
   * Given a reserved username (obtained via [reserveUsername]), this will confirm that reservation, assigning the user that username.
   * It will also create a new username link. Therefore, be sure to call [updateUsernameDisplayForCurrentLink] instead if all that has changed is the
   * casing, and you want to keep the link the same.
   */
  fun confirmUsernameAndCreateNewLink(username: Username): Single<UsernameSetResult> {
    return Single
      .fromCallable { confirmUsernameAndCreateNewLinkInternal(username) }
      .subscribeOn(Schedulers.io())
  }

  /**
   * Attempts to reclaim the username that is currently stored on disk if necessary.
   * This is intended to be used after registration.
   *
   * This method call may result in mutating [SignalStore] state.
   */
  @WorkerThread
  @JvmStatic
  fun reclaimUsernameIfNecessary(): UsernameReclaimResult {
    if (!SignalStore.misc.needsUsernameRestore) {
      Log.d(TAG, "[reclaimUsernameIfNecessary] No need to restore username. Skipping.")
      return UsernameReclaimResult.SUCCESS
    }

    val username = SignalStore.account.username
    val link = SignalStore.account.usernameLink

    if (username == null || link == null) {
      Log.d(TAG, "[reclaimUsernameIfNecessary] No username or link to restore. Skipping.")
      SignalStore.misc.needsUsernameRestore = false
      return UsernameReclaimResult.SUCCESS
    }

    val result = reclaimUsernameIfNecessaryInternal(Username(username), link)

    when (result) {
      UsernameReclaimResult.SUCCESS -> {
        Log.i(TAG, "[reclaimUsernameIfNecessary] Successfully reclaimed username and link.")
        SignalStore.misc.needsUsernameRestore = false
      }

      UsernameReclaimResult.PERMANENT_ERROR -> {
        Log.w(TAG, "[reclaimUsernameIfNecessary] Permanently failed to reclaim username and link. User will see an error.")
        SignalStore.account.usernameSyncState = AccountValues.UsernameSyncState.USERNAME_AND_LINK_CORRUPTED
        SignalStore.misc.needsUsernameRestore = false
      }

      UsernameReclaimResult.NETWORK_ERROR -> {
        Log.w(TAG, "[reclaimUsernameIfNecessary] Hit a transient network error while trying to reclaim username and link.")
      }
    }

    return result
  }

  /**
   * Deletes the username from the local user's account
   */
  @JvmStatic
  fun deleteUsernameAndLink(): Single<UsernameDeleteResult> {
    return Single
      .fromCallable { deleteUsernameInternal() }
      .subscribeOn(Schedulers.io())
  }

  /**
   * Creates or rotates the username link for the local user.
   */
  fun createOrResetUsernameLink(): Single<UsernameLinkResetResult> {
    if (!NetworkUtil.isConnected(AppDependencies.application)) {
      Log.w(TAG, "[createOrResetUsernameLink] No network! Not making any changes.")
      return Single.just(UsernameLinkResetResult.NetworkUnavailable)
    }

    val usernameString = SignalStore.account.username
    if (usernameString.isNullOrBlank()) {
      Log.w(TAG, "[createOrResetUsernameLink] No username set! Cannot rotate the link!")
      return Single.just(UsernameLinkResetResult.UnexpectedError)
    }

    val username = try {
      Username(usernameString)
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "[createOrResetUsernameLink] Failed to parse our own username! Cannot rotate the link!")
      return Single.just(UsernameLinkResetResult.UnexpectedError)
    }

    return Single
      .fromCallable {
        try {
          SignalStore.account.usernameLink = null

          Log.d(TAG, "[createOrResetUsernameLink] Creating username link...")
          val components = accountManager.createUsernameLink(username)
          SignalStore.account.usernameLink = components

          if (SignalStore.account.usernameSyncState == AccountValues.UsernameSyncState.LINK_CORRUPTED) {
            SignalStore.account.usernameSyncState = AccountValues.UsernameSyncState.IN_SYNC
            SignalStore.account.usernameSyncErrorCount = 0
          }

          SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
          StorageSyncHelper.scheduleSyncForDataChange()
          Log.d(TAG, "[createOrResetUsernameLink] Username link created.")

          UsernameLinkResetResult.Success(components)
        } catch (e: IOException) {
          Log.w(TAG, "[createOrResetUsernameLink] Failed to rotate the username!", e)
          UsernameLinkResetResult.NetworkError
        }
      }
      .subscribeOn(Schedulers.io())
  }

  /**
   * Given a full username link, this will do the necessary parsing and network lookups to resolve it to a (username, ACI) pair.
   */
  @JvmStatic
  fun fetchUsernameAndAciFromLink(url: String): Single<UsernameLinkConversionResult> {
    val components: UsernameLinkComponents = parseLink(url) ?: return Single.just(UsernameLinkConversionResult.Invalid)

    return Single
      .fromCallable {
        var username: Username? = null

        try {
          val encryptedUsername: ByteArray = accountManager.getEncryptedUsernameFromLinkServerId(components.serverId)
          val link = Username.UsernameLink(components.entropy, encryptedUsername)

          username = Username.fromLink(link)

          val aci = accountManager.getAciByUsername(username)

          UsernameLinkConversionResult.Success(username, aci)
        } catch (e: IOException) {
          Log.w(TAG, "[convertLinkToUsername] Failed to lookup user.", e)

          if (e is NonSuccessfulResponseCodeException) {
            when (e.code) {
              404 -> UsernameLinkConversionResult.NotFound(username)
              422 -> UsernameLinkConversionResult.Invalid
              else -> UsernameLinkConversionResult.NetworkError
            }
          } else {
            UsernameLinkConversionResult.NetworkError
          }
        } catch (e: BaseUsernameException) {
          Log.w(TAG, "[convertLinkToUsername] Bad username conversion.", e)
          UsernameLinkConversionResult.Invalid
        }
      }
      .subscribeOn(Schedulers.io())
  }

  @JvmStatic
  fun fetchAciForUsername(username: String): Single<UsernameAciFetchResult> {
    return Single.fromCallable {
      try {
        val aci: ACI = AppDependencies.signalServiceAccountManager.getAciByUsername(Username(username))
        UsernameAciFetchResult.Success(aci)
      } catch (e: UsernameIsNotAssociatedWithAnAccountException) {
        Log.w(TAG, "[fetchAciFromUsername] Failed to get ACI for username hash", e)
        UsernameAciFetchResult.NotFound
      } catch (e: BaseUsernameException) {
        Log.w(TAG, "[fetchAciFromUsername] Invalid username", e)
        UsernameAciFetchResult.NotFound
      } catch (e: IOException) {
        Log.w(TAG, "[fetchAciFromUsername] Hit network error while trying to resolve ACI from username", e)
        UsernameAciFetchResult.NetworkError
      }
    }
  }

  /**
   * Parses out the [UsernameLinkComponents] from a link if possible, otherwise null.
   * You need to make a separate network request to convert these components into a username.
   */
  @JvmStatic
  fun parseLink(url: String): UsernameLinkComponents? {
    val match: MatchResult = URL_REGEX.find(url) ?: return null
    val path: String = match.groups[2]?.value ?: return null
    val allBytes: ByteArray = Base64.decode(path)

    if (allBytes.size != 48) {
      return null
    }

    val entropy: ByteArray = allBytes.slice(0 until 32).toByteArray()
    val serverId: ByteArray = allBytes.slice(32 until allBytes.size).toByteArray()
    val serverIdUuid: UUID = UuidUtil.parseOrNull(serverId) ?: return null

    return UsernameLinkComponents(entropy = entropy, serverId = serverIdUuid)
  }

  fun UsernameLinkComponents.toLink(): String {
    val combined: ByteArray = this.entropy + this.serverId.toByteArray()
    val base64 = Base64.encodeUrlSafeWithoutPadding(combined)
    return BASE_URL + base64
  }

  fun isValidLink(url: String): Boolean {
    return parseLink(url) != null
  }

  @JvmStatic
  fun onUsernameConsistencyValidated() {
    SignalStore.account.usernameSyncState = AccountValues.UsernameSyncState.IN_SYNC

    if (SignalStore.account.usernameSyncErrorCount > 0) {
      Log.i(TAG, "Username consistency validated. There were previously ${SignalStore.account.usernameSyncErrorCount} error(s).")
      SignalStore.account.usernameSyncErrorCount = 0
    }
  }

  @JvmStatic
  fun onUsernameMismatchDetected() {
    SignalStore.account.usernameSyncErrorCount++

    if (SignalStore.account.usernameSyncErrorCount >= USERNAME_SYNC_ERROR_THRESHOLD) {
      Log.w(TAG, "We've now seen ${SignalStore.account.usernameSyncErrorCount} mismatches in a row. Marking username and link as corrupted.")
      SignalStore.account.usernameSyncState = AccountValues.UsernameSyncState.USERNAME_AND_LINK_CORRUPTED
      SignalStore.account.usernameSyncErrorCount = 0
    } else {
      Log.w(TAG, "Username mismatch reported. At ${SignalStore.account.usernameSyncErrorCount} / $USERNAME_SYNC_ERROR_THRESHOLD tries.")
    }
  }

  @JvmStatic
  fun onUsernameLinkMismatchDetected() {
    SignalStore.account.usernameSyncErrorCount++

    if (SignalStore.account.usernameSyncErrorCount >= USERNAME_SYNC_ERROR_THRESHOLD) {
      Log.w(TAG, "We've now seen ${SignalStore.account.usernameSyncErrorCount} mismatches in a row. Marking link as corrupted.")
      SignalStore.account.usernameSyncState = AccountValues.UsernameSyncState.LINK_CORRUPTED
      SignalStore.account.usernameLink = null
      SignalStore.account.usernameSyncErrorCount = 0
      StorageSyncHelper.scheduleSyncForDataChange()
    } else {
      Log.w(TAG, "Link mismatch reported. At ${SignalStore.account.usernameSyncErrorCount} / $USERNAME_SYNC_ERROR_THRESHOLD tries.")
    }
  }

  @WorkerThread
  private fun reserveUsernameInternal(nickname: String, discriminator: String?): Result<UsernameState.Reserved, UsernameSetResult> {
    return try {
      val candidates: List<Username> = if (discriminator == null) {
        Username.candidatesFrom(nickname, UsernameUtil.MIN_NICKNAME_LENGTH, UsernameUtil.MAX_NICKNAME_LENGTH)
      } else {
        listOf(Username("$nickname${Usernames.DELIMITER}$discriminator"))
      }

      val hashes: List<String> = candidates
        .map { Base64.encodeUrlSafeWithoutPadding(it.hash) }

      val response = accountManager.reserveUsername(hashes)

      val hashIndex = hashes.indexOf(response.usernameHash)
      if (hashIndex == -1) {
        Log.w(TAG, "[reserveUsername] The response hash could not be found in our set of hashes.")
        return failure(UsernameSetResult.CANDIDATE_GENERATION_ERROR)
      }

      Log.i(TAG, "[reserveUsername] Successfully reserved username.")
      success(UsernameState.Reserved(candidates[hashIndex]))
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "[reserveUsername] An error occurred while generating candidates.")
      failure(UsernameSetResult.CANDIDATE_GENERATION_ERROR)
    } catch (e: UsernameTakenException) {
      Log.w(TAG, "[reserveUsername] Username taken.")
      failure(UsernameSetResult.USERNAME_UNAVAILABLE)
    } catch (e: UsernameMalformedException) {
      Log.w(TAG, "[reserveUsername] Username malformed.")
      failure(UsernameSetResult.USERNAME_INVALID)
    } catch (e: RateLimitException) {
      Log.w(TAG, "[reserveUsername] Rate limit exceeded.")
      failure(UsernameSetResult.RATE_LIMIT_ERROR)
    } catch (e: IOException) {
      Log.w(TAG, "[reserveUsername] Generic network exception.", e)
      failure(UsernameSetResult.NETWORK_ERROR)
    }
  }

  @WorkerThread
  private fun updateUsernameDisplayForCurrentLinkInternal(updatedUsername: Username): UsernameSetResult {
    Log.i(TAG, "[updateUsernameDisplayForCurrentLink] Beginning username update...")

    if (!NetworkUtil.isConnected(AppDependencies.application)) {
      Log.w(TAG, "[deleteUsernameInternal] No network connection! Not attempting the request.")
      return UsernameSetResult.NETWORK_ERROR
    }

    return try {
      val oldUsernameLink = SignalStore.account.usernameLink ?: return UsernameSetResult.USERNAME_INVALID
      val newUsernameLink = updatedUsername.generateLink(oldUsernameLink.entropy)
      val usernameLinkComponents = accountManager.updateUsernameLink(newUsernameLink)

      SignalStore.account.username = updatedUsername.username
      SignalStore.account.usernameLink = usernameLinkComponents
      SignalDatabase.recipients.setUsername(Recipient.self().id, updatedUsername.username)
      SignalStore.account.usernameSyncState = AccountValues.UsernameSyncState.IN_SYNC
      SignalStore.account.usernameSyncErrorCount = 0

      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
      Log.i(TAG, "[updateUsernameDisplayForCurrentLink] Successfully updated username.")

      UsernameSetResult.SUCCESS
    } catch (e: IOException) {
      Log.w(TAG, "[updateUsernameDisplayForCurrentLink] Generic network exception.", e)
      UsernameSetResult.NETWORK_ERROR
    }
  }

  @WorkerThread
  private fun confirmUsernameAndCreateNewLinkInternal(username: Username): UsernameSetResult {
    Log.i(TAG, "[confirmUsernameAndCreateNewLink] Beginning username confirmation...")

    if (!NetworkUtil.isConnected(AppDependencies.application)) {
      Log.w(TAG, "[confirmUsernameAndCreateNewLink] No network connection! Not attempting the request.")
      return UsernameSetResult.NETWORK_ERROR
    }

    return try {
      val linkComponents: UsernameLinkComponents = accountManager.confirmUsernameAndCreateNewLink(username)

      SignalStore.account.username = username.username
      SignalStore.account.usernameLink = linkComponents
      SignalDatabase.recipients.setUsername(Recipient.self().id, username.username)
      SignalStore.account.usernameSyncState = AccountValues.UsernameSyncState.IN_SYNC
      SignalStore.account.usernameSyncErrorCount = 0

      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
      Log.i(TAG, "[confirmUsernameAndCreateNewLink] Successfully confirmed username.")

      UsernameSetResult.SUCCESS
    } catch (e: UsernameTakenException) {
      Log.w(TAG, "[confirmUsernameAndCreateNewLink] Username gone.")
      UsernameSetResult.USERNAME_UNAVAILABLE
    } catch (e: UsernameIsNotReservedException) {
      Log.w(TAG, "[confirmUsernameAndCreateNewLink] Username was not reserved.")
      UsernameSetResult.USERNAME_INVALID
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "[confirmUsernameAndCreateNewLink] Username was not reserved.")
      UsernameSetResult.USERNAME_INVALID
    } catch (e: IOException) {
      Log.w(TAG, "[confirmUsernameAndCreateNewLink] Generic network exception.", e)
      UsernameSetResult.NETWORK_ERROR
    }
  }

  @WorkerThread
  private fun deleteUsernameInternal(): UsernameDeleteResult {
    if (!NetworkUtil.isConnected(AppDependencies.application)) {
      Log.w(TAG, "[deleteUsernameInternal] No network connection! Not attempting the request.")
      return UsernameDeleteResult.NETWORK_ERROR
    }

    return try {
      accountManager.deleteUsername()
      SignalDatabase.recipients.setUsername(Recipient.self().id, null)
      SignalStore.account.username = null
      SignalStore.account.usernameLink = null
      SignalStore.account.usernameSyncState = AccountValues.UsernameSyncState.IN_SYNC
      SignalStore.account.usernameSyncErrorCount = 0
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
      Log.i(TAG, "[deleteUsername] Successfully deleted the username.")
      UsernameDeleteResult.SUCCESS
    } catch (e: IOException) {
      Log.w(TAG, "[deleteUsername] Generic network exception.", e)
      UsernameDeleteResult.NETWORK_ERROR
    }
  }

  @WorkerThread
  @JvmStatic
  private fun reclaimUsernameIfNecessaryInternal(username: Username, usernameLinkComponents: UsernameLinkComponents): UsernameReclaimResult {
    try {
      accountManager.reclaimUsernameAndLink(username, usernameLinkComponents)
    } catch (e: UsernameTakenException) {
      Log.w(TAG, "[reclaimUsername] Username gone.")
      return UsernameReclaimResult.PERMANENT_ERROR
    } catch (e: UsernameIsNotReservedException) {
      Log.w(TAG, "[reclaimUsername] Username was not reserved.")
      return UsernameReclaimResult.PERMANENT_ERROR
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "[reclaimUsername] Invalid username.")
      return UsernameReclaimResult.PERMANENT_ERROR
    } catch (e: IOException) {
      Log.w(TAG, "[reclaimUsername] Network error.", e)
      return UsernameReclaimResult.NETWORK_ERROR
    }

    return UsernameReclaimResult.SUCCESS
  }

  enum class UsernameSetResult {
    SUCCESS,
    USERNAME_UNAVAILABLE,
    USERNAME_INVALID,
    NETWORK_ERROR,
    CANDIDATE_GENERATION_ERROR,
    RATE_LIMIT_ERROR
  }

  enum class UsernameReclaimResult {
    SUCCESS,
    PERMANENT_ERROR,
    NETWORK_ERROR
  }

  enum class UsernameDeleteResult {
    SUCCESS,
    NETWORK_ERROR
  }

  internal interface Callback<E> {
    fun onComplete(result: E)
  }

  sealed class UsernameLinkConversionResult {
    /** Successfully converted. Contains the username. */
    data class Success(val username: Username, val aci: ACI) : UsernameLinkConversionResult()

    /** Failed to convert due to a network error. */
    object NetworkError : UsernameLinkConversionResult()

    /** Failed to convert because the link or contents were invalid. */
    object Invalid : UsernameLinkConversionResult()

    /** No user exists for the given link. */
    data class NotFound(val username: Username?) : UsernameLinkConversionResult()
  }

  sealed class UsernameAciFetchResult {
    class Success(val aci: ACI) : UsernameAciFetchResult()
    object NotFound : UsernameAciFetchResult()
    object NetworkError : UsernameAciFetchResult()
  }
}
