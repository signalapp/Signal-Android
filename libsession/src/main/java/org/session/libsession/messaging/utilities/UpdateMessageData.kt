package org.session.libsession.messaging.utilities

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonParseException
import org.session.libsignal.service.api.messages.SignalServiceGroup
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.logging.Log
import java.util.*

// class used to save update messages details
class UpdateMessageData () {

    var kind: Kind? = null

    //the annotations below are required for serialization. Any new Kind class MUST be declared as JsonSubTypes as well
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes(
            JsonSubTypes.Type(Kind.GroupUpdate::class, name = "GroupUpdate")
    )
    sealed class Kind {
        class GroupUpdate( var type: SignalServiceGroup.Type, var groupName: String?, var updatedMembers: Collection<String>): Kind() {
            constructor(): this(SignalServiceGroup.Type.UNKNOWN, null, Collections.emptyList()) //default constructor required for json serialization
        }
    }

    constructor(kind: Kind): this() {
        this.kind = kind
    }

    companion object {
        val TAG = UpdateMessageData::class.simpleName

        fun buildGroupUpdate(type: SignalServiceGroup.Type, name: String, members: Collection<String>): UpdateMessageData {
            return when(type) {
                SignalServiceGroup.Type.NAME_CHANGE -> UpdateMessageData(Kind.GroupUpdate(type, name, Collections.emptyList()))
                SignalServiceGroup.Type.MEMBER_ADDED -> UpdateMessageData(Kind.GroupUpdate(type,null, members))
                SignalServiceGroup.Type.MEMBER_REMOVED -> UpdateMessageData(Kind.GroupUpdate(type,null, members))
                else -> UpdateMessageData(Kind.GroupUpdate(type,null, Collections.emptyList()))
            }
        }

        fun fromJSON(json: String): UpdateMessageData {
             return try {
                JsonUtil.fromJson(json, UpdateMessageData::class.java)
            } catch (e: JsonParseException) {
                Log.e(TAG, "${e.message}")
                UpdateMessageData(Kind.GroupUpdate(SignalServiceGroup.Type.UNKNOWN, null, Collections.emptyList()))
            }
        }
    }

    fun toJSON(): String {
        return JsonUtil.toJson(this)
    }
}
