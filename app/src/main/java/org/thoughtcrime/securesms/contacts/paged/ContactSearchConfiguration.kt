package org.thoughtcrime.securesms.contacts.paged

import org.thoughtcrime.securesms.contacts.HeaderAction

/**
 * A strongly typed descriptor of how a given list of contacts should be formatted
 */
class ContactSearchConfiguration private constructor(
  val query: String?,
  val sections: List<Section>
) {
  sealed class Section(val sectionKey: SectionKey) {

    abstract val includeHeader: Boolean
    open val headerAction: HeaderAction? = null
    abstract val expandConfig: ExpandConfig?

    /**
     * Distribution lists and group stories.
     */
    data class Stories(
      val groupStories: Set<ContactSearchData.Story> = emptySet(),
      override val includeHeader: Boolean,
      override val headerAction: HeaderAction? = null,
      override val expandConfig: ExpandConfig? = null
    ) : Section(SectionKey.STORIES)

    /**
     * Recent contacts
     */
    data class Recents(
      val limit: Int = 25,
      val groupsOnly: Boolean = false,
      val includeInactiveGroups: Boolean = false,
      val includeGroupsV1: Boolean = false,
      val includeSms: Boolean = false,
      override val includeHeader: Boolean,
      override val expandConfig: ExpandConfig? = null
    ) : Section(SectionKey.RECENTS)

    /**
     * 1:1 Recipients
     */
    data class Individuals(
      val includeSelf: Boolean,
      val transportType: TransportType,
      override val includeHeader: Boolean,
      override val expandConfig: ExpandConfig? = null
    ) : Section(SectionKey.INDIVIDUALS)

    /**
     * Group Recipients
     */
    data class Groups(
      val includeMms: Boolean = false,
      val includeV1: Boolean = false,
      val includeInactive: Boolean = false,
      val returnAsGroupStories: Boolean = false,
      override val includeHeader: Boolean,
      override val expandConfig: ExpandConfig? = null
    ) : Section(SectionKey.GROUPS)
  }

  /**
   * Describes a given section. Useful for labeling sections and managing expansion state.
   */
  enum class SectionKey {
    STORIES,
    RECENTS,
    INDIVIDUALS,
    GROUPS
  }

  /**
   * Describes how a given section can be expanded.
   */
  data class ExpandConfig(
    val isExpanded: Boolean,
    val maxCountWhenNotExpanded: Int = 2
  )

  /**
   * Network transport type for individual recipients.
   */
  enum class TransportType {
    PUSH,
    SMS,
    ALL
  }

  companion object {
    /**
     * DSL Style builder function. Example:
     *
     * ```
     * val configuration = ContactSearchConfiguration.build {
     *   query = "My Query"
     *   addSection(Recents(...))
     * }
     * ```
     */
    fun build(builderFunction: Builder.() -> Unit): ContactSearchConfiguration {
      return ConfigurationBuilder().let {
        it.builderFunction()
        it.build()
      }
    }
  }

  /**
   * Internal builder class with build method.
   */
  private class ConfigurationBuilder : Builder {
    private val sections: MutableList<Section> = mutableListOf()

    override var query: String? = null

    override fun addSection(section: Section) {
      sections.add(section)
    }

    fun build(): ContactSearchConfiguration {
      return ContactSearchConfiguration(query, sections)
    }
  }

  /**
   * Exposed Builder interface without build method.
   */
  interface Builder {
    var query: String?
    fun addSection(section: Section)
  }
}
