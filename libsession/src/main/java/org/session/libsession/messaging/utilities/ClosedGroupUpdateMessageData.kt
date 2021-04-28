package org.session.libsession.messaging.utilities

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonParseException
import org.session.libsignal.service.api.messages.SignalServiceGroup
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.logging.Log
import java.util.*

// class used to save update messages details
class ClosedGroupUpdateMessageData () {

    var kind: Kind? = null

    //the annotations below are required for serialization. Any new Kind class MUST be declared as JsonSubTypes as well
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes(
            JsonSubTypes.Type(Kind.GroupCreation::class, name = "GroupCreation"),
            JsonSubTypes.Type(Kind.GroupNameChange::class, name = "GroupNameChange"),
            JsonSubTypes.Type(Kind.GroupMemberAdded::class, name = "GroupMemberAdded"),
            JsonSubTypes.Type(Kind.GroupMemberRemoved::class, name = "GroupMemberRemoved"),
            JsonSubTypes.Type(Kind.GroupMemberLeft::class, name = "GroupMemberLeft")
    )
    sealed class Kind() {
        class GroupCreation(): Kind()
        class GroupNameChange(val name: String): Kind() {
            constructor(): this("") //default constructor required for json serialization
        }
        class GroupMemberAdded(val updatedMembers: Collection<String>): Kind() {
            constructor(): this(Collections.emptyList())
        }
        class GroupMemberRemoved(val updatedMembers: Collection<String>): Kind() {
            constructor(): this(Collections.emptyList())
        }
        class GroupMemberLeft(): Kind()
    }

    constructor(kind: Kind): this() {
        this.kind = kind
    }

    companion object {
        val TAG = ClosedGroupUpdateMessageData::class.simpleName

        fun buildGroupUpdate(type: SignalServiceGroup.Type, name: String, members: Collection<String>): ClosedGroupUpdateMessageData? {
            return when(type) {
                SignalServiceGroup.Type.CREATION -> ClosedGroupUpdateMessageData(Kind.GroupCreation())
                SignalServiceGroup.Type.NAME_CHANGE -> ClosedGroupUpdateMessageData(Kind.GroupNameChange(name))
                SignalServiceGroup.Type.MEMBER_ADDED -> ClosedGroupUpdateMessageData(Kind.GroupMemberAdded(members))
                SignalServiceGroup.Type.MEMBER_REMOVED -> ClosedGroupUpdateMessageData(Kind.GroupMemberRemoved(members))
                SignalServiceGroup.Type.QUIT -> ClosedGroupUpdateMessageData(Kind.GroupMemberLeft())
                else -> null
            }
        }

        fun fromJSON(json: String): ClosedGroupUpdateMessageData? {
             return try {
                JsonUtil.fromJson(json, ClosedGroupUpdateMessageData::class.java)
            } catch (e: JsonParseException) {
                Log.e(TAG, "${e.message}")
                null
            }
        }
    }

    fun toJSON(): String {
        return JsonUtil.toJson(this)
    }
}
