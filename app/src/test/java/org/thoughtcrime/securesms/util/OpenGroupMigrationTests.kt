package org.thoughtcrime.securesms.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.LokiAPIDatabase
import org.thoughtcrime.securesms.database.LokiMessageDatabase
import org.thoughtcrime.securesms.database.LokiThreadDatabase
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.SmsDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.groups.OpenGroupMigrator
import org.thoughtcrime.securesms.groups.OpenGroupMigrator.OpenGroupMapping
import org.thoughtcrime.securesms.groups.OpenGroupMigrator.roomStub

class OpenGroupMigrationTests {

    companion object {
        const val EXAMPLE_LEGACY_ENCODED_OPEN_GROUP = "__loki_public_chat_group__!687474703a2f2f3131362e3230332e37302e33332e6f78656e"
        const val EXAMPLE_NEW_ENCODED_OPEN_GROUP = "__loki_public_chat_group__!68747470733a2f2f6f70656e2e67657473657373696f6e2e6f72672e6f78656e"
        const val OXEN_STUB_HEX = "6f78656e"

        const val EXAMPLE_LEGACY_SERVER_ID = "http://116.203.70.33.oxen"
        const val EXAMPLE_NEW_SERVER_ID = "https://open.getsession.org.oxen"

        const val LEGACY_THREAD_ID = 1L
        const val NEW_THREAD_ID = 2L
    }

    private fun legacyOpenGroupRecipient(additionalMocks: ((KStubbing<Recipient>) -> Unit) ? = null) = mock<Recipient> {
        on { address } doReturn Address.fromSerialized(EXAMPLE_LEGACY_ENCODED_OPEN_GROUP)
        on { isOpenGroupRecipient } doReturn true
        additionalMocks?.let { it(this) }
    }

    private fun newOpenGroupRecipient(additionalMocks: ((KStubbing<Recipient>) -> Unit) ? = null) = mock<Recipient> {
        on { address } doReturn Address.fromSerialized(EXAMPLE_NEW_ENCODED_OPEN_GROUP)
        on { isOpenGroupRecipient } doReturn true
        additionalMocks?.let { it(this) }
    }

    private fun legacyThreadRecord(additionalRecipientMocks: ((KStubbing<Recipient>) -> Unit) ? = null, additionalThreadMocks: ((KStubbing<ThreadRecord>) -> Unit)? = null) = mock<ThreadRecord> {
        val returnedRecipient = legacyOpenGroupRecipient(additionalRecipientMocks)
        on { recipient } doReturn returnedRecipient
        on { threadId } doReturn LEGACY_THREAD_ID
    }

    private fun newThreadRecord(additionalRecipientMocks: ((KStubbing<Recipient>) -> Unit)? = null, additionalThreadMocks: ((KStubbing<ThreadRecord>) -> Unit)? = null) = mock<ThreadRecord> {
        val returnedRecipient = newOpenGroupRecipient(additionalRecipientMocks)
        on { recipient } doReturn returnedRecipient
        on { threadId } doReturn NEW_THREAD_ID
    }

    @Test
    fun `it should generate the correct room stubs for legacy groups`() {
        val mockRecipient = legacyOpenGroupRecipient()
        assertEquals(OXEN_STUB_HEX, mockRecipient.roomStub())
    }

    @Test
    fun `it should generate the correct room stubs for new groups`() {
        val mockNewRecipient = newOpenGroupRecipient()
        assertEquals(OXEN_STUB_HEX, mockNewRecipient.roomStub())
    }

    @Test
    fun `it should return correct mappings`() {
        val legacyThread = legacyThreadRecord()
        val newThread = newThreadRecord()

        val expectedMapping = listOf(
            OpenGroupMapping(OXEN_STUB_HEX, LEGACY_THREAD_ID, NEW_THREAD_ID)
        )

        assertTrue(expectedMapping.containsAll(OpenGroupMigrator.getExistingMappings(listOf(legacyThread), listOf(newThread))))
    }

    @Test
    fun `it should return no mappings if there are no legacy open groups`() {
        val mappings = OpenGroupMigrator.getExistingMappings(listOf(), listOf())
        assertTrue(mappings.isEmpty())
    }

    @Test
    fun `it should return no mappings if there are only new open groups`() {
        val newThread = newThreadRecord()
        val mappings = OpenGroupMigrator.getExistingMappings(emptyList(), listOf(newThread))
        assertTrue(mappings.isEmpty())
    }

    @Test
    fun `it should return null new thread in mappings if there are only legacy open groups`() {
        val legacyThread = legacyThreadRecord()
        val mappings = OpenGroupMigrator.getExistingMappings(listOf(legacyThread), emptyList())
        val expectedMappings = listOf(
            OpenGroupMapping(OXEN_STUB_HEX, LEGACY_THREAD_ID, null)
        )
        assertTrue(expectedMappings.containsAll(mappings))
    }

    @Test
    fun `test migration thread DB calls legacy and returns if no legacy official groups`() {
        val mockedThreadDb = mock<ThreadDatabase> {
            on { legacyOxenOpenGroups } doReturn emptyList()
        }
        val mockedDbComponent = mock<DatabaseComponent> {
            on { threadDatabase() } doReturn mockedThreadDb
        }

        OpenGroupMigrator.migrate(mockedDbComponent)

        verify(mockedDbComponent).threadDatabase()
        verify(mockedThreadDb).legacyOxenOpenGroups
        verifyNoMoreInteractions(mockedThreadDb)
    }

    @Test
    fun `it should migrate on thread, group and loki dbs with correct values for legacy only migration`() {
        // mock threadDB
        val capturedThreadId = argumentCaptor<Long>()
        val capturedNewEncoded = argumentCaptor<String>()
        val mockedThreadDb = mock<ThreadDatabase> {
            val legacyThreadRecord = legacyThreadRecord()
            on { legacyOxenOpenGroups } doReturn listOf(legacyThreadRecord)
            on { httpsOxenOpenGroups } doReturn emptyList()
            on { migrateEncodedGroup(capturedThreadId.capture(), capturedNewEncoded.capture()) } doAnswer {}
        }

        // mock groupDB
        val capturedGroupLegacyEncoded = argumentCaptor<String>()
        val capturedGroupNewEncoded = argumentCaptor<String>()
        val mockedGroupDb = mock<GroupDatabase> {
            on {
                migrateEncodedGroup(
                    capturedGroupLegacyEncoded.capture(),
                    capturedGroupNewEncoded.capture()
                )
            } doAnswer {}
        }

        // mock LokiAPIDB
        val capturedLokiLegacyGroup = argumentCaptor<String>()
        val capturedLokiNewGroup = argumentCaptor<String>()
        val mockedLokiApi = mock<LokiAPIDatabase> {
            on { migrateLegacyOpenGroup(capturedLokiLegacyGroup.capture(), capturedLokiNewGroup.capture()) } doAnswer {}
        }

        val pubKey = OpenGroupApi.defaultServerPublicKey
        val room = "oxen"
        val legacyServer = OpenGroupApi.legacyDefaultServer
        val newServer = OpenGroupApi.defaultServer

        val lokiThreadOpenGroup = argumentCaptor<OpenGroup>()
        val mockedLokiThreadDb = mock<LokiThreadDatabase> {
            on { getOpenGroupChat(eq(LEGACY_THREAD_ID)) } doReturn OpenGroup(legacyServer, room, "Oxen", 0, pubKey)
            on { setOpenGroupChat(lokiThreadOpenGroup.capture(), eq(LEGACY_THREAD_ID)) } doAnswer {}
        }

        val mockedDbComponent = mock<DatabaseComponent> {
            on { threadDatabase() } doReturn mockedThreadDb
            on { groupDatabase() } doReturn mockedGroupDb
            on { lokiAPIDatabase() } doReturn mockedLokiApi
            on { lokiThreadDatabase() } doReturn mockedLokiThreadDb
        }

        OpenGroupMigrator.migrate(mockedDbComponent)

        // expect threadDB migration to reflect new thread values:
        // thread ID = 1, encoded ID = new encoded ID
        assertEquals(LEGACY_THREAD_ID, capturedThreadId.firstValue)
        assertEquals(EXAMPLE_NEW_ENCODED_OPEN_GROUP, capturedNewEncoded.firstValue)

        // expect groupDB migration to reflect new thread values:
        // legacy encoded ID, new encoded ID
        assertEquals(EXAMPLE_LEGACY_ENCODED_OPEN_GROUP, capturedGroupLegacyEncoded.firstValue)
        assertEquals(EXAMPLE_NEW_ENCODED_OPEN_GROUP, capturedGroupNewEncoded.firstValue)

        // expect Loki API DB migration to reflect new thread values:
        assertEquals("${OpenGroupApi.legacyDefaultServer}.oxen", capturedLokiLegacyGroup.firstValue)
        assertEquals("${OpenGroupApi.defaultServer}.oxen", capturedLokiNewGroup.firstValue)

        assertEquals(newServer, lokiThreadOpenGroup.firstValue.server)

    }

    @Test
    fun `it should migrate and delete legacy thread with conflicting new and old values`() {

        // mock threadDB
        val capturedThreadId = argumentCaptor<Long>()
        val mockedThreadDb = mock<ThreadDatabase> {
            val legacyThreadRecord = legacyThreadRecord()
            val newThreadRecord = newThreadRecord()
            on { legacyOxenOpenGroups } doReturn listOf(legacyThreadRecord)
            on { httpsOxenOpenGroups } doReturn listOf(newThreadRecord)
            on { deleteConversation(capturedThreadId.capture()) } doAnswer {}
        }

        // mock groupDB
        val capturedGroupLegacyEncoded = argumentCaptor<String>()
        val mockedGroupDb = mock<GroupDatabase> {
            on { delete(capturedGroupLegacyEncoded.capture()) } doReturn true
        }

        // mock LokiAPIDB
        val capturedLokiLegacyGroup = argumentCaptor<String>()
        val capturedLokiNewGroup = argumentCaptor<String>()
        val mockedLokiApi = mock<LokiAPIDatabase> {
            on { migrateLegacyOpenGroup(capturedLokiLegacyGroup.capture(), capturedLokiNewGroup.capture()) } doAnswer {}
        }

        // mock messaging dbs
        val migrateMmsFromThreadId = argumentCaptor<Long>()
        val migrateMmsToThreadId = argumentCaptor<Long>()

        val mockedMmsDb = mock<MmsDatabase> {
            on { migrateThreadId(migrateMmsFromThreadId.capture(), migrateMmsToThreadId.capture()) } doAnswer {}
        }

        val migrateSmsFromThreadId = argumentCaptor<Long>()
        val migrateSmsToThreadId = argumentCaptor<Long>()
        val mockedSmsDb = mock<SmsDatabase> {
            on { migrateThreadId(migrateSmsFromThreadId.capture(), migrateSmsToThreadId.capture()) } doAnswer {}
        }

        val lokiFromThreadId = argumentCaptor<Long>()
        val lokiToThreadId = argumentCaptor<Long>()
        val mockedLokiMessageDatabase = mock<LokiMessageDatabase> {
            on { migrateThreadId(lokiFromThreadId.capture(), lokiToThreadId.capture()) } doAnswer {}
        }

        val mockedLokiThreadDb = mock<LokiThreadDatabase> {
            on { removeOpenGroupChat(eq(LEGACY_THREAD_ID)) } doAnswer {}
        }

        val mockedDbComponent = mock<DatabaseComponent> {
            on { threadDatabase() } doReturn mockedThreadDb
            on { groupDatabase() } doReturn mockedGroupDb
            on { lokiAPIDatabase() } doReturn mockedLokiApi
            on { mmsDatabase() } doReturn mockedMmsDb
            on { smsDatabase() } doReturn mockedSmsDb
            on { lokiMessageDatabase() } doReturn mockedLokiMessageDatabase
            on { lokiThreadDatabase() } doReturn mockedLokiThreadDb
        }

        OpenGroupMigrator.migrate(mockedDbComponent)

        // should delete thread by thread ID
        assertEquals(LEGACY_THREAD_ID, capturedThreadId.firstValue)

        // should delete group by legacy encoded ID
        assertEquals(EXAMPLE_LEGACY_ENCODED_OPEN_GROUP, capturedGroupLegacyEncoded.firstValue)

        // should migrate SMS from legacy thread ID to new thread ID
        assertEquals(LEGACY_THREAD_ID, migrateSmsFromThreadId.firstValue)
        assertEquals(NEW_THREAD_ID, migrateSmsToThreadId.firstValue)

        // should migrate MMS from legacy thread ID to new thread ID
        assertEquals(LEGACY_THREAD_ID, migrateMmsFromThreadId.firstValue)
        assertEquals(NEW_THREAD_ID, migrateMmsToThreadId.firstValue)

    }



}