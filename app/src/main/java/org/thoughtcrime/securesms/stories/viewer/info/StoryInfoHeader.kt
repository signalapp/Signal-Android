package org.thoughtcrime.securesms.stories.viewer.info

import android.view.View
import android.widget.TextView
import android.widget.Toast
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible
import java.util.Locale

/**
 * Holds information around the sent time, received time, and file size of a given story.
 */
object StoryInfoHeader {
  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.story_info_header))
  }

  class Model(val sentMillis: Long, val receivedMillis: Long, val size: Long) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean = true

    override fun areContentsTheSame(newItem: Model): Boolean {
      return newItem.sentMillis == sentMillis && newItem.receivedMillis == receivedMillis && newItem.size == size
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val sentView: TextView = itemView.findViewById(R.id.story_info_view_sent_label)
    private val recvView: TextView = itemView.findViewById(R.id.story_info_view_received_label)
    private val sizeView: TextView = itemView.findViewById(R.id.story_info_view_file_size_label)
    private val sizeHeader: TextView = itemView.findViewById(R.id.story_info_view_file_size_heading)

    override fun bind(model: Model) {
      if (model.sentMillis > 0L) {
        sentView.visible = true
        sentView.text = DateUtils.getTimeString(context, Locale.getDefault(), model.sentMillis)
        itemView.setOnLongClickListener {
          Util.copyToClipboard(context, model.sentMillis.toString())
          Toast.makeText(context, R.string.MyStoriesFragment__copied_sent_timestamp_to_clipboard, Toast.LENGTH_SHORT).show()
          true
        }
      } else {
        sentView.visible = false
        itemView.setOnLongClickListener(null)
      }

      if (model.receivedMillis > 0L) {
        recvView.visible = true
        recvView.text = DateUtils.getTimeString(context, Locale.getDefault(), model.receivedMillis)
      } else {
        recvView.visible = false
      }

      if (model.size > 0L) {
        sizeView.visible = true
        sizeHeader.visible = true
        sizeView.text = Util.getPrettyFileSize(model.size)
      } else {
        sizeView.visible = false
        sizeHeader.visible = false
      }
    }
  }
}
