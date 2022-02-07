package org.thoughtcrime.securesms.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewSeparatorBinding
import org.thoughtcrime.securesms.util.toPx
import org.session.libsession.utilities.ThemeUtil

class LabeledSeparatorView : RelativeLayout {

    private lateinit var binding: ViewSeparatorBinding
    private val path = Path()

    private val paint: Paint by lazy {
        val result = Paint()
        result.style = Paint.Style.STROKE
        result.color = ThemeUtil.getThemedColor(context, R.attr.dividerHorizontal)
        result.strokeWidth = toPx(1, resources).toFloat()
        result.isAntiAlias = true
        result
    }

    // region Lifecycle
    constructor(context: Context) : super(context) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        setUpViewHierarchy()
    }

    private fun setUpViewHierarchy() {
        binding = ViewSeparatorBinding.inflate(LayoutInflater.from(context))
        val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(binding.root, layoutParams)
        setWillNotDraw(false)
    }
    // endregion

    // region Updating
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        val hMargin = toPx(16, resources).toFloat()
        path.reset()
        path.moveTo(0.0f, h / 2)
        path.lineTo(binding.titleTextView.left - hMargin, h / 2)
        path.addRoundRect(binding.titleTextView.left - hMargin, toPx(1, resources).toFloat(), binding.titleTextView.right + hMargin, h - toPx(1, resources).toFloat(), h / 2, h / 2, Path.Direction.CCW)
        path.moveTo(binding.titleTextView.right + hMargin, h / 2)
        path.lineTo(w, h / 2)
        path.close()
        c.drawPath(path, paint)
    }
    // endregion
}