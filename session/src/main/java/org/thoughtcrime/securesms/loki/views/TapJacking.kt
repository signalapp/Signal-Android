package org.thoughtcrime.securesms.loki.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

private fun isPotentialTapJack(event: MotionEvent): Boolean {
    if (event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED == MotionEvent.FLAG_WINDOW_IS_OBSCURED) { return true }
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
        (event.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED == MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)) { return true }
    return false
}

open class TapJackingProofButton : androidx.appcompat.widget.AppCompatButton {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onFilterTouchEventForSecurity(event: MotionEvent): Boolean {
        if (isPotentialTapJack(event)) {
            Toast.makeText(context, "Interaction temporarily disabled for security purposes.", Toast.LENGTH_LONG).show()
            return false
        } else {
            return super.onFilterTouchEventForSecurity(event)
        }
    }
}

open class TapJackingProofEditText : androidx.appcompat.widget.AppCompatEditText {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onFilterTouchEventForSecurity(event: MotionEvent): Boolean {
        if (isPotentialTapJack(event)) {
            Toast.makeText(context, "Interaction temporarily disabled for security purposes.", Toast.LENGTH_LONG).show()
            return false
        } else {
            return super.onFilterTouchEventForSecurity(event)
        }
    }
}

open class TapJackingProofTextView : androidx.appcompat.widget.AppCompatTextView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onFilterTouchEventForSecurity(event: MotionEvent): Boolean {
        if (isPotentialTapJack(event)) {
            Toast.makeText(context, "Interaction temporarily disabled for security purposes.", Toast.LENGTH_LONG).show()
            return false
        } else {
            return super.onFilterTouchEventForSecurity(event)
        }
    }
}

open class TapJackingProofLinearLayout : LinearLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onFilterTouchEventForSecurity(event: MotionEvent): Boolean {
        if (isPotentialTapJack(event)) {
            Toast.makeText(context, "Interaction temporarily disabled for security purposes.", Toast.LENGTH_LONG).show()
            return false
        } else {
            return super.onFilterTouchEventForSecurity(event)
        }
    }
}