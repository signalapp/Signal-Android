/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.signal.core.util.Base64
import org.signal.core.util.StreamUtil
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.push.ServiceId
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.random.Random

@RunWith(Parameterized::class)
class ImportExportTestSuite(private val path: String) {
  companion object {
    val SELF_ACI = ServiceId.ACI.from(UUID.fromString("77770000-b477-4f35-a824-d92987a63641"))
    val SELF_PNI = ServiceId.PNI.from(UUID.fromString("77771111-b014-41fb-bf73-05cb2ec52910"))
    const val SELF_E164 = "+10000000000"
    val SELF_PROFILE_KEY = ProfileKey(Random.nextBytes(32))
    val MASTER_KEY = Base64.decode("sHuBMP4ToZk4tcNU+S8eBUeCt8Am5EZnvuqTBJIR4Do")

    const val TESTS_FOLDER = "backupTests"

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<Array<String>> {
      val testFiles = InstrumentationRegistry.getInstrumentation().context.resources.assets.list(TESTS_FOLDER)
      return testFiles?.map { arrayOf(it) }!!.toList()
    }
  }

  @Before
  fun setup() {
    SignalStore.svr.setMasterKey(MasterKey(MASTER_KEY), "1234")
    SignalStore.account.setE164(SELF_E164)
    SignalStore.account.setAci(SELF_ACI)
    SignalStore.account.setPni(SELF_PNI)
    SignalStore.account.generateAciIdentityKeyIfNecessary()
    SignalStore.account.generatePniIdentityKeyIfNecessary()
  }

  @Test
  fun testBinProto() {
    val binProtoBytes: ByteArray = InstrumentationRegistry.getInstrumentation().context.resources.assets.open("${TESTS_FOLDER}/$path").use {
      StreamUtil.readFully(it)
    }
    import(binProtoBytes)
    val generatedBackupData = BackupRepository.export()
    compare(binProtoBytes, generatedBackupData)
  }

  private fun import(importData: ByteArray) {
    BackupRepository.import(
      length = importData.size.toLong(),
      inputStreamFactory = { ByteArrayInputStream(importData) },
      selfData = BackupRepository.SelfData(SELF_ACI, SELF_PNI, SELF_E164, SELF_PROFILE_KEY),
      plaintext = true
    )
  }

  // TODO compare with libsignal's library
  private fun compare(import: ByteArray, export: ByteArray) {
  }
}
