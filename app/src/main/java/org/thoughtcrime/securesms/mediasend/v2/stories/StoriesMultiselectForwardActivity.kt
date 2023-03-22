package org.thoughtcrime.securesms.mediasend.v2.stories

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.Glide
import kotlinx.parcelize.Parcelize
import org.signal.core.util.getParcelableArrayListExtraCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardActivity
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.visible

class StoriesMultiselectForwardActivity : MultiselectForwardActivity() {

  companion object {
    private const val PREVIEW_MEDIA = "preview_media"
  }

  override val contentViewId: Int = R.layout.stories_multiselect_forward_activity

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    val preview1View: ImageView = findViewById(R.id.preview_media_1)
    val preview2View: ImageView = findViewById(R.id.preview_media_2)
    val previewMedia: List<Uri> = intent.getParcelableArrayListExtraCompat(PREVIEW_MEDIA, Uri::class.java)!!

    preview1View.visible = previewMedia.isNotEmpty()
    preview2View.visible = previewMedia.size > 1

    if (previewMedia.isNotEmpty()) {
      Glide.with(this)
        .load(DecryptableStreamUriLoader.DecryptableUri(previewMedia.first()))
        .into(preview1View)
    }

    if (previewMedia.size > 1) {
      Glide.with(this).load(DecryptableStreamUriLoader.DecryptableUri(previewMedia[1])).into(preview2View)
    }
  }

  override fun getSearchConfiguration(fragmentManager: FragmentManager, contactSearchState: ContactSearchState): ContactSearchConfiguration? {
    return ContactSearchConfiguration.build {
      query = contactSearchState.query

      addSection(
        ContactSearchConfiguration.Section.Stories(
          groupStories = contactSearchState.groupStories,
          includeHeader = true,
          headerAction = Stories.getHeaderAction(fragmentManager)
        )
      )
    }
  }

  @Suppress("WrongViewCast")
  override fun getContainer(): ViewGroup {
    return findViewById(R.id.content)
  }

  class SelectionContract : ActivityResultContract<Args, List<ContactSearchKey.RecipientSearchKey>>() {

    private val multiselectContract = MultiselectForwardActivity.SelectionContract()

    override fun createIntent(context: Context, input: Args): Intent {
      return multiselectContract.createIntent(context, input.multiselectForwardFragmentArgs)
        .setClass(context, StoriesMultiselectForwardActivity::class.java)
        .putExtra(PREVIEW_MEDIA, ArrayList(input.previews))
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<ContactSearchKey.RecipientSearchKey> {
      return multiselectContract.parseResult(resultCode, intent)
    }
  }

  @Parcelize
  class Args(
    val multiselectForwardFragmentArgs: MultiselectForwardFragmentArgs,
    val previews: List<Uri>
  ) : Parcelable
}
