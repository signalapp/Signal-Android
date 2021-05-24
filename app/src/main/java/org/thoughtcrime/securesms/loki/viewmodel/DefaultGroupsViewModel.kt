package org.thoughtcrime.securesms.loki.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2

typealias DefaultGroups = List<OpenGroupAPIV2.DefaultGroup>
typealias GroupState = State<DefaultGroups>

class DefaultGroupsViewModel : ViewModel() {

    init {
        OpenGroupAPIV2.getDefaultRoomsIfNeeded()
    }

    val defaultRooms = OpenGroupAPIV2.defaultRooms.map<DefaultGroups, GroupState> {
        State.Success(it)
    }.onStart {
        emit(State.Loading)
    }.asLiveData()
}