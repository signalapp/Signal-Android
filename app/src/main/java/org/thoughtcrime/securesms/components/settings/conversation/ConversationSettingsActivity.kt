package org.thoughtcrime.securesms.components.settings.conversation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLSettingsActivity
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.ParcelableGroupId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.DynamicConversationSettingsTheme
import org.thoughtcrime.securesms.util.DynamicTheme

open class ConversationSettingsActivity : DSLSettingsActivity(), ConversationSettingsFragment.Callback {

  override val dynamicTheme: DynamicTheme = DynamicConversationSettingsTheme()

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    ActivityCompat.postponeEnterTransition(this)
    setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
    super.onCreate(savedInstanceState, ready)
  }

  override fun onContentWillRender() {
    ActivityCompat.startPostponedEnterTransition(this)
  }

  override fun finish() {
    super.finish()
    overridePendingTransition(0, R.anim.slide_fade_to_bottom)
  }

  companion object {

    @JvmStatic
    fun createTransitionBundle(context: Context, avatar: View, windowContent: View): Bundle? {
      return if (context is Activity) {
        ActivityOptionsCompat.makeSceneTransitionAnimation(
          context,
          Pair.create(avatar, "avatar"),
          Pair.create(windowContent, "window_content")
        ).toBundle()
      } else {
        null
      }
    }

    @JvmStatic
    fun createTransitionBundle(context: Context, avatar: View): Bundle? {
      return if (context is Activity) {
        ActivityOptionsCompat.makeSceneTransitionAnimation(
          context,
          avatar,
          "avatar"
        ).toBundle()
      } else {
        null
      }
    }

    @JvmStatic
    fun forGroup(context: Context, groupId: GroupId): Intent {
      val startBundle = ConversationSettingsFragmentArgs.Builder(null, ParcelableGroupId.from(groupId), null)
        .build()
        .toBundle()

      return getIntent(context)
        .putExtra(ARG_START_BUNDLE, startBundle)
    }

    @JvmStatic
    fun forRecipient(context: Context, recipientId: RecipientId): Intent {
      val startBundle = ConversationSettingsFragmentArgs.Builder(recipientId, null, null)
        .build()
        .toBundle()

      return getIntent(context)
        .putExtra(ARG_START_BUNDLE, startBundle)
    }

    @JvmStatic
    fun forCall(context: Context, callPeer: Recipient, callMessageIds: LongArray): Intent {
      val startBundleBuilder = if (callPeer.isGroup) {
        ConversationSettingsFragmentArgs.Builder(null, ParcelableGroupId.from(callPeer.requireGroupId()), callMessageIds)
      } else {
        ConversationSettingsFragmentArgs.Builder(callPeer.id, null, callMessageIds)
      }

      val startBundle = startBundleBuilder.build().toBundle()

      return getIntent(context)
        .setClass(context, CallInfoActivity::class.java)
        .putExtra(ARG_START_BUNDLE, startBundle)
    }

    private fun getIntent(context: Context): Intent {
      return Intent(context, ConversationSettingsActivity::class.java)
        .putExtra(ARG_NAV_GRAPH, R.navigation.conversation_settings)
    }
  }
}
