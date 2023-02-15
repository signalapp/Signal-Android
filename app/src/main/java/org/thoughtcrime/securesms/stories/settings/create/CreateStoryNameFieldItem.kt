package org.thoughtcrime.securesms.stories.settings.create

import android.view.View
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout
import org.signal.core.util.EditTextUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

/**
 * Field that user can utilize to enter the name of a new distribution list.
 */
object CreateStoryNameFieldItem {

  fun register(adapter: MappingAdapter, onTextChanged: (CharSequence) -> Unit) {
    adapter.registerFactory(Model::class.java, LayoutFactory({ ViewHolder(it, onTextChanged) }, R.layout.stories_create_story_name_field_item))
  }

  class Model(
    val body: CharSequence,
    val error: CharSequence?
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean = true
    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) && body == newItem.body && error == newItem.error
    }
  }

  class ViewHolder(itemView: View, onTextChanged: (CharSequence) -> Unit) : MappingViewHolder<Model>(itemView) {

    private val editTextWrapper: TextInputLayout = itemView.findViewById(R.id.edit_text_wrapper)
    private val editText: EditText = itemView.findViewById<EditText>(R.id.edit_text).apply {
      EditTextUtil.addGraphemeClusterLimitFilter(this, 23)
      doAfterTextChanged {
        if (it != null) {
          onTextChanged(it)
        }
      }
    }

    override fun bind(model: Model) {
      if (model.body.toString() != editText.text.toString()) {
        editText.setText(model.body)
      }

      editTextWrapper.error = model.error
    }
  }
}
