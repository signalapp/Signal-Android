package org.thoughtcrime.securesms.stories.landing

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import org.thoughtcrime.securesms.MainNavigator
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.stories.dialogs.StoryContextMenu
import org.thoughtcrime.securesms.stories.my.MyStoriesActivity
import org.thoughtcrime.securesms.stories.settings.StorySettingsActivity
import org.thoughtcrime.securesms.stories.tabs.ConversationListTabsViewModel
import org.thoughtcrime.securesms.stories.viewer.StoryViewerActivity
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.visible

/**
 * The "landing page" for Stories.
 */
class StoriesLandingFragment :
  DSLSettingsFragment(
    layoutId = R.layout.stories_landing_fragment,
    menuId = R.menu.story_landing_menu,
    titleId = R.string.ConversationListTabs__stories
  ),
  MainNavigator.BackHandler {

  private lateinit var emptyNotice: View
  private lateinit var cameraFab: View

  private val lifecycleDisposable = LifecycleDisposable()

  private val viewModel: StoriesLandingViewModel by viewModels(
    factoryProducer = {
      StoriesLandingViewModel.Factory(StoriesLandingRepository(requireContext()))
    }
  )

  private val tabsViewModel: ConversationListTabsViewModel by viewModels(ownerProducer = { requireActivity() })

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    StoriesLandingItem.register(adapter)
    MyStoriesItem.register(adapter)
    ExpandHeader.register(adapter)

    lifecycleDisposable.bindTo(viewLifecycleOwner)
    emptyNotice = requireView().findViewById(R.id.empty_notice)
    cameraFab = requireView().findViewById(R.id.camera_fab)

    cameraFab.setOnClickListener {
      Permissions.with(this)
        .request(Manifest.permission.CAMERA)
        .ifNecessary()
        .withRationaleDialog(getString(R.string.ConversationActivity_to_capture_photos_and_video_allow_signal_access_to_the_camera), R.drawable.ic_camera_24)
        .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_the_camera_permission_to_take_photos_or_video))
        .onAllGranted { startActivity(MediaSelectionActivity.camera(requireContext())) }
        .onAnyDenied { Toast.makeText(requireContext(), R.string.ConversationActivity_signal_needs_camera_permissions_to_take_photos_or_video, Toast.LENGTH_LONG).show() }
        .execute()
    }

    viewModel.state.observe(viewLifecycleOwner) {
      adapter.submitList(getConfiguration(it).toMappingModelList())
      emptyNotice.visible = it.hasNoStories
    }
  }

  private fun getConfiguration(state: StoriesLandingState): DSLConfiguration {
    return configure {
      val (stories, hidden) = state.storiesLandingItems.map {
        createStoryLandingItem(it)
      }.partition {
        !it.data.isHidden
      }

      if (state.displayMyStoryItem) {
        customPref(
          MyStoriesItem.Model(
            onClick = {
              cameraFab.performClick()
            }
          )
        )
      }

      stories.forEach { item ->
        customPref(item)
      }

      if (hidden.isNotEmpty()) {
        customPref(
          ExpandHeader.Model(
            title = DSLSettingsText.from(R.string.StoriesLandingFragment__hidden_stories),
            isExpanded = state.isHiddenContentVisible,
            onClick = { viewModel.setHiddenContentVisible(it) }
          )
        )
      }

      if (state.isHiddenContentVisible) {
        hidden.forEach { item ->
          customPref(item)
        }
      }
    }
  }

  private fun createStoryLandingItem(data: StoriesLandingItemData): StoriesLandingItem.Model {
    return StoriesLandingItem.Model(
      data = data,
      onRowClick = {
        if (it.data.storyRecipient.isMyStory) {
          startActivity(Intent(requireContext(), MyStoriesActivity::class.java))
        } else {
          startActivity(StoryViewerActivity.createIntent(requireContext(), it.data.storyRecipient.id))
        }
      },
      onForwardStory = {
        MultiselectForwardFragmentArgs.create(requireContext(), it.data.primaryStory.multiselectCollection.toSet()) { args ->
          MultiselectForwardFragment.showBottomSheet(childFragmentManager, args)
        }
      },
      onGoToChat = {
        startActivity(ConversationIntents.createBuilder(requireContext(), it.data.storyRecipient.id, -1L).build())
      },
      onHideStory = {
        if (!it.data.isHidden) {
          handleHideStory(it)
        } else {
          lifecycleDisposable += viewModel.setHideStory(it.data.storyRecipient, it.data.isHidden).subscribe()
        }
      },
      onShareStory = {
        StoryContextMenu.share(this@StoriesLandingFragment, it.data.primaryStory as MediaMmsMessageRecord)
      },
      onSave = {
        StoryContextMenu.save(requireContext(), it.data.primaryStory.messageRecord)
      },
      onDeleteStory = {
        handleDeleteStory(it)
      }
    )
  }

  private fun handleDeleteStory(model: StoriesLandingItem.Model) {
    lifecycleDisposable += StoryContextMenu.delete(requireContext(), setOf(model.data.primaryStory.messageRecord)).subscribe()
  }

  private fun handleHideStory(model: StoriesLandingItem.Model) {
    MaterialAlertDialogBuilder(requireContext(), R.style.Signal_ThemeOverlay_Dialog_Rounded)
      .setTitle(R.string.StoriesLandingFragment__hide_story)
      .setMessage(getString(R.string.StoriesLandingFragment__new_story_updates, model.data.storyRecipient.getShortDisplayName(requireContext())))
      .setPositiveButton(R.string.StoriesLandingFragment__hide) { _, _ ->
        viewModel.setHideStory(model.data.storyRecipient, true).subscribe {
          Snackbar.make(cameraFab, R.string.StoriesLandingFragment__story_hidden, Snackbar.LENGTH_SHORT)
            .setTextColor(Color.WHITE)
            .setAnchorView(cameraFab)
            .setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE)
            .show()
        }
      }
      .setNegativeButton(android.R.string.cancel) { _, _ -> }
      .show()
  }

  override fun onBackPressed(): Boolean {
    tabsViewModel.onChatsSelected()
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return if (item.itemId == R.id.action_settings) {
      startActivity(StorySettingsActivity.getIntent(requireContext()))
      true
    } else {
      false
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }
}
