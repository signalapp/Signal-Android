package org.thoughtcrime.securesms.home.search

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import network.loki.messenger.databinding.ViewGlobalSearchInputBinding

class GlobalSearchInputLayout @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs),
        View.OnFocusChangeListener,
        View.OnClickListener,
        TextWatcher, TextView.OnEditorActionListener {

    var binding: ViewGlobalSearchInputBinding = ViewGlobalSearchInputBinding.inflate(LayoutInflater.from(context), this, true)

    var listener: GlobalSearchInputLayoutListener? = null

    private val _query = MutableStateFlow<CharSequence?>(null)
    val query: StateFlow<CharSequence?> = _query

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        binding.searchInput.onFocusChangeListener = this
        binding.searchInput.addTextChangedListener(this)
        binding.searchInput.setOnEditorActionListener(this)
        binding.searchCancel.setOnClickListener(this)
        binding.searchClear.setOnClickListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    override fun onFocusChange(v: View?, hasFocus: Boolean) {
        if (v === binding.searchInput) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(windowToken, 0)
            listener?.onInputFocusChanged(hasFocus)
        }
    }

    override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
        if (v === binding.searchInput && actionId == EditorInfo.IME_ACTION_SEARCH) {
            binding.searchInput.clearFocus()
            return true
        }
        return false
    }

    override fun onClick(v: View?) {
        if (v === binding.searchCancel) {
            clearSearch(true)
        } else if (v === binding.searchClear) {
            clearSearch(false)
        }
    }

    fun clearSearch(clearFocus: Boolean) {
        binding.searchInput.text = null
        if (clearFocus) {
            binding.searchInput.clearFocus()
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        _query.value = s?.toString()
    }

    interface GlobalSearchInputLayoutListener {
        fun onInputFocusChanged(hasFocus: Boolean)
    }

}