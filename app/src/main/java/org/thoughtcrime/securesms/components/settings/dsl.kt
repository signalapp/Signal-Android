package org.thoughtcrime.securesms.components.settings

import androidx.annotation.CallSuper
import androidx.annotation.Px
import androidx.annotation.StringRes
import org.thoughtcrime.securesms.components.settings.models.AsyncSwitch
import org.thoughtcrime.securesms.components.settings.models.Button
import org.thoughtcrime.securesms.components.settings.models.Space
import org.thoughtcrime.securesms.components.settings.models.Text
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList

fun configure(init: DSLConfiguration.() -> Unit): DSLConfiguration {
  val configuration = DSLConfiguration()
  configuration.init()
  return configuration
}

class DSLConfiguration {
  private val children = arrayListOf<MappingModel<*>>()

  fun customPref(customPreference: MappingModel<*>) {
    children.add(customPreference)
  }

  fun radioListPref(
    title: DSLSettingsText,
    icon: DSLSettingsIcon? = null,
    dialogTitle: DSLSettingsText = title,
    isEnabled: Boolean = true,
    listItems: Array<String>,
    selected: Int,
    confirmAction: Boolean = false,
    onSelected: (Int) -> Unit
  ) {
    val preference = RadioListPreference(
      title = title,
      icon = icon,
      isEnabled = isEnabled,
      dialogTitle = dialogTitle,
      listItems = listItems,
      selected = selected,
      confirmAction = confirmAction,
      onSelected = onSelected
    )
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

  fun asyncSwitchPref(
    title: DSLSettingsText,
    isEnabled: Boolean = true,
    isChecked: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit
  ) {
    val preference = AsyncSwitch.Model(title, isEnabled, isChecked, isProcessing, onClick)
    children.add(preference)
  }

  fun switchPref(
    title: DSLSettingsText,
    summary: DSLSettingsText? = null,
    icon: DSLSettingsIcon? = null,
    isEnabled: Boolean = true,
    isChecked: Boolean,
    onClick: () -> Unit
  ) {
    val preference = SwitchPreference(title, summary, icon, isEnabled, isChecked, onClick)
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
    icon: DSLSettingsIcon? = null,
    isEnabled: Boolean = true,
    onClick: () -> Unit
  ) {
    val preference = ClickPreference(title, summary, icon, isEnabled, onClick)
    children.add(preference)
  }

  fun longClickPref(
    title: DSLSettingsText,
    summary: DSLSettingsText? = null,
    icon: DSLSettingsIcon? = null,
    isEnabled: Boolean = true,
    onLongClick: () -> Unit
  ) {
    val preference = LongClickPreference(title, summary, icon, isEnabled, onLongClick)
    children.add(preference)
  }

  fun externalLinkPref(
    title: DSLSettingsText,
    icon: DSLSettingsIcon? = null,
    @StringRes linkId: Int
  ) {
    val preference = ExternalLinkPreference(title, icon, linkId)
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

  fun noPadTextPref(title: DSLSettingsText) {
    val preference = Text(title)
    children.add(Text.Model(preference))
  }

  fun space(@Px pixels: Int) {
    val preference = Space(pixels)
    children.add(Space.Model(preference))
  }

  fun primaryButton(
    text: DSLSettingsText,
    isEnabled: Boolean = true,
    onClick: () -> Unit
  ) {
    val preference = Button.Model.Primary(text, null, isEnabled, onClick)
    children.add(preference)
  }

  fun secondaryButtonNoOutline(
    text: DSLSettingsText,
    icon: DSLSettingsIcon? = null,
    isEnabled: Boolean = true,
    onClick: () -> Unit
  ) {
    val preference = Button.Model.SecondaryNoOutline(text, icon, isEnabled, onClick)
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
  open val icon: DSLSettingsIcon? = null,
  open val isEnabled: Boolean = true,
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
      newItem.icon == icon &&
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
  override val icon: DSLSettingsIcon? = null,
  override val isEnabled: Boolean,
  val dialogTitle: DSLSettingsText = title,
  val listItems: Array<String>,
  val selected: Int,
  val onSelected: (Int) -> Unit,
  val confirmAction: Boolean = false
) : PreferenceModel<RadioListPreference>() {

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
  override val icon: DSLSettingsIcon? = null,
  override val isEnabled: Boolean,
  val isChecked: Boolean,
  val onClick: () -> Unit
) : PreferenceModel<SwitchPreference>() {
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
  override val icon: DSLSettingsIcon? = null,
  override val isEnabled: Boolean = true,
  val onClick: () -> Unit
) : PreferenceModel<ClickPreference>()

class LongClickPreference(
  override val title: DSLSettingsText,
  override val summary: DSLSettingsText? = null,
  override val icon: DSLSettingsIcon? = null,
  override val isEnabled: Boolean = true,
  val onLongClick: () -> Unit
) : PreferenceModel<LongClickPreference>()

class ExternalLinkPreference(
  override val title: DSLSettingsText,
  override val icon: DSLSettingsIcon?,
  @StringRes val linkId: Int
) : PreferenceModel<ExternalLinkPreference>()

class SectionHeaderPreference(override val title: DSLSettingsText) : PreferenceModel<SectionHeaderPreference>()
