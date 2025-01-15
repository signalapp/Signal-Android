package org.thoughtcrime.securesms.groups.v2.processing

import android.annotation.SuppressLint
import android.app.Application
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.mockk.verify
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
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedString
import org.signal.storageservice.protos.groups.local.DecryptedTimer
import org.thoughtcrime.securesms.database.GroupStateTestData
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context
import org.thoughtcrime.securesms.database.model.databaseprotos.member
import org.thoughtcrime.securesms.database.model.databaseprotos.pendingMember
import org.thoughtcrime.securesms.database.model.databaseprotos.requestingMember
import org.thoughtcrime.securesms.database.setNewDescription
import org.thoughtcrime.securesms.database.setNewTitle
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupNotAMemberException
import org.thoughtcrime.securesms.groups.GroupsV2Authorization
import org.thoughtcrime.securesms.groups.v2.ProfileKeySet
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor.ProfileAndMessageHelper
import org.thoughtcrime.securesms.jobmanager.JobManager
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.logging.CustomSignalProtocolLogger
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupResponse
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations
import org.whispersystems.signalservice.api.groupsv2.NotAbleToApplyGroupV2ChangeException
import org.whispersystems.signalservice.api.groupsv2.ReceivedGroupSendEndorsements
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.push.ServiceIds
import org.whispersystems.signalservice.internal.push.exceptions.NotInGroupException
import java.io.IOException
import java.util.Optional
import java.util.UUID

@Suppress("UsePropertyAccessSyntax")
@SuppressLint("CheckResult")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class GroupsV2StateProcessorTest {

  companion object {
    private val masterKey = GroupMasterKey(fromStringCondensed("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))
    private val secretParams = GroupSecretParams.deriveFromMasterKey(masterKey)
    private val groupId = GroupId.v2(masterKey)
    private val selfAci: ACI = ACI.from(UUID.randomUUID())
    private val serviceIds: ServiceIds = ServiceIds(selfAci, PNI.from(UUID.randomUUID()))
    private val otherAci: ACI = ACI.from(UUID.randomUUID())
    private val selfAndOthers: List<DecryptedMember> = listOf(member(selfAci), member(otherAci))
    private val others: List<DecryptedMember> = listOf(member(otherAci))
  }

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private lateinit var groupTable: GroupTable
  private lateinit var recipientTable: RecipientTable
  private lateinit var threadTable: ThreadTable
  private lateinit var groupsV2API: GroupsV2Api
  private lateinit var groupsV2Authorization: GroupsV2Authorization
  private lateinit var groupsV2Operations: GroupsV2Operations
  private lateinit var profileAndMessageHelper: ProfileAndMessageHelper
  private lateinit var jobManager: JobManager

  private lateinit var processor: GroupsV2StateProcessor

  @Before
  fun setUp() {
    mockkObject(SignalStore)
    every { SignalStore.internal.gv2IgnoreP2PChanges } returns false

    Log.initialize(SystemOutLogger())
    SignalProtocolLoggerProvider.setProvider(CustomSignalProtocolLogger())

    groupTable = mockk()
    recipientTable = mockk()
    threadTable = mockk()
    groupsV2API = mockk()
    groupsV2Operations = mockk()
    groupsV2Authorization = mockk()
    profileAndMessageHelper = spyk(ProfileAndMessageHelper(serviceIds.aci, masterKey, groupId))
    jobManager = mockk()

    mockkStatic(AppDependencies::class)
    every { AppDependencies.jobManager } returns jobManager
    every { AppDependencies.signalServiceAccountManager.getGroupsV2Api() } returns groupsV2API
    every { AppDependencies.groupsV2Authorization } returns groupsV2Authorization
    every { AppDependencies.groupsV2Operations } returns groupsV2Operations

    mockkObject(SignalDatabase)
    every { SignalDatabase.groups } returns groupTable
    every { SignalDatabase.recipients } returns recipientTable
    every { SignalDatabase.threads } returns threadTable

    mockkObject(ProfileAndMessageHelper)
    every { ProfileAndMessageHelper.create(any(), any(), any()) } returns profileAndMessageHelper

    every { groupsV2Operations.forGroup(secretParams) } answers { callOriginal() }

    processor = GroupsV2StateProcessor.forGroup(serviceIds, masterKey, secretParams)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  private fun given(init: GroupStateTestData.() -> Unit) {
    val data = givenData(init)

    every { groupTable.getGroup(any<GroupId.V2>()) } returns data.groupRecord
    every { groupTable.isUnknownGroup(any<GroupId>()) } returns !data.groupRecord.isPresent
    every { groupTable.isUnknownGroup(any<Optional<GroupRecord>>()) } returns !data.groupRecord.isPresent
    every { groupTable.isActive(groupId) } returns data.groupRecord.map { it.isActive }.orElse(false)

    every { groupsV2Authorization.getAuthorizationForToday(serviceIds, secretParams) } returns null

    if (data.expectTableUpdate) {
      justRun { groupTable.update(any<GroupMasterKey>(), any<DecryptedGroup>(), any<ReceivedGroupSendEndorsements>()) }
    }

    if (data.expectTableCreate) {
      every { groupTable.create(any<GroupMasterKey>(), any<DecryptedGroup>(), any<ReceivedGroupSendEndorsements>()) } returns groupId
    }

    if (data.expectTableUpdate || data.expectTableCreate) {
      justRun { profileAndMessageHelper.storeMessage(any(), any(), any()) }
      justRun { profileAndMessageHelper.persistLearnedProfileKeys(any<ProfileKeySet>()) }
    }

    data.serverState?.let { serverState ->
      every { groupsV2API.getGroup(any(), any()) } returns DecryptedGroupResponse(serverState, null)
    }

    data.changeSet?.let { changeSet ->
      every { groupsV2API.getGroupHistoryPage(any(), data.requestedRevision, any(), data.includeFirst, 0) } returns changeSet.toApiResponse()
    }

    every { groupsV2API.getGroupAsResult(any(), any()) } answers { callOriginal() }

    data.joinedAtRevision?.let { joinedAt ->
      every { groupsV2API.getGroupJoinedAt(any()) } returns NetworkResult.Success(joinedAt)
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
      changeSet {
      }
      apiCallParameters(requestedRevision = 5, includeFirst = false)
      joinedAtRevision = 0
    }

    val result = processor.updateLocalGroupToRevision(
      targetRevision = GroupsV2StateProcessor.LATEST,
      timestamp = 0
    )

    assertThat(result.updateStatus, "local and server match revisions")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_CONSISTENT_OR_AHEAD)
  }

  @Test
  fun `when local revision matches requested revision, then return consistent or ahead`() {
    given {
      localState(
        revision = 5,
        members = selfAndOthers
      )
    }

    val result = processor.updateLocalGroupToRevision(
      targetRevision = 5,
      timestamp = 0
    )

    assertThat(result.updateStatus, "local and server match revisions")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_CONSISTENT_OR_AHEAD)
  }

  @Test
  fun `when local revision is one less than latest server version, then update from server with group change only`() {
    given {
      localState(
        revision = 5,
        title = "Fdsa",
        members = selfAndOthers
      )
      changeSet {
        changeLog(6) {
          change {
            setNewTitle("Asdf")
          }
        }
      }
      apiCallParameters(requestedRevision = 5, includeFirst = false)
      joinedAtRevision = 0
      expectTableUpdate = true
    }

    val result = processor.updateLocalGroupToRevision(
      targetRevision = GroupsV2StateProcessor.LATEST,
      timestamp = 0
    )

    assertThat(result.updateStatus, "local should update to server")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
    assertThat(result.latestServer)
      .isNotNull()
      .transform {
        assertThat(it.title, "title changed to match server").isEqualTo("Asdf")
      }

    verify { groupTable.update(masterKey, result.latestServer!!, null) }
  }

  @Test
  fun `when local revision is two less than server revision, then update from server with full group state and change`() {
    given {
      localState(
        revision = 5,
        title = "Fdsa",
        members = selfAndOthers
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
      apiCallParameters(requestedRevision = 5, includeFirst = false)
      joinedAtRevision = 0
      expectTableUpdate = true
    }

    val result = processor.updateLocalGroupToRevision(
      targetRevision = GroupsV2StateProcessor.LATEST,
      timestamp = 0
    )

    assertThat(result.updateStatus, "local should update to server")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
    assertThat(result.latestServer)
      .isNotNull()
      .transform {
        assertThat(it.revision, "revision matches server").isEqualTo(7)
        assertThat(it.title, "title changed on server to final result").isEqualTo("Asdf!")
      }

    verify { groupTable.update(masterKey, result.latestServer!!, null) }
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
      apiCallParameters(requestedRevision = 100, includeFirst = false)
      joinedAtRevision = 0
      expectTableUpdate = true
    }

    val result = processor.updateLocalGroupToRevision(
      targetRevision = GroupsV2StateProcessor.LATEST,
      timestamp = 0
    )

    assertThat(result.updateStatus, "local should update to server")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
    assertThat(result.latestServer)
      .isNotNull()
      .transform {
        assertThat(it.revision, "revision matches server").isEqualTo(111)
        assertThat(it.title, "title changed on server to final result").isEqualTo("And beyond")
        assertThat(it.description, "Description updated in change after full snapshot").isEqualTo("Description")
      }

    verify { groupTable.update(masterKey, result.latestServer!!, null) }
  }

  @Test
  fun `when receiving peer change for next revision, then apply change without server call`() {
    given {
      localState(
        revision = 5,
        disappearingMessageTimer = DecryptedTimer(1000)
      )
      expectTableUpdate = true
    }

    val signedChange = DecryptedGroupChange(
      revision = 6,
      newTimer = DecryptedTimer(duration = 5000)
    )

    val result = processor.updateLocalGroupToRevision(
      targetRevision = 6,
      timestamp = 0,
      signedGroupChange = signedChange,
      serverGuid = UUID.randomUUID().toString()
    )

    assertThat(result.latestServer)
      .isNotNull()
      .transform {
        assertThat(it.revision, "revision matches peer change").isEqualTo(6)
        assertThat(it.disappearingMessagesTimer)
          .isNotNull()
          .transform { timer ->
            assertThat(timer.duration, "timer changed by peer change").isEqualTo(5000)
          }
      }

    verify { groupTable.update(masterKey, result.latestServer!!, null) }
  }

  @Test
  fun applyP2PPromotePendingPni() {
    given {
      localState(
        revision = 5,
        members = others,
        pendingMembers = listOf(pendingMember(serviceIds.pni))
      )
      expectTableUpdate = true
    }

    val signedChange = DecryptedGroupChange(
      revision = 6,
      promotePendingPniAciMembers = listOf(member(selfAci).copy(pniBytes = serviceIds.pni.toByteString()))
    )

    justRun { jobManager.add(any()) }

    val result = processor.updateLocalGroupToRevision(
      targetRevision = 6,
      timestamp = 0,
      signedGroupChange = signedChange,
      serverGuid = UUID.randomUUID().toString()
    )

    assertThat(result.latestServer)
      .isNotNull()
      .transform {
        assertThat(it.revision, "revision matches peer change").isEqualTo(6)
        val memberBytes = it.members.map { member -> member.aciBytes }
        assertThat(memberBytes, "member promoted by peer change").contains(selfAci.toByteString())
      }

    verify { jobManager.add(ofType(DirectoryRefreshJob::class)) }
    verify { groupTable.update(masterKey, result.latestServer!!, null) }
  }

  @Test
  fun updateFromServerIfUnableToApplyP2PChange() {
    given {
      localState(
        revision = 1,
        members = selfAndOthers
      )
      serverState(
        revision = 2,
        title = "Breaking Signal for Science",
        members = selfAndOthers
      )
      changeSet {
        changeLog(2) {
          fullSnapshot(serverState)
        }
      }
      apiCallParameters(1, false)
      joinedAtRevision = 0
      expectTableUpdate = true
    }

    mockkStatic(DecryptedGroupUtil::class)
    every { DecryptedGroupUtil.apply(any(), any()) } throws NotAbleToApplyGroupV2ChangeException()

    val signedChange = DecryptedGroupChange(
      revision = 2,
      newTitle = DecryptedString("Breaking Signal for Science")
    )

    val result = processor.updateLocalGroupToRevision(
      targetRevision = 2,
      timestamp = 0,
      signedGroupChange = signedChange,
      serverGuid = UUID.randomUUID().toString()
    )

    assertThat(result.updateStatus, "local should update to server")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
    assertThat(result.latestServer)
      .isNotNull()
      .transform {
        assertThat(it.revision, "revision matches server").isEqualTo(2)
      }

    verify { groupsV2API.getGroupHistoryPage(secretParams, 1, any(), false, 0) }

    unmockkStatic(DecryptedGroupUtil::class)
  }

  @Test(expected = GroupNotAMemberException::class)
  fun skipP2PChangeForGroupNotIn() {
    given {
      localState(
        revision = 1,
        members = others,
        active = false
      )
    }

    every { groupsV2API.getGroupJoinedAt(any()) } returns NetworkResult.StatusCodeError(NotInGroupException())
    every { groupsV2API.getGroupAsResult(any(), any()) } returns NetworkResult.StatusCodeError(NotInGroupException())
    mockkStatic(Recipient::class)
    every { Recipient.externalGroupExact(groupId) } returns Recipient()
    every { threadTable.getThreadIdFor(any()) } returns null

    val signedChange = DecryptedGroupChange(
      revision = 2,
      newTitle = DecryptedString("Breaking Signal for Science"),
      newDescription = DecryptedString("We break stuff, because we must.")
    )

    processor.updateLocalGroupToRevision(
      targetRevision = 2,
      timestamp = 0,
      signedGroupChange = signedChange,
      serverGuid = UUID.randomUUID().toString()
    )
  }

  @Test
  fun applyP2PChangeForGroupWeThinkAreIn() {
    given {
      localState(
        revision = 1,
        members = others,
        active = false
      )
      expectTableUpdate = true
    }

    every { groupsV2API.getGroupJoinedAt(any()) } returns NetworkResult.StatusCodeError(NotInGroupException())
    every { groupsV2API.getGroupAsResult(any(), any()) } returns NetworkResult.StatusCodeError(NotInGroupException())

    val signedChange = DecryptedGroupChange(
      revision = 3,
      newMembers = listOf(member(selfAci))
    )

    val result = processor.updateLocalGroupToRevision(
      targetRevision = GroupsV2StateProcessor.LATEST,
      timestamp = 0,
      signedGroupChange = signedChange,
      serverGuid = UUID.randomUUID().toString()
    )

    assertThat(result.updateStatus, "local should update to server")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
    assertThat(result.latestServer)
      .isNotNull()
      .transform {
        assertThat(it.revision, "revision matches server").isEqualTo(3)
      }

    verify { groupTable.update(masterKey, result.latestServer!!, null) }
  }

  @Test
  fun `when freshly added to a group, with no group changes after being added, then update from server at the revision we were added`() {
    given {
      changeSet {
        changeLog(2) {
          fullSnapshot(
            title = "Breaking Signal for Science",
            description = "We break stuff, because we must.",
            members = listOf(member(otherAci), member(selfAci, joinedAt = 2))
          )
        }
      }
      apiCallParameters(2, true)
      joinedAtRevision = 2
      expectTableCreate = true
    }

    val result = processor.updateLocalGroupToRevision(
      targetRevision = 2,
      timestamp = 0
    )

    assertThat(result.updateStatus, "local should update to server")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
    assertThat(result.latestServer)
      .isNotNull()
      .transform {
        assertThat(it.revision, "revision matches server").isEqualTo(2)
      }

    verify { groupTable.create(masterKey, result.latestServer!!, null) }
  }

  @Test
  fun `when freshly added to a group, with additional group changes after being added, then only update from server at the revision we were added, and then schedule pulling additional changes later`() {
    given {
      changeSet {
        changeLog(2) {
          fullSnapshot(
            title = "Baking Signal for Science",
            description = "We break stuff, because we must.",
            members = listOf(member(otherAci), member(selfAci, joinedAt = 2))
          )
        }
        changeLog(3) {
          change {
            setNewTitle("Breaking Signal for Science")
          }
        }
      }
      apiCallParameters(2, true)
      joinedAtRevision = 2
      expectTableCreate = true
    }

    every { groupTable.isUnknownGroup(any<GroupId>()) } returns true
    justRun { jobManager.add(any()) }

    val result = processor.updateLocalGroupToRevision(
      targetRevision = 2,
      timestamp = 0
    )

    assertThat(result.updateStatus, "local should update to revision added")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
    assertThat(result.latestServer)
      .isNotNull()
      .given {
        assertThat(it.revision, "revision matches peer revision added").isEqualTo(2)
        assertThat(it.title, "title matches that as it was in revision added").isEqualTo("Baking Signal for Science")
      }

    verify { jobManager.add(ofType(RequestGroupV2InfoJob::class)) }
    verify { groupTable.create(masterKey, result.latestServer!!, null) }
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
      expectTableUpdate = true
    }

    val result = processor.updateLocalGroupToRevision(
      targetRevision = GroupsV2StateProcessor.LATEST,
      timestamp = 0
    )

    assertThat(result.updateStatus, "local should update to server")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
    assertThat(result.latestServer)
      .isNotNull()
      .transform {
        assertThat(it.revision, "revision matches latest server").isEqualTo(10)
      }

    verify { groupTable.update(masterKey, result.latestServer!!, null) }
  }

  @Test
  fun `when request to join group is approved, with no group changes after approved, then update from server to revision we were added`() {
    given {
      localState(
        revision = GroupsV2StateProcessor.PLACEHOLDER_REVISION,
        title = "Beam me up",
        requestingMembers = listOf(requestingMember(selfAci))
      )

      changeSet {
        changeLog(3) {
          fullSnapshot(
            title = "Beam me up",
            members = listOf(member(otherAci), member(selfAci, joinedAt = 3))
          )
          change {
            newMembers += member(selfAci, joinedAt = 3)
          }
        }
      }
      apiCallParameters(requestedRevision = 3, includeFirst = true)
      joinedAtRevision = 3
      expectTableUpdate = true
    }

    val result = processor.updateLocalGroupToRevision(
      targetRevision = 3,
      timestamp = 0
    )

    assertThat(result.updateStatus, "local should update to server")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
    assertThat(result.latestServer)
      .isNotNull()
      .transform {
        assertThat(it.revision, "revision matches server").isEqualTo(3)
      }

    verify { groupTable.update(masterKey, result.latestServer!!, null) }
  }

  @Test
  fun `when request to join group is approved, with group changes occurring after approved, then update from server to revision we were added, and then schedule pulling additional changes later`() {
    given {
      localState(
        revision = GroupsV2StateProcessor.PLACEHOLDER_REVISION,
        title = "Beam me up",
        requestingMembers = listOf(requestingMember(selfAci))
      )
      changeSet {
        changeLog(3) {
          fullSnapshot(
            title = "Beam me up",
            members = listOf(member(otherAci), member(selfAci, joinedAt = 3))
          )
          change {
            newMembers += member(selfAci, joinedAt = 3)
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
      joinedAtRevision = 3
      expectTableUpdate = true
    }

    justRun { jobManager.add(any()) }

    val result = processor.updateLocalGroupToRevision(
      targetRevision = 3,
      timestamp = 0
    )

    assertThat(result.updateStatus, "local should update to server")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
    assertThat(result.latestServer)
      .isNotNull()
      .transform {
        assertThat(it.revision, "revision matches revision approved at").isEqualTo(3)
        assertThat(it.title, "title matches revision approved at").isEqualTo("Beam me up")
      }

    verify { jobManager.add(ofType(RequestGroupV2InfoJob::class)) }
    verify { groupTable.update(masterKey, result.latestServer!!, null) }
  }

  @Test
  fun `when local state for same revision does not match server, then successfully update from server to latest revision`() {
    val randomMembers = listOf(member(UUID.randomUUID()), member(UUID.randomUUID()))

    given {
      localState(
        revision = 100,
        title = "Title",
        members = others
      )
      changeSet {
        changeLog(100) {
          fullSnapshot(
            extendGroup = localState,
            members = selfAndOthers + randomMembers[0] + randomMembers[1]
          )
          change {
            newMembers += member(selfAci, joinedAt = 100)
          }
        }
        changeLog(101) {
          change {
            deleteMembers += randomMembers[1].aciBytes
            modifiedProfileKeys += randomMembers[0]
          }
        }
      }
      apiCallParameters(100, true)
      joinedAtRevision = 100
      expectTableUpdate = true
    }

    val result = processor.updateLocalGroupToRevision(
      targetRevision = GroupsV2StateProcessor.LATEST,
      timestamp = 0
    )

    assertThat(result.updateStatus, "local should update to server")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
    assertThat(result.latestServer)
      .isNotNull()
      .transform {
        assertThat(it.revision, "revision matches latest revision on server").isEqualTo(101)
      }

    verify { groupTable.update(masterKey, result.latestServer!!, null) }
  }

  /**
   * If for some reason we missed a member being added in our local state, and then we preform a multi-revision update,
   * we should now know about the member and add update messages to the chat.
   */
  @Test
  fun missedMemberAddResolvesWithMultipleRevisionUpdate() {
    val secondOther = member(ACI.from(UUID.randomUUID()))

    given {
      localState(
        revision = 8,
        title = "Whatever",
        members = selfAndOthers
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
      apiCallParameters(requestedRevision = 8, includeFirst = false)
      joinedAtRevision = 0
      expectTableUpdate = true
    }

    val updateMessageContextArgs = mutableListOf<DecryptedGroupV2Context>()
    every { profileAndMessageHelper.storeMessage(capture(updateMessageContextArgs), any(), any()) } returns Unit

    val result = processor.updateLocalGroupToRevision(
      targetRevision = GroupsV2StateProcessor.LATEST,
      timestamp = 0
    )

    assertThat(result.updateStatus, "local should update to server")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
    assertThat(result.latestServer)
      .isNotNull()
      .transform {
        assertThat(it.members, "members contains second other").contains(secondOther)
      }

    val newMembers = updateMessageContextArgs.mapNotNull { it.change?.newMembers }
    assertThat(newMembers, "group update messages contains new member add")
      .any { it.contains(secondOther) }

    verify { groupTable.update(masterKey, result.latestServer!!, null) }
  }

  /**
   * If for some reason we missed a member being added in our local state, and then we preform a forced sanity update,
   * we should now know about the member and any other changes, and add update messages to the chat.
   */
  @Test
  fun missedMemberAddResolvesWithForcedUpdate() {
    val secondOther = member(ACI.from(UUID.randomUUID()))

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
      expectTableUpdate = true
    }

    val updateMessageContextArgs = mutableListOf<DecryptedGroupV2Context>()
    every { profileAndMessageHelper.insertUpdateMessages(any(), any(), any(), any()) } answers { callOriginal() }
    every { profileAndMessageHelper.storeMessage(capture(updateMessageContextArgs), any(), any()) } returns Unit

    val result = processor.forceSanityUpdateFromServer(0)
    assertThat(result.updateStatus, "local should update to server")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
    assertThat(result.latestServer)
      .isNotNull()
      .transform {
        assertThat(it.members, "members contains second other").contains(secondOther)
        assertThat(it.title, "title should be updated").isEqualTo("Changed")
      }

    val newMembers = updateMessageContextArgs.mapNotNull { it.change?.newMembers }
    assertThat(newMembers, "group update messages contains new member add").any { it.contains(secondOther) }

    val newTitles = updateMessageContextArgs.mapNotNull { it.change?.newTitle?.value_ }
    assertThat(newTitles, "group update messages contains title change").contains("Changed")

    verify { groupTable.update(masterKey, result.latestServer!!, null) }
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
    assertThat(result.updateStatus, "local should be unchanged")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_CONSISTENT_OR_AHEAD)
  }

  /** No local group state fails gracefully during force update */
  @Test
  fun missingLocalGroupStateForForcedUpdate() {
    given { }

    val result = processor.forceSanityUpdateFromServer(0)
    assertThat(result.updateStatus, "local should be unchanged")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_CONSISTENT_OR_AHEAD)
  }

  @Test(expected = GroupNotAMemberException::class)
  fun serverNotInGroupFailsForForcedUpdate() {
    given {
      localState(
        revision = 5,
        members = selfAndOthers
      )
    }

    every { groupsV2API.getGroup(any(), any()) } throws NotInGroupException()

    processor.forceSanityUpdateFromServer(0)
  }

  @Test(expected = IOException::class)
  fun serverVerificationFailedFailsForForcedUpdate() {
    given {
      localState(
        revision = 5,
        members = selfAndOthers
      )
    }

    every { groupsV2API.getGroup(any(), any()) } throws VerificationFailedException()

    processor.forceSanityUpdateFromServer(0)
  }

  @Test
  fun restoreFromPlaceholderForcedUpdate() {
    given {
      localState(
        revision = GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION
      )
      serverState(
        revision = 10,
        members = selfAndOthers,
        title = "Asdf!"
      )
      expectTableUpdate = true
    }

    val result = processor.forceSanityUpdateFromServer(0)

    assertThat(result.updateStatus, "local should update to server")
      .isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
    assertThat(result.latestServer)
      .isNotNull()
      .transform {
        assertThat(it.revision, "revision matches server").isEqualTo(10)
        assertThat(it.title, "title changed on server to final result").isEqualTo("Asdf!")
      }

    verify { groupTable.update(masterKey, result.latestServer!!, null) }
  }

  /**
   * If we think a group is not active locally, but the server returns state for us indicating it thinks we are active,
   * apply the state regardless of revision.
   */
  @Test
  fun localInactiveButActiveOnServer() {
    given {
      localState(
        active = false,
        revision = 5,
        members = selfAndOthers
      )
      changeSet {
      }
      apiCallParameters(requestedRevision = 5, includeFirst = false)
      joinedAtRevision = 0
      expectTableUpdate = true
    }

    val result = processor.updateLocalGroupToRevision(
      targetRevision = GroupsV2StateProcessor.LATEST,
      timestamp = 0
    )

    assertThat(result.updateStatus, "inactive local is still updated with same revision").isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
  }

  /**
   * If we think a group is not active locally, but the server returns state for us indicating it thinks we are active,
   * apply the state regardless of revision.
   */
  @Test
  fun forceUpdateLocalInactiveButActiveOnServer() {
    given {
      localState(
        active = false,
        revision = 5,
        members = selfAndOthers
      )
      serverState(
        revision = 5,
        members = selfAndOthers
      )
      apiCallParameters(requestedRevision = 5, includeFirst = false)
      joinedAtRevision = 0
      expectTableUpdate = true
    }

    val result = processor.forceSanityUpdateFromServer(
      timestamp = 0
    )

    assertThat(result.updateStatus, "inactive local is still updated given same revision from server").isEqualTo(GroupUpdateResult.UpdateStatus.GROUP_UPDATED)
  }
}
