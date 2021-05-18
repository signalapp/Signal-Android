package org.thoughtcrime.securesms.components.settings

import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.thoughtcrime.securesms.util.MappingModel
import org.thoughtcrime.securesms.util.MappingModelList

private const val UNSET = -1

fun configure(init: DSLConfiguration.() -> Unit): DSLConfiguration {
  val configuration = DSLConfiguration()
  configuration.init()
  return configuration
}

class DSLConfiguration {
  private val children = arrayListOf<PreferenceModel<*>>()

  fun customPref(customPreference: PreferenceModel<*>) {
    children.add(customPreference)
  }

  fun radioListPref(
    title: DSLSettingsText,
    @DrawableRes iconId: Int = UNSET,
    isEnabled: Boolean = true,
    listItems: Array<String>,
    selected: Int,
    onSelected: (Int) -> Unit
  ) {
    val preference = RadioListPreference(title, iconId, isEnabled, listItems, selected, onSelected)
    children.add(preference)
  }

  fun multiSelectPref(
    title: DSLSettingsText,
    isEnabled: Boolean = true,
    listItems: Array<String>,
    selected: BooleanArray,
    onSelected: (BooleanArray) -> Unit
  ) {
    val preference = MultiSelectListPreference(title, isEnabled, listItems, selected, onSelected)
    children.add(preference)
  }

  fun switchPref(
    title: DSLSettingsText,
    summary: DSLSettingsText? = null,
    @DrawableRes iconId: Int = UNSET,
    isEnabled: Boolean = true,
    isChecked: Boolean,
    onClick: () -> Unit
  ) {
    val preference = SwitchPreference(title, summary, iconId, isEnabled, isChecked, onClick)
    children.add(preference)
  }

  fun radioPref(
    title: DSLSettingsText,
    summary: DSLSettingsText? = null,
    isEnabled: Boolean = true,
    isChecked: Boolean,
    onClick: () -> Unit
  ) {
    val preference = RadioPreference(title, summary, isEnabled, isChecked, onClick)
    children.add(preference)
  }

  fun clickPref(
    title: DSLSettingsText,
    summary: DSLSettingsText? = null,
    @DrawableRes iconId: Int = UNSET,
    isEnabled: Boolean = true,
    onClick: () -> Unit
  ) {
    val preference = ClickPreference(title, summary, iconId, isEnabled, onClick)
    children.add(preference)
  }

  fun externalLinkPref(
    title: DSLSettingsText,
    @DrawableRes iconId: Int = UNSET,
    @StringRes linkId: Int
  ) {
    val preference = ExternalLinkPreference(title, iconId, linkId)
    children.add(preference)
  }

  fun dividerPref() {
    val preference = DividerPreference()
    children.add(preference)
  }

  fun sectionHeaderPref(title: DSLSettingsText) {
    val preference = SectionHeaderPreference(title)
    children.add(preference)
  }

  fun sectionHeaderPref(title: Int) {
    val preference = SectionHeaderPreference(DSLSettingsText.from(title))
    children.add(preference)
  }

  fun textPref(
    title: DSLSettingsText? = null,
    summary: DSLSettingsText? = null
  ) {
    val preference = TextPreference(title, summary)
    children.add(preference)
  }

  fun toMappingModelList(): MappingModelList = MappingModelList().apply { addAll(children) }
}

abstract class PreferenceModel<T : PreferenceModel<T>>(
  open val title: DSLSettingsText? = null,
  open val summary: DSLSettingsText? = null,
  @DrawableRes open val iconId: Int = UNSET,
  open val isEnabled: Boolean = true
) : MappingModel<T> {
  override fun areItemsTheSame(newItem: T): Boolean {
    return when {
      title != null -> title == newItem.title
      summary != null -> summary == newItem.summary
      else -> throw AssertionError("Could not determine equality of $newItem. Did you forget to override this method?")
    }
  }

  @CallSuper
  override fun areContentsTheSame(newItem: T): Boolean {
    return areItemsTheSame(newItem) &&
      newItem.summary == summary &&
      newItem.iconId == iconId &&
      newItem.isEnabled == isEnabled
  }
}

class TextPreference(
  title: DSLSettingsText?,
  summary: DSLSettingsText?
) : PreferenceModel<TextPreference>(title = title, summary = summary)

class DividerPreference : PreferenceModel<DividerPreference>() {
  override fun areItemsTheSame(newItem: DividerPreference) = true
}

class RadioListPreference(
  override val title: DSLSettingsText,
  @DrawableRes override val iconId: Int = UNSET,
  override val isEnabled: Boolean,
  val listItems: Array<String>,
  val selected: Int,
  val onSelected: (Int) -> Unit
) : PreferenceModel<RadioListPreference>(title = title, iconId = iconId, isEnabled = isEnabled) {

  override fun areContentsTheSame(newItem: RadioListPreference): Boolean {
    return super.areContentsTheSame(newItem) && listItems.contentEquals(newItem.listItems) && selected == newItem.selected
  }
}

class MultiSelectListPreference(
  override val title: DSLSettingsText,
  override val isEnabled: Boolean,
  val listItems: Array<String>,
  val selected: BooleanArray,
  val onSelected: (BooleanArray) -> Unit
) : PreferenceModel<MultiSelectListPreference>(title = title, isEnabled = isEnabled) {
  override fun areContentsTheSame(newItem: MultiSelectListPreference): Boolean {
    return super.areContentsTheSame(newItem) &&
      listItems.contentEquals(newItem.listItems) &&
      selected.contentEquals(newItem.selected)
  }
}

class SwitchPreference(
  override val title: DSLSettingsText,
  override val summary: DSLSettingsText? = null,
  @DrawableRes override val iconId: Int = UNSET,
  isEnabled: Boolean,
  val isChecked: Boolean,
  val onClick: () -> Unit
) : PreferenceModel<SwitchPreference>(title = title, summary = summary, iconId = iconId, isEnabled = isEnabled) {
  override fun areContentsTheSame(newItem: SwitchPreference): Boolean {
    return super.areContentsTheSame(newItem) && isChecked == newItem.isChecked
  }
}

class RadioPreference(
  title: DSLSettingsText,
  summary: DSLSettingsText? = null,
  isEnabled: Boolean,
  val isChecked: Boolean,
  val onClick: () -> Unit
) : PreferenceModel<RadioPreference>(title = title, summary = summary, isEnabled = isEnabled) {
  override fun areContentsTheSame(newItem: RadioPreference): Boolean {
    return super.areContentsTheSame(newItem) && isChecked == newItem.isChecked
  }
}

class ClickPreference(
  override val title: DSLSettingsText,
  override val summary: DSLSettingsText? = null,
  @DrawableRes override val iconId: Int = UNSET,
  isEnabled: Boolean = true,
  val onClick: () -> Unit
) : PreferenceModel<ClickPreference>(title = title, summary = summary, iconId = iconId, isEnabled = isEnabled)

class ExternalLinkPreference(
  override val title: DSLSettingsText,
  @DrawableRes override val iconId: Int,
  @StringRes val linkId: Int
) : PreferenceModel<ExternalLinkPreference>(title = title, iconId = iconId)

class SectionHeaderPreference(override val title: DSLSettingsText) : PreferenceModel<SectionHeaderPreference>(title = title)
