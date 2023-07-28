package org.thoughtcrime.securesms.groups.v2.processing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.both
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasProperty
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.Hex.fromStringCondensed
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.logging.SignalProtocolLoggerProvider
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedString
import org.signal.storageservice.protos.groups.local.DecryptedTimer
import org.thoughtcrime.securesms.SignalStoreRule
import org.thoughtcrime.securesms.database.GroupStateTestData
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context
import org.thoughtcrime.securesms.database.model.databaseprotos.member
import org.thoughtcrime.securesms.database.model.databaseprotos.requestingMember
import org.thoughtcrime.securesms.database.setNewDescription
import org.thoughtcrime.securesms.database.setNewTitle
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupsV2Authorization
import org.thoughtcrime.securesms.jobmanager.JobManager
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.logging.CustomSignalProtocolLogger
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api
import org.whispersystems.signalservice.api.groupsv2.PartialDecryptedGroup
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.push.ServiceIds
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class GroupsV2StateProcessorTest {

  companion object {
    private val masterKey = GroupMasterKey(fromStringCondensed("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))
    private val selfAci: ACI = ACI.from(UUID.randomUUID())
    private val serviceIds: ServiceIds = ServiceIds(selfAci, PNI.from(UUID.randomUUID()))
    private val otherAci: ACI = ACI.from(UUID.randomUUID())
    private val selfAndOthers: List<DecryptedMember> = listOf(member(selfAci), member(otherAci))
    private val others: List<DecryptedMember> = listOf(member(otherAci))
  }

  private lateinit var groupTable: GroupTable
  private lateinit var recipientTable: RecipientTable
  private lateinit var groupsV2API: GroupsV2Api
  private lateinit var groupsV2Authorization: GroupsV2Authorization
  private lateinit var profileAndMessageHelper: GroupsV2StateProcessor.ProfileAndMessageHelper
  private lateinit var jobManager: JobManager

  private lateinit var processor: GroupsV2StateProcessor.StateProcessorForGroup

  @get:Rule
  val signalStore: SignalStoreRule = SignalStoreRule()

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())
    SignalProtocolLoggerProvider.setProvider(CustomSignalProtocolLogger())

    groupTable = mockk(relaxed = true)
    recipientTable = mockk()
    groupsV2API = mockk()
    groupsV2Authorization = mockk(relaxed = true)
    profileAndMessageHelper = mockk(relaxed = true)
    jobManager = mockk(relaxed = true)

    mockkStatic(ApplicationDependencies::class)
    every { ApplicationDependencies.getJobManager() } returns jobManager

    processor = GroupsV2StateProcessor.StateProcessorForGroup(serviceIds, ApplicationProvider.getApplicationContext(), groupTable, groupsV2API, groupsV2Authorization, masterKey, profileAndMessageHelper)
  }

  @After
  fun tearDown() {
//    reset(ApplicationDependencies.getJobManager())
  }

  private fun given(init: GroupStateTestData.() -> Unit) {
    val data = givenData(init)

    every { groupTable.getGroup(any<GroupId.V2>()) } returns data.groupRecord
    every { groupTable.isUnknownGroup(any<GroupId>()) } returns !data.groupRecord.isPresent

    data.serverState?.let { serverState ->
      val testPartial = object : PartialDecryptedGroup(null, serverState, null, null) {
        override fun getFullyDecryptedGroup(): DecryptedGroup {
          return serverState
        }
      }

      every { groupsV2API.getPartialDecryptedGroup(any(), any()) } returns testPartial
      every { groupsV2API.getGroup(any(), any()) } returns serverState
    }

    data.changeSet?.let { changeSet ->
      every { groupsV2API.getGroupHistoryPage(any(), data.requestedRevision, any(), data.includeFirst) } returns changeSet.toApiResponse()
    }
  }

  private fun givenData(init: GroupStateTestData.() -> Unit): GroupStateTestData {
    val data = GroupStateTestData(masterKey)
    data.init()
    return data
  }

  @Test
  fun `when local revision matches server revision, then return consistent or ahead`() {
    given {
      localState(
        revision = 5,
        members = selfAndOthers
      )
      serverState(
        revision = 5,
        extendGroup = localState
      )
    }

    val result = processor.updateLocalGroupToRevision(GroupsV2StateProcessor.LATEST, 0, null)
    assertThat("local and server match revisions", result.groupState, `is`(GroupsV2StateProcessor.GroupState.GROUP_CONSISTENT_OR_AHEAD))
  }

  @Test
  fun `when local revision is one less than latest server version, then update from server with group change only`() {
    given {
      localState(
        revision = 5,
        title = "Fdsa",
        members = selfAndOthers
      )
      serverState(
        revision = 6,
        extendGroup = localState,
        title = "Asdf"
      )
      changeSet {
        changeLog(6) {
          change {
            setNewTitle("Asdf")
          }
        }
      }
      apiCallParameters(requestedRevision = 5, includeFirst = false)
    }

    val result = processor.updateLocalGroupToRevision(GroupsV2StateProcessor.LATEST, 0, null)
    assertThat("local should update to server", result.groupState, `is`(GroupsV2StateProcessor.GroupState.GROUP_UPDATED))
    assertThat("title changed to match server", result.latestServer!!.title, `is`("Asdf"))
  }

  @Test
  fun `when local revision is two less than server revision, then update from server with full group state and change`() {
    given {
      localState(
        revision = 5,
        title = "Fdsa",
        members = selfAndOthers
      )
      serverState(
        revision = 7,
        title = "Asdf!"
      )
      changeSet {
        changeLog(6) {
          fullSnapshot(extendGroup = localState, title = "Asdf")
          change {
            setNewTitle("Asdf")
          }
        }
        changeLog(7) {
          change {
            setNewTitle("Asdf!")
          }
        }
      }
      apiCallParameters(requestedRevision = 5, includeFirst = true)
    }

    val result = processor.updateLocalGroupToRevision(GroupsV2StateProcessor.LATEST, 0, null)
    assertThat("local should update to server", result.groupState, `is`(GroupsV2StateProcessor.GroupState.GROUP_UPDATED))
    assertThat("revision matches server", result.latestServer!!.revision, `is`(7))
    assertThat("title changed on server to final result", result.latestServer!!.title, `is`("Asdf!"))
  }

  @Test
  fun `when change log returns a group state more than one higher than our local state, then still update to server state`() {
    given {
      localState(
        revision = 100,
        title = "To infinity",
        members = selfAndOthers
      )
      serverState(
        revision = 111,
        title = "And beyond",
        description = "Description"
      )
      changeSet {
        changeLog(110) {
          fullSnapshot(
            extendGroup = localState,
            title = "And beyond"
          )
        }
        changeLog(111) {
          change {
            setNewDescription("Description")
          }
        }
      }
    }

    val result = processor.updateLocalGroupToRevision(GroupsV2StateProcessor.LATEST, 0, null)
    assertThat("local should update to server", result.groupState, `is`(GroupsV2StateProcessor.GroupState.GROUP_UPDATED))
    assertThat("revision matches server", result.latestServer!!.revision, `is`(111))
    assertThat("title changed on server to final result", result.latestServer!!.title, `is`("And beyond"))
    assertThat("Description updated in change after full snapshot", result.latestServer!!.description, `is`("Description"))
  }

  @Test
  fun `when receiving peer change for next revision, then apply change without server call`() {
    given {
      localState(
        revision = 5,
        disappearingMessageTimer = DecryptedTimer.newBuilder().setDuration(1000).build()
      )
    }

    val signedChange = DecryptedGroupChange.newBuilder().apply {
      revision = 6
      setNewTimer(DecryptedTimer.newBuilder().setDuration(5000))
    }

    val result = processor.updateLocalGroupToRevision(6, 0, signedChange.build())
    assertThat("revision matches peer change", result.latestServer!!.revision, `is`(6))
    assertThat("timer changed by peer change", result.latestServer!!.disappearingMessagesTimer.duration, `is`(5000))
  }

  @Test
  fun `when freshly added to a group, with no group changes after being added, then update from server at the revision we were added`() {
    given {
      serverState(
        revision = 2,
        title = "Breaking Signal for Science",
        description = "We break stuff, because we must.",
        members = listOf(member(otherAci), member(selfAci, joinedAt = 2))
      )
      changeSet {
        changeLog(2) {
          fullSnapshot(serverState)
        }
      }
      apiCallParameters(2, true)
    }

    val result = processor.updateLocalGroupToRevision(2, 0, DecryptedGroupChange.getDefaultInstance())
    assertThat("local should update to server", result.groupState, `is`(GroupsV2StateProcessor.GroupState.GROUP_UPDATED))
    assertThat("revision matches server", result.latestServer!!.revision, `is`(2))
  }

  @Test
  fun `when freshly added to a group, with additional group changes after being added, then only update from server at the revision we were added, and then schedule pulling additional changes later`() {
    given {
      serverState(
        revision = 3,
        title = "Breaking Signal for Science",
        description = "We break stuff, because we must.",
        members = listOf(member(otherAci), member(selfAci, joinedAt = 2))
      )
      changeSet {
        changeLog(2) {
          fullSnapshot(serverState, title = "Baking Signal for Science")
        }
        changeLog(3) {
          change {
            setNewTitle("Breaking Signal for Science")
          }
        }
      }
      apiCallParameters(2, true)
    }

    every { groupTable.isUnknownGroup(any<GroupId>()) } returns true

    val result = processor.updateLocalGroupToRevision(2, 0, DecryptedGroupChange.getDefaultInstance())

    assertThat("local should update to revision added", result.groupState, `is`(GroupsV2StateProcessor.GroupState.GROUP_UPDATED))
    assertThat("revision matches peer revision added", result.latestServer!!.revision, `is`(2))
    assertThat("title matches that as it was in revision added", result.latestServer!!.title, `is`("Baking Signal for Science"))
    verify { jobManager.add(ofType(RequestGroupV2InfoJob::class)) }
  }

  @Test
  fun `when learning of a group via storage service, then update from server to latest revision`() {
    given {
      localState(
        revision = GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION
      )
      serverState(
        revision = 10,
        title = "Stargate Fan Club",
        description = "Indeed.",
        members = selfAndOthers
      )
    }

    val result = processor.updateLocalGroupToRevision(GroupsV2StateProcessor.LATEST, 0, null)

    assertThat("local should update to server", result.groupState, `is`(GroupsV2StateProcessor.GroupState.GROUP_UPDATED))
    assertThat("revision matches latest server", result.latestServer!!.revision, `is`(10))
  }

  @Test
  fun `when request to join group is approved, with no group changes after approved, then update from server to revision we were added`() {
    given {
      localState(
        revision = GroupsV2StateProcessor.PLACEHOLDER_REVISION,
        title = "Beam me up",
        requestingMembers = listOf(requestingMember(selfAci))
      )
      serverState(
        revision = 3,
        title = "Beam me up",
        members = listOf(member(otherAci), member(selfAci, joinedAt = 3))
      )
      changeSet {
        changeLog(3) {
          fullSnapshot(serverState)
          change {
            addNewMembers(member(selfAci, joinedAt = 3))
          }
        }
      }
      apiCallParameters(requestedRevision = 3, includeFirst = true)
    }

    val result = processor.updateLocalGroupToRevision(3, 0, null)

    assertThat("local should update to server", result.groupState, `is`(GroupsV2StateProcessor.GroupState.GROUP_UPDATED))
    assertThat("revision matches server", result.latestServer!!.revision, `is`(3))
  }

  @Test
  fun `when request to join group is approved, with group changes occurring after approved, then update from server to revision we were added, and then schedule pulling additional changes later`() {
    given {
      localState(
        revision = GroupsV2StateProcessor.PLACEHOLDER_REVISION,
        title = "Beam me up",
        requestingMembers = listOf(requestingMember(selfAci))
      )
      serverState(
        revision = 5,
        title = "Beam me up!",
        members = listOf(member(otherAci), member(selfAci, joinedAt = 3))
      )
      changeSet {
        changeLog(3) {
          fullSnapshot(extendGroup = serverState, title = "Beam me up")
          change {
            addNewMembers(member(selfAci, joinedAt = 3))
          }
        }
        changeLog(4) {
          change {
            setNewTitle("May the force be with you")
          }
        }
        changeLog(5) {
          change {
            setNewTitle("Beam me up!")
          }
        }
      }
      apiCallParameters(requestedRevision = 3, includeFirst = true)
    }

    val result = processor.updateLocalGroupToRevision(3, 0, null)

    assertThat("local should update to server", result.groupState, `is`(GroupsV2StateProcessor.GroupState.GROUP_UPDATED))
    assertThat("revision matches revision approved at", result.latestServer!!.revision, `is`(3))
    assertThat("title matches revision approved at", result.latestServer!!.title, `is`("Beam me up"))
    verify { jobManager.add(ofType(RequestGroupV2InfoJob::class)) }
  }

  @Test
  fun `when failing to update fully to desired revision, then try again forcing inclusion of full group state, and then successfully update from server to latest revision`() {
    val randomMembers = listOf(member(UUID.randomUUID()), member(UUID.randomUUID()))
    given {
      localState(
        revision = 100,
        title = "Title",
        members = others
      )
      serverState(
        extendGroup = localState,
        revision = 101,
        members = listOf(others[0], randomMembers[0], member(selfAci, joinedAt = 100))
      )
      changeSet {
        changeLog(100) {
          change {
            addNewMembers(member(selfAci, joinedAt = 100))
          }
        }
        changeLog(101) {
          change {
            addDeleteMembers(randomMembers[1].uuid)
            addModifiedProfileKeys(randomMembers[0])
          }
        }
      }
      apiCallParameters(100, false)
    }

    val secondApiCallChangeSet = GroupStateTestData(masterKey).apply {
      changeSet {
        changeLog(100) {
          fullSnapshot(
            extendGroup = localState,
            members = selfAndOthers + randomMembers[0] + randomMembers[1]
          )
          change {
            addNewMembers(member(selfAci, joinedAt = 100))
          }
        }
        changeLog(101) {
          change {
            addDeleteMembers(randomMembers[1].uuid)
            addModifiedProfileKeys(randomMembers[0])
          }
        }
      }
    }
    every { groupsV2API.getGroupHistoryPage(any(), 100, any(), true) } returns secondApiCallChangeSet.changeSet!!.toApiResponse()

    val result = processor.updateLocalGroupToRevision(GroupsV2StateProcessor.LATEST, 0, null)

    assertThat("local should update to server", result.groupState, `is`(GroupsV2StateProcessor.GroupState.GROUP_UPDATED))
    assertThat("revision matches latest revision on server", result.latestServer!!.revision, `is`(101))
  }

  /**
   * If for some reason we missed a member being added in our local state, and then we preform a multi-revision update,
   * we should now know about the member and add update messages to the chat.
   */
  @Test
  fun missedMemberAddResolvesWithMultipleRevisionUpdate() {
    val secondOther = member(ACI.from(UUID.randomUUID()))

    profileAndMessageHelper.masterKey = masterKey

    val updateMessageContextArgs = mutableListOf<DecryptedGroupV2Context>()
    every { profileAndMessageHelper.insertUpdateMessages(any(), any(), any()) } answers { callOriginal() }
    every { profileAndMessageHelper.storeMessage(capture(updateMessageContextArgs), any()) } returns Unit

    given {
      localState(
        revision = 8,
        title = "Whatever",
        members = selfAndOthers
      )
      serverState(
        revision = 10,
        title = "Changed",
        members = selfAndOthers + secondOther
      )
      changeSet {
        changeLog(9) {
          change {
            setNewTitle("Mid-Change")
          }
          fullSnapshot(
            title = "Mid-Change",
            members = selfAndOthers + secondOther
          )
        }
        changeLog(10) {
          change {
            setNewTitle("Changed")
          }
        }
      }
      apiCallParameters(requestedRevision = 8, includeFirst = true)
    }

    val result = processor.updateLocalGroupToRevision(GroupsV2StateProcessor.LATEST, 0, null)
    assertThat("local should update to server", result.groupState, `is`(GroupsV2StateProcessor.GroupState.GROUP_UPDATED))
    assertThat("members contains second other", result.latestServer!!.membersList, hasItem(secondOther))

    assertThat("group update messages contains new member add", updateMessageContextArgs.map { it.change.newMembersList }, hasItem(hasItem(secondOther)))
  }

  /**
   * If for some reason we missed a member being added in our local state, and then we preform a forced sanity update,
   * we should now know about the member and any other changes, and add update messages to the chat.
   */
  @Test
  fun missedMemberAddResolvesWithForcedUpdate() {
    val secondOther = member(ACI.from(UUID.randomUUID()))

    profileAndMessageHelper.masterKey = masterKey

    val updateMessageContextArgs = mutableListOf<DecryptedGroupV2Context>()
    every { profileAndMessageHelper.insertUpdateMessages(any(), any(), any()) } answers { callOriginal() }
    every { profileAndMessageHelper.storeMessage(capture(updateMessageContextArgs), any()) } returns Unit

    given {
      localState(
        revision = 10,
        title = "Title",
        members = selfAndOthers
      )
      serverState(
        revision = 10,
        title = "Changed",
        members = selfAndOthers + secondOther
      )
    }

    val result = processor.forceSanityUpdateFromServer(0)
    assertThat("local should update to server", result.groupState, `is`(GroupsV2StateProcessor.GroupState.GROUP_UPDATED))
    assertThat("members contains second other", result.latestServer!!.membersList, hasItem(secondOther))
    assertThat("title should be updated", result.latestServer!!.title, `is`("Changed"))

    assertThat("group update messages contains new member add", updateMessageContextArgs.map { it.change.newMembersList }, hasItem(hasItem(secondOther)))

    assertThat(
      "group update messages contains title change",
      updateMessageContextArgs.map { it.change.newTitle },
      hasItem(both<DecryptedString>(notNullValue()).and(hasProperty("value", `is`("Changed"))))
    )
  }

  /**
   * If we preform a forced sanity update, with no differences between local and server, then it should be no-op.
   */
  @Test
  fun noDifferencesNoOpsWithForcedUpdate() {
    given {
      localState(
        revision = 10,
        title = "Title",
        members = selfAndOthers
      )
      serverState(
        revision = 10,
        title = "Title",
        members = selfAndOthers
      )
    }

    val result = processor.forceSanityUpdateFromServer(0)
    assertThat("local should be unchanged", result.groupState, `is`(GroupsV2StateProcessor.GroupState.GROUP_CONSISTENT_OR_AHEAD))
  }
}
