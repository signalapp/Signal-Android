package org.thoughtcrime.securesms.loki.api

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.text.TextUtils
import androidx.annotation.WorkerThread
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.opengroups.*
import org.session.libsession.messaging.sending_receiving.pollers.OpenGroupPoller
import org.session.libsession.messaging.sending_receiving.pollers.OpenGroupV2Poller
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.Util
import org.session.libsignal.service.loki.api.opengroups.PublicChat
import org.thoughtcrime.securesms.database.DatabaseContentProviders
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.util.BitmapUtil
import java.util.concurrent.Executors

class PublicChatManager(private val context: Context) {
  private var chats = mutableMapOf<Long, OpenGroup>()
  private var v2Chats = mutableMapOf<Long, OpenGroupV2>()
  private val pollers = mutableMapOf<Long, OpenGroupPoller>()
  private val v2Pollers = mutableMapOf<Long, OpenGroupV2Poller>()
  private val observers = mutableMapOf<Long, ContentObserver>()
  private var isPolling = false
  private val executorService = Executors.newScheduledThreadPool(4)

  public fun areAllCaughtUp(): Boolean {
    var areAllCaughtUp = true
    refreshChatsAndPollers()
    for ((threadID, _) in chats) {
      val poller = pollers[threadID]
      areAllCaughtUp = if (poller != null) areAllCaughtUp && poller.isCaughtUp else true
    }
    return areAllCaughtUp
  }

  public fun markAllAsNotCaughtUp() {
    refreshChatsAndPollers()
    for ((threadID, chat) in chats) {
      val poller = pollers[threadID] ?: OpenGroupPoller(chat, executorService)
      poller.isCaughtUp = false
    }
  }

  public fun startPollersIfNeeded() {
    refreshChatsAndPollers()

    for ((threadId, chat) in chats) {
      val poller = pollers[threadId] ?: OpenGroupPoller(chat, executorService)
      poller.startIfNeeded()
      listenToThreadDeletion(threadId)
      if (!pollers.containsKey(threadId)) { pollers[threadId] = poller }
    }
    for ((threadId, chat) in v2Chats) {
      val poller = v2Pollers[threadId] ?: OpenGroupV2Poller(chat, executorService)
      poller.startIfNeeded()
      listenToThreadDeletion(threadId)
      if (!v2Pollers.containsKey(threadId)) { v2Pollers[threadId] = poller }
    }
    isPolling = true
  }

  public fun stopPollers() {
    pollers.values.forEach { it.stop() }
    isPolling = false
    executorService.shutdown()
  }

  //TODO Declare a specific type of checked exception instead of "Exception".
  @WorkerThread
  @Throws(java.lang.Exception::class)
  public fun addChat(server: String, channel: Long): OpenGroup {
    // Ensure the auth token is acquired.
    OpenGroupAPI.getAuthToken(server).get()

    val channelInfo = OpenGroupAPI.getChannelInfo(channel, server).get()
    return addChat(server, channel, channelInfo)
  }

  @WorkerThread
  fun addChat(server: String, room: String): OpenGroupV2 {
    // Ensure the auth token is acquired.
    OpenGroupAPIV2.getAuthToken(room, server).get()

    val channelInfo = OpenGroupAPIV2.getInfo(room, server).get()
    return addChat(server, room, channelInfo)
  }

  @WorkerThread
  public fun addChat(server: String, channel: Long, info: OpenGroupInfo): OpenGroup {
    val chat = PublicChat(channel, server, info.displayName, true)
    var threadID = GroupManager.getOpenGroupThreadID(chat.id, context)
    var profilePicture: Bitmap? = null
    // Create the group if we don't have one
    if (threadID < 0) {
      if (info.profilePictureURL.isNotEmpty()) {
        val profilePictureAsByteArray = OpenGroupAPI.downloadOpenGroupProfilePicture(server, info.profilePictureURL)
        profilePicture = BitmapUtil.fromByteArray(profilePictureAsByteArray)
      }
      val result = GroupManager.createOpenGroup(chat.id, context, profilePicture, chat.displayName)
      threadID = result.threadId
    }
    DatabaseFactory.getLokiThreadDatabase(context).setPublicChat(chat, threadID)
    // Set our name on the server
    val displayName = TextSecurePreferences.getProfileName(context)
    if (!TextUtils.isEmpty(displayName)) {
      OpenGroupAPI.setDisplayName(displayName, server)
    }
    // Start polling
    Util.runOnMain { startPollersIfNeeded() }

    return OpenGroup.from(chat)
  }

  @WorkerThread
  fun addChat(server: String, room: String, info: OpenGroupAPIV2.Info): OpenGroupV2 {
    val chat = OpenGroupV2(server, room, info.id, info.name, info.imageID)
    val threadID = GroupManager.getOpenGroupThreadID(chat.id, context)
    var profilePicture: Bitmap? = null
    if (threadID < 0) {
      val imageID = info.imageID
      if (!imageID.isNullOrEmpty()) {
        val profilePictureAsByteArray = OpenGroupAPIV2.downloadOpenGroupProfilePicture(imageID)
        profilePicture = BitmapUtil.fromByteArray(profilePictureAsByteArray)
      }
      val result = GroupManager.createOpenGroup(chat.id, context, profilePicture, info.name)
    }
    return chat
  }

  public fun removeChat(server: String, channel: Long) {
    val threadDB = DatabaseFactory.getThreadDatabase(context)
    val groupId = PublicChat.getId(channel, server)
    val threadId = GroupManager.getOpenGroupThreadID(groupId, context)
    val groupAddress = threadDB.getRecipientForThreadId(threadId)!!.address.serialize()
    GroupManager.deleteGroup(groupAddress, context)

    Util.runOnMain { startPollersIfNeeded() }
  }

  private fun refreshChatsAndPollers() {
    val storage = MessagingConfiguration.shared.storage
    val chatsInDB = storage.getAllOpenGroups()
    val v2ChatsInDB = storage.getAllV2OpenGroups()
    val removedChatThreadIds = chats.keys.filter { !chatsInDB.keys.contains(it) }
    removedChatThreadIds.forEach { pollers.remove(it)?.stop() }

    // Only append to chats if we have a thread for the chat
    chats = chatsInDB.filter { GroupManager.getOpenGroupThreadID(it.value.id, context) > -1 }.toMutableMap()
    v2Chats = v2ChatsInDB.filter { GroupManager.getOpenGroupThreadID(it.value.id, context) > -1 }.toMutableMap()
  }

  private fun listenToThreadDeletion(threadID: Long) {
    if (threadID < 0 || observers[threadID] != null) { return }
    val observer = createDeletionObserver(threadID) {
      val chat = chats[threadID]

      // Reset last message cache
      if (chat != null) {
        val apiDatabase = DatabaseFactory.getLokiAPIDatabase(context)
        apiDatabase.removeLastDeletionServerID(chat.channel, chat.server)
        apiDatabase.removeLastMessageServerID(chat.channel, chat.server)
      }

      DatabaseFactory.getLokiThreadDatabase(context).removePublicChat(threadID)
      pollers.remove(threadID)?.stop()
      observers.remove(threadID)
      startPollersIfNeeded()
    }
    observers[threadID] = observer

    context.applicationContext.contentResolver.registerContentObserver(DatabaseContentProviders.Conversation.getUriForThread(threadID), true, observer)
  }

  private fun createDeletionObserver(threadID: Long, onDelete: Runnable): ContentObserver {
    return object : ContentObserver(null) {

      override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        // Stop the poller if thread is deleted
        try {
          if (!DatabaseFactory.getThreadDatabase(context).hasThread(threadID)) {
            onDelete.run()
            context.applicationContext.contentResolver.unregisterContentObserver(this)
          }
        } catch (e: Exception) {
          // TODO: Handle
        }
      }
    }
  }
}