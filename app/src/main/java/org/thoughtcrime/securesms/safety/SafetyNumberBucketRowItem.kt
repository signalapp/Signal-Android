package org.thoughtcrime.securesms.safety

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible

object SafetyNumberBucketRowItem {
  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(DistributionListModel::class.java, LayoutFactory(::DistributionListViewHolder, R.layout.safety_number_bucket_row_item))
    mappingAdapter.registerFactory(GroupModel::class.java, LayoutFactory(::GroupViewHolder, R.layout.safety_number_bucket_row_item))
    mappingAdapter.registerFactory(ContactsModel::class.java, LayoutFactory(::ContactsViewHolder, R.layout.safety_number_bucket_row_item))
  }

  fun createModel(
    safetyNumberBucket: SafetyNumberBucket,
    actionItemsProvider: (SafetyNumberBucket) -> List<ActionItem>
  ): MappingModel<*> {
    return when (safetyNumberBucket) {
      SafetyNumberBucket.ContactsBucket -> ContactsModel()
      is SafetyNumberBucket.DistributionListBucket -> DistributionListModel(safetyNumberBucket, actionItemsProvider)
      is SafetyNumberBucket.GroupBucket -> GroupModel(safetyNumberBucket)
    }
  }

  private class DistributionListModel(
    val distributionListBucket: SafetyNumberBucket.DistributionListBucket,
    val actionItemsProvider: (SafetyNumberBucket) -> List<ActionItem>
  ) : MappingModel<DistributionListModel> {
    override fun areItemsTheSame(newItem: DistributionListModel): Boolean {
      return distributionListBucket.distributionListId == newItem.distributionListBucket.distributionListId
    }

    override fun areContentsTheSame(newItem: DistributionListModel): Boolean {
      return distributionListBucket == newItem.distributionListBucket
    }
  }

  private class GroupModel(val groupBucket: SafetyNumberBucket.GroupBucket) : MappingModel<GroupModel> {
    override fun areItemsTheSame(newItem: GroupModel): Boolean {
      return groupBucket.recipient.id == newItem.groupBucket.recipient.id
    }

    override fun areContentsTheSame(newItem: GroupModel): Boolean {
      return groupBucket.recipient.hasSameContent(newItem.groupBucket.recipient)
    }
  }

  private class ContactsModel : MappingModel<ContactsModel> {
    override fun areItemsTheSame(newItem: ContactsModel): Boolean = true

    override fun areContentsTheSame(newItem: ContactsModel): Boolean = true
  }

  private class DistributionListViewHolder(itemView: View) : BaseViewHolder<DistributionListModel>(itemView) {
    override fun getTitle(model: DistributionListModel): String {
      return if (model.distributionListBucket.distributionListId == DistributionListId.MY_STORY) {
        context.getString(R.string.Recipient_my_story)
      } else {
        model.distributionListBucket.name
      }
    }

    override fun bindMenuListener(model: DistributionListModel, menuView: View) {
      menuView.setOnClickListener {
        SignalContextMenu.Builder(menuView, menuView.rootView as ViewGroup)
          .offsetX(DimensionUnit.DP.toPixels(16f).toInt())
          .offsetY(DimensionUnit.DP.toPixels(16f).toInt())
          .show(model.actionItemsProvider(model.distributionListBucket))
      }
    }
  }

  private class GroupViewHolder(itemView: View) : BaseViewHolder<GroupModel>(itemView) {
    override fun getTitle(model: GroupModel): String {
      return model.groupBucket.recipient.getDisplayName(context)
    }

    override fun bindMenuListener(model: GroupModel, menuView: View) {
      menuView.visible = false
    }
  }

  private class ContactsViewHolder(itemView: View) : BaseViewHolder<ContactsModel>(itemView) {
    override fun getTitle(model: ContactsModel): String {
      return context.getString(R.string.SafetyNumberBucketRowItem__contacts)
    }

    override fun bindMenuListener(model: ContactsModel, menuView: View) {
      menuView.visible = false
    }
  }

  private abstract class BaseViewHolder<T : MappingModel<*>>(itemView: View) : MappingViewHolder<T>(itemView) {

    private val titleView: TextView = findViewById(R.id.safety_number_bucket_header)
    private val menuView: View = findViewById(R.id.safety_number_bucket_menu)

    override fun bind(model: T) {
      titleView.text = getTitle(model)
      bindMenuListener(model, menuView)
    }

    abstract fun getTitle(model: T): String
    abstract fun bindMenuListener(model: T, menuView: View)
  }
}
