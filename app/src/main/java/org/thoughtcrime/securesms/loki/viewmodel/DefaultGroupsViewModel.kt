package org.thoughtcrime.securesms.loki.viewmodel

import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.session.libsession.messaging.opengroups.OpenGroupAPIV2
import org.session.libsignal.utilities.logging.Log

class DefaultGroupsViewModel : ViewModel() {

    init {
        OpenGroupAPIV2.getDefaultRoomsIfNeeded()
    }

    val defaultRooms = OpenGroupAPIV2.defaultRooms.asLiveData().distinctUntilChanged().switchMap { groups ->
        liveData {
            // load images etc
            emit(State.Loading)
            val images = groups.filterNot { it.imageID.isNullOrEmpty() }.map { group ->
                val image = viewModelScope.async(Dispatchers.IO) {
                    try {
                        OpenGroupAPIV2.downloadOpenGroupProfilePicture(group.imageID!!)
                    } catch (e: Exception) {
                        Log.e("Loki", "Error getting group profile picture", e)
                        null
                    }
                }
                group.id to image
            }.toMap()
            val defaultGroups = groups.map { group ->
                DefaultGroup(group.id, group.name, images[group.id]?.await())
            }
            emit(State.Success(defaultGroups))
        }
    }

}

data class DefaultGroup(val id: String, val name: String, val image: ByteArray?)