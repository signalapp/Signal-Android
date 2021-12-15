package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.utilities.GroupUtil

class GroupAvatarDownloadJob(val room: String, val server: String) : Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 10

    override fun execute() {
        val storage = MessagingModuleConfiguration.shared.storage
        try {
            val info = OpenGroupAPIV2.getInfo(room, server).get()
            val bytes = OpenGroupAPIV2.downloadOpenGroupProfilePicture(info.id, server).get()
            val groupId = GroupUtil.getEncodedOpenGroupID("$server.$room".toByteArray())
            storage.updateProfilePicture(groupId, bytes)
            storage.updateTimestampUpdated(groupId, System.currentTimeMillis())
            delegate?.handleJobSucceeded(this)
        } catch (e: Exception) {
            delegate?.handleJobFailed(this, e)
        }
    }

    override fun serialize(): Data {
        return Data.Builder()
            .putString(ROOM, room)
            .putString(SERVER, server)
            .build()
    }

    override fun getFactoryKey(): String = KEY

    companion object {
        const val KEY = "GroupAvatarDownloadJob"

        private const val ROOM = "room"
        private const val SERVER = "server"
    }

    class Factory : Job.Factory<GroupAvatarDownloadJob> {

        override fun create(data: Data): GroupAvatarDownloadJob {
            return GroupAvatarDownloadJob(
                data.getString(ROOM),
                data.getString(SERVER)
            )
        }
    }
}