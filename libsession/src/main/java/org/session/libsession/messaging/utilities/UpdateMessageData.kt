package org.session.libsession.messaging.utilities

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonParseException
import org.session.libsignal.messages.SignalServiceGroup
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import java.util.*

// class used to save update messages details
class UpdateMessageData () {

    var kind: Kind? = null

    //the annotations below are required for serialization. Any new Kind class MUST be declared as JsonSubTypes as well
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes(
            JsonSubTypes.Type(Kind.GroupCreation::class, name = "GroupCreation"),
            JsonSubTypes.Type(Kind.GroupNameChange::class, name = "GroupNameChange"),
            JsonSubTypes.Type(Kind.GroupMemberAdded::class, name = "GroupMemberAdded"),
            JsonSubTypes.Type(Kind.GroupMemberRemoved::class, name = "GroupMemberRemoved"),
            JsonSubTypes.Type(Kind.GroupMemberLeft::class, name = "GroupMemberLeft"),
            JsonSubTypes.Type(Kind.OpenGroupInvitation::class, name = "OpenGroupInvitation")
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
        class OpenGroupInvitation(val groupUrl: String, val groupName: String): Kind() {
            constructor(): this("", "")
        }
    }

    constructor(kind: Kind): this() {
        this.kind = kind
    }

    companion object {
        val TAG = UpdateMessageData::class.simpleName

        fun buildGroupUpdate(type: SignalServiceGroup.Type, name: String, members: Collection<String>): UpdateMessageData? {
            return when(type) {
                SignalServiceGroup.Type.CREATION -> UpdateMessageData(Kind.GroupCreation())
                SignalServiceGroup.Type.NAME_CHANGE -> UpdateMessageData(Kind.GroupNameChange(name))
                SignalServiceGroup.Type.MEMBER_ADDED -> UpdateMessageData(Kind.GroupMemberAdded(members))
                SignalServiceGroup.Type.MEMBER_REMOVED -> UpdateMessageData(Kind.GroupMemberRemoved(members))
                SignalServiceGroup.Type.QUIT -> UpdateMessageData(Kind.GroupMemberLeft())
                else -> null
            }
        }

        fun buildOpenGroupInvitation(url: String, name: String): UpdateMessageData {
            return UpdateMessageData(Kind.OpenGroupInvitation(url, name))
        }

        fun fromJSON(json: String): UpdateMessageData? {
             return try {
                JsonUtil.fromJson(json, UpdateMessageData::class.java)
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
