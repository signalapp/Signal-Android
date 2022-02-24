package org.thoughtcrime.securesms.stories.viewer.page

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.colors.AvatarColor

class TestFragment : Fragment(R.layout.test_fragment) {
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    (view as AppCompatImageView).setImageDrawable(ColorDrawable(AvatarColor.random().colorInt()))
  }
}
