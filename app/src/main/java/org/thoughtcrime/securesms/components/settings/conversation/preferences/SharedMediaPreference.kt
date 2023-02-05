package org.thoughtcrime.securesms.components.settings.conversation.preferences

import android.database.Cursor
import android.view.View
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ThreadPhotoRailView
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.mms.GlideApp
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
    val mediaCursor: Cursor,
    val mediaIds: List<Long>,
    val onMediaRecordClick: (MediaTable.MediaRecord, Boolean) -> Unit
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
      rail.setCursor(GlideApp.with(rail), model.mediaCursor)
      rail.setListener {
        model.onMediaRecordClick(it, ViewUtil.isLtr(rail))
      }
    }
  }
}
