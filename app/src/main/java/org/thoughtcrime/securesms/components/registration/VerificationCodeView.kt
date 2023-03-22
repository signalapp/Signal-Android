package org.thoughtcrime.securesms.components.registration

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.widget.FrameLayout
import com.google.android.material.textfield.TextInputLayout
import org.thoughtcrime.securesms.R

class VerificationCodeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0) :
  FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
  private val containers: MutableList<TextInputLayout> = ArrayList(6)
  private val textWatcher = PasteTextWatcher()
  private var listener: OnCodeEnteredListener? = null
  private var index = 0

  init {
    inflate(context, R.layout.verification_code_view, this)
    containers.add(findViewById(R.id.container_zero))
    containers.add(findViewById(R.id.container_one))
    containers.add(findViewById(R.id.container_two))
    containers.add(findViewById(R.id.container_three))
    containers.add(findViewById(R.id.container_four))
    containers.add(findViewById(R.id.container_five))

    containers.forEach { it.editText?.showSoftInputOnFocus = false }
    containers.forEach { it.editText?.addTextChangedListener(textWatcher) }
  }

  fun setOnCompleteListener(listener: OnCodeEnteredListener?) {
    this.listener = listener
  }

  fun append(digit: Int) {
    if (index >= containers.size) return
    containers[index++].editText?.setText(digit.toString())

    if (index == containers.size) {
      listener?.onCodeComplete(containers.joinToString("") { it.editText?.text.toString() })
      return
    }

    containers[index].editText?.requestFocus()
  }

  fun delete() {
    if (index < 0) return
    val editText = if (index == 0) containers[index].editText else containers[--index].editText
    editText?.setText("")
    containers[index].editText?.requestFocus()
  }

  fun clear() {
    if (index != 0) {
      containers.forEach { it.editText?.setText("") }
      index = 0
      containers[index].editText?.requestFocus()
    }
  }

  interface OnCodeEnteredListener {
    fun onCodeComplete(code: String)
  }

  inner class PasteTextWatcher : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
      if (s == null) {
        return
      }

      if (s.length > 1) {
        val enteredText = s.toList()
        enteredText.forEach {
          val castInt = it.digitToIntOrNull()
          if (castInt == null) {
            s.clear()
            return@forEach
          } else {
            append(castInt)
          }
        }
      }
    }
  }
}
