package org.thoughtcrime.securesms.avatar.vector

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.SimpleColorFilter
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarBundler
import org.thoughtcrime.securesms.avatar.AvatarColorItem
import org.thoughtcrime.securesms.avatar.Avatars
import org.thoughtcrime.securesms.components.recyclerview.GridDividerDecoration
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

/**
 * Fragment to create an avatar based off a default vector.
 */
class VectorAvatarCreationFragment : Fragment(R.layout.vector_avatar_creation_fragment) {

  private val viewModel: VectorAvatarCreationViewModel by viewModels(factoryProducer = this::createFactory)

  private fun createFactory(): VectorAvatarCreationViewModel.Factory {
    val args = VectorAvatarCreationFragmentArgs.fromBundle(requireArguments())
    val vectorBundle = args.vectorAvatar

    return VectorAvatarCreationViewModel.Factory(AvatarBundler.extractVector(vectorBundle))
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar: Toolbar = view.findViewById(R.id.vector_avatar_creation_toolbar)
    val recycler: RecyclerView = view.findViewById(R.id.vector_avatar_creation_recycler)
    val doneButton: View = view.findViewById(R.id.vector_avatar_creation_done)
    val preview: ImageView = view.findViewById(R.id.vector_avatar_creation_image)

    val adapter = MappingAdapter()
    recycler.adapter = adapter
    recycler.addItemDecoration(GridDividerDecoration(4, ViewUtil.dpToPx(16)))
    AvatarColorItem.registerViewHolder(adapter) {
      viewModel.setColor(it)
    }

    viewModel.state.observe(viewLifecycleOwner) { state ->
      preview.background.colorFilter = SimpleColorFilter(state.currentAvatar.color.backgroundColor)
      preview.setImageResource(requireNotNull(Avatars.getDrawableResource(state.currentAvatar.key)))
      adapter.submitList(state.colors().map { AvatarColorItem.Model(it) })
    }

    toolbar.setNavigationOnClickListener { Navigation.findNavController(view).popBackStack() }
    doneButton.setOnClickListener {
      setFragmentResult(REQUEST_KEY_VECTOR, AvatarBundler.bundleVector(viewModel.getCurrentAvatar()))
      Navigation.findNavController(it).popBackStack()
    }
  }

  companion object {
    const val REQUEST_KEY_VECTOR = "org.thoughtcrime.securesms.avatar.text.VECTOR"
  }
}
