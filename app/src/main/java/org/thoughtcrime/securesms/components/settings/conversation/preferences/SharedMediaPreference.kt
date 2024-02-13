package org.thoughtcrime.securesms.components.settings.conversation.preferences

import android.view.View
import com.bumptech.glide.Glide
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ThreadPhotoRailView
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

/**
 * Renders the shared media photo rail.
 */
object SharedMediaPreference {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.conversation_settings_shared_media))
  }

  class Model(
    val mediaRecords: List<MediaTable.MediaRecord>,
    val mediaIds: List<Long>,
    val onMediaRecordClick: (View, MediaTable.MediaRecord, Boolean) -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return true
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) &&
        mediaIds == newItem.mediaIds
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val rail: ThreadPhotoRailView = itemView.findViewById(R.id.rail_view)

    override fun bind(model: Model) {
      rail.setMediaRecords(Glide.with(rail), model.mediaRecords)
      rail.setListener { v, m ->
        model.onMediaRecordClick(v, m, ViewUtil.isLtr(rail))
      }
    }
  }
}
