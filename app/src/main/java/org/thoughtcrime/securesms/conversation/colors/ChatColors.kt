package org.thoughtcrime.securesms.conversation.colors

import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.ColorInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import com.google.common.base.Objects
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.components.RotatableGradientDrawable
import org.thoughtcrime.securesms.database.model.databaseprotos.ChatColor
import org.thoughtcrime.securesms.util.customizeOnDraw
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * ChatColors represent how to render the avatar and bubbles in a given context.
 *
 * @param id             The identifier for this chat color. It is either BuiltIn, NotSet, or Custom(long)
 * @param linearGradient The LinearGradient to render. Null if this is for a single color.
 * @param singleColor    The single color to render. Null if this is for a linear gradient.
 */
@Parcelize
class ChatColors(
  val id: Id,
  val linearGradient: LinearGradient?,
  val singleColor: Int?
) : Parcelable {

  fun isGradient(): Boolean = linearGradient != null
  fun isSolid(): Boolean = singleColor != null

  /**
   * Returns the Drawable to render the linear gradient, or null if this ChatColors is a single color.
   */
  val chatBubbleMask: Drawable
    get() {
      return when {
        linearGradient != null -> {
          RotatableGradientDrawable(
            linearGradient.degrees,
            linearGradient.colors,
            linearGradient.positions
          )
        }
        else -> {
          ColorDrawable(asSingleColor())
        }
      }
    }

  /**
   * Returns the [Brush] to render the linear gradient, or a solid-color brush if this ChatColors is a single color.
   */
  val chatBubbleBrush: Brush
    get() {
      return when {
        linearGradient != null -> ChatBubbleGradientBrush(
          degrees = linearGradient.degrees,
          colors = linearGradient.colors.map { ComposeColor(it) },
          colorStops = linearGradient.positions.toList()
        )
        else -> SolidColor(ComposeColor(asSingleColor()))
      }
    }

  /**
   * Returns the ColorFilter to apply to a conversation bubble or other relevant piece of UI.
   */
  @IgnoredOnParcel
  val chatBubbleColorFilter: ColorFilter = PorterDuffColorFilter(Color.TRANSPARENT, PorterDuff.Mode.SRC_IN)

  @ColorInt
  fun asSingleColor(): Int {
    if (singleColor != null) {
      return singleColor
    }

    if (linearGradient != null) {
      return linearGradient.colors.last()
    }

    throw AssertionError()
  }

  fun serialize(): ChatColor {
    val builder: ChatColor.Builder = ChatColor.Builder()

    if (linearGradient != null) {
      val gradientBuilder = ChatColor.LinearGradient.Builder()

      gradientBuilder.rotation = linearGradient.degrees
      gradientBuilder.colors = linearGradient.colors.toList()
      gradientBuilder.positions = linearGradient.positions.toList()

      builder.linearGradient(gradientBuilder.build())
    }

    if (singleColor != null) {
      builder.singleColor(ChatColor.SingleColor.Builder().color(singleColor).build())
    }

    return builder.build()
  }

  fun getColors(): IntArray {
    return linearGradient?.colors ?: if (singleColor != null) {
      intArrayOf(singleColor)
    } else {
      throw AssertionError()
    }
  }

  fun getDegrees(): Float {
    return linearGradient?.degrees ?: 180f
  }

  fun asCircle(): Drawable {
    val toWrap: Drawable = chatBubbleMask
    val path = Path()

    return toWrap.customizeOnDraw { wrapped, canvas ->
      canvas.save()
      path.rewind()
      path.addCircle(
        wrapped.bounds.exactCenterX(),
        wrapped.bounds.exactCenterY(),
        min(wrapped.bounds.exactCenterX(), wrapped.bounds.exactCenterY()),
        Path.Direction.CW
      )
      canvas.clipPath(path)
      wrapped.draw(canvas)
      canvas.restore()
    }
  }

  fun withId(id: Id): ChatColors = ChatColors(id, linearGradient, singleColor)

  fun matchesWithoutId(other: ChatColors): Boolean {
    if (linearGradient != other.linearGradient) return false
    if (singleColor != other.singleColor) return false

    return true
  }

  override fun equals(other: Any?): Boolean {
    val otherChatColors: ChatColors = (other as? ChatColors) ?: return false

    if (id != otherChatColors.id) return false
    if (linearGradient != otherChatColors.linearGradient) return false
    if (singleColor != otherChatColors.singleColor) return false

    return true
  }

  override fun hashCode(): Int {
    return Objects.hashCode(linearGradient, singleColor, id)
  }

  companion object {
    /**
     * Converts a network chat color into our domain model object.
     *
     * Has additional protection to ensure that a rogue client can't send us a malformed
     * color.
     */
    @JvmStatic
    fun forChatColor(id: Id, chatColor: ChatColor): ChatColors {
      if (chatColor.singleColor == null && chatColor.linearGradient == null) {
        return ChatColorsPalette.Bubbles.ULTRAMARINE
      }

      return if (chatColor.linearGradient != null) {
        val linearGradient = LinearGradient(
          chatColor.linearGradient.rotation,
          chatColor.linearGradient.colors.toIntArray(),
          chatColor.linearGradient.positions.toFloatArray()
        )

        forGradient(id, linearGradient)
      } else if (chatColor.singleColor != null) {
        val singleColor = chatColor.singleColor.color

        forColor(id, singleColor)
      } else {
        ChatColorsPalette.Bubbles.ULTRAMARINE
      }
    }

    @JvmStatic
    fun forGradient(id: Id, linearGradient: LinearGradient): ChatColors = ChatColors(id, linearGradient, null)

    @JvmStatic
    fun forColor(id: Id, @ColorInt color: Int): ChatColors = ChatColors(id, null, color)
  }

  sealed class Id(val longValue: Long) : Parcelable {
    /**
     * Represents user selection of 'auto'.
     */
    object Auto : Id(-2)

    /**
     * Represents a built in color.
     */
    object BuiltIn : Id(-1)

    /**
     * Represents an unsaved or un-set option.
     */
    object NotSet : Id(0)

    /**
     * Represents a custom created ChatColors.
     */
    class Custom internal constructor(id: Long) : Id(id)

    override fun equals(other: Any?): Boolean {
      return longValue == (other as? Id)?.longValue
    }

    override fun hashCode(): Int {
      return Objects.hashCode(longValue)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
      dest.writeLong(longValue)
    }

    override fun describeContents(): Int = 0

    companion object {
      @JvmStatic
      fun forLongValue(longValue: Long): Id {
        return when (longValue) {
          -2L -> Auto
          -1L -> BuiltIn
          0L -> NotSet
          else -> Custom(longValue)
        }
      }

      @JvmField
      val CREATOR = object : Parcelable.Creator<Id> {
        override fun createFromParcel(parcel: Parcel): Id {
          return forLongValue(parcel.readLong())
        }

        override fun newArray(size: Int): Array<Id?> {
          return arrayOfNulls(size)
        }
      }
    }
  }

  @Parcelize
  data class LinearGradient(
    val degrees: Float,
    val colors: IntArray,
    val positions: FloatArray
  ) : Parcelable {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as LinearGradient

      if (!colors.contentEquals(other.colors)) return false
      if (!positions.contentEquals(other.positions)) return false

      return true
    }

    override fun hashCode(): Int {
      var result = colors.contentHashCode()
      result = 31 * result + positions.contentHashCode()
      return result
    }
  }
}

/**
 * [ShaderBrush] equivalent of [RotatableGradientDrawable], matching its geometry so the two render identically.
 */
private class ChatBubbleGradientBrush(
  private val degrees: Float,
  private val colors: List<ComposeColor>,
  private val colorStops: List<Float>
) : ShaderBrush() {
  override fun createShader(size: Size): Shader {
    val radians = Math.toRadians(degrees + DEGREE_OFFSET)
    val cos = cos(radians)
    val sin = sin(radians)

    val halfWidth = size.width / 2.0
    val halfHeight = size.height / 2.0

    // Half-extents of the axis-aligned bounds of the rotated box, matching RotatableGradientDrawable.fillRect.
    val fillHalfWidth = max(halfWidth, halfWidth * abs(cos) + halfHeight * abs(sin))
    val fillHalfHeight = max(halfHeight, halfWidth * abs(sin) + halfHeight * abs(cos))

    val center = Offset(size.width / 2f, size.height / 2f)
    val axis = Offset(
      x = (fillHalfWidth * cos - fillHalfHeight * sin).toFloat(),
      y = (fillHalfWidth * sin + fillHalfHeight * cos).toFloat()
    )

    return LinearGradientShader(
      from = center - axis,
      to = center + axis,
      colors = colors,
      colorStops = colorStops,
      tileMode = TileMode.Clamp
    )
  }

  companion object {
    /** Mirrors [RotatableGradientDrawable]'s offset that puts 0f at the bottom of the surface. */
    private const val DEGREE_OFFSET = 225.0
  }
}
