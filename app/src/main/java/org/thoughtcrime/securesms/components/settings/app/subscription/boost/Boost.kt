package org.thoughtcrime.securesms.components.settings.app.subscription.boost

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Typeface
import android.os.Build
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.View
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.animation.doOnEnd
import androidx.core.text.isDigitsOnly
import com.google.android.material.button.MaterialButton
import org.signal.core.util.BidiUtil
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible
import java.lang.Integer.min
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.regex.Pattern

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
    val minimumAmount: FiatMoney,
    val isCustomAmountFocused: Boolean,
    val isCustomAmountTooSmall: Boolean,
    val onCustomAmountChanged: (String) -> Unit,
    val onCustomAmountFocusChanged: (Boolean) -> Unit
  ) : PreferenceModel<SelectionModel>(isEnabled = isEnabled) {
    override fun areItemsTheSame(newItem: SelectionModel): Boolean = true

    override fun areContentsTheSame(newItem: SelectionModel): Boolean {
      return super.areContentsTheSame(newItem) &&
        newItem.boosts == boosts &&
        newItem.selectedBoost == selectedBoost &&
        newItem.currency == currency &&
        newItem.isCustomAmountFocused == isCustomAmountFocused &&
        newItem.isCustomAmountTooSmall == isCustomAmountTooSmall &&
        newItem.minimumAmount.amount == minimumAmount.amount &&
        newItem.minimumAmount.currency == minimumAmount.currency
    }
  }

  private class SelectionViewHolder(itemView: View) : MappingViewHolder<SelectionModel>(itemView) {

    private val boost1: MaterialButton = itemView.findViewById(R.id.boost_1)
    private val boost2: MaterialButton = itemView.findViewById(R.id.boost_2)
    private val boost3: MaterialButton = itemView.findViewById(R.id.boost_3)
    private val boost4: MaterialButton = itemView.findViewById(R.id.boost_4)
    private val boost5: MaterialButton = itemView.findViewById(R.id.boost_5)
    private val boost6: MaterialButton = itemView.findViewById(R.id.boost_6)
    private val custom: AppCompatEditText = itemView.findViewById(R.id.boost_custom)
    private val error: TextView = itemView.findViewById(R.id.boost_custom_too_small)

    private val boostButtons: List<MaterialButton>
      get() {
        return if (ViewUtil.isLtr(context)) {
          listOf(boost1, boost2, boost3, boost4, boost5, boost6)
        } else {
          listOf(boost3, boost2, boost1, boost6, boost5, boost4)
        }
      }

    private var filter: MoneyFilter? = null

    init {
      custom.filters = emptyArray()
    }

    override fun bind(model: SelectionModel) {
      itemView.isEnabled = model.isEnabled

      error.text = context.getString(
        R.string.Boost__the_minimum_amount_you_can_donate_is_s,
        FiatMoneyUtil.format(
          context.resources,
          model.minimumAmount,
          FiatMoneyUtil.formatOptions().trimZerosAfterDecimal()
        )
      )

      error.visible = model.isCustomAmountTooSmall

      model.boosts.zip(boostButtons).forEach { (boost, button) ->
        val isSelected = boost == model.selectedBoost && !model.isCustomAmountFocused
        button.isSelected = isSelected
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

        if (Build.VERSION.SDK_INT >= 28) {
          val weight = if (isSelected) 500 else 400
          button.typeface = Typeface.create(null, weight, false)
        } else {
          button.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
      }

      if (filter == null || filter?.currency != model.currency) {
        custom.removeTextChangedListener(filter)

        filter = MoneyFilter(model.currency, custom) {
          model.onCustomAmountChanged(it)
        }

        custom.keyListener = filter
        custom.addTextChangedListener(filter)

        custom.setText("")
      }

      custom.isSelected = model.isCustomAmountFocused
      custom.setOnFocusChangeListener { _, hasFocus ->
        model.onCustomAmountFocusChanged(hasFocus)
      }

      if (model.isCustomAmountFocused && !custom.hasFocus()) {
        ViewUtil.focusAndShowKeyboard(custom)
      } else if (!model.isCustomAmountFocused && custom.hasFocus()) {
        ViewUtil.hideKeyboard(context, custom)
        custom.clearFocus()
      }
    }
  }

  private class HeadingViewHolder(itemView: View) : MappingViewHolder<HeadingModel>(itemView) {

    private val badgeImageView: BadgeImageView = itemView as BadgeImageView

    override fun bind(model: HeadingModel) {
      badgeImageView.setBadge(model.boostBadge)
    }
  }

  @VisibleForTesting
  class MoneyFilter(val currency: Currency, private val text: AppCompatEditText? = null, private val onCustomAmountChanged: (String) -> Unit = {}) : DigitsKeyListener(false, true), TextWatcher {

    val separator = DecimalFormatSymbols.getInstance().decimalSeparator
    val separatorCount = min(1, currency.defaultFractionDigits)
    val symbol: String = currency.getSymbol(Locale.getDefault())

    /**
     * From Character.isDigit:
     *
     * * '\u0030' through '\u0039', ISO-LATIN-1 digits ('0' through '9')
     * * '\u0660' through '\u0669', Arabic-Indic digits
     * * '\u06F0' through '\u06F9', Extended Arabic-Indic digits
     * * '\u0966' through '\u096F', Devanagari digits
     * * '\uFF10' through '\uFF19', Fullwidth digits
     */
    val digitsGroup: String = "[\\u0030-\\u0039]|[\\u0660-\\u0669]|[\\u06F0-\\u06F9]|[\\u0966-\\u096F]|[\\uFF10-\\uFF19]"
    val zeros: String = "\\u0030|\\u0660|\\u06F0|\\u0966|\\uFF10"

    val pattern: Pattern = "($digitsGroup)*([$separator]){0,$separatorCount}($digitsGroup){0,${currency.defaultFractionDigits}}".toPattern()
    val symbolPattern: Regex = """\s*${Regex.escape(symbol)}\s*""".toRegex()
    val leadingZeroesPattern: Regex = """^($zeros)*""".toRegex()

    override fun filter(
      source: CharSequence,
      start: Int,
      end: Int,
      dest: Spanned,
      dstart: Int,
      dend: Int
    ): CharSequence? {
      val result = dest.subSequence(0, dstart).toString() + source.toString() + dest.subSequence(dend, dest.length)
      val resultWithoutCurrencyPrefix = BidiUtil.stripBidiIndicator(result.removePrefix(symbol).removeSuffix(symbol).trim())

      if (resultWithoutCurrencyPrefix.length == 1 && !resultWithoutCurrencyPrefix.isDigitsOnly() && resultWithoutCurrencyPrefix != separator.toString()) {
        return dest.subSequence(dstart, dend)
      }

      val matcher = pattern.matcher(resultWithoutCurrencyPrefix)

      if (!matcher.matches()) {
        return dest.subSequence(dstart, dend)
      }

      return null
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(s: Editable?) {
      if (s.isNullOrEmpty()) return

      val hasSymbol = s.startsWith(symbol) || s.endsWith(symbol)
      if (hasSymbol && symbolPattern.matchEntire(s.toString()) != null) {
        s.clear()
      } else if (!hasSymbol) {
        val formatter = NumberFormat.getCurrencyInstance()
        formatter.currency = currency

        if (s.contains(separator)) {
          formatter.minimumFractionDigits = s.split(separator).last().length
        } else {
          formatter.minimumFractionDigits = 0
        }

        formatter.maximumFractionDigits = currency.defaultFractionDigits
        formatter.isGroupingUsed = false

        val value = s.toString().toDoubleOrNull()

        if (value != null) {
          val formatted = formatter.format(value)

          modifyEditable {
            s.replace(0, s.length, formatted)
            if (formatted.endsWith(symbol)) {
              val result: MatchResult? = symbolPattern.find(formatted)
              if (result != null && result.range.first < s.length) {
                text?.setSelection(result.range.first)
              }
            }
          }
        }
      }

      val withoutSymbol = s.removePrefix(symbol).removeSuffix(symbol).trim().toString()
      val withoutLeadingZeroes: String = try {
        NumberFormat.getInstance().apply {
          isGroupingUsed = false

          if (s.contains(separator)) {
            minimumFractionDigits = s.split(separator).last().length
          }
        }.format(withoutSymbol.toBigDecimal()) + (if (withoutSymbol.endsWith(separator)) separator else "")
      } catch (e: NumberFormatException) {
        withoutSymbol
      }

      if (withoutSymbol != withoutLeadingZeroes) {
        modifyEditable {
          val start = s.indexOf(withoutSymbol)
          s.replace(start, start + withoutSymbol.length, withoutLeadingZeroes)
        }
      }

      onCustomAmountChanged(s.removePrefix(symbol).removeSuffix(symbol).trim().toString())
    }

    private fun modifyEditable(modification: () -> Unit) {
      text?.removeTextChangedListener(this)
      text?.keyListener = null

      modification()

      text?.addTextChangedListener(this)
      text?.keyListener = this
    }
  }

  companion object {
    fun register(adapter: MappingAdapter) {
      adapter.registerFactory(SelectionModel::class.java, LayoutFactory({ SelectionViewHolder(it) }, R.layout.boost_preference))
      adapter.registerFactory(HeadingModel::class.java, LayoutFactory({ HeadingViewHolder(it) }, R.layout.boost_preview_preference))
      adapter.registerFactory(LoadingModel::class.java, LayoutFactory({ LoadingViewHolder(it) }, R.layout.boost_loading_preference))
    }
  }
}
