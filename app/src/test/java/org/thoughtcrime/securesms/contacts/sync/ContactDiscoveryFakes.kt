/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.sync

import android.net.Uri
import android.provider.ContactsContract
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.signal.contacts.SystemContactsRepository
import org.signal.contacts.SystemContactsRepository.ContactDetails
import org.signal.contacts.SystemContactsRepository.ContactIterator
import org.signal.contacts.SystemContactsRepository.ContactPhoneDetails
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.network.NetworkResult
import org.signal.network.api.CdsApi
import org.thoughtcrime.securesms.keyvalue.MiscellaneousValues
import org.thoughtcrime.securesms.net.SignalNetwork
import org.whispersystems.signalservice.api.cds.CdsiV2Service
import java.util.Optional
import java.util.UUID
import java.util.function.Consumer

/**
 * An in-memory stand-in for the CDSI service. Numbers in [registered] are returned when asked about, and every
 * request is recorded in [requests]. Installs itself via [SignalNetwork.init].
 */
class FakeCdsServer {

  val registered: MutableMap<String, Account> = mutableMapOf()
  val requests: MutableList<Request> = mutableListOf()

  /** When set, the next request fails with this result instead of returning. */
  var nextFailure: NetworkResult<CdsiV2Service.Response>? = null

  private var tokenCounter = 0

  fun install() {
    val api: CdsApi = mockk()
    every { api.getRegisteredUsers(any(), any(), any(), any(), any(), any(), any()) } answers {
      val previous: Set<String> = arg(0)
      val new: Set<String> = arg(1)
      val token: Optional<ByteArray> = arg(3)
      val tokenSaver: Consumer<ByteArray> = arg(6)

      requests += Request(previousE164s = previous, newE164s = new, token = token.orElse(null))

      val failure = nextFailure
      if (failure != null) {
        nextFailure = null
        failure
      } else {
        tokenSaver.accept(byteArrayOf((++tokenCounter).toByte()))

        val results = (previous + new)
          .mapNotNull { e164 -> registered[e164]?.let { e164 to CdsiV2Service.ResponseItem(it.pni, Optional.ofNullable(it.aci)) } }
          .toMap()

        NetworkResult.Success(CdsiV2Service.Response(results, new.size))
      }
    }
    val signalNetwork: SignalNetwork = mockk()
    every { signalNetwork.cdsApi } returns api
    SignalNetwork.init(signalNetwork)
  }

  fun uninstall() {
    SignalNetwork.init(SignalNetwork())
  }

  fun register(e164: String, aci: ACI? = ACI.from(UUID.randomUUID()), pni: PNI = PNI.from(UUID.randomUUID())): Account {
    return Account(aci, pni).also { registered[e164] = it }
  }

  data class Account(val aci: ACI?, val pni: PNI)

  class Request(
    val previousE164s: Set<String>,
    val newE164s: Set<String>,
    val token: ByteArray?
  )
}

/**
 * An in-memory stand-in for the device's address book, served through a mocked [SystemContactsRepository].
 */
class FakeSystemContacts {

  val contacts: MutableList<Contact> = mutableListOf()

  private var nextContactId = 1L

  fun install() {
    mockkObject(SystemContactsRepository)
    mockkStatic(SystemContactsRepository::class)
    every { SystemContactsRepository.getAllDisplayNumbers(any()) } answers { contacts.map { it.e164 }.toSet() }
    every { SystemContactsRepository.getAllSystemContacts(any(), any(), any()) } answers { iteratorOf(contacts) }
    every { SystemContactsRepository.getContactDetailsByQueries(any(), any(), any(), any()) } answers {
      val queries: List<String> = arg(2)
      iteratorOf(contacts.filter { it.e164 in queries })
    }
  }

  fun uninstall() {
    unmockkStatic(SystemContactsRepository::class)
    unmockkObject(SystemContactsRepository)
  }

  fun add(e164: String, givenName: String, familyName: String? = null): Contact {
    val contactId = nextContactId++
    return Contact(
      e164 = e164,
      givenName = givenName,
      familyName = familyName,
      contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
    ).also { contacts += it }
  }

  fun remove(e164: String) {
    contacts.removeAll { it.e164 == e164 }
  }

  private fun iteratorOf(contacts: List<Contact>): ContactIterator {
    val details = contacts.map {
      ContactDetails(
        givenName = it.givenName,
        familyName = it.familyName,
        numbers = listOf(
          ContactPhoneDetails(
            contactUri = it.contactUri,
            displayName = listOfNotNull(it.givenName, it.familyName).joinToString(" "),
            photoUri = null,
            number = it.e164,
            type = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
            label = null
          )
        )
      )
    }.iterator()

    return object : ContactIterator {
      override fun hasNext(): Boolean = details.hasNext()
      override fun next(): ContactDetails = details.next()
    }
  }

  data class Contact(
    val e164: String,
    val givenName: String,
    val familyName: String?,
    val contactUri: Uri
  )
}

/**
 * Backs the CDS-related values on a mocked [MiscellaneousValues] with real state so that it persists across refreshes.
 */
class FakeCdsStoreState(private val misc: MiscellaneousValues) {

  var cdsToken: ByteArray? = null
  var cdsBlockedUntil: Long = 0
  var hasSuccessfullyRetrievedDirectory: Boolean = false
  var permanentlyBlocked: Boolean = false

  fun install() {
    every { misc.cdsToken } answers { cdsToken }
    every { misc.cdsToken = any() } answers { cdsToken = firstArg() }
    every { misc.cdsBlockedUtil } answers { cdsBlockedUntil }
    every { misc.cdsBlockedUtil = any() } answers { cdsBlockedUntil = firstArg() }
    every { misc.isCdsBlocked } answers { cdsBlockedUntil > 0 }
    every { misc.clearCdsBlocked() } answers { cdsBlockedUntil = 0 }
    every { misc.markCdsPermanentlyBlocked() } answers { permanentlyBlocked = true }
    every { misc.hasSuccessfullyRetrievedDirectory } answers { hasSuccessfullyRetrievedDirectory }
    every { misc.hasSuccessfullyRetrievedDirectory = any() } answers { hasSuccessfullyRetrievedDirectory = firstArg() }
  }
}
