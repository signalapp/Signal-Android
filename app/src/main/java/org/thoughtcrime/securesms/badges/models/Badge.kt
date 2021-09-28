package org.thoughtcrime.securesms.badges.models

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Parcelable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.parcelize.Parcelize
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.Badges.selectable
import org.thoughtcrime.securesms.badges.glide.BadgeSpriteTransformation
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder
import org.thoughtcrime.securesms.util.ThemeUtil
import java.security.MessageDigest

typealias OnBadgeClicked = (Badge, Boolean) -> Unit

/**
 * A Badge that can be collected and displayed by a user.
 */
@Parcelize
data class Badge(
  val id: String,
  val category: Category,
  val name: String,
  val description: String,
  val imageUrl: Uri,
  val imageDensity: String,
  val expirationTimestamp: Long,
  val visible: Boolean,
) : Parcelable, Key {

  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
    messageDigest.update(id.toByteArray(Key.CHARSET))
    messageDigest.update(imageUrl.toString().toByteArray(Key.CHARSET))
    messageDigest.update(imageDensity.toByteArray(Key.CHARSET))
  }

  fun resolveDescription(shortName: String): String {
    return description.replace("{short_name}", shortName)
  }

  class EmptyModel : PreferenceModel<EmptyModel>() {
    override fun areItemsTheSame(newItem: EmptyModel): Boolean = true
  }

  class EmptyViewHolder(itemView: View) : MappingViewHolder<EmptyModel>(itemView) {

    private val name: TextView = itemView.findViewById(R.id.name)

    init {
      itemView.isEnabled = false
      itemView.isFocusable = false
      itemView.isClickable = false
      itemView.visibility = View.INVISIBLE

      name.text = " "
    }

    override fun bind(model: EmptyModel) = Unit
  }

  class Model(
    val badge: Badge,
    val isSelected: Boolean = false
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return newItem.badge.id == badge.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) && badge == newItem.badge && isSelected == newItem.isSelected
    }

    override fun getChangePayload(newItem: Model): Any? {
      return if (badge == newItem.badge && isSelected != newItem.isSelected) {
        SELECTION_CHANGED
      } else {
        null
      }
    }
  }

  class ViewHolder(itemView: View, private val onBadgeClicked: OnBadgeClicked) : MappingViewHolder<Model>(itemView) {

    private val badge: ImageView = itemView.findViewById(R.id.badge)
    private val name: TextView = itemView.findViewById(R.id.name)
    private val target = Target(badge)

    override fun bind(model: Model) {
      itemView.setOnClickListener {
        onBadgeClicked(model.badge, model.isSelected)
      }

      if (payload.isNotEmpty()) {
        if (model.isSelected) {
          target.animateToStart()
        } else {
          target.animateToEnd()
        }
        return
      }

      GlideApp.with(badge)
        .load(model.badge)
        .downsample(DownsampleStrategy.NONE)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .transform(BadgeSpriteTransformation(BadgeSpriteTransformation.Size.XLARGE, model.badge.imageDensity, ThemeUtil.isDarkTheme(context)))
        .into(target)

      if (model.isSelected) {
        target.setAnimationToStart()
      } else {
        target.setAnimationToEnd()
      }

      name.text = model.badge.name
    }
  }

  enum class Category(val code: String) {
    Donor("donor"),
    Other("other"),
    Testing("testing"); // Will be removed before final release

    companion object {
      fun fromCode(code: String): Category {
        return when (code) {
          "donor" -> Donor
          "testing" -> Testing
          else -> Other
        }
      }
    }
  }

  private class Target(view: ImageView) : CustomViewTarget<ImageView, Drawable>(view) {

    private val animator: BadgeAnimator = BadgeAnimator()

    override fun onLoadFailed(errorDrawable: Drawable?) {
      view.setImageDrawable(errorDrawable)
    }

    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
      val drawable = resource.selectable(
        DimensionUnit.DP.toPixels(2.5f),
        ContextCompat.getColor(view.context, R.color.signal_inverse_primary),
        animator
      )

      view.setImageDrawable(drawable)
    }

    override fun onResourceCleared(placeholder: Drawable?) {
      view.setImageDrawable(placeholder)
    }

    fun setAnimationToStart() {
      animator.setState(BadgeAnimator.State.START)
      view.drawable?.invalidateSelf()
    }

    fun setAnimationToEnd() {
      animator.setState(BadgeAnimator.State.END)
      view.drawable?.invalidateSelf()
    }

    fun animateToStart() {
      animator.setState(BadgeAnimator.State.REVERSE)
      view.drawable?.invalidateSelf()
    }

    fun animateToEnd() {
      animator.setState(BadgeAnimator.State.FORWARD)
      view.drawable?.invalidateSelf()
    }
  }

  companion object {
    private val SELECTION_CHANGED = Any()

    fun register(mappingAdapter: MappingAdapter, onBadgeClicked: OnBadgeClicked) {
      mappingAdapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it, onBadgeClicked) }, R.layout.badge_preference_view))
      mappingAdapter.registerFactory(EmptyModel::class.java, MappingAdapter.LayoutFactory({ EmptyViewHolder(it) }, R.layout.badge_preference_view))
    }
  }

  @Parcelize
  data class ImageSet(
    val ldpi: String,
    val mdpi: String,
    val hdpi: String,
    val xhdpi: String,
    val xxhdpi: String,
    val xxxhdpi: String
  ) : Parcelable {
    fun getByDensity(density: String): String {
      return when (density) {
        "ldpi" -> ldpi
        "mdpi" -> mdpi
        "hdpi" -> hdpi
        "xhdpi" -> xhdpi
        "xxhdpi" -> xxhdpi
        "xxxhdpi" -> xxxhdpi
        else -> xhdpi
      }
    }
  }
}
