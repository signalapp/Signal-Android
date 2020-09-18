package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import network.loki.messenger.R
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*

object AvatarPlaceholderGenerator {
    private val tmpFloatArray = FloatArray(3)

    private const val EMPTY_LABEL = "0";

    fun getSHA512(input:String):String{
        val md: MessageDigest = MessageDigest.getInstance("SHA-512")
        val messageDigest = md.digest(input.toByteArray())

        // Convert byte array into signum representation
        val no = BigInteger(1, messageDigest)

        // Convert message digest into hex value
        var hashtext: String = no.toString(16)

        // Add preceding 0s to make it 32 bit
        while (hashtext.length < 32) {
            hashtext = "0$hashtext"
        }

        // return the HashText
        return hashtext
    }

    fun generate(context: Context, pixelSize: Int, hashString: String, displayName: String?): BitmapDrawable {
        val hash: Long
        if (hashString.length >= 12 && hashString.matches(Regex("^[0-9A-Fa-f]+\$"))) {
            hash = AvatarPlaceholderGenerator.getSHA512(hashString).substring(0 until 12).toLong(16)
        } else {
            hash = 0
        }

        // Do not cache color array, it may be different depends on the current theme.
        val colorArray = context.resources.getIntArray(R.array.profile_picture_placeholder_colors)
        val colorPrimary = colorArray[(hash % colorArray.size).toInt()]

        val labelText = when {
            !TextUtils.isEmpty(displayName) -> extractLabel(displayName!!.capitalize())
            !TextUtils.isEmpty(hashString) -> extractLabel(hashString)
            else -> EMPTY_LABEL
        }

        val bitmap = Bitmap.createBitmap(pixelSize, pixelSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background/frame
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, pixelSize.toFloat(),
                colorPrimary,
                colorPrimary,
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

    private fun extractLabel(content: String): String {
        var content = content.trim()
        if (content.isEmpty()) return EMPTY_LABEL
        return if (content.length > 2 && content.startsWith("05")) {
            content[2].toString().toUpperCase(Locale.ROOT)
        } else {
            content.first().toString().toUpperCase(Locale.ROOT)
        }
    }
}