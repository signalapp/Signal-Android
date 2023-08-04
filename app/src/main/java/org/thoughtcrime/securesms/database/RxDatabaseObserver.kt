package org.thoughtcrime.securesms.database

import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Emitter
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import java.util.concurrent.TimeUnit

/**
 * Provide a shared Rx interface to listen to database updates and ensure listeners
 * execute on [Schedulers.io].
 */
object RxDatabaseObserver {

  val conversationList: Flowable<Unit> by lazy { conversationListFlowable() }
  val notificationProfiles: Flowable<Unit> by lazy { notificationProfilesFlowable() }

  private fun conversationListFlowable(): Flowable<Unit> {
    return databaseFlowable { listener ->
      ApplicationDependencies.getDatabaseObserver().registerConversationListObserver(listener)
    }
  }

  fun conversation(threadId: Long): Flowable<Unit> {
    return databaseFlowable { listener ->
      ApplicationDependencies.getDatabaseObserver().registerVerboseConversationObserver(threadId, listener)
    }
  }

  @Suppress("RedundantUnitExpression")
  private fun notificationProfilesFlowable(): Flowable<Unit> {
    return Flowable.combineLatest(
      Flowable.interval(0, 30, TimeUnit.SECONDS),
      databaseFlowable { ApplicationDependencies.getDatabaseObserver().registerNotificationProfileObserver(it) }
    ) { _, _ -> Unit }
  }

  private fun databaseFlowable(registerObserver: (RxObserver) -> Unit): Flowable<Unit> {
    val flowable = Flowable.create(
      {
        val listener = RxObserver(it)

        registerObserver(listener)
        it.setCancellable { ApplicationDependencies.getDatabaseObserver().unregisterObserver(listener) }

        listener.prime()
      },
      BackpressureStrategy.LATEST
    )

    return flowable
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.io())
      .replay(1)
      .refCount()
      .observeOn(Schedulers.io())
  }

  private class RxObserver(private val emitter: Emitter<Unit>) : DatabaseObserver.Observer {
    fun prime() {
      emitter.onNext(Unit)
    }

    override fun onChanged() {
      emitter.onNext(Unit)
    }
  }
}
