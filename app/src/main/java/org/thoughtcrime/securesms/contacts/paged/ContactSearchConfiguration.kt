package org.thoughtcrime.securesms.contacts.paged

import org.thoughtcrime.securesms.contacts.HeaderAction

/**
 * A strongly typed descriptor of how a given list of contacts should be formatted
 */
class ContactSearchConfiguration private constructor(
  val query: String?,
  val hasEmptyState: Boolean,
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
      val mode: Mode = Mode.ALL,
      val includeInactiveGroups: Boolean = false,
      val includeGroupsV1: Boolean = false,
      val includeSms: Boolean = false,
      val includeSelf: Boolean = false,
      override val includeHeader: Boolean,
      override val expandConfig: ExpandConfig? = null
    ) : Section(SectionKey.RECENTS) {
      enum class Mode {
        INDIVIDUALS,
        GROUPS,
        ALL
      }
    }

    /**
     * 1:1 Recipients
     */
    data class Individuals(
      val includeSelf: Boolean,
      val transportType: TransportType,
      override val includeHeader: Boolean,
      override val expandConfig: ExpandConfig? = null,
      val includeLetterHeaders: Boolean = false
    ) : Section(SectionKey.INDIVIDUALS)

    /**
     * Group Recipients
     */
    data class Groups(
      val includeMms: Boolean = false,
      val includeV1: Boolean = false,
      val includeInactive: Boolean = false,
      val returnAsGroupStories: Boolean = false,
      val sortOrder: ContactSearchSortOrder = ContactSearchSortOrder.NATURAL,
      val shortSummary: Boolean = false,
      override val includeHeader: Boolean,
      override val expandConfig: ExpandConfig? = null
    ) : Section(SectionKey.GROUPS)

    data class Arbitrary(
      val types: Set<String>
    ) : Section(SectionKey.ARBITRARY) {
      override val includeHeader: Boolean = false
      override val expandConfig: ExpandConfig? = null
    }

    data class GroupMembers(
      override val includeHeader: Boolean = true,
      override val expandConfig: ExpandConfig? = null
    ) : Section(SectionKey.GROUP_MEMBERS)

    data class Chats(
      val isUnreadOnly: Boolean = false,
      override val includeHeader: Boolean = true,
      override val expandConfig: ExpandConfig? = null
    ) : Section(SectionKey.CHATS)

    data class Messages(
      override val includeHeader: Boolean = true,
      override val expandConfig: ExpandConfig? = null
    ) : Section(SectionKey.MESSAGES)
  }

  /**
   * Describes a given section. Useful for labeling sections and managing expansion state.
   */
  enum class SectionKey {
    /**
     * Lists My Stories, distribution lists, as well as group stories.
     */
    STORIES,

    /**
     * Recent chats.
     */
    RECENTS,

    /**
     * 1:1 Contacts with whom I've started a chat.
     */
    INDIVIDUALS,

    /**
     * Active groups the user is a member of
     */
    GROUPS,

    /**
     * Arbitrary row (think new group button, username row, etc)
     */
    ARBITRARY,

    /**
     * Contacts that are members of groups user is in that they've not explicitly
     * started a conversation with.
     */
    GROUP_MEMBERS,

    /**
     * 1:1 and Group chats
     */
    CHATS,

    /**
     * Messages from 1:1 and Group chats
     */
    MESSAGES
  }

  /**
   * Describes how a given section can be expanded.
   */
  data class ExpandConfig(
    val isExpanded: Boolean,
    val maxCountWhenNotExpanded: (ActiveContactCount) -> Int = { 2 }
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
    @JvmStatic
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
    override var hasEmptyState: Boolean = false

    override fun addSection(section: Section) {
      sections.add(section)
    }

    fun build(): ContactSearchConfiguration {
      return ContactSearchConfiguration(query, hasEmptyState, sections)
    }
  }

  /**
   * Exposed Builder interface without build method.
   */
  interface Builder {
    var query: String?
    var hasEmptyState: Boolean

    fun arbitrary(first: String, vararg rest: String) {
      addSection(Section.Arbitrary(setOf(first) + rest.toSet()))
    }

    fun addSection(section: Section)
  }
}
