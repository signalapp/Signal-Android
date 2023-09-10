package org.thoughtcrime.securesms.profiles.manage

import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.Result
import org.signal.core.util.Result.Companion.failure
import org.signal.core.util.Result.Companion.success
import org.signal.core.util.logging.Log
import org.signal.libsignal.usernames.BaseUsernameException
import org.signal.libsignal.usernames.Username
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.main.UsernameLinkResetResult
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.NetworkUtil
import org.thoughtcrime.securesms.util.UsernameUtil
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.push.exceptions.UsernameIsNotReservedException
import org.whispersystems.signalservice.api.push.exceptions.UsernameMalformedException
import org.whispersystems.signalservice.api.push.exceptions.UsernameTakenException
import org.whispersystems.util.Base64UrlSafe
import java.io.IOException

/**
 * Performs various actions around usernames and username links.
 */
class UsernameRepository {
  private val accountManager: SignalServiceAccountManager = ApplicationDependencies.getSignalServiceAccountManager()

  /**
   * Given a nickname, this will temporarily reserve a matching discriminator that can later be confirmed via [confirmUsername].
   */
  fun reserveUsername(nickname: String): Single<Result<UsernameState.Reserved, UsernameSetResult>> {
    return Single
      .fromCallable { reserveUsernameInternal(nickname) }
      .subscribeOn(Schedulers.io())
  }

  /**
   * Given a reserved username (obtained via [reserveUsername]), this will confirm that reservation, assigning the user that username.
   */
  fun confirmUsername(reserved: UsernameState.Reserved): Single<UsernameSetResult> {
    return Single
      .fromCallable { confirmUsernameInternal(reserved) }
      .subscribeOn(Schedulers.io())
  }

  /**
   * Deletes the username from the local user's account
   */
  fun deleteUsername(): Single<UsernameDeleteResult> {
    return Single
      .fromCallable { deleteUsernameInternal() }
      .subscribeOn(Schedulers.io())
  }

  /**
   * Creates or rotates the username link for the local user. If successful,  the [UsernameLinkComponents] will be returned.
   * If it fails for any reason, the optional will be empty.
   *
   * The assumption here is that when the user clicks this button, they will either have a new link, or no link at all.
   * This is to prevent indeterminate states where the network call fails but may have actually succeeded, that kind of thing.
   * As such, it's recommended to block calling this method on a network check.
   */
  fun createOrResetUsernameLink(): Single<UsernameLinkResetResult> {
    if (!NetworkUtil.isConnected(ApplicationDependencies.getApplication())) {
      Log.w(TAG, "[createOrRotateUsernameLink] No network! Not making any changes.")
      return Single.just(UsernameLinkResetResult.NetworkUnavailable)
    }

    val usernameString = SignalStore.account().username
    if (usernameString.isNullOrBlank()) {
      Log.w(TAG, "[createOrRotateUsernameLink] No username set! Cannot rotate the link!")
      return Single.just(UsernameLinkResetResult.UnexpectedError)
    }

    val username = try {
      Username(usernameString)
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "[createOrRotateUsernameLink] Failed to parse our own username! Cannot rotate the link!")
      return Single.just(UsernameLinkResetResult.UnexpectedError)
    }

    return Single
      .fromCallable {
        try {
          SignalStore.account().usernameLink = null

          Log.d(TAG, "[createOrRotateUsernameLink] Creating username link...")
          val components = accountManager.createUsernameLink(username)
          SignalStore.account().usernameLink = components
          SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
          StorageSyncHelper.scheduleSyncForDataChange()
          Log.d(TAG, "[createOrRotateUsernameLink] Username link created.")

          UsernameLinkResetResult.Success(components)
        } catch (e: IOException) {
          Log.w(TAG, "[createOrRotateUsernameLink] Failed to rotate the username!")
          UsernameLinkResetResult.NetworkError
        }
      }
      .subscribeOn(Schedulers.io())
  }

  /**
   * Given a full username link, this will do the necessary parsing and network lookups to resolve it to a (username, ACI) pair.
   */
  fun convertLinkToUsernameAndAci(url: String): Single<UsernameLinkConversionResult> {
    val components: UsernameLinkComponents = UsernameUtil.parseLink(url) ?: return Single.just(UsernameLinkConversionResult.Invalid)

    return Single
      .fromCallable {
        var username: Username? = null

        try {
          val encryptedUsername: ByteArray = accountManager.getEncryptedUsernameFromLinkServerId(components.serverId)
          val link = Username.UsernameLink(components.entropy, encryptedUsername)

          username = Username.fromLink(link)

          val aci = accountManager.getAciByUsernameHash(UsernameUtil.hashUsernameToBase64(username.toString()))

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

  @WorkerThread
  private fun reserveUsernameInternal(nickname: String): Result<UsernameState.Reserved, UsernameSetResult> {
    return try {
      val candidates: List<Username> = Username.candidatesFrom(nickname, UsernameUtil.MIN_LENGTH, UsernameUtil.MAX_LENGTH)

      val hashes: List<String> = candidates
        .map { Base64UrlSafe.encodeBytesWithoutPadding(it.hash) }

      val response = accountManager.reserveUsername(hashes)

      val hashIndex = hashes.indexOf(response.usernameHash)
      if (hashIndex == -1) {
        Log.w(TAG, "[reserveUsername] The response hash could not be found in our set of hashes.")
        return failure(UsernameSetResult.CANDIDATE_GENERATION_ERROR)
      }

      Log.i(TAG, "[reserveUsername] Successfully reserved username.")
      success(UsernameState.Reserved(candidates[hashIndex].username, response))
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "[reserveUsername] An error occurred while generating candidates.")
      failure(UsernameSetResult.CANDIDATE_GENERATION_ERROR)
    } catch (e: UsernameTakenException) {
      Log.w(TAG, "[reserveUsername] Username taken.")
      failure(UsernameSetResult.USERNAME_UNAVAILABLE)
    } catch (e: UsernameMalformedException) {
      Log.w(TAG, "[reserveUsername] Username malformed.")
      failure(UsernameSetResult.USERNAME_INVALID)
    } catch (e: IOException) {
      Log.w(TAG, "[reserveUsername] Generic network exception.", e)
      failure(UsernameSetResult.NETWORK_ERROR)
    }
  }

  @WorkerThread
  private fun confirmUsernameInternal(reserved: UsernameState.Reserved): UsernameSetResult {
    return try {
      val username = Username(reserved.username)
      accountManager.confirmUsername(reserved.username, reserved.reserveUsernameResponse)
      SignalStore.account().username = username.username
      SignalStore.account().usernameLink = null
      SignalDatabase.recipients.setUsername(Recipient.self().id, reserved.username)
      SignalStore.account().usernameOutOfSync = false
      Log.i(TAG, "[confirmUsername] Successfully confirmed username.")

      if (tryToSetUsernameLink(username)) {
        Log.i(TAG, "[confirmUsername] Successfully confirmed username link.")
      } else {
        Log.w(TAG, "[confirmUsername] Failed to confirm a username link. We'll try again when the user goes to view their link.")
      }

      UsernameSetResult.SUCCESS
    } catch (e: UsernameTakenException) {
      Log.w(TAG, "[confirmUsername] Username gone.")
      UsernameSetResult.USERNAME_UNAVAILABLE
    } catch (e: UsernameIsNotReservedException) {
      Log.w(TAG, "[confirmUsername] Username was not reserved.")
      UsernameSetResult.USERNAME_INVALID
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "[confirmUsername] Username was not reserved.")
      UsernameSetResult.USERNAME_INVALID
    } catch (e: IOException) {
      Log.w(TAG, "[confirmUsername] Generic network exception.", e)
      UsernameSetResult.NETWORK_ERROR
    }
  }

  private fun tryToSetUsernameLink(username: Username): Boolean {
    for (i in 0..2) {
      try {
        val linkComponents = accountManager.createUsernameLink(username)
        SignalStore.account().usernameLink = linkComponents
        return true
      } catch (e: IOException) {
        Log.w(TAG, "[tryToSetUsernameLink] Failed with IOException on attempt " + (i + 1) + "/3", e)
      }
    }

    return false
  }

  @WorkerThread
  private fun deleteUsernameInternal(): UsernameDeleteResult {
    return try {
      accountManager.deleteUsername()
      SignalDatabase.recipients.setUsername(Recipient.self().id, null)
      SignalStore.account().usernameOutOfSync = false
      Log.i(TAG, "[deleteUsername] Successfully deleted the username.")
      UsernameDeleteResult.SUCCESS
    } catch (e: IOException) {
      Log.w(TAG, "[deleteUsername] Generic network exception.", e)
      UsernameDeleteResult.NETWORK_ERROR
    }
  }

  enum class UsernameSetResult {
    SUCCESS, USERNAME_UNAVAILABLE, USERNAME_INVALID, NETWORK_ERROR, CANDIDATE_GENERATION_ERROR
  }

  enum class UsernameDeleteResult {
    SUCCESS, NETWORK_ERROR
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

  companion object {
    private val TAG = Log.tag(UsernameRepository::class.java)
  }
}
