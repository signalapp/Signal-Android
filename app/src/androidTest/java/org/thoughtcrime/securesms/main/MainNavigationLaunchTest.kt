/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.models.media.Media
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.log.CallLogFragment
import org.thoughtcrime.securesms.conversation.ConversationArgs
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.v2.ConversationFragment
import org.thoughtcrime.securesms.conversationlist.ConversationListArchiveFragment
import org.thoughtcrime.securesms.conversationlist.ConversationListFragment
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.landing.StoriesLandingFragment
import org.thoughtcrime.securesms.testing.SignalActivityRule
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end launch tests for [MainActivity], covering cold-launch and onNewIntent paths
 * through [MainNavigationViewModel].
 */
@RunWith(AndroidJUnit4::class)
class MainNavigationLaunchTest {

  @get:Rule
  val harness = SignalActivityRule(othersCount = 2)

  private val context: Context get() = harness.context
  private val recipient: RecipientId get() = harness.others.first()

  /**
   * Share-target cold-launch regression test. Pre-fix, wrapNavigator() re-routed the
   * early-staged Conversation through goTo(), whose async wallpaper-prefetch path emitted
   * a SECOND internalDetailLocation with a fresh ConversationArgs — recreating the
   * fragment and dropping share data.
   */
  @Test
  fun coldLaunch_shareIntent_createsFragmentExactlyOnceWithShareData() {
    val timestamp = System.currentTimeMillis()
    val mimeType = "image/jpeg"
    val blob = realBlob(byteArrayOf(0x01, 0x02, 0x03), mimeType)
    val intent = shareToConversationIntent(
      recipient = recipient,
      blob = blob,
      mimeType = mimeType,
      shareDataTimestamp = timestamp
    )

    launchSync(intent).use { launched ->
      val recorder = launched.recorder

      try {
        await(timeoutMs = 10_000, description = "ConversationFragment to be added") {
          recorder.createdArgs.isNotEmpty()
        }
      } catch (e: IllegalStateException) {
        val vm = runOnMainSync { launched.activity.mainNavigationViewModel() }
        val state = runOnMainSync {
          buildString {
            appendLine("--- diagnostic dump ---")
            appendLine("fragments observed: ${recorder.allCreated}")
            appendLine("activity fragments: ${launched.activity.supportFragmentManager.fragments.map { it::class.simpleName }}")
            appendLine("vm.currentListLocation: ${vm.mainNavigationState.value.currentListLocation}")
            appendLine("vm.earlyNavigationDetailLocationRequested: ${vm.earlyNavigationDetailLocationRequested}")
          }
        }
        throw IllegalStateException("${e.message}\n$state", e)
      }

      val expectedName = runOnMainSync { Recipient.resolved(recipient).getDisplayName(context) }
      awaitConversationTitle(launched, expectedName)

      // Give the post-navigator wallpaper-prefetch path a chance to emit a (pre-fix)
      // duplicate second nav before we count fragments.
      Thread.sleep(750)

      check(recorder.createdArgs.size == 1) {
        "Expected exactly one ConversationFragment, got ${recorder.createdArgs.size}"
      }
      val args = recorder.createdArgs.single()
      check(args.shareDataTimestamp == timestamp) {
        "Expected shareDataTimestamp=$timestamp, got ${args.shareDataTimestamp}"
      }
      check(args.recipientId == recipient) {
        "Expected recipient=$recipient, got ${args.recipientId}"
      }
      check(args.draftMedia == blob) {
        "Expected draftMedia=$blob, got ${args.draftMedia}"
      }
    }
  }

  /**
   * Image-share cold-launch: the dispatch path through `ShareOrDraftData.StartSendMedia`
   * that hops the user from the conversation into the media-send screen
   * ([MediaSelectionActivity]). Asserts that the secondary activity actually launches and
   * that its [MediaReviewFragment] surfaces the recipient's display name in the top
   * corner — i.e. it knows who the share is targeted at.
   */
  @Test
  fun coldLaunch_shareImageIntent_opensMediaSendForRecipient() {
    val media = realJpegMedia()
    val intent = shareImageIntent(recipient = recipient, media = media)

    launchSync(intent).use { launched ->
      val mediaSend = launched.awaitActivity(MediaSelectionActivity::class.java, timeoutMs = 20_000)
      val expectedName = runOnMainSync { Recipient.resolved(recipient).getDisplayName(context) }

      await(timeoutMs = 15_000, description = "recipient label populated in MediaReviewFragment") {
        // await() already runs the predicate on the main thread; nesting another
        // runOnMainSync here would throw "can not be called from the main application thread".
        mediaSend.findViewById<TextView>(R.id.recipient)?.text?.toString() == expectedName
      }

      // Exactly one ConversationFragment should have been created — the share dispatch
      // happens from inside it, then it stays put while the media editor sits on top.
      check(launched.recorder.createdArgs.size == 1) {
        "Expected exactly one ConversationFragment for image share, got ${launched.recorder.createdArgs.size}"
      }
    }
  }

  /**
   * Text-share cold-launch: the dispatch path through `ShareOrDraftData.SetText`. Asserts
   * the navigation boundary — one ConversationFragment, no secondary activity pushed on
   * top — *and* that the draft text actually shows up in the composer the user sees.
   */
  @Test
  fun coldLaunch_shareTextIntent_opensConversationWithDraftText() {
    val draftText = "hello from share"
    val intent = shareTextIntent(recipient = recipient, text = draftText)

    launchSync(intent).use { launched ->
      val recorder = launched.recorder
      await(timeoutMs = 10_000, description = "ConversationFragment to be added") {
        recorder.createdArgs.isNotEmpty()
      }

      awaitComposerText(launched, draftText)

      // Give a beat for any spurious second navigation to surface.
      Thread.sleep(750)

      check(recorder.createdArgs.size == 1) {
        "Expected exactly one ConversationFragment, got ${recorder.createdArgs.size}"
      }
      val args = recorder.createdArgs.single()
      check(args.recipientId == recipient) {
        "Expected recipient=$recipient, got ${args.recipientId}"
      }
      check(args.draftMedia == null) {
        "Expected no draftMedia, got ${args.draftMedia}"
      }
      check(launched.nonMainActivities().isEmpty()) {
        "Text share should not launch a secondary activity, got ${launched.nonMainActivities().map { it::class.simpleName }}"
      }
    }
  }

  @Test
  fun coldLaunch_notificationIntent_opensConversation() {
    val intent = notificationToConversationIntent(recipient)
    launchSync(intent).use { launched ->
      val recorder = launched.recorder
      await(timeoutMs = 10_000, description = "ConversationFragment to be added") {
        recorder.createdArgs.isNotEmpty()
      }

      val expectedName = runOnMainSync { Recipient.resolved(recipient).getDisplayName(context) }
      awaitConversationTitle(launched, expectedName)

      check(recorder.createdArgs.size == 1) {
        "Expected exactly one ConversationFragment, got ${recorder.createdArgs.size}"
      }
      val args = recorder.createdArgs.single()
      check(args.recipientId == recipient) {
        "Expected recipient=$recipient, got ${args.recipientId}"
      }
      check(args.threadId > 0) {
        "Expected threadId > 0, got ${args.threadId}"
      }
      check(args.draftMedia == null) {
        "Expected no draftMedia, got ${args.draftMedia}"
      }
      check(args.shareDataTimestamp == -1L) {
        "Expected shareDataTimestamp=-1 for notification path, got ${args.shareDataTimestamp}"
      }
      val vm = runOnMainSync { launched.activity.mainNavigationViewModel() }
      check(vm.mainNavigationState.value.currentListLocation == MainNavigationListLocation.CHATS) {
        "Expected currentListLocation=CHATS, got ${vm.mainNavigationState.value.currentListLocation}"
      }
    }
  }

  @Test
  fun coldLaunch_tabIntent_setsListLocation() {
    val intent = tabIntent(MainNavigationListLocation.CALLS)
    launchSync(intent).use { launched ->
      val recorder = launched.recorder
      awaitListFragment(launched, MainNavigationListLocation.CALLS)

      val vm = runOnMainSync { launched.activity.mainNavigationViewModel() }
      check(vm.mainNavigationState.value.currentListLocation == MainNavigationListLocation.CALLS) {
        "Expected VM CALLS, got ${vm.mainNavigationState.value.currentListLocation}"
      }
      Thread.sleep(750)
      check(recorder.createdArgs.isEmpty()) {
        "Expected no ConversationFragment for tab launch, got ${recorder.createdArgs.size}"
      }
    }
  }

  /**
   * Locks down present cold-launch behaviour for KEY_DETAIL_LOCATION: today it is only
   * consumed by onNewIntent. If a future change starts handling it on cold launch, this
   * test should fail and force a deliberate decision.
   */
  @Test
  fun coldLaunch_detailLocationIntent_isNoOpToday() {
    val intent = detailLocationIntent(MainNavigationDetailLocation.Chats.ConversationSettings(recipient))
    launchSync(intent).use { launched ->
      val recorder = launched.recorder
      Thread.sleep(1500)
      check(recorder.createdArgs.isEmpty()) {
        "KEY_DETAIL_LOCATION is currently only handled by onNewIntent. If a future change " +
          "starts handling it on cold launch, update or delete this test. Got: ${recorder.allCreated}"
      }
      val vm = runOnMainSync { launched.activity.mainNavigationViewModel() }
      check(vm.earlyNavigationDetailLocationRequested == null) {
        "Expected no early detail to be staged, got ${vm.earlyNavigationDetailLocationRequested}"
      }
    }
  }

  @Test
  fun coldLaunch_deepLinkIntent_reachesChatsList() {
    val intent = deepLinkIntent(Uri.parse("https://signal.org/test-not-a-real-deeplink"))
    launchSync(intent).use { launched ->
      val recorder = launched.recorder
      awaitListFragment(launched, MainNavigationListLocation.CHATS)

      val vm = runOnMainSync { launched.activity.mainNavigationViewModel() }
      check(vm.mainNavigationState.value.currentListLocation == MainNavigationListLocation.CHATS) {
        "Expected CHATS for deep-link launch, got ${vm.mainNavigationState.value.currentListLocation}"
      }
      check(recorder.createdArgs.isEmpty()) {
        "Expected no ConversationFragment for deep-link launch, got ${recorder.createdArgs.size}"
      }
    }
  }

  @Test
  fun coldLaunch_noExtras_defaultsToChats() {
    val intent = Intent(context, MainActivity::class.java)
    launchSync(intent).use { launched ->
      val recorder = launched.recorder
      awaitListFragment(launched, MainNavigationListLocation.CHATS)

      val vm = runOnMainSync { launched.activity.mainNavigationViewModel() }
      check(vm.mainNavigationState.value.currentListLocation == MainNavigationListLocation.CHATS) {
        "Expected default CHATS, got ${vm.mainNavigationState.value.currentListLocation}"
      }
      Thread.sleep(750)
      check(vm.earlyNavigationDetailLocationRequested == null) {
        "Expected no early detail, got ${vm.earlyNavigationDetailLocationRequested}"
      }
      check(recorder.createdArgs.isEmpty()) {
        "Expected no ConversationFragment for bare launch, got ${recorder.createdArgs.size}"
      }
    }
  }

  @Test
  fun warmStart_onNewIntent_conversationIntent_opensConversation() {
    launchSync(Intent(context, MainActivity::class.java)).use { launched ->
      val recorder = launched.recorder
      // Let the bare list settle so we know any further fragment adds came from onNewIntent.
      Thread.sleep(1000)
      val baseline = recorder.createdArgs.size

      val warmIntent = notificationToConversationIntent(recipient)
      runOnMainSync {
        InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(launched.activity, warmIntent)
      }

      await(timeoutMs = 10_000, description = "ConversationFragment after onNewIntent") {
        recorder.createdArgs.size > baseline
      }

      val expectedName = runOnMainSync { Recipient.resolved(recipient).getDisplayName(context) }
      awaitConversationTitle(launched, expectedName)

      val newArgs = recorder.createdArgs.drop(baseline)
      check(newArgs.size == 1) { "Expected one new ConversationFragment, got ${newArgs.size}" }
      check(newArgs.single().recipientId == recipient) {
        "Expected recipient=$recipient, got ${newArgs.single().recipientId}"
      }
    }
  }

  /**
   * Mid-conversation onNewIntent with `KEY_DETAIL_LOCATION = Empty` — the contract used
   * by [ConversationSettingsFragment.goToConversationList] to drop back to the chat list
   * on phones. No new ConversationFragment should be added.
   */
  @Test
  fun warmStart_onNewIntent_emptyDetailIntent_returnsToList() {
    launchSync(notificationToConversationIntent(recipient)).use { launched ->
      val recorder = launched.recorder
      await(timeoutMs = 10_000, description = "initial ConversationFragment") {
        recorder.createdArgs.isNotEmpty()
      }
      val baseline = recorder.createdArgs.size

      val warmIntent = detailLocationIntent(MainNavigationDetailLocation.Empty)
      runOnMainSync {
        InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(launched.activity, warmIntent)
      }

      await(description = "no new ConversationFragment after Empty detail intent") {
        recorder.createdArgs.size == baseline
      }
      // The user-visible signal that we're "back on the list" is the chat list fragment
      // being attached, not just the VM saying CHATS.
      awaitListFragment(launched, MainNavigationListLocation.CHATS)

      val vm = runOnMainSync { launched.activity.mainNavigationViewModel() }
      check(vm.mainNavigationState.value.currentListLocation == MainNavigationListLocation.CHATS) {
        "Expected CHATS, got ${vm.mainNavigationState.value.currentListLocation}"
      }
    }
  }

  @Test
  fun warmStart_onNewIntent_tabIntent_switchesList() {
    launchSync(Intent(context, MainActivity::class.java)).use { launched ->
      awaitListFragment(launched, MainNavigationListLocation.CHATS)

      val warmIntent = tabIntent(MainNavigationListLocation.CALLS)
      runOnMainSync {
        InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(launched.activity, warmIntent)
      }

      awaitListFragment(launched, MainNavigationListLocation.CALLS)

      val vm = runOnMainSync { launched.activity.mainNavigationViewModel() }
      check(vm.mainNavigationState.value.currentListLocation == MainNavigationListLocation.CALLS) {
        "Expected VM CALLS, got ${vm.mainNavigationState.value.currentListLocation}"
      }
      check(launched.recorder.createdArgs.isEmpty()) {
        "Expected no ConversationFragment for tab switch, got ${launched.recorder.createdArgs.size}"
      }
    }
  }

  @Test
  fun recreate_midConversation_restoresState() {
    launchSync(notificationToConversationIntent(recipient)).use { launched ->
      val recorder = launched.recorder
      await(timeoutMs = 10_000, description = "initial ConversationFragment") {
        recorder.createdArgs.isNotEmpty()
      }
      val expectedName = runOnMainSync { Recipient.resolved(recipient).getDisplayName(context) }
      awaitConversationTitle(launched, expectedName)
      val initial = recorder.createdArgs.first()

      runOnMainSync { launched.activity.recreate() }

      await(timeoutMs = 15_000, description = "ConversationFragment after recreate") {
        recorder.createdArgs.size >= 2
      }
      // Verify the user-visible title rebinds after recreate, not just the args.
      awaitConversationTitle(launched, expectedName)

      val recreated = recorder.createdArgs[1]
      check(recreated.recipientId == initial.recipientId) {
        "Recipient changed across recreate: ${initial.recipientId} -> ${recreated.recipientId}"
      }
      check(recreated.threadId == initial.threadId) {
        "Thread changed across recreate: ${initial.threadId} -> ${recreated.threadId}"
      }
    }
  }

  @Test
  fun recreate_midTab_restoresTab() {
    launchSync(tabIntent(MainNavigationListLocation.CALLS)).use { launched ->
      awaitListFragment(launched, MainNavigationListLocation.CALLS)

      runOnMainSync { launched.activity.recreate() }

      // Verify the user-visible tab content rebinds after recreate, not just the VM. The
      // recorder removes destroyed fragments, so this only passes once the post-recreate
      // CallLogFragment instance is attached.
      awaitListFragment(launched, MainNavigationListLocation.CALLS)

      // launched.activity returns the *latest* MainActivity (the holder updates in
      // onActivityCreated), so this reads the post-recreate VM instance.
      val location = runOnMainSync {
        launched.activity.mainNavigationViewModel().mainNavigationState.value.currentListLocation
      }
      check(location == MainNavigationListLocation.CALLS) {
        "Expected VM CALLS post-recreate, got $location"
      }
      check(launched.recorder.createdArgs.isEmpty()) {
        "Expected no ConversationFragment across tab recreate, got ${launched.recorder.createdArgs.size}"
      }
    }
  }

  @Test
  fun recreate_midShareConversation_preservesShareData() {
    val timestamp = System.currentTimeMillis()
    val mimeType = "image/jpeg"
    val blob = realBlob(byteArrayOf(0x01, 0x02, 0x03), mimeType)
    val intent = shareToConversationIntent(
      recipient = recipient,
      blob = blob,
      mimeType = mimeType,
      shareDataTimestamp = timestamp
    )

    launchSync(intent).use { launched ->
      val recorder = launched.recorder
      await(timeoutMs = 10_000, description = "initial ConversationFragment") {
        recorder.createdArgs.isNotEmpty()
      }
      val expectedName = runOnMainSync { Recipient.resolved(recipient).getDisplayName(context) }
      awaitConversationTitle(launched, expectedName)
      val initialCount = recorder.createdArgs.size

      runOnMainSync { launched.activity.recreate() }

      await(timeoutMs = 15_000, description = "ConversationFragment after recreate") {
        recorder.createdArgs.size > initialCount
      }
      awaitConversationTitle(launched, expectedName)

      val recreated = recorder.createdArgs.last()
      check(recreated.shareDataTimestamp == timestamp) {
        "shareDataTimestamp not preserved across recreate: $timestamp -> ${recreated.shareDataTimestamp}"
      }
      check(recreated.draftMedia == blob) {
        "draftMedia not preserved across recreate: $blob -> ${recreated.draftMedia}"
      }
    }
  }

  // region Helpers

  /**
   * Mirrors [org.thoughtcrime.securesms.sharing.v2.ShareActivity.openConversation]. We
   * deliberately drop the producer's `clearTop` flags (NEW_TASK | CLEAR_TOP | SINGLE_TOP)
   * — they are launch-routing concerns that are incompatible with our lifecycle monitor.
   */
  private fun shareToConversationIntent(
    recipient: RecipientId,
    blob: Uri,
    mimeType: String,
    draftText: String? = null,
    shareDataTimestamp: Long = System.currentTimeMillis()
  ): Intent {
    val builder = ConversationIntents.createBuilder(context, recipient, -1L).blockingGet()
    val conversationIntent = builder
      .withDataUri(blob)
      .withDataType(mimeType)
      .withMedia(emptyList())
      .withDraftText(draftText)
      .withStickerLocator(null)
      .asBorderless(false)
      .withShareDataTimestamp(shareDataTimestamp)
      .build()

    return Intent(context, MainActivity::class.java).apply {
      action = ConversationIntents.ACTION
      putExtras(conversationIntent)
    }
  }

  /**
   * Mirrors the image-share path through [org.thoughtcrime.securesms.sharing.v2.ShareActivity.openConversation]:
   * a non-empty `media` list is what flips dispatch to `ShareOrDraftData.StartSendMedia`,
   * which is what triggers the hop to the media-send screen.
   */
  private fun shareImageIntent(recipient: RecipientId, media: Media): Intent {
    val builder = ConversationIntents.createBuilder(context, recipient, -1L).blockingGet()
    val conversationIntent = builder
      .withDataUri(media.uri)
      .withDataType(media.contentType)
      .withMedia(listOf(media))
      .withStickerLocator(null)
      .asBorderless(false)
      .withShareDataTimestamp(System.currentTimeMillis())
      .build()

    return Intent(context, MainActivity::class.java).apply {
      action = ConversationIntents.ACTION
      putExtras(conversationIntent)
    }
  }

  /**
   * Mirrors a text-only share. Empty media list + non-null draft text routes dispatch to
   * `ShareOrDraftData.SetText`.
   */
  private fun shareTextIntent(recipient: RecipientId, text: String): Intent {
    val builder = ConversationIntents.createBuilder(context, recipient, -1L).blockingGet()
    val conversationIntent = builder
      .withMedia(emptyList())
      .withDraftText(text)
      .withStickerLocator(null)
      .asBorderless(false)
      .withShareDataTimestamp(System.currentTimeMillis())
      .build()

    return Intent(context, MainActivity::class.java).apply {
      action = ConversationIntents.ACTION
      putExtras(conversationIntent)
    }
  }

  private fun notificationToConversationIntent(recipient: RecipientId): Intent {
    val conversationIntent = ConversationIntents.createBuilder(context, recipient, -1L)
      .blockingGet()
      .build()

    return Intent(context, MainActivity::class.java).apply {
      action = ConversationIntents.ACTION
      putExtras(conversationIntent)
    }
  }

  private fun tabIntent(tab: MainNavigationListLocation): Intent {
    return Intent(context, MainActivity::class.java)
      .putExtra("STARTING_TAB", tab)
  }

  private fun detailLocationIntent(location: MainNavigationDetailLocation): Intent {
    return Intent(context, MainActivity::class.java)
      .putExtra("DETAIL_LOCATION", location)
  }

  private fun realBlob(bytes: ByteArray, mimeType: String): Uri {
    return BlobProvider.getInstance()
      .forData(bytes)
      .withMimeType(mimeType)
      .createForSingleSessionInMemory()
  }

  /**
   * Build a [Media] backed by a real 1×1 JPEG. The media-send screen attempts to decode
   * the image during MediaReviewFragment setup, so a fake byte array won't survive — we
   * need genuine JPEG bytes for the fragment to reach the state where `R.id.recipient`
   * is populated.
   */
  private fun realJpegMedia(): Media {
    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val bytes = ByteArrayOutputStream().use { stream ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
      stream.toByteArray()
    }
    bitmap.recycle()
    val uri = realBlob(bytes, "image/jpeg")
    return Media(
      uri = uri,
      contentType = "image/jpeg",
      date = 0L,
      width = 1,
      height = 1,
      size = bytes.size.toLong(),
      duration = 0L,
      isBorderless = false,
      isVideoGif = false,
      bucketId = null,
      caption = null,
      transformProperties = null,
      fileName = null
    )
  }

  /**
   * Mirrors [org.thoughtcrime.securesms.deeplinks.DeepLinkEntryActivity]: bare clearTop
   * plus a [Uri] in the data field.
   */
  private fun deepLinkIntent(data: Uri): Intent {
    return Intent(context, MainActivity::class.java).setData(data)
  }

  /**
   * Synchronously launch [MainActivity] and return the running instance plus a fragment
   * recorder wired up *before* the activity is created.
   *
   * We bypass [androidx.test.core.app.ActivityScenario] and
   * [android.app.Instrumentation.startActivitySync] because both fail for our case:
   * ActivityScenario's lifecycle tracker misses CREATED/STARTED/RESUMED for activities
   * launched with a custom-action intent, and `startActivitySync` waits for main-thread
   * idle which never arrives while MainActivity's composition + ConversationFragment
   * setup keeps the looper busy.
   */
  private fun launchSync(intent: Intent): LaunchedActivity {
    val recorder = ConversationFragmentRecorder()
    val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    val resumed = CountDownLatch(1)
    val activityHolder = arrayOfNulls<MainActivity>(1)
    val allActivities: MutableList<Activity> = Collections.synchronizedList(mutableListOf())
    val callbacks = object : Application.ActivityLifecycleCallbacks {
      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        allActivities += activity
        if (activity is MainActivity) {
          activityHolder[0] = activity
          activity.supportFragmentManager.registerFragmentLifecycleCallbacks(recorder, true)
        }
      }

      override fun onActivityStarted(activity: Activity) = Unit
      override fun onActivityResumed(activity: Activity) {
        if (activity is MainActivity) resumed.countDown()
      }

      override fun onActivityPaused(activity: Activity) = Unit
      override fun onActivityStopped(activity: Activity) = Unit
      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
      override fun onActivityDestroyed(activity: Activity) {
        allActivities.remove(activity)
      }
    }
    app.registerActivityLifecycleCallbacks(callbacks)

    // Application.startActivity from a non-Activity context requires FLAG_ACTIVITY_NEW_TASK.
    val launchIntent = Intent(intent).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    try {
      app.startActivity(launchIntent)
    } catch (t: Throwable) {
      app.unregisterActivityLifecycleCallbacks(callbacks)
      throw t
    }

    if (!resumed.await(15, TimeUnit.SECONDS)) {
      app.unregisterActivityLifecycleCallbacks(callbacks)
      error("MainActivity did not reach RESUMED within 15s")
    }
    return LaunchedActivity(activityHolder, recorder, app, callbacks, allActivities)
  }

  private fun <T> runOnMainSync(block: () -> T): T {
    var result: Result<T> = Result.failure(IllegalStateException("runOnMainSync did not produce a result"))
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      result = runCatching(block)
    }
    return result.getOrThrow()
  }

  private fun await(
    timeoutMs: Long = 5_000,
    pollMs: Long = 50,
    description: String = "condition",
    predicate: () -> Boolean
  ) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      if (runOnMainSync(predicate)) return
      Thread.sleep(pollMs)
    }
    error("Timed out after ${timeoutMs}ms waiting for $description")
  }

  private fun MainActivity.mainNavigationViewModel(): MainNavigationViewModel {
    return ViewModelProvider(this as FragmentActivity, MainNavigationViewModel.Factory())[MainNavigationViewModel::class.java]
  }

  /**
   * Wait until the latest [ConversationFragment]'s composer EditText shows [expected].
   * setDraftText is invoked off the InputReadyState/ShareOrDraftData reactive chain, so the
   * text won't be present at fragment-create time — we have to poll the rendered view.
   */
  private fun awaitComposerText(launched: LaunchedActivity, expected: String) {
    await(timeoutMs = 15_000, description = "composer shows \"$expected\"") {
      val frag = launched.recorder.latestActive() ?: return@await false
      val view = frag.view ?: return@await false
      view.findViewById<TextView>(R.id.embedded_text_editor)?.text?.toString() == expected
    }
  }

  /**
   * Wait until the latest [ConversationFragment]'s toolbar shows [expected]. Scoped through
   * R.id.conversation_title_view to avoid colliding with other R.id.title uses.
   */
  private fun awaitConversationTitle(launched: LaunchedActivity, expected: String) {
    await(timeoutMs = 15_000, description = "conversation title shows \"$expected\"") {
      val frag = launched.recorder.latestActive() ?: return@await false
      val view = frag.view ?: return@await false
      val titleHost = view.findViewById<View>(R.id.conversation_title_view) ?: return@await false
      titleHost.findViewById<TextView>(R.id.title)?.text?.toString() == expected
    }
  }

  /**
   * MainActivity hosts each tab as a different [Fragment] via Compose's `AndroidFragment`
   * (see MainActivity.kt:662-698). The user sees the content of whichever one is currently
   * attached, so a tab assertion that reads the FragmentManager is a real user-visible
   * signal — strictly stronger than reading the VM's `currentListLocation`.
   */
  private fun listFragmentClass(location: MainNavigationListLocation): Class<out Fragment> = when (location) {
    MainNavigationListLocation.CHATS -> ConversationListFragment::class.java
    MainNavigationListLocation.ARCHIVE -> ConversationListArchiveFragment::class.java
    MainNavigationListLocation.CALLS -> CallLogFragment::class.java
    MainNavigationListLocation.STORIES -> StoriesLandingFragment::class.java
  }

  private fun awaitListFragment(launched: LaunchedActivity, location: MainNavigationListLocation) {
    val expected = listFragmentClass(location)
    try {
      await(timeoutMs = 10_000, description = "${expected.simpleName} attached for $location") {
        launched.recorder.isAttached(expected)
      }
    } catch (e: IllegalStateException) {
      throw IllegalStateException("${e.message}; currently attached: ${launched.recorder.attachedNames()}", e)
    }
  }

  // endregion

  // region Types

  /**
   * Records every [ConversationFragment] added under an activity's fragment manager,
   * capturing each fragment's arguments at create-time.
   */
  private class ConversationFragmentRecorder : FragmentManager.FragmentLifecycleCallbacks() {
    val createdArgs: MutableList<ConversationArgs> = mutableListOf()
    val allCreated: MutableList<String> = mutableListOf()
    private val active: MutableList<ConversationFragment> = mutableListOf()
    private val attached: MutableList<Fragment> = mutableListOf()
    var destroyedCount: Int = 0
      private set

    /** Most-recently-added still-attached ConversationFragment, or null. Main-thread read. */
    fun latestActive(): ConversationFragment? = active.lastOrNull()

    /**
     * Exact class match (not [Class.isInstance]) — `ConversationListArchiveFragment`
     * extends `ConversationListFragment`, so an `isInstance` check for CHATS would falsely
     * pass when the archive list is attached.
     */
    fun isAttached(clazz: Class<out Fragment>): Boolean = attached.any { it::class.java == clazz }

    fun attachedNames(): List<String> = attached.map { it::class.simpleName ?: it::class.java.name }

    override fun onFragmentCreated(fm: FragmentManager, f: Fragment, savedInstanceState: android.os.Bundle?) {
      allCreated += f::class.simpleName ?: f::class.java.name
      attached += f
      if (f is ConversationFragment) {
        createdArgs += ConversationIntents.readArgsFromBundle(f.requireArguments())
        active += f
      }
    }

    override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
      attached.remove(f)
      if (f is ConversationFragment) {
        active.remove(f)
        destroyedCount++
      }
    }
  }

  private class LaunchedActivity(
    private val activityHolder: Array<MainActivity?>,
    val recorder: ConversationFragmentRecorder,
    private val app: Application,
    private val callbacks: Application.ActivityLifecycleCallbacks,
    private val allActivities: MutableList<Activity>
  ) : AutoCloseable {
    /**
     * Always returns the *latest* MainActivity instance so reads follow `recreate()`.
     */
    val activity: MainActivity get() = checkNotNull(activityHolder[0]) { "No active MainActivity" }

    /**
     * Poll until an activity of [clazz] has been created, then return it. Used to assert
     * the share-image flow's hop into MediaSelectionActivity.
     */
    fun <T : Activity> awaitActivity(clazz: Class<T>, timeoutMs: Long = 10_000): T {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() < deadline) {
        val match = synchronized(allActivities) {
          allActivities.firstOrNull { clazz.isInstance(it) }
        }
        if (match != null) return clazz.cast(match)!!
        Thread.sleep(50)
      }
      val seen = synchronized(allActivities) { allActivities.map { it::class.simpleName } }
      error("Timed out after ${timeoutMs}ms waiting for ${clazz.simpleName}; saw $seen")
    }

    fun nonMainActivities(): List<Activity> = synchronized(allActivities) {
      allActivities.filter { it !is MainActivity }.toList()
    }

    override fun close() {
      // Don't wait for looper idle — secondary activities (e.g. MediaSelectionActivity
      // opened by share processing) can keep it busy indefinitely. Finish every tracked
      // activity so subsequent tests start from a clean slate.
      val toFinish = synchronized(allActivities) { allActivities.toList() }
      if (toFinish.isNotEmpty()) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
          toFinish.forEach { it.finish() }
        }
      }
      app.unregisterActivityLifecycleCallbacks(callbacks)
    }
  }

  // endregion
}
