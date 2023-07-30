package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import com.google.android.material.card.MaterialCardView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.visible

/**
 * A small card with a circular progress indicator in it. Usable in place
 * of a ProgressDialog, which is deprecated.
 *
 * Remember to add this as the last UI element in your XML hierarchy so it'll
 * draw over top of other elements.
 */
class ProgressCard @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : MaterialCardView(context, attrs) {

  private val title: TextView

  init {
    inflate(context, R.layout.progress_card, this)

    title = findViewById(R.id.progress_card_text)

    if (attrs != null) {
      context.withStyledAttributes(attrs, R.styleable.ProgressCard) {
        setTitleText(getString(R.styleable.ProgressCard_progressCardTitle))
      }
    } else {
      setTitleText(null)
    }
  }

  fun setTitleText(titleText: String?) {
    title.visible = !titleText.isNullOrEmpty()
    title.text = titleText
  }
}
