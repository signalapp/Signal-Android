package org.thoughtcrime.securesms.components.settings

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible

class DSLSettingsAdapter : MappingAdapter() {
  init {
    registerFactory(ClickPreference::class.java, LayoutFactory(::ClickPreferenceViewHolder, R.layout.dsl_preference_item))
    registerFactory(TextPreference::class.java, LayoutFactory(::TextPreferenceViewHolder, R.layout.dsl_preference_item))
    registerFactory(RadioListPreference::class.java, LayoutFactory(::RadioListPreferenceViewHolder, R.layout.dsl_preference_item))
    registerFactory(MultiSelectListPreference::class.java, LayoutFactory(::MultiSelectListPreferenceViewHolder, R.layout.dsl_preference_item))
    registerFactory(ExternalLinkPreference::class.java, LayoutFactory(::ExternalLinkPreferenceViewHolder, R.layout.dsl_preference_item))
    registerFactory(DividerPreference::class.java, LayoutFactory(::DividerPreferenceViewHolder, R.layout.dsl_divider_item))
    registerFactory(SectionHeaderPreference::class.java, LayoutFactory(::SectionHeaderPreferenceViewHolder, R.layout.dsl_section_header))
    registerFactory(SwitchPreference::class.java, LayoutFactory(::SwitchPreferenceViewHolder, R.layout.dsl_switch_preference_item))
    registerFactory(RadioPreference::class.java, LayoutFactory(::RadioPreferenceViewHolder, R.layout.dsl_radio_preference_item))
  }
}

abstract class PreferenceViewHolder<T : PreferenceModel<T>>(itemView: View) : MappingViewHolder<T>(itemView) {
   val iconView: ImageView = itemView.findViewById(R.id.icon)
   val titleView: TextView = itemView.findViewById(R.id.title)
   val summaryView: TextView = itemView.findViewById(R.id.summary)

   val res = context.resources
   val mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height)
   val mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height)

   val mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize)
   val mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize)

   val mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x)
   val mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x)

  open lateinit var focusedView:View
  @SuppressLint("LogNotSignal")
  @CallSuper
  override fun bind(model: T) {
    setIsRecyclable(false)
    listOf(itemView, titleView, summaryView).forEach {
      it.isEnabled = model.isEnabled
    }

    val icon = model.icon?.resolve(context)
    iconView.setImageDrawable(icon)
    iconView.visible = icon != null

    itemView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
      titleView.isSelected = true
      startFocusAnimation(titleView, hasFocus)
      if (hasFocus) {
        focusedView = itemView
      }
    }

    val title = model.title?.resolve(context)
    if (title != null) {
      titleView.text = model.title?.resolve(context)
      titleView.visibility = View.VISIBLE
    } else {
      titleView.visibility = View.GONE
    }

    val summary = model.summary?.resolve(context)
    if (summary != null) {
      summaryView.text = summary
      summaryView.visibility = View.GONE

      val spans = (summaryView.text as? Spanned)?.getSpans(0, summaryView.text.length, ClickableSpan::class.java)
      if (spans?.isEmpty() == false) {
        summaryView.movementMethod = LinkMovementMethod.getInstance()
      } else {
        summaryView.movementMethod = null
      }
      summaryView.visibility = if (model.isVisiable) View.VISIBLE else View.GONE
    } else {
      summaryView.visibility = View.GONE
      summaryView.movementMethod = null
    }
  }

  @SuppressLint("LogNotSignal")
  open fun startFocusAnimation(v: View, focused: Boolean) {
    val va: ValueAnimator
    va = if (focused) {
      ValueAnimator.ofFloat(0f, 1f)
    } else {
      ValueAnimator.ofFloat(1f, 0f)
    }
    va.addUpdateListener { valueAnimator ->
      val scale = valueAnimator.animatedValue as Float
      val height = (mFocusHeight - mNormalHeight).toFloat() * scale + mNormalHeight.toFloat()
      val textsize = (mFocusTextSize - mNormalTextSize).toFloat() * scale + mNormalTextSize
      val padding = mNormalPaddingX.toFloat() - (mNormalPaddingX - mFocusPaddingX).toFloat() * scale
      val alpha = (0x81.toFloat() + (0xff - 0x81).toFloat() * scale).toInt()
      val color = alpha * 0x1000000 + 0xffffff
      (v as TextView).textSize = textsize
      (v as TextView).setTextColor(color)
      v.setPadding(
        padding.toInt(), v.getPaddingTop(),
        v.getPaddingRight(), v.getPaddingBottom()
      )
      v.getLayoutParams().height = height.toInt()
    }
    val FastOutLinearInInterpolator = FastOutLinearInInterpolator()
    va.interpolator = FastOutLinearInInterpolator
    va.duration = 270
    va.start()
  }

}

class TextPreferenceViewHolder(itemView: View) : PreferenceViewHolder<TextPreference>(itemView)

class ClickPreferenceViewHolder(itemView: View) : PreferenceViewHolder<ClickPreference>(itemView) {
  override fun bind(model: ClickPreference) {
    super.bind(model)
    itemView.setOnClickListener { model.onClick() }
  }
}

class RadioListPreferenceViewHolder(itemView: View) : PreferenceViewHolder<RadioListPreference>(itemView) {
  override fun bind(model: RadioListPreference) {
    super.bind(model)

    summaryView.visibility = View.GONE
    summaryView.text = model.listItems[model.selected]

    itemView.setOnClickListener {
      var selection = -1
      val builder = MaterialAlertDialogBuilder(context)
        .setTitle(model.dialogTitle.resolve(context))
        .setSingleChoiceItems(model.listItems, model.selected) { dialog, which ->
          if (model.confirmAction) {
            selection = which
          } else {
            model.onSelected(which)
            dialog.dismiss()
          }
        }

      if (model.confirmAction) {
        builder
          .setPositiveButton(android.R.string.ok) { dialog, _ ->
            model.onSelected(selection)
            dialog.dismiss()
          }
          .setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.dismiss()
          }
          .show()
      } else {
        builder.show()
      }
    }
  }
}

class MultiSelectListPreferenceViewHolder(itemView: View) : PreferenceViewHolder<MultiSelectListPreference>(itemView) {
  override fun bind(model: MultiSelectListPreference) {
    super.bind(model)

    summaryView.visibility = View.GONE
    val summaryText = model.selected
      .mapIndexed { index, isChecked -> if (isChecked) model.listItems[index] else null }
      .filterNotNull()
      .joinToString(", ")

    if (summaryText.isEmpty()) {
      summaryView.setText(R.string.preferences__none)
    } else {
      summaryView.text = summaryText
    }

    val selected = model.selected.copyOf()

    itemView.setOnClickListener {
      MaterialAlertDialogBuilder(context)
        .setTitle(model.title.resolve(context))
        .setMultiChoiceItems(model.listItems, selected) { _, _, _ ->
          // Intentionally empty
        }
        .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
        .setPositiveButton(android.R.string.ok) { d, _ ->
          model.onSelected(selected)
          d.dismiss()
        }
        .show()
    }
  }
}

class SwitchPreferenceViewHolder(itemView: View) : PreferenceViewHolder<SwitchPreference>(itemView) {


  private val switchWidget: SwitchMaterial = itemView.findViewById(R.id.switch_widget)
  val off: String = "OFF"
  val on: String = "ON"

  override fun bind(model: SwitchPreference) {
    super.bind(model)
    switchWidget.isEnabled = model.isEnabled

    var switchState: String = if (model.isChecked) on else off
    titleView.text = titleView.text.toString() + " " + switchState
    switchWidget
    itemView.setOnClickListener {
      model.onClick()
    }
  }
}

class RadioPreferenceViewHolder(itemView: View) : PreferenceViewHolder<RadioPreference>(itemView) {

  private val radioButton: RadioButton = itemView.findViewById(R.id.radio_widget)

  override fun bind(model: RadioPreference) {
    super.bind(model)
    radioButton.isChecked = model.isChecked
    itemView.setOnClickListener {
      model.onClick()
    }
  }
}

class ExternalLinkPreferenceViewHolder(itemView: View) : PreferenceViewHolder<ExternalLinkPreference>(itemView) {
  override fun bind(model: ExternalLinkPreference) {
    super.bind(model)

    val externalLinkIcon = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_open_20))
    externalLinkIcon.setBounds(0, 0, ViewUtil.dpToPx(20), ViewUtil.dpToPx(20))

    if (ViewUtil.isLtr(itemView)) {
      titleView.setCompoundDrawables(null, null, externalLinkIcon, null)
    } else {
      titleView.setCompoundDrawables(externalLinkIcon, null, null, null)
    }

    itemView.setOnClickListener { CommunicationActions.openBrowserLink(itemView.context, itemView.context.getString(model.linkId)) }
  }
}

class DividerPreferenceViewHolder(itemView: View) : MappingViewHolder<DividerPreference>(itemView) {
  override fun bind(model: DividerPreference) = Unit
}

class SectionHeaderPreferenceViewHolder(itemView: View) : MappingViewHolder<SectionHeaderPreference>(itemView) {

  private val sectionHeader: TextView = itemView.findViewById(R.id.section_header)

  override fun bind(model: SectionHeaderPreference) {
    sectionHeader.text = model.title.resolve(context)
  }
}
