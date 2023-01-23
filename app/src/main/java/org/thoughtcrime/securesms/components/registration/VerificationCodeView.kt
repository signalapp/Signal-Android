package org.thoughtcrime.securesms.components.registration

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.google.android.material.textfield.TextInputLayout
import org.thoughtcrime.securesms.R

class VerificationCodeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0) :
  FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
  private val containers: MutableList<TextInputLayout> = ArrayList(6)
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
  }

  fun setOnCompleteListener(listener: OnCodeEnteredListener?) {
    this.listener = listener
  }

  fun append(digit: Int) {
    if (index >= containers.size) return
    containers[index++].editText?.setText(digit.toString())

    if (index == containers.size) {
      listener?.onCodeComplete(containers.joinToString("") { it.editText?.text.toString() })
    }
  }

  fun delete() {
    if (index <= 0) return
    containers[--index].editText?.setText("")
  }

  fun clear() {
    if (index != 0) {
      containers.forEach { it.editText?.setText("") }
      index = 0
    }
  }

  interface OnCodeEnteredListener {
    fun onCodeComplete(code: String)
  }
}
