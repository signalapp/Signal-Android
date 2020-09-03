package org.thoughtcrime.securesms.loki.todo

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import network.loki.messenger.R
import java.util.*

object AvatarPlaceholderGenerator {

    private const val EMPTY_LABEL = "0";

    private val tmpFloatArray = FloatArray(3)

    fun generate(context: Context, pixelSize: Int, hashString: String, displayName: String?): BitmapDrawable {
        //TODO That should be replaced with a proper hash extraction code.
        val hash: Long
        val hexRegex = Regex("^[0-9A-Fa-f]+\$")
        if (hashString.length >= 12 && hashString.matches(hexRegex)) {
            hash = hashString.substring(0 until 12).toLong(16)
        } else {
            hash = hashString.toLong(16)
        }

        // Do not cache color array, it may be different depends on the current theme.
        val colorArray = context.resources.getIntArray(R.array.user_pic_placeholder_primary)
        val colorPrimary = colorArray[(hash % colorArray.size).toInt()]
        val colorSecondary = changeColorHueBy(colorPrimary, 12f)

        val labelText = when {
            !TextUtils.isEmpty(displayName) -> extractLabel(displayName!!)
            !TextUtils.isEmpty(hashString) -> extractLabel(hashString)
            else -> EMPTY_LABEL
        }

        val bitmap = Bitmap.createBitmap(pixelSize, pixelSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background/frame
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, pixelSize.toFloat(),
                colorPrimary,
                colorSecondary,
                Shader.TileMode.REPEAT)
        canvas.drawCircle(pixelSize.toFloat() / 2, pixelSize.toFloat() / 2, pixelSize.toFloat() / 2, paint)

        // Draw text
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = pixelSize * 0.5f
        textPaint.color = Color.WHITE
        val areaRect = Rect(0, 0, pixelSize, pixelSize)
        val textBounds = RectF(areaRect)
        textBounds.right = textPaint.measureText(labelText)
        textBounds.bottom = textPaint.descent() - textPaint.ascent()
        textBounds.left += (areaRect.width() - textBounds.right) * 0.5f
        textBounds.top += (areaRect.height() - textBounds.bottom) * 0.5f
        canvas.drawText(labelText, textBounds.left, textBounds.top - textPaint.ascent(), textPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    @ColorInt
    private fun changeColorHueBy(@ColorInt color: Int, hueDelta: Float): Int {
        val hslColor = tmpFloatArray
        ColorUtils.colorToHSL(color, hslColor)
        hslColor[0] = (hslColor[0] + hueDelta) % 360f
        return ColorUtils.HSLToColor(hslColor)
    }

    private fun extractLabel(content: String): String {
        var content = content.trim()
        if (content.isEmpty()) return EMPTY_LABEL

        return content.first().toString().toUpperCase(Locale.ROOT)
    }
}