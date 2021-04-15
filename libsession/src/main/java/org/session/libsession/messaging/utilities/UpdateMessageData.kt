package org.session.libsession.messaging.utilities

import com.fasterxml.jackson.core.JsonParseException
import org.session.libsignal.service.api.messages.SignalServiceGroup
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.logging.Log
import java.util.*

// class used to save update messages details
class UpdateMessageData () {

    var type: SignalServiceGroup.Type = SignalServiceGroup.Type.UNKNOWN
    var groupName: String? = null
    var updatedMembers: Collection<String> = Collections.emptyList()

    constructor(type: SignalServiceGroup.Type, groupName: String?, updatedMembers: Collection<String>): this() {
        this.type = type
        this.groupName = groupName
        this.updatedMembers = updatedMembers
    }

    companion object {
        val TAG = UpdateMessageData::class.simpleName

        fun buildGroupUpdate(type: SignalServiceGroup.Type, name: String, members: Collection<String>): UpdateMessageData {
            return when(type) {
                SignalServiceGroup.Type.NAME_CHANGE -> UpdateMessageData(type, name, Collections.emptyList())
                SignalServiceGroup.Type.MEMBER_ADDED -> UpdateMessageData(type,null, members)
                SignalServiceGroup.Type.MEMBER_REMOVED -> UpdateMessageData(type,null, members)
                else -> UpdateMessageData(type,null, Collections.emptyList())
            }
        }

        fun fromJSON(json: String): UpdateMessageData {
            return try {
                JsonUtil.fromJson(json, UpdateMessageData::class.java)
            } catch (e: JsonParseException) {
                Log.e(TAG, "${e.message}")
                UpdateMessageData(SignalServiceGroup.Type.UNKNOWN, null, Collections.emptyList())
            }
        }
    }

    fun toJSON(): String {
        return JsonUtil.toJson(this)
    }

}