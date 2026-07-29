/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.Base64
import org.signal.core.util.censor
import org.signal.core.util.serialization.ByteArrayToBase64Serializer
import org.signal.core.util.serialization.ECPublicKeyToBase64Serializer
import org.signal.core.util.serialization.KEMPublicKeyToBase64Serializer
import org.signal.core.util.serialization.SignalJson
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.network.rest.RequestSpec
import org.signal.network.rest.RestStatusCodeError
import org.signal.network.rest.SignalRestClient
import org.signal.network.rest.bodyString
import org.signal.network.rest.toTypedResult
import java.net.URLEncoder
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Registration-related endpoints.
 *
 * These endpoints are used before the account is registered, so they are either unauthenticated
 * or authenticate with explicitly-provided credentials rather than any stored on the client.
 */
class RegistrationApiV2(private val restClient: SignalRestClient) {

  companion object {
    private val APPLICATION_JSON = "application/json".toMediaType()

    /** Drops null properties instead of emitting explicit nulls, for bodies where a field is meant to be absent entirely. */
    @OptIn(ExperimentalSerializationApi::class)
    private val JSON_OMITTING_NULLS = Json(SignalJson.json) { explicitNulls = false }
  }

  /**
   * Request that the service initialize a new registration session.
   *
   * `POST /v1/verification/session`
   * - 200: Success, body is session metadata
   * - 422: Request is invalid
   * - 429: Rate limited
   */
  suspend fun createVerificationSession(e164: String, fcmToken: String?, mcc: String?, mnc: String?): RequestResult<SessionMetadata, CreateSessionError> {
    val result = restClient.request(
      RequestSpec(
        method = RequestSpec.Method.POST,
        host = RequestSpec.Host.Service,
        path = "/v1/verification/session",
        body = CreateVerificationSessionRequestBody(
          number = e164,
          pushToken = fcmToken,
          pushTokenType = if (fcmToken != null) "fcm" else null,
          mcc = mcc,
          mnc = mnc
        ).toJsonRequestBody()
      )
    )

    return result.toTypedResult(
      parseSuccess = { SignalJson.json.decodeFromString<SessionMetadata>(it.bodyString()) },
      mapError = { error ->
        when (error.statusCode) {
          422 -> CreateSessionError.InvalidRequest(error.bodyString())
          429 -> CreateSessionError.RateLimited(error.retryAfter())
          else -> null
        }
      }
    )
  }

  /**
   * Retrieve current status of a registration session.
   *
   * `GET /v1/verification/session/{session-id}`
   * - 200: Success, body is session metadata
   * - 400: Request is invalid
   * - 404: Session not found
   * - 422: Session id is malformed
   */
  suspend fun getSessionStatus(sessionId: String): RequestResult<SessionMetadata, GetSessionStatusError> {
    val result = restClient.request(
      RequestSpec(
        method = RequestSpec.Method.GET,
        host = RequestSpec.Host.Service,
        path = "/v1/verification/session/$sessionId"
      )
    )

    return result.toTypedResult(
      parseSuccess = { SignalJson.json.decodeFromString<SessionMetadata>(it.bodyString()) },
      mapError = { error ->
        when (error.statusCode) {
          400 -> GetSessionStatusError.InvalidRequest(error.bodyString())
          404 -> GetSessionStatusError.SessionNotFound(error.bodyString())
          422 -> GetSessionStatusError.InvalidSessionId(error.bodyString())
          else -> null
        }
      }
    )
  }

  /**
   * Update a registration session with a solved captcha token and/or a push challenge token.
   *
   * `PATCH /v1/verification/session/{session-id}`
   * - 200: Success, body is session metadata
   * - 400: Request is invalid
   * - 404: Session not found
   * - 409: Rejected update
   * - 429: Rate limited, body is session metadata
   */
  suspend fun updateVerificationSession(
    sessionId: String,
    fcmToken: String? = null,
    mcc: String? = null,
    mnc: String? = null,
    captchaToken: String? = null,
    pushChallengeToken: String? = null
  ): RequestResult<SessionMetadata, UpdateSessionError> {
    val result = restClient.request(
      RequestSpec(
        method = RequestSpec.Method.PATCH,
        host = RequestSpec.Host.Service,
        path = "/v1/verification/session/$sessionId",
        body = UpdateVerificationSessionRequestBody(
          captcha = captchaToken,
          pushToken = fcmToken,
          pushTokenType = if (fcmToken != null) "fcm" else null,
          pushChallenge = pushChallengeToken,
          mcc = mcc,
          mnc = mnc
        ).toJsonRequestBody()
      )
    )

    return result.toTypedResult(
      parseSuccess = { SignalJson.json.decodeFromString<SessionMetadata>(it.bodyString()) },
      mapError = { error ->
        when (error.statusCode) {
          400 -> UpdateSessionError.InvalidRequest(error.bodyString())
          404 -> UpdateSessionError.SessionNotFound(error.bodyString())
          409 -> UpdateSessionError.RejectedUpdate(error.bodyString())
          429 -> UpdateSessionError.RateLimited(error.retryAfter(), SignalJson.json.decodeFromString<SessionMetadata>(error.bodyString()))
          else -> null
        }
      }
    )
  }

  /**
   * Request that a verification code be sent to the session's phone number over the given transport.
   *
   * `POST /v1/verification/session/{session-id}/code`
   * - 200: Success, body is session metadata
   * - 400: Session id is malformed
   * - 404: Session not found
   * - 409: Missing request information or already verified, body is session metadata
   * - 418: Could not fulfill request with the requested transport, body is session metadata
   * - 429: Rate limited, body is session metadata
   * - 440: Third-party service error
   *
   * @param androidSmsRetrieverSupported whether the system framework will automatically parse the incoming verification message.
   */
  suspend fun requestVerificationCode(
    sessionId: String,
    locale: Locale?,
    androidSmsRetrieverSupported: Boolean,
    transport: VerificationCodeTransport
  ): RequestResult<SessionMetadata, RequestVerificationCodeError> {
    val headers = if (locale != null) {
      mapOf("Accept-Language" to "${locale.language}-${locale.country}")
    } else {
      emptyMap()
    }

    val transportValue = when (transport) {
      VerificationCodeTransport.SMS -> "sms"
      VerificationCodeTransport.VOICE -> "voice"
    }

    val result = restClient.request(
      RequestSpec(
        method = RequestSpec.Method.POST,
        host = RequestSpec.Host.Service,
        path = "/v1/verification/session/$sessionId/code",
        body = VerificationCodeRequestBody(
          transport = transportValue,
          client = if (androidSmsRetrieverSupported) "android-2021-03" else "android"
        ).toJsonRequestBody(),
        headers = headers
      )
    )

    return result.toTypedResult(
      parseSuccess = { SignalJson.json.decodeFromString<SessionMetadata>(it.bodyString()) },
      mapError = { error ->
        when (error.statusCode) {
          400 -> RequestVerificationCodeError.InvalidSessionId(error.bodyString())
          404 -> RequestVerificationCodeError.SessionNotFound(error.bodyString())
          409 -> RequestVerificationCodeError.MissingRequestInformationOrAlreadyVerified(SignalJson.json.decodeFromString<SessionMetadata>(error.bodyString()))
          418 -> RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport(SignalJson.json.decodeFromString<SessionMetadata>(error.bodyString()))
          429 -> RequestVerificationCodeError.RateLimited(error.retryAfter(), SignalJson.json.decodeFromString<SessionMetadata>(error.bodyString()))
          440 -> RequestVerificationCodeError.ThirdPartyServiceError(SignalJson.json.decodeFromString<ThirdPartyServiceErrorResponse>(error.bodyString()))
          else -> null
        }
      }
    )
  }

  /**
   * Submit a verification code sent by the service to prove the registrant's control of the phone number.
   *
   * `PUT /v1/verification/session/{session-id}/code`
   * - 200: Success, body is session metadata
   * - 400: Session id or verification code is malformed
   * - 404: Session not found
   * - 409: Session already verified or no code requested, body is session metadata
   * - 429: Rate limited, body is session metadata
   */
  suspend fun submitVerificationCode(sessionId: String, verificationCode: String): RequestResult<SessionMetadata, SubmitVerificationCodeError> {
    val result = restClient.request(
      RequestSpec(
        method = RequestSpec.Method.PUT,
        host = RequestSpec.Host.Service,
        path = "/v1/verification/session/$sessionId/code",
        body = SubmitVerificationCodeRequestBody(verificationCode).toJsonRequestBody()
      )
    )

    return result.toTypedResult(
      parseSuccess = { SignalJson.json.decodeFromString<SessionMetadata>(it.bodyString()) },
      mapError = { error ->
        when (error.statusCode) {
          400 -> SubmitVerificationCodeError.InvalidSessionIdOrVerificationCode(error.bodyString())
          404 -> SubmitVerificationCodeError.SessionNotFound(error.bodyString())
          409 -> SubmitVerificationCodeError.SessionAlreadyVerifiedOrNoCodeRequested(SignalJson.json.decodeFromString<SessionMetadata>(error.bodyString()))
          429 -> SubmitVerificationCodeError.RateLimited(error.retryAfter(), SignalJson.json.decodeFromString<SessionMetadata>(error.bodyString()))
          else -> null
        }
      }
    )
  }

  /**
   * Submit the cryptographic assets required for an account to use the service.
   * Must provide one of ([sessionId], [recoveryPassword]), but not both.
   *
   * `POST /v1/registration`
   * - 200: Success, body is the account response
   * - 401: Session not found or not verified
   * - 403: Registration recovery password is incorrect
   * - 409: Device transfer is possible
   * - 422: Request is invalid
   * - 423: Registration lock is active
   * - 429: Rate limited
   *
   * @param e164 The phone number in E.164 format (used as username for basic auth)
   * @param password The password for basic auth
   */
  suspend fun registerAccount(
    e164: String,
    password: String,
    sessionId: String?,
    recoveryPassword: String?,
    attributes: AccountAttributes,
    aciPreKeys: PreKeyCollection,
    pniPreKeys: PreKeyCollection,
    fcmToken: String?,
    skipDeviceTransfer: Boolean
  ): RequestResult<RegisterAccountResponse, RegisterAccountError> {
    require((sessionId != null) xor (recoveryPassword != null)) { "You must supply one and only one of either: Session ID, or Recovery Password." }

    val body = RegisterAccountRequestBody(
      sessionId = sessionId,
      recoveryPassword = recoveryPassword,
      accountAttributes = attributes,
      aciIdentityKey = Base64.encodeWithoutPadding(aciPreKeys.identityKey.serialize()),
      pniIdentityKey = Base64.encodeWithoutPadding(pniPreKeys.identityKey.serialize()),
      aciSignedPreKey = aciPreKeys.signedPreKey.toSignedPreKeyEntity(),
      pniSignedPreKey = pniPreKeys.signedPreKey.toSignedPreKeyEntity(),
      aciPqLastResortPreKey = aciPreKeys.lastResortKyberPreKey.toKyberPreKeyEntity(),
      pniPqLastResortPreKey = pniPreKeys.lastResortKyberPreKey.toKyberPreKeyEntity(),
      gcmToken = if (attributes.fetchesMessages) null else fcmToken?.let { GcmRegistrationId(it, true) },
      skipDeviceTransfer = skipDeviceTransfer
    )

    val result = restClient.request(
      RequestSpec(
        method = RequestSpec.Method.POST,
        host = RequestSpec.Host.Service,
        path = "/v1/registration",
        body = body.toJsonRequestBody(),
        auth = RequestSpec.Auth.Header("Authorization", basicAuth(e164, password))
      )
    )

    return result.toTypedResult(
      parseSuccess = { SignalJson.json.decodeFromString<RegisterAccountResponse>(it.bodyString()) },
      mapError = { error ->
        when (error.statusCode) {
          401 -> RegisterAccountError.SessionNotFoundOrNotVerified(error.bodyString())
          403 -> RegisterAccountError.RegistrationRecoveryPasswordIncorrect(error.bodyString())
          409 -> RegisterAccountError.DeviceTransferPossible
          422 -> RegisterAccountError.InvalidRequest(error.bodyString())
          423 -> RegisterAccountError.RegistrationLock(SignalJson.json.decodeFromString<RegistrationLockResponse>(error.bodyString()))
          429 -> RegisterAccountError.RateLimited(error.retryAfter())
          else -> null
        }
      }
    )
  }

  /**
   * Validates the provided SVR2 auth credentials, returning information on their usability.
   *
   * `POST /v2/svr/auth/check`
   * - 200: Success, body describes the usability of each credential
   * - 400, 422: Request is invalid
   * - 401: Unauthorized
   */
  suspend fun checkSvr2AuthCredentials(e164: String, credentials: List<SvrCredentials>): RequestResult<CheckSvrCredentialsResponse, CheckSvrCredentialsError> {
    val request = CheckSvrCredentialsRequest.createForCredentials(number = e164, credentials = credentials)

    val result = restClient.request(
      RequestSpec(
        method = RequestSpec.Method.POST,
        host = RequestSpec.Host.Service,
        path = "/v2/svr/auth/check",
        body = request.toJsonRequestBody()
      )
    )

    return result.toTypedResult(
      parseSuccess = { SignalJson.json.decodeFromString<CheckSvrCredentialsResponse>(it.bodyString()) },
      mapError = { error ->
        when (error.statusCode) {
          400, 422 -> CheckSvrCredentialsError.InvalidRequest(error.bodyString())
          401 -> CheckSvrCredentialsError.Unauthorized
          else -> null
        }
      }
    )
  }

  /**
   * Set [RestoreMethod] on the server for use by the old device to update UX.
   *
   * `PUT /v1/devices/restore_account/{token}`
   * - 204: Success
   * - 429: Rate limited
   */
  suspend fun setRestoreMethod(token: String, method: RestoreMethod): RequestResult<Unit, SetRestoreMethodError> {
    val result = restClient.request(
      RequestSpec(
        method = RequestSpec.Method.PUT,
        host = RequestSpec.Host.Service,
        path = "/v1/devices/restore_account/%s".format(Locale.US, URLEncoder.encode(token, "UTF-8")),
        body = RestoreMethodBody(method).toJsonRequestBody()
      )
    )

    return result.toTypedResult(
      parseSuccess = { },
      mapError = { error ->
        when (error.statusCode) {
          429 -> SetRestoreMethodError.RateLimited(error.retryAfter())
          else -> SetRestoreMethodError.InvalidRequest("HTTP ${error.statusCode}")
        }
      }
    )
  }

  /**
   * Registers a device as a linked device on a pre-existing account, authenticating via basic auth
   * built from [password] and the [aci].
   *
   * `PUT /v1/devices/link`
   * - 200: Success, body is the link device response
   * - 403: Incorrect account verification
   * - 409: Device missing required account capability
   * - 411: Account reached max number of linked devices
   * - 422: Request is invalid
   * - 429: Rate limited
   *
   * @param pniPreKeys The PNI pre-keys, or null if the account has no PNI. Omitted from the request when null.
   */
  suspend fun registerAsSecondaryDevice(
    aci: ACI,
    password: String,
    verificationCode: String,
    attributes: DeviceAttributes,
    aciPreKeys: PreKeyCollection,
    pniPreKeys: PreKeyCollection?,
    fcmToken: String?
  ): RequestResult<LinkDeviceResponse, RegisterAsLinkedDeviceError> {
    val request = RegisterAsSecondaryDeviceRequestBody(
      verificationCode = verificationCode,
      accountAttributes = attributes,
      aciSignedPreKey = aciPreKeys.signedPreKey.toSignedPreKeyEntity(),
      pniSignedPreKey = pniPreKeys?.signedPreKey?.toSignedPreKeyEntity(),
      aciPqLastResortPreKey = aciPreKeys.lastResortKyberPreKey.toKyberPreKeyEntity(),
      pniPqLastResortPreKey = pniPreKeys?.lastResortKyberPreKey?.toKyberPreKeyEntity(),
      gcmToken = fcmToken?.let { GcmRegistrationId(it, true) }
    )

    val result = restClient.request(
      RequestSpec(
        method = RequestSpec.Method.PUT,
        host = RequestSpec.Host.Service,
        path = "/v1/devices/link",
        body = request.toJsonRequestBodyOmittingNulls(),
        auth = RequestSpec.Auth.Header("Authorization", basicAuth(aci.toString(), password))
      )
    )

    return result.toTypedResult(
      parseSuccess = { SignalJson.json.decodeFromString<LinkDeviceResponse>(it.bodyString()) },
      mapError = { error ->
        when (error.statusCode) {
          403 -> RegisterAsLinkedDeviceError.IncorrectVerification
          409 -> RegisterAsLinkedDeviceError.MissingCapability
          411 -> RegisterAsLinkedDeviceError.MaxLinkedDevices
          422 -> RegisterAsLinkedDeviceError.InvalidRequest(error.bodyString())
          429 -> RegisterAsLinkedDeviceError.RateLimited(error.retryAfter())
          else -> null
        }
      }
    )
  }

  private fun RestStatusCodeError.retryAfter(): Duration {
    return headers["retry-after"]?.toLongOrNull()?.seconds ?: 0.seconds
  }

  private fun basicAuth(username: String, password: String): String {
    return Credentials.basic(username, password, Charsets.UTF_8)
  }

  private inline fun <reified T> T.toJsonRequestBody(): RequestBody {
    return SignalJson.json.encodeToString(this).toRequestBody(APPLICATION_JSON)
  }

  private inline fun <reified T> T.toJsonRequestBodyOmittingNulls(): RequestBody {
    return JSON_OMITTING_NULLS.encodeToString(this).toRequestBody(APPLICATION_JSON)
  }

  private fun SignedPreKeyRecord.toSignedPreKeyEntity(): SignedPreKeyEntity {
    return SignedPreKeyEntity(id.toLong(), keyPair.publicKey, signature)
  }

  private fun KyberPreKeyRecord.toKyberPreKeyEntity(): KyberPreKeyEntity {
    return KyberPreKeyEntity(id.toLong(), keyPair.publicKey, signature)
  }

  @Serializable
  data class SessionMetadata(
    val id: String,
    val nextSms: Long?,
    val nextCall: Long?,
    val nextVerificationAttempt: Long?,
    val allowedToRequestCode: Boolean,
    val requestedInformation: List<String>,
    val verified: Boolean
  ) {
    override fun toString(): String = "SessionMetadata(id=${id.censor()}, nextSms=$nextSms, nextCall=$nextCall, nextVerificationAttempt=$nextVerificationAttempt, allowedToRequestCode=$allowedToRequestCode, requestedInformation=$requestedInformation, verified=$verified)"
  }

  @OptIn(ExperimentalSerializationApi::class)
  @Serializable
  class AccountAttributes(
    val signalingKey: String?,
    val registrationId: Int,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val voice: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val video: Boolean = true,
    val fetchesMessages: Boolean,
    val registrationLock: String?,
    @Serializable(with = ByteArrayToBase64Serializer::class)
    val unidentifiedAccessKey: ByteArray?,
    val unrestrictedUnidentifiedAccess: Boolean,
    val discoverableByPhoneNumber: Boolean,
    val capabilities: Capabilities?,
    val name: String? = null,
    val pniRegistrationId: Int,
    val recoveryPassword: String?
  ) {

    @Serializable
    data class Capabilities(
      val storage: Boolean,
      val versionedExpirationTimer: Boolean,
      val attachmentBackfill: Boolean,
      val spqr: Boolean,
      val usernameChangeSyncMessage: Boolean
    )
  }

  @Serializable
  class DeviceAttributes(
    val fetchesMessages: Boolean,
    val registrationId: Int,
    /** Null for an account with no PNI, in which case it is omitted from the request. */
    val pniRegistrationId: Int?,
    val name: String?,
    val capabilities: AccountAttributes.Capabilities?
  )

  @Serializable
  data class RegisterAccountResponse(
    @SerialName("uuid") val aci: String,
    val pni: String,
    @SerialName("number") val e164: String,
    val usernameHash: String?,
    val usernameLinkHandle: String?,
    val storageCapable: Boolean,
    val entitlements: Entitlements?,
    val reregistration: Boolean
  ) {
    @Serializable
    data class Entitlements(
      val badges: List<Badge>,
      val backup: Backup?
    )

    @Serializable
    data class Badge(
      val id: String,
      val expirationSeconds: Long,
      val visible: Boolean
    )

    @Serializable
    data class Backup(
      val backupLevel: Long,
      val expirationSeconds: Long
    )
  }

  @Serializable
  data class RegistrationLockResponse(
    val timeRemaining: Long,
    val svr2Credentials: SvrCredentials
  )

  @Serializable
  data class SvrCredentials(
    val username: String,
    val password: String
  ) {
    override fun toString(): String = "SvrCredentials(username=${username.censor()}, password=${password.censor()})"
  }

  @Serializable
  data class CheckSvrCredentialsResponse(
    val matches: Map<String, String>
  ) {
    /**
     * The first valid credential, if any.
     *
     * The response is structured like this:
     * {
     *   matches: {
     *     <token>: "match|no-match|invalid"
     *   }
     * }
     *
     * So we find the first map entry with "match". The token is "username:password", so we split it apart.
     * Important: The password can have ":" in it, so we need to make sure to just split on the first ":".
     */
    val validCredential: SvrCredentials? by lazy {
      matches.entries.firstOrNull { it.value == "match" }?.key?.split(":", limit = 2)?.let { SvrCredentials(it[0], it[1]) }
    }
  }

  @Serializable
  data class CheckSvrCredentialsRequest(
    val number: String,
    val tokens: List<String>
  ) {
    companion object {
      fun createForCredentials(number: String, credentials: List<SvrCredentials>): CheckSvrCredentialsRequest {
        return CheckSvrCredentialsRequest(
          number = number,
          tokens = credentials.map { "${it.username}:${it.password}" }
        )
      }
    }
  }

  @Serializable
  data class ThirdPartyServiceErrorResponse(
    val reason: String,
    val permanentFailure: Boolean
  )

  /** Minimal view of the `PUT /v1/devices/link` success body; we only need the assigned device id. */
  @Serializable
  data class LinkDeviceResponse(
    val deviceId: Int
  )

  data class PreKeyCollection(
    val identityKey: IdentityKey,
    val signedPreKey: SignedPreKeyRecord,
    val lastResortKyberPreKey: KyberPreKeyRecord
  )

  enum class VerificationCodeTransport {
    SMS, VOICE
  }

  /**
   * The user's chosen restore method, reported to the server so the old device's UX can update.
   */
  enum class RestoreMethod {
    REMOTE_BACKUP, LOCAL_BACKUP, DEVICE_TRANSFER, DECLINE
  }

  @Serializable
  private class CreateVerificationSessionRequestBody(
    val number: String,
    val pushToken: String? = null,
    val pushTokenType: String? = null,
    val mcc: String? = null,
    val mnc: String? = null
  )

  @Serializable
  private class UpdateVerificationSessionRequestBody(
    val captcha: String? = null,
    val pushToken: String? = null,
    val pushTokenType: String? = null,
    val pushChallenge: String? = null,
    val mcc: String? = null,
    val mnc: String? = null
  )

  @Serializable
  private class VerificationCodeRequestBody(
    val transport: String,
    val client: String
  )

  @Serializable
  private class SubmitVerificationCodeRequestBody(
    val code: String
  )

  @OptIn(ExperimentalSerializationApi::class)
  @Serializable
  private class RegisterAccountRequestBody(
    val sessionId: String? = null,
    val recoveryPassword: String? = null,
    val accountAttributes: AccountAttributes,
    val aciIdentityKey: String,
    val pniIdentityKey: String,
    val aciSignedPreKey: SignedPreKeyEntity,
    val pniSignedPreKey: SignedPreKeyEntity,
    val aciPqLastResortPreKey: KyberPreKeyEntity,
    val pniPqLastResortPreKey: KyberPreKeyEntity,
    val gcmToken: GcmRegistrationId? = null,
    val skipDeviceTransfer: Boolean,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val requireAtomic: Boolean = true
  )

  @Serializable
  private class RegisterAsSecondaryDeviceRequestBody(
    val verificationCode: String,
    val accountAttributes: DeviceAttributes,
    val aciSignedPreKey: SignedPreKeyEntity,
    val pniSignedPreKey: SignedPreKeyEntity? = null,
    val aciPqLastResortPreKey: KyberPreKeyEntity,
    val pniPqLastResortPreKey: KyberPreKeyEntity? = null,
    val gcmToken: GcmRegistrationId? = null
  )

  @Serializable
  private class RestoreMethodBody(
    val method: RestoreMethod
  )

  @Serializable
  private class GcmRegistrationId(
    val gcmRegistrationId: String,
    val webSocketChannel: Boolean
  )

  @Serializable
  private class SignedPreKeyEntity(
    val keyId: Long,
    @Serializable(with = ECPublicKeyToBase64Serializer::class)
    val publicKey: ECPublicKey,
    @Serializable(with = ByteArrayToBase64Serializer::class)
    val signature: ByteArray
  )

  @Serializable
  private class KyberPreKeyEntity(
    val keyId: Long,
    @Serializable(with = KEMPublicKeyToBase64Serializer::class)
    val publicKey: KEMPublicKey,
    @Serializable(with = ByteArrayToBase64Serializer::class)
    val signature: ByteArray
  )

  sealed class CreateSessionError : BadRequestError {
    data class InvalidRequest(val message: String) : CreateSessionError()
    data class RateLimited(val retryAfter: Duration) : CreateSessionError()
  }

  sealed class GetSessionStatusError : BadRequestError {
    data class InvalidSessionId(val message: String) : GetSessionStatusError()
    data class SessionNotFound(val message: String) : GetSessionStatusError()
    data class InvalidRequest(val message: String) : GetSessionStatusError()
  }

  sealed class UpdateSessionError : BadRequestError {
    data class RejectedUpdate(val message: String) : UpdateSessionError()
    data class SessionNotFound(val message: String) : UpdateSessionError()
    data class InvalidRequest(val message: String) : UpdateSessionError()
    data class RateLimited(val retryAfter: Duration, val session: SessionMetadata) : UpdateSessionError()
  }

  sealed class RequestVerificationCodeError : BadRequestError {
    data class InvalidSessionId(val message: String) : RequestVerificationCodeError()
    data class SessionNotFound(val message: String) : RequestVerificationCodeError()
    data class MissingRequestInformationOrAlreadyVerified(val session: SessionMetadata) : RequestVerificationCodeError()
    data class CouldNotFulfillWithRequestedTransport(val session: SessionMetadata) : RequestVerificationCodeError()
    data class InvalidRequest(val message: String) : RequestVerificationCodeError()
    data class RateLimited(val retryAfter: Duration, val session: SessionMetadata) : RequestVerificationCodeError()
    data class ThirdPartyServiceError(val data: ThirdPartyServiceErrorResponse) : RequestVerificationCodeError()
  }

  sealed class SubmitVerificationCodeError : BadRequestError {
    data class InvalidSessionIdOrVerificationCode(val message: String) : SubmitVerificationCodeError()
    data class SessionNotFound(val message: String) : SubmitVerificationCodeError()
    data class SessionAlreadyVerifiedOrNoCodeRequested(val session: SessionMetadata) : SubmitVerificationCodeError()
    data class RateLimited(val retryAfter: Duration, val session: SessionMetadata) : SubmitVerificationCodeError()
  }

  sealed class RegisterAccountError : BadRequestError {
    data class SessionNotFoundOrNotVerified(val message: String) : RegisterAccountError()
    data class RegistrationRecoveryPasswordIncorrect(val message: String) : RegisterAccountError()
    data object DeviceTransferPossible : RegisterAccountError()
    data class InvalidRequest(val message: String) : RegisterAccountError()
    data class RegistrationLock(val data: RegistrationLockResponse) : RegisterAccountError()
    data class RateLimited(val retryAfter: Duration) : RegisterAccountError()
  }

  sealed class CheckSvrCredentialsError : BadRequestError {
    data object Unauthorized : CheckSvrCredentialsError()
    data class InvalidRequest(val message: String) : CheckSvrCredentialsError()
  }

  sealed class SetRestoreMethodError : BadRequestError {
    data class InvalidRequest(val message: String) : SetRestoreMethodError()
    data class RateLimited(val retryAfter: Duration) : SetRestoreMethodError()
  }

  sealed interface RegisterAsLinkedDeviceError : BadRequestError {
    data object IncorrectVerification : RegisterAsLinkedDeviceError
    data object MissingCapability : RegisterAsLinkedDeviceError
    data object MaxLinkedDevices : RegisterAsLinkedDeviceError
    data class InvalidRequest(val message: String? = null) : RegisterAsLinkedDeviceError
    data class RateLimited(val retryAfter: Duration?) : RegisterAsLinkedDeviceError
  }
}
