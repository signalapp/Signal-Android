package org.thoughtcrime.securesms.loki

import android.content.Context
import android.database.ContentObserver
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.DatabaseContentProviders
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.loki.api.LokiGroupChat
import org.whispersystems.signalservice.loki.api.LokiGroupChatAPI
import java.util.HashSet

class LokiPublicChatManager(private val context: Context) {
  private var chats = mutableMapOf<Long, LokiGroupChat>()
  private val pollers = mutableMapOf<Long, LokiGroupChatPoller>()
  private val observers = mutableMapOf<Long, ContentObserver>()
  private var isPolling = false

  public fun startPollersIfNeeded() {
    refreshChatsAndPollers()

    for ((threadId, chat) in chats) {
      val poller = pollers[threadId] ?: LokiGroupChatPoller(context, chat)
      poller.startIfNeeded()
      listenToThreadDeletion(threadId)
      if (!pollers.containsKey(threadId)) { pollers[threadId] = poller }
    }
    isPolling = true
  }

  public fun stopPollers() {
    pollers.values.forEach { it.stop() }
    isPolling = false
  }

  public fun addChat(server: String, channel: Long): Promise<LokiGroupChat, Exception> {
    val groupChatAPI = ApplicationContext.getInstance(context).lokiGroupChatAPI ?: return Promise.ofFail(IllegalStateException("LokiGroupChatAPI is not set!"))
    return groupChatAPI.getAuthToken(server).bind {
      groupChatAPI.getChannelInfo(channel, server)
    }.map {
      addChat(server, channel, it)
    }
  }

  public fun addChat(server: String, channel: Long, name: String): LokiGroupChat {
    val chat = LokiGroupChat(channel, server, name, true)
    var threadID =  GroupManager.getThreadId(chat.id, context)
    // Create the group if we don't have one
    if (threadID < 0) {
      val result = GroupManager.createGroup(chat.id, context, HashSet(), null, chat.displayName, false)
      threadID = result.threadId
    }
    DatabaseFactory.getLokiThreadDatabase(context).setGroupChat(chat, threadID)
    startPollersIfNeeded()

    // Set our name on the server
    ApplicationContext.getInstance(context).lokiGroupChatAPI?.setDisplayName(server, TextSecurePreferences.getProfileName(context))

    return chat
  }

  private fun refreshChatsAndPollers() {
    val chatsInDB = DatabaseFactory.getLokiThreadDatabase(context).getAllGroupChats()
    val removedChatThreadIds = chats.keys.filter { !chatsInDB.keys.contains(it) }
    removedChatThreadIds.forEach { pollers.remove(it)?.stop() }

    // Only append to chats if we have a thread for the chat
    chats = chatsInDB.filter { GroupManager.getThreadId(it.value.id, context) > -1 }.toMutableMap()
  }

  private fun listenToThreadDeletion(threadID: Long) {
    if (threadID < 0 || observers[threadID] != null) { return }
    val observer = createDeletionObserver(threadID, Runnable {
      val chat = chats[threadID]

      // Reset last message cache
      if (chat != null) {
        val apiDatabase = DatabaseFactory.getLokiAPIDatabase(context)
        apiDatabase.removeLastDeletionServerID(chat.channel, chat.server)
        apiDatabase.removeLastMessageServerID(chat.channel, chat.server)
      }

      DatabaseFactory.getLokiThreadDatabase(context).removeGroupChat(threadID)
      pollers.remove(threadID)?.stop()
      observers.remove(threadID)
      startPollersIfNeeded()
    })
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