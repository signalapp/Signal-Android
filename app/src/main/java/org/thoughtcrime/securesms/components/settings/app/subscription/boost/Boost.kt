package org.thoughtcrime.securesms.components.settings.app.subscription.boost

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.animation.doOnEnd
import com.google.android.material.button.MaterialButton
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.text.AfterTextChanged
import org.thoughtcrime.securesms.util.visible
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency

/**
 * A Signal Boost is a one-time ephemeral show of support. Each boost level
 * can unlock a corresponding badge for a time determined by the server.
 */
data class Boost(
  val price: FiatMoney
) {

  /**
   * A heading containing a 96dp rendering of the boost's badge.
   */
  class HeadingModel(
    val boostBadge: Badge
  ) : PreferenceModel<HeadingModel>() {
    override fun areItemsTheSame(newItem: HeadingModel): Boolean = true

    override fun areContentsTheSame(newItem: HeadingModel): Boolean {
      return super.areContentsTheSame(newItem) && newItem.boostBadge == boostBadge
    }
  }

  class LoadingModel : PreferenceModel<LoadingModel>() {
    override fun areItemsTheSame(newItem: LoadingModel): Boolean = true
  }

  class LoadingViewHolder(itemView: View) : MappingViewHolder<LoadingModel>(itemView) {

    private val animator: Animator = AnimatorSet().apply {
      val fadeTo25Animator = ObjectAnimator.ofFloat(itemView, "alpha", 0.8f, 0.25f).apply {
        duration = 1000L
      }

      val fadeTo80Animator = ObjectAnimator.ofFloat(itemView, "alpha", 0.25f, 0.8f).apply {
        duration = 300L
      }

      playSequentially(fadeTo25Animator, fadeTo80Animator)
      doOnEnd {
        if (itemView.isAttachedToWindow) {
          start()
        }
      }
    }

    override fun bind(model: LoadingModel) {
    }

    override fun onAttachedToWindow() {
      if (animator.isStarted) {
        animator.resume()
      } else {
        animator.start()
      }
    }

    override fun onDetachedFromWindow() {
      animator.pause()
    }
  }

  /**
   * A widget that allows a user to select from six different amounts, or enter a custom amount.
   */
  class SelectionModel(
    val boosts: List<Boost>,
    val selectedBoost: Boost?,
    val currency: Currency,
    override val isEnabled: Boolean,
    val onBoostClick: (View, Boost) -> Unit,
    val isCustomAmountFocused: Boolean,
    val onCustomAmountChanged: (String) -> Unit,
    val onCustomAmountFocusChanged: (Boolean) -> Unit,
  ) : PreferenceModel<SelectionModel>(isEnabled = isEnabled) {
    override fun areItemsTheSame(newItem: SelectionModel): Boolean = true

    override fun areContentsTheSame(newItem: SelectionModel): Boolean {
      return super.areContentsTheSame(newItem) &&
        newItem.boosts == boosts &&
        newItem.selectedBoost == selectedBoost &&
        newItem.currency == currency &&
        newItem.isCustomAmountFocused == isCustomAmountFocused
    }
  }

  private class SelectionViewHolder(itemView: View) : MappingViewHolder<SelectionModel>(itemView) {

    private val boost1: MaterialButton = itemView.findViewById(R.id.boost_1)
    private val boost2: MaterialButton = itemView.findViewById(R.id.boost_2)
    private val boost3: MaterialButton = itemView.findViewById(R.id.boost_3)
    private val boost4: MaterialButton = itemView.findViewById(R.id.boost_4)
    private val boost5: MaterialButton = itemView.findViewById(R.id.boost_5)
    private val boost6: MaterialButton = itemView.findViewById(R.id.boost_6)
    private val currencyStart: TextView = itemView.findViewById(R.id.boost_currency_start)
    private val currencyEnd: TextView = itemView.findViewById(R.id.boost_currency_end)
    private val custom: AppCompatEditText = itemView.findViewById(R.id.boost_custom)

    private var textChangedWatcher: TextWatcher? = null

    private val boostButtons: List<MaterialButton>
      get() {
        return if (ViewUtil.isLtr(context)) {
          listOf(boost1, boost2, boost3, boost4, boost5, boost6)
        } else {
          listOf(boost3, boost2, boost1, boost6, boost5, boost4)
        }
      }

    init {
      custom.filters = emptyArray()
    }

    override fun bind(model: SelectionModel) {
      itemView.isEnabled = model.isEnabled

      model.boosts.zip(boostButtons).forEach { (boost, button) ->
        button.isSelected = boost == model.selectedBoost && !model.isCustomAmountFocused
        button.text = FiatMoneyUtil.format(
          context.resources,
          boost.price,
          FiatMoneyUtil
            .formatOptions()
            .trimZerosAfterDecimal()
        )
        button.setOnClickListener {
          model.onBoostClick(it, boost)
          custom.clearFocus()
        }
      }

      currencyStart.text = model.currency.symbol
      currencyEnd.text = model.currency.symbol

      if (model.currency.defaultFractionDigits > 0) {
        custom.inputType = EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_FLAG_DECIMAL
        custom.filters = arrayOf(DecimalPlacesFilter(model.currency.defaultFractionDigits, custom.keyListener as DigitsKeyListener))
      } else {
        custom.inputType = EditorInfo.TYPE_CLASS_NUMBER
        custom.filters = arrayOf()
      }

      custom.removeTextChangedListener(textChangedWatcher)

      textChangedWatcher = AfterTextChanged {
        model.onCustomAmountChanged(it.toString())
      }

      custom.addTextChangedListener(textChangedWatcher)
      custom.setText("")

      custom.setOnFocusChangeListener { _, hasFocus ->
        val isCurrencyAtFrontOfNumber = currencyIsAtFrontOfNumber(model.currency)

        currencyStart.visible = isCurrencyAtFrontOfNumber && hasFocus
        currencyEnd.visible = !isCurrencyAtFrontOfNumber && hasFocus

        custom.gravity = if (hasFocus) (Gravity.START or Gravity.CENTER_VERTICAL) else Gravity.CENTER
        model.onCustomAmountFocusChanged(hasFocus)
      }

      if (model.isCustomAmountFocused && !custom.hasFocus()) {
        ViewUtil.focusAndShowKeyboard(custom)
      } else if (!model.isCustomAmountFocused && custom.hasFocus()) {
        ViewUtil.hideKeyboard(context, custom)
        custom.clearFocus()
      }
    }

    private fun currencyIsAtFrontOfNumber(currency: Currency): Boolean {
      val formatter = NumberFormat.getCurrencyInstance().apply {
        this.currency = currency
      }
      return formatter.format(1).startsWith(currency.symbol)
    }
  }

  /**
   * Restricts output of the given Digits filter to the given number of decimal places.
   */
  private class DecimalPlacesFilter(private val decimalPlaces: Int, private val digitsKeyListener: DigitsKeyListener) : InputFilter {

    private val decimalSeparator = DecimalFormatSymbols.getInstance().decimalSeparator
    private val builder = SpannableStringBuilder()

    override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
      val keyListenerResult = digitsKeyListener.filter(source, start, end, dest, dstart, dend)

      builder.clear()
      builder.clearSpans()

      val toInsert = keyListenerResult ?: source.substring(start, end)

      builder.append(dest)

      if (dstart == dend) {
        builder.insert(dstart, toInsert)
      } else {
        builder.replace(dstart, dend, toInsert)
      }

      val separatorIndex = builder.indexOf(decimalSeparator)
      return if (separatorIndex > -1) {
        val suffix = builder.split(decimalSeparator).last()
        if (suffix.length > decimalPlaces) {
          dest.subSequence(dstart, dend)
        } else {
          null
        }
      } else {
        null
      }
    }
  }

  private class HeadingViewHolder(itemView: View) : MappingViewHolder<HeadingModel>(itemView) {

    private val badgeImageView: BadgeImageView = itemView as BadgeImageView

    override fun bind(model: HeadingModel) {
      badgeImageView.setBadge(model.boostBadge)
    }
  }

  companion object {
    fun register(adapter: MappingAdapter) {
      adapter.registerFactory(SelectionModel::class.java, MappingAdapter.LayoutFactory({ SelectionViewHolder(it) }, R.layout.boost_preference))
      adapter.registerFactory(HeadingModel::class.java, MappingAdapter.LayoutFactory({ HeadingViewHolder(it) }, R.layout.boost_preview_preference))
      adapter.registerFactory(LoadingModel::class.java, MappingAdapter.LayoutFactory({ LoadingViewHolder(it) }, R.layout.boost_loading_preference))
    }
  }
}
