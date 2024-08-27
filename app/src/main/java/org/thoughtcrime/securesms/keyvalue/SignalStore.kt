package org.thoughtcrime.securesms.keyvalue

import androidx.annotation.VisibleForTesting
import androidx.preference.PreferenceDataStore
import org.signal.core.util.ResettableLazy
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies.application

/**
 * Simple, encrypted key-value store.
 */
class SignalStore(private val store: KeyValueStore) {

  val accountValues = AccountValues(store)
  val svrValues = SvrValues(store)
  val registrationValues = RegistrationValues(store)
  val pinValues = PinValues(store)
  val remoteConfigValues = RemoteConfigValues(store)
  val storageServiceValues = StorageServiceValues(store)
  val uiHintValues = UiHintValues(store)
  val tooltipValues = TooltipValues(store)
  val miscValues = MiscellaneousValues(store)
  val internalValues = InternalValues(store)
  val emojiValues = EmojiValues(store)
  val settingsValues = SettingsValues(store)
  val certificateValues = CertificateValues(store)
  val phoneNumberPrivacyValues = PhoneNumberPrivacyValues(store)
  val onboardingValues = OnboardingValues(store)
  val wallpaperValues = WallpaperValues(store)
  val paymentsValues = PaymentsValues(store)
  val inAppPaymentValues = InAppPaymentValues(store)
  val proxyValues = ProxyValues(store)
  val rateLimitValues = RateLimitValues(store)
  val chatColorsValues = ChatColorsValues(store)
  val imageEditorValues = ImageEditorValues(store)
  val notificationProfileValues = NotificationProfileValues(store)
  val releaseChannelValues = ReleaseChannelValues(store)
  val storyValues = StoryValues(store)
  val apkUpdateValues = ApkUpdateValues(store)
  val backupValues = BackupValues(store)

  val plainTextValues = PlainTextSharedPrefsDataStore(application)

  companion object {

    private var instanceOverride: SignalStore? = null
    private val _instance = ResettableLazy {
      instanceOverride ?: SignalStore(KeyValueStore(KeyValueDatabase.getInstance(application)))
    }
    private val instance by _instance

    @JvmStatic
    fun onFirstEverAppLaunch() {
      account.onFirstEverAppLaunch()
      svr.onFirstEverAppLaunch()
      registration.onFirstEverAppLaunch()
      pin.onFirstEverAppLaunch()
      remoteConfig.onFirstEverAppLaunch()
      storageService.onFirstEverAppLaunch()
      uiHints.onFirstEverAppLaunch()
      tooltips.onFirstEverAppLaunch()
      misc.onFirstEverAppLaunch()
      internal.onFirstEverAppLaunch()
      emoji.onFirstEverAppLaunch()
      settings.onFirstEverAppLaunch()
      certificate.onFirstEverAppLaunch()
      phoneNumberPrivacy.onFirstEverAppLaunch()
      onboarding.onFirstEverAppLaunch()
      wallpaper.onFirstEverAppLaunch()
      payments.onFirstEverAppLaunch()
      inAppPayments.onFirstEverAppLaunch()
      proxy.onFirstEverAppLaunch()
      rateLimit.onFirstEverAppLaunch()
      chatColors.onFirstEverAppLaunch()
      imageEditor.onFirstEverAppLaunch()
      notificationProfile.onFirstEverAppLaunch()
      releaseChannel.onFirstEverAppLaunch()
      story.onFirstEverAppLaunch()
    }

    @JvmStatic
    val keysToIncludeInBackup: List<String>
      get() {
        return account.keysToIncludeInBackup +
          svr.keysToIncludeInBackup +
          registration.keysToIncludeInBackup +
          pin.keysToIncludeInBackup +
          remoteConfig.keysToIncludeInBackup +
          storageService.keysToIncludeInBackup +
          uiHints.keysToIncludeInBackup +
          tooltips.keysToIncludeInBackup +
          misc.keysToIncludeInBackup +
          internal.keysToIncludeInBackup +
          emoji.keysToIncludeInBackup +
          settings.keysToIncludeInBackup +
          certificate.keysToIncludeInBackup +
          phoneNumberPrivacy.keysToIncludeInBackup +
          onboarding.keysToIncludeInBackup +
          wallpaper.keysToIncludeInBackup +
          payments.keysToIncludeInBackup +
          inAppPayments.keysToIncludeInBackup +
          proxy.keysToIncludeInBackup +
          rateLimit.keysToIncludeInBackup +
          chatColors.keysToIncludeInBackup +
          imageEditor.keysToIncludeInBackup +
          notificationProfile.keysToIncludeInBackup +
          releaseChannel.keysToIncludeInBackup +
          story.keysToIncludeInBackup
      }

    /**
     * Forces the store to re-fetch all of it's data from the database.
     * Should only be used for testing!
     */
    @VisibleForTesting
    fun resetCache() {
      instance.store.resetCache()
    }

    /**
     * Restoring a backup changes the underlying disk values, so the cache needs to be reset.
     */
    @JvmStatic
    fun onPostBackupRestore() {
      instance.store.resetCache()
    }

    @JvmStatic
    @get:JvmName("account")
    val account: AccountValues
      get() = instance.accountValues

    @JvmStatic
    @get:JvmName("svr")
    val svr: SvrValues
      get() = instance.svrValues

    @JvmStatic
    @get:JvmName("registration")
    val registration: RegistrationValues
      get() = instance.registrationValues

    @JvmStatic
    @get:JvmName("pin")
    val pin: PinValues
      get() = instance.pinValues

    val remoteConfig: RemoteConfigValues
      get() = instance.remoteConfigValues

    @JvmStatic
    @get:JvmName("storageService")
    val storageService: StorageServiceValues
      get() = instance.storageServiceValues

    @JvmStatic
    @get:JvmName("uiHints")
    val uiHints: UiHintValues
      get() = instance.uiHintValues

    @JvmStatic
    @get:JvmName("tooltips")
    val tooltips: TooltipValues
      get() = instance.tooltipValues

    @JvmStatic
    @get:JvmName("misc")
    val misc: MiscellaneousValues
      get() = instance.miscValues

    @JvmStatic
    @get:JvmName("internal")
    val internal: InternalValues
      get() = instance.internalValues

    @JvmStatic
    @get:JvmName("emoji")
    val emoji: EmojiValues
      get() = instance.emojiValues

    @JvmStatic
    @get:JvmName("settings")
    val settings: SettingsValues
      get() = instance.settingsValues

    @JvmStatic
    @get:JvmName("certificate")
    val certificate: CertificateValues
      get() = instance.certificateValues

    @JvmStatic
    @get:JvmName("phoneNumberPrivacy")
    val phoneNumberPrivacy: PhoneNumberPrivacyValues
      get() = instance.phoneNumberPrivacyValues

    @JvmStatic
    @get:JvmName("onboarding")
    val onboarding: OnboardingValues
      get() = instance.onboardingValues

    @JvmStatic
    @get:JvmName("wallpaper")
    val wallpaper: WallpaperValues
      get() = instance.wallpaperValues

    @JvmStatic
    @get:JvmName("payments")
    val payments: PaymentsValues
      get() = instance.paymentsValues

    @JvmStatic
    @get:JvmName("inAppPayments")
    val inAppPayments: InAppPaymentValues
      get() = instance.inAppPaymentValues

    @JvmStatic
    @get:JvmName("proxy")
    val proxy: ProxyValues
      get() = instance.proxyValues

    @JvmStatic
    @get:JvmName("rateLimit")
    val rateLimit: RateLimitValues
      get() = instance.rateLimitValues

    @JvmStatic
    @get:JvmName("chatColors")
    val chatColors: ChatColorsValues
      get() = instance.chatColorsValues

    val imageEditor: ImageEditorValues
      get() = instance.imageEditorValues

    val notificationProfile: NotificationProfileValues
      get() = instance.notificationProfileValues

    @JvmStatic
    @get:JvmName("releaseChannel")
    val releaseChannel: ReleaseChannelValues
      get() = instance.releaseChannelValues

    @JvmStatic
    @get:JvmName("story")
    val story: StoryValues
      get() = instance.storyValues

    val apkUpdate: ApkUpdateValues
      get() = instance.apkUpdateValues

    @JvmStatic
    @get:JvmName("backup")
    val backup: BackupValues
      get() = instance.backupValues

    val groupsV2AciAuthorizationCache: GroupsV2AuthorizationSignalStoreCache
      get() = GroupsV2AuthorizationSignalStoreCache.createAciCache(instance.store)

    val plaintext: PlainTextSharedPrefsDataStore
      get() = instance.plainTextValues

    fun getPreferenceDataStore(): PreferenceDataStore {
      return SignalPreferenceDataStore(instance.store)
    }

    /**
     * Ensures any pending writes are finished. Only intended to be called by
     * [SignalUncaughtExceptionHandler].
     */
    @JvmStatic
    fun blockUntilAllWritesFinished() {
      instance.store.blockUntilAllWritesFinished()
    }

    /**
     * Allows you to set a custom KeyValueStore to read from. Only for testing!
     */
    @VisibleForTesting
    fun testInject(store: KeyValueStore) {
      instanceOverride = SignalStore(store)
      _instance.reset()
    }

    /**
     * Allows you to set a custom SignalStore to read from. Only for testing!
     */
    @VisibleForTesting
    fun testInject(store: SignalStore) {
      instanceOverride = store
      _instance.reset()
    }

    fun clearAllDataForBackupRestore() {
      releaseChannel.clearReleaseChannelRecipientId()
      account.clearRegistrationButKeepCredentials()
    }
  }
}
