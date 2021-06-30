package org.thoughtcrime.securesms.components.settings.conversation.preferences

import android.view.View
import android.widget.TextView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.Hex
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder
import java.util.UUID

object InternalPreference {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory(::ViewHolder, R.layout.conversation_settings_internal_preference))
  }

  class Model(
    private val recipient: Recipient,
    val onDisableProfileSharingClick: () -> Unit,
    val onDeleteSessionClick: () -> Unit
  ) : PreferenceModel<Model>() {

    val body: String get() {
      return String.format(
        """
        -- Profile Name --
        [${recipient.profileName.givenName}] [${recipient.profileName.familyName}]
        
        -- Profile Sharing --
        ${recipient.isProfileSharing}
        
        -- Profile Key (Base64) --
        ${recipient.profileKey?.let(Base64::encodeBytes) ?: "None"}
        
        -- Profile Key (Hex) --
        ${recipient.profileKey?.let(Hex::toStringCondensed) ?: "None"}
        
        -- Sealed Sender Mode --
        ${recipient.unidentifiedAccessMode}
        
        -- UUID --
        ${recipient.uuid.transform { obj: UUID -> obj.toString() }.or("None")}
        
        -- RecipientId --
        ${recipient.id.serialize()}
        """.trimIndent(),
      )
    }

    override fun areItemsTheSame(newItem: Model): Boolean {
      return recipient == newItem.recipient
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val body: TextView = itemView.findViewById(R.id.internal_preference_body)
    private val disableProfileSharing: View = itemView.findViewById(R.id.internal_disable_profile_sharing)
    private val deleteSession: View = itemView.findViewById(R.id.internal_delete_session)

    override fun bind(model: Model) {
      body.text = model.body
      disableProfileSharing.setOnClickListener { model.onDisableProfileSharingClick() }
      deleteSession.setOnClickListener { model.onDeleteSessionClick() }
    }
  }
}
