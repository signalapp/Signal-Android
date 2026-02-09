package org.thoughtcrime.securesms.components.settings.conversation.preferences

import android.text.SpannableStringBuilder
import android.view.View
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabelPillView
import org.thoughtcrime.securesms.groups.memberlabel.StyledMemberLabel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ContextUtil
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible
import org.signal.core.ui.R as CoreUiR

/**
 * Renders a Recipient as a row item with an icon, avatar, label/status, and admin state.
 */
object RecipientPreference {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.group_recipient_list_item))
  }

  class Model(
    val recipient: Recipient,
    val isAdmin: Boolean = false,
    val memberLabel: StyledMemberLabel? = null,
    val lifecycleOwner: LifecycleOwner? = null,
    val onClick: (() -> Unit)? = null
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return recipient.id == newItem.recipient.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) &&
        recipient.hasSameContent(newItem.recipient) &&
        isAdmin == newItem.isAdmin &&
        memberLabel == newItem.memberLabel
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {
    private val avatar: AvatarImageView = itemView.findViewById(R.id.recipient_avatar)
    private val name: TextView = itemView.findViewById(R.id.recipient_name)
    private val about: TextView? = itemView.findViewById(R.id.recipient_about)
    private val memberLabelView: MemberLabelPillView? = itemView.findViewById(R.id.recipient_member_label)
    private val admin: View? = itemView.findViewById(R.id.admin)
    private val badge: BadgeImageView = itemView.findViewById(R.id.recipient_badge)

    private var recipient: Recipient? = null

    private val recipientObserver = Observer<Recipient> { recipient ->
      onRecipientChanged(recipient)
    }

    override fun bind(model: Model) {
      if (model.onClick != null) {
        itemView.setOnClickListener { model.onClick.invoke() }
      } else {
        itemView.setOnClickListener(null)
      }

      if (model.lifecycleOwner != null) {
        observeRecipient(model.lifecycleOwner, model.recipient)
        model.memberLabel?.let(::showMemberLabel)
      } else {
        onRecipientChanged(model.recipient, model.memberLabel)
      }

      admin?.visible = model.isAdmin
    }

    override fun onViewRecycled() {
      unbind()
    }

    private fun onRecipientChanged(recipient: Recipient, memberLabel: StyledMemberLabel? = null) {
      avatar.setRecipient(recipient)
      badge.setBadgeFromRecipient(recipient)
      name.text = if (recipient.isSelf) {
        context.getString(R.string.Recipient_you)
      } else {
        if (recipient.isSystemContact) {
          SpannableStringBuilder(recipient.getDisplayName(context)).apply {
            val drawable = ContextUtil.requireDrawable(context, R.drawable.symbol_person_circle_24).apply {
              setTint(ContextCompat.getColor(context, CoreUiR.color.signal_colorOnSurface))
            }
            SpanUtil.appendCenteredImageSpan(this, drawable, 16, 16)
          }
        } else {
          recipient.getDisplayName(context)
        }
      }

      when {
        memberLabel != null -> showMemberLabel(memberLabel)

        !recipient.combinedAboutAndEmoji.isNullOrEmpty() -> {
          about?.text = recipient.combinedAboutAndEmoji
          about?.visible = true
          memberLabelView?.visible = false
        }

        else -> {
          memberLabelView?.visible = false
          about?.visible = false
        }
      }
    }

    private fun showMemberLabel(styledLabel: StyledMemberLabel) {
      memberLabelView?.apply {
        style = MemberLabelPillView.Style(
          horizontalPadding = 8.dp,
          verticalPadding = 2.dp,
          textStyle = { MaterialTheme.typography.bodySmall }
        )
        setLabel(styledLabel.label, styledLabel.tintColor)
        visible = true
      }

      about?.visible = false
    }

    private fun observeRecipient(lifecycleOwner: LifecycleOwner?, recipient: Recipient?) {
      this.recipient?.live()?.liveData?.removeObserver(recipientObserver)

      this.recipient = recipient

      lifecycleOwner?.let {
        this.recipient?.live()?.liveData?.observe(lifecycleOwner, recipientObserver)
      }
    }

    private fun unbind() {
      observeRecipient(null, null)
    }
  }
}
