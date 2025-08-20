/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.crypto

import org.signal.core.util.Base64
import org.signal.libsignal.metadata.certificate.SenderCertificate
import org.signal.libsignal.zkgroup.groupsend.GroupSendFullToken
import org.whispersystems.signalservice.api.groupsv2.GroupSendEndorsements

/**
 * Provides single interface for the various ways to send via sealed sender.
 */
sealed class SealedSenderAccess {

  abstract val senderCertificate: SenderCertificate
  abstract val headerName: String
  abstract val headerValue: String

  val header: String
    get() = "$headerName:$headerValue"

  abstract fun switchToFallback(): SealedSenderAccess?

  open fun applyHeader(): Boolean = true

  /**
   * For sending to an single recipient using group send endorsement/token first and then fallback to
   * access key if available.
   */
  class IndividualGroupSendTokenFirst(
    private val groupSendToken: GroupSendFullToken,
    override val senderCertificate: SenderCertificate,
    val unidentifiedAccess: UnidentifiedAccess? = null
  ) : SealedSenderAccess() {

    override val headerName: String = "Group-Send-Token"
    override val headerValue: String by lazy { Base64.encodeWithPadding(groupSendToken.serialize()) }

    override fun switchToFallback(): SealedSenderAccess? {
      return if (unidentifiedAccess != null) {
        IndividualUnidentifiedAccessFirst(unidentifiedAccess)
      } else {
        null
      }
    }
  }

  /**
   * For sending to an single recipient using access key first and then fallback to group send
   * token if available. The token is created lazily via the provided [createGroupSendToken] function.
   */
  class IndividualUnidentifiedAccessFirst(
    val unidentifiedAccess: UnidentifiedAccess,
    private val createGroupSendToken: CreateGroupSendToken? = null
  ) : SealedSenderAccess() {

    override val senderCertificate: SenderCertificate
      get() = unidentifiedAccess.unidentifiedCertificate

    override val headerName: String = "Unidentified-Access-Key"
    override val headerValue: String by lazy { Base64.encodeWithPadding(unidentifiedAccess.unidentifiedAccessKey) }

    override fun switchToFallback(): SealedSenderAccess? {
      val groupSendToken = createGroupSendToken?.create()
      return if (groupSendToken != null) {
        IndividualGroupSendTokenFirst(groupSendToken, senderCertificate)
      } else {
        null
      }
    }
  }

  /**
   * For sending to a "group" of recipients using group send endorsements/tokens.
   */
  class GroupGroupSendToken(
    private val groupSendEndorsements: GroupSendEndorsements
  ) : SealedSenderAccess() {

    override val headerName: String = "Group-Send-Token"
    override val headerValue: String by lazy { Base64.encodeWithPadding(groupSendEndorsements.serialize()) }

    override val senderCertificate: SenderCertificate
      get() = groupSendEndorsements.sealedSenderCertificate

    override fun switchToFallback(): SealedSenderAccess? {
      return null
    }
  }

  class StorySendNoop(override val senderCertificate: SenderCertificate) : SealedSenderAccess() {
    override val headerName: String = ""
    override val headerValue: String = ""

    override fun switchToFallback(): SealedSenderAccess? {
      return null
    }

    override fun applyHeader(): Boolean = false
  }

  /**
   * Provide a lazy way to create a group send token.
   */
  fun interface CreateGroupSendToken {
    fun create(): GroupSendFullToken?
  }

  companion object {
    @JvmField
    val NONE: SealedSenderAccess? = null

    @JvmStatic
    fun forIndividualWithGroupFallback(
      unidentifiedAccess: UnidentifiedAccess?,
      senderCertificate: SenderCertificate?,
      createGroupSendToken: CreateGroupSendToken?
    ): SealedSenderAccess? {
      if (unidentifiedAccess != null) {
        return IndividualUnidentifiedAccessFirst(unidentifiedAccess, createGroupSendToken)
      }

      val groupSendToken = createGroupSendToken?.create()
      if (groupSendToken != null && senderCertificate != null) {
        return IndividualGroupSendTokenFirst(groupSendToken, senderCertificate)
      }

      return null
    }

    @JvmStatic
    fun forIndividual(unidentifiedAccess: UnidentifiedAccess?): SealedSenderAccess? {
      return unidentifiedAccess?.let { IndividualUnidentifiedAccessFirst(it) }
    }

    @JvmStatic
    fun forFanOutGroupSend(groupSendTokens: List<GroupSendFullToken?>?, senderCertificate: SenderCertificate?, unidentifiedAccesses: List<UnidentifiedAccess?>): List<SealedSenderAccess?> {
      if (groupSendTokens == null) {
        return unidentifiedAccesses.map { a -> forIndividual(a) }
      }

      require(groupSendTokens.size == unidentifiedAccesses.size)

      return groupSendTokens
        .zip(unidentifiedAccesses)
        .map { (token, unidentifiedAccess) ->
          if (unidentifiedAccess != null) {
            IndividualUnidentifiedAccessFirst(unidentifiedAccess) { token }
          } else if (token != null && senderCertificate != null) {
            IndividualGroupSendTokenFirst(token, senderCertificate)
          } else {
            null
          }
        }
    }

    @JvmStatic
    fun forGroupSend(senderCertificate: SenderCertificate?, groupSendEndorsements: GroupSendEndorsements?, forStory: Boolean): SealedSenderAccess {
      if (forStory) {
        return StorySendNoop(senderCertificate!!)
      }

      return GroupGroupSendToken(groupSendEndorsements!!)
    }

    @JvmStatic
    fun isUnrestrictedForStory(sealedSenderAccess: SealedSenderAccess?): Boolean {
      return when (sealedSenderAccess) {
        is IndividualGroupSendTokenFirst -> sealedSenderAccess.unidentifiedAccess?.isUnrestrictedForStory ?: false
        is IndividualUnidentifiedAccessFirst -> sealedSenderAccess.unidentifiedAccess.isUnrestrictedForStory
        else -> false
      }
    }
  }
}
