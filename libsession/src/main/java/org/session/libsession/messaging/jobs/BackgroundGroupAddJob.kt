package org.session.libsession.messaging.jobs

import okhttp3.HttpUrl
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.utilities.Log

class BackgroundGroupAddJob(val joinUrl: String): Job {

    companion object {
        const val KEY = "BackgroundGroupAddJob"

        private const val JOIN_URL = "joinUri"
    }

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 1

    val openGroupId: String? get() {
        val url = HttpUrl.parse(joinUrl) ?: return null
        val server = OpenGroup.getServer(joinUrl)?.toString()?.removeSuffix("/") ?: return null
        val room = url.pathSegments().firstOrNull() ?: return null
        return "$server.$room"
    }

    override fun execute() {
        try {
            val storage = MessagingModuleConfiguration.shared.storage
            val allOpenGroups = storage.getAllOpenGroups().map { it.value.joinURL }
            if (allOpenGroups.contains(joinUrl)) {
                Log.e("OpenGroupDispatcher", "Failed to add group because", DuplicateGroupException())
                delegate?.handleJobFailed(this, DuplicateGroupException())
                return
            }
            // get image
            val url = HttpUrl.parse(joinUrl) ?: throw Exception("Group joinUrl isn't valid")
            val server = OpenGroup.getServer(joinUrl)
            val serverString = server.toString().removeSuffix("/")
            val publicKey = url.queryParameter("public_key") ?: throw Exception("Group public key isn't valid")
            val room = url.pathSegments().firstOrNull() ?: throw Exception("Group room isn't valid")
            storage.setOpenGroupPublicKey(serverString, publicKey)
            // get info and auth token
            storage.addOpenGroup(joinUrl)
            val info = OpenGroupApi.getRoomInfo(room, serverString).get()
            val imageId = info.imageId
            if (imageId != null) {
                val bytes = OpenGroupApi.downloadOpenGroupProfilePicture(serverString, room, imageId).get()
                val groupId = GroupUtil.getEncodedOpenGroupID("$server.$room".toByteArray())
                storage.updateProfilePicture(groupId, bytes)
                storage.updateTimestampUpdated(groupId, System.currentTimeMillis())
            }
            storage.onOpenGroupAdded(joinUrl)
        } catch (e: Exception) {
            Log.e("OpenGroupDispatcher", "Failed to add group because",e)
            delegate?.handleJobFailed(this, e)
            return
        }
        Log.d("Loki", "Group added successfully")
        delegate?.handleJobSucceeded(this)
    }

    override fun serialize(): Data = Data.Builder()
        .putString(JOIN_URL, joinUrl)
        .build()

    override fun getFactoryKey(): String = KEY

    class DuplicateGroupException: Exception("Current open groups already contains this group")

    class Factory : Job.Factory<BackgroundGroupAddJob> {
        override fun create(data: Data): BackgroundGroupAddJob {
            return BackgroundGroupAddJob(
                data.getString(JOIN_URL)
            )
        }
    }

}