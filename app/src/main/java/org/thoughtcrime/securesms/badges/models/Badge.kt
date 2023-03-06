package org.thoughtcrime.securesms.badges.models

import android.animation.ObjectAnimator
import android.net.Uri
import android.os.Parcelable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.glide.BadgeSpriteTransformation
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.util.ThemeUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import java.security.MessageDigest

typealias OnBadgeClicked = (Badge, Boolean, Boolean) -> Unit

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
  val duration: Long
) : Parcelable, Key {

  fun isExpired(): Boolean = expirationTimestamp < System.currentTimeMillis() && expirationTimestamp > 0
  fun isBoost(): Boolean = id == BOOST_BADGE_ID
  fun isGift(): Boolean = id == GIFT_BADGE_ID
  fun isSubscription(): Boolean = !isBoost() && !isGift()

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
    val isSelected: Boolean = false,
    val isFaded: Boolean = false
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return newItem.badge.id == badge.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) &&
        badge == newItem.badge &&
        isSelected == newItem.isSelected &&
        isFaded == newItem.isFaded
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

    private val check: ImageView = itemView.findViewById(R.id.checkmark)
    private val badge: ImageView = itemView.findViewById(R.id.badge)
    private val name: TextView = itemView.findViewById(R.id.name)

    private var checkAnimator: ObjectAnimator? = null

    init {
      check.isSelected = true
    }

    override fun bind(model: Model) {
      itemView.setOnClickListener {
        onBadgeClicked(model.badge, model.isSelected, model.isFaded)
      }

      checkAnimator?.cancel()
      if (payload.isNotEmpty()) {
        checkAnimator = if (model.isSelected) {
          ObjectAnimator.ofFloat(check, "alpha", 1f)
        } else {
          ObjectAnimator.ofFloat(check, "alpha", 0f)
        }
        checkAnimator?.start()
        return
      }

      badge.alpha = if (model.badge.isExpired() || model.isFaded) 0.5f else 1f

      GlideApp.with(badge)
        .load(model.badge)
        .downsample(DownsampleStrategy.NONE)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .transform(
          BadgeSpriteTransformation(BadgeSpriteTransformation.Size.BADGE_64, model.badge.imageDensity, ThemeUtil.isDarkTheme(context))
        )
        .into(badge)

      if (model.isSelected) {
        check.alpha = 1f
      } else {
        check.alpha = 0f
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

  companion object {
    const val BOOST_BADGE_ID = "BOOST"
    const val GIFT_BADGE_ID = "GIFT"

    private val SELECTION_CHANGED = Any()

    fun register(mappingAdapter: MappingAdapter, onBadgeClicked: OnBadgeClicked) {
      mappingAdapter.registerFactory(Model::class.java, LayoutFactory({ ViewHolder(it, onBadgeClicked) }, R.layout.badge_preference_view))
      mappingAdapter.registerFactory(EmptyModel::class.java, LayoutFactory({ EmptyViewHolder(it) }, R.layout.badge_preference_view))
    }
  }
}
