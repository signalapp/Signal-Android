package org.thoughtcrime.securesms.database.model

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Denotes whether a given recipient has stories, and whether those stories are viewed or unviewed.
 */
enum class StoryViewState {
  NONE,
  UNVIEWED,
  VIEWED;

  companion object {
    @JvmStatic
    fun getForRecipientId(recipientId: RecipientId): Observable<StoryViewState> {
      return Observable.create<StoryViewState> { emitter ->
        fun refresh() {
          emitter.onNext(SignalDatabase.mms.getStoryViewState(recipientId))
        }

        val storyObserver = DatabaseObserver.Observer {
          refresh()
        }

        ApplicationDependencies.getDatabaseObserver().registerStoryObserver(recipientId, storyObserver)
        emitter.setCancellable {
          ApplicationDependencies.getDatabaseObserver().unregisterObserver(storyObserver)
        }

        refresh()
      }.observeOn(Schedulers.io())
    }
  }
}
