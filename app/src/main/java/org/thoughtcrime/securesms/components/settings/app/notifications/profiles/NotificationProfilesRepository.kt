package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.NotificationProfileDatabase
import org.thoughtcrime.securesms.database.RxDatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.toLocalDateTime
import org.thoughtcrime.securesms.util.toMillis

/**
 * One stop shop for all your Notification Profile data needs.
 */
class NotificationProfilesRepository {
  private val database: NotificationProfileDatabase = SignalDatabase.notificationProfiles

  fun getProfiles(): Flowable<List<NotificationProfile>> {
    return RxDatabaseObserver
      .notificationProfiles
      .map { database.getProfiles() }
      .subscribeOn(Schedulers.io())
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

      val databaseObserver: DatabaseObserver = AppDependencies.databaseObserver
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

  fun toggleAllowAllMentions(profileId: Long): Single<NotificationProfile> {
    return getProfile(profileId)
      .take(1)
      .singleOrError()
      .flatMap { updateProfile(it.copy(allowAllMentions = !it.allowAllMentions)) }
      .map { (it as NotificationProfileDatabase.NotificationProfileChangeResult.Success).notificationProfile }
  }

  fun toggleAllowAllCalls(profileId: Long): Single<NotificationProfile> {
    return getProfile(profileId)
      .take(1)
      .singleOrError()
      .flatMap { updateProfile(it.copy(allowAllCalls = !it.allowAllCalls)) }
      .map { (it as NotificationProfileDatabase.NotificationProfileChangeResult.Success).notificationProfile }
  }

  fun manuallyToggleProfile(profile: NotificationProfile, now: Long = System.currentTimeMillis()): Completable {
    return manuallyToggleProfile(profile.id, profile.schedule, now)
  }

  fun manuallyToggleProfile(profileId: Long, schedule: NotificationProfileSchedule, now: Long = System.currentTimeMillis()): Completable {
    return Completable.fromAction {
      val profiles = database.getProfiles()
      val activeProfile = NotificationProfiles.getActiveProfile(profiles, now)

      if (profileId == activeProfile?.id) {
        SignalStore.notificationProfile.manuallyEnabledProfile = 0
        SignalStore.notificationProfile.manuallyEnabledUntil = 0
        SignalStore.notificationProfile.manuallyDisabledAt = now
        SignalStore.notificationProfile.lastProfilePopup = 0
        SignalStore.notificationProfile.lastProfilePopupTime = 0
      } else {
        val inScheduledWindow = schedule.isCurrentlyActive(now)
        SignalStore.notificationProfile.manuallyEnabledProfile = profileId
        SignalStore.notificationProfile.manuallyEnabledUntil = if (inScheduledWindow) schedule.endDateTime(now.toLocalDateTime()).toMillis() else Long.MAX_VALUE
        SignalStore.notificationProfile.manuallyDisabledAt = now
      }
    }
      .doOnComplete { AppDependencies.databaseObserver.notifyNotificationProfileObservers() }
      .subscribeOn(Schedulers.io())
  }

  fun manuallyEnableProfileForDuration(profileId: Long, enableUntil: Long, now: Long = System.currentTimeMillis()): Completable {
    return Completable.fromAction {
      SignalStore.notificationProfile.manuallyEnabledProfile = profileId
      SignalStore.notificationProfile.manuallyEnabledUntil = enableUntil
      SignalStore.notificationProfile.manuallyDisabledAt = now
    }
      .doOnComplete { AppDependencies.databaseObserver.notifyNotificationProfileObservers() }
      .subscribeOn(Schedulers.io())
  }

  fun manuallyEnableProfileForSchedule(profileId: Long, schedule: NotificationProfileSchedule, now: Long = System.currentTimeMillis()): Completable {
    return Completable.fromAction {
      val inScheduledWindow = schedule.isCurrentlyActive(now)
      SignalStore.notificationProfile.manuallyEnabledProfile = if (inScheduledWindow) profileId else 0
      SignalStore.notificationProfile.manuallyEnabledUntil = if (inScheduledWindow) schedule.endDateTime(now.toLocalDateTime()).toMillis() else Long.MAX_VALUE
      SignalStore.notificationProfile.manuallyDisabledAt = if (inScheduledWindow) now else 0
    }
      .doOnComplete { AppDependencies.databaseObserver.notifyNotificationProfileObservers() }
      .subscribeOn(Schedulers.io())
  }

  class NotificationProfileNotFoundException : Throwable()
}
