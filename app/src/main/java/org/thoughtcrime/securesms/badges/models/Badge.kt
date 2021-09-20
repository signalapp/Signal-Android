package org.thoughtcrime.securesms.badges.models

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.load.Key
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.Badges.selectable
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder
import java.security.MessageDigest

typealias OnBadgeClicked = (Badge, Boolean) -> Unit

/**
 * A Badge that can be collected and displayed by a user.
 */
data class Badge(
  val id: String,
  val category: Category,
  val imageUrl: Uri,
  val name: String,
  val description: String,
  val expirationTimestamp: Long,
  val visible: Boolean
) : Parcelable, Key {

  constructor(parcel: Parcel) : this(
    requireNotNull(parcel.readString()),
    Category.fromCode(requireNotNull(parcel.readString())),
    requireNotNull(parcel.readParcelable(Uri::class.java.classLoader)),
    requireNotNull(parcel.readString()),
    requireNotNull(parcel.readString()),
    parcel.readLong(),
    parcel.readByte() == 1.toByte()
  )

  override fun describeContents(): Int {
    return 0
  }

  override fun writeToParcel(parcel: Parcel, flags: Int) {
    parcel.writeString(id)
    parcel.writeString(category.code)
    parcel.writeParcelable(imageUrl, flags)
    parcel.writeString(name)
    parcel.writeString(description)
    parcel.writeLong(expirationTimestamp)
    parcel.writeByte(if (visible) 1 else 0)
  }

  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
    messageDigest.update(id.toByteArray(Key.CHARSET))
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
        ContextCompat.getColor(view.context, R.color.signal_background_primary),
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

  companion object CREATOR : Parcelable.Creator<Badge> {
    private val SELECTION_CHANGED = Any()

    override fun createFromParcel(parcel: Parcel): Badge {
      return Badge(parcel)
    }

    override fun newArray(size: Int): Array<Badge?> {
      return arrayOfNulls(size)
    }

    fun register(mappingAdapter: MappingAdapter, onBadgeClicked: OnBadgeClicked) {
      mappingAdapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it, onBadgeClicked) }, R.layout.badge_preference_view))
      mappingAdapter.registerFactory(EmptyModel::class.java, MappingAdapter.LayoutFactory({ EmptyViewHolder(it) }, R.layout.badge_preference_view))
    }
  }
}
