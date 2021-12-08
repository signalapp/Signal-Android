package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.NotificationProfileDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * One stop shop for all your Notification Profile data needs.
 */
class NotificationProfilesRepository {
  private val database: NotificationProfileDatabase = SignalDatabase.notificationProfiles

  fun getProfiles(): Observable<List<NotificationProfile>> {
    return Observable.create { emitter: ObservableEmitter<List<NotificationProfile>> ->
      val databaseObserver: DatabaseObserver = ApplicationDependencies.getDatabaseObserver()
      val profileObserver = DatabaseObserver.Observer { emitter.onNext(database.getProfiles()) }

      databaseObserver.registerNotificationProfileObserver(profileObserver)

      emitter.setCancellable { databaseObserver.unregisterObserver(profileObserver) }
      emitter.onNext(database.getProfiles())
    }.subscribeOn(Schedulers.io())
  }

  fun getProfile(profileId: Long): Observable<NotificationProfile> {
    return Observable.create { emitter: ObservableEmitter<NotificationProfile> ->
      val emitProfile: () -> Unit = {
        val profile: NotificationProfile? = database.getProfile(profileId)
        if (profile != null) {
          emitter.onNext(profile)
        } else {
          emitter.onError(NotificationProfileNotFoundException())
        }
      }

      val databaseObserver: DatabaseObserver = ApplicationDependencies.getDatabaseObserver()
      val profileObserver = DatabaseObserver.Observer { emitProfile() }

      databaseObserver.registerNotificationProfileObserver(profileObserver)

      emitter.setCancellable { databaseObserver.unregisterObserver(profileObserver) }
      emitProfile()
    }.subscribeOn(Schedulers.io())
  }

  fun createProfile(name: String, selectedEmoji: String): Single<NotificationProfileDatabase.NotificationProfileChangeResult> {
    return Single.fromCallable { database.createProfile(name = name, emoji = selectedEmoji, color = AvatarColor.random(), createdAt = System.currentTimeMillis()) }
      .subscribeOn(Schedulers.io())
  }

  fun updateProfile(profileId: Long, name: String, selectedEmoji: String): Single<NotificationProfileDatabase.NotificationProfileChangeResult> {
    return Single.fromCallable { database.updateProfile(profileId = profileId, name = name, emoji = selectedEmoji) }
      .subscribeOn(Schedulers.io())
  }

  fun updateProfile(profile: NotificationProfile): Single<NotificationProfileDatabase.NotificationProfileChangeResult> {
    return Single.fromCallable { database.updateProfile(profile) }
      .subscribeOn(Schedulers.io())
  }

  fun updateAllowedMembers(profileId: Long, recipients: Set<RecipientId>): Single<NotificationProfile> {
    return Single.fromCallable { database.setAllowedRecipients(profileId, recipients) }
      .subscribeOn(Schedulers.io())
  }

  fun removeMember(profileId: Long, recipientId: RecipientId): Single<NotificationProfile> {
    return Single.fromCallable { database.removeAllowedRecipient(profileId, recipientId) }
      .subscribeOn(Schedulers.io())
  }

  fun addMember(profileId: Long, recipientId: RecipientId): Single<NotificationProfile> {
    return Single.fromCallable { database.addAllowedRecipient(profileId, recipientId) }
      .subscribeOn(Schedulers.io())
  }

  fun deleteProfile(profileId: Long): Completable {
    return Completable.fromCallable { database.deleteProfile(profileId) }
      .subscribeOn(Schedulers.io())
  }

  fun updateSchedule(schedule: NotificationProfileSchedule): Completable {
    return Completable.fromCallable { database.updateSchedule(schedule) }
      .subscribeOn(Schedulers.io())
  }

  fun manuallyToggleProfile(profile: NotificationProfile, now: Long = System.currentTimeMillis()): Completable {
    return Completable.fromAction {
      val profiles = database.getProfiles()
      val activeProfile = NotificationProfiles.getActiveProfile(profiles, now)

      if (profile.id == activeProfile?.id) {
        SignalStore.notificationProfileValues().manuallyEnabledProfile = 0
        SignalStore.notificationProfileValues().manuallyEnabledUntil = 0
        SignalStore.notificationProfileValues().manuallyDisabledAt = now
        SignalStore.notificationProfileValues().lastProfilePopup = 0
        SignalStore.notificationProfileValues().lastProfilePopupTime = 0
      } else {
        val inScheduledWindow = profile.schedule.isCurrentlyActive(now)
        SignalStore.notificationProfileValues().manuallyEnabledProfile = if (inScheduledWindow) 0 else profile.id
        SignalStore.notificationProfileValues().manuallyEnabledUntil = if (inScheduledWindow) 0 else Long.MAX_VALUE
        SignalStore.notificationProfileValues().manuallyDisabledAt = if (inScheduledWindow) 0 else now
      }
    }
      .doOnComplete { ApplicationDependencies.getDatabaseObserver().notifyNotificationProfileObservers() }
      .subscribeOn(Schedulers.io())
  }

  fun manuallyEnableProfileForDuration(profileId: Long, enableUntil: Long, now: Long = System.currentTimeMillis()): Completable {
    return Completable.fromAction {
      SignalStore.notificationProfileValues().manuallyEnabledProfile = profileId
      SignalStore.notificationProfileValues().manuallyEnabledUntil = enableUntil
      SignalStore.notificationProfileValues().manuallyDisabledAt = now
    }
      .doOnComplete { ApplicationDependencies.getDatabaseObserver().notifyNotificationProfileObservers() }
      .subscribeOn(Schedulers.io())
  }

  class NotificationProfileNotFoundException : Throwable()
}
