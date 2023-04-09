package org.thoughtcrime.securesms.contacts.paged

import org.thoughtcrime.securesms.contacts.HeaderAction

/**
 * A strongly typed descriptor of how a given list of contacts should be formatted
 */
class ContactSearchConfiguration private constructor(
  val query: String?,
  val sections: List<Section>,
  val emptyStateSections: List<Section>
) {

  /**
   * Describes the configuration for a given section of content in search results.
   */
  sealed class Section(val sectionKey: SectionKey) {

    abstract val includeHeader: Boolean
    open val headerAction: HeaderAction? = null
    abstract val expandConfig: ExpandConfig?

    /**
     * Section representing the "extra" item.
     */
    object Empty : Section(SectionKey.EMPTY) {
      override val includeHeader: Boolean = false
      override val expandConfig: ExpandConfig? = null
    }

    /**
     * Distribution lists and group stories.
     *
     * Key: [ContactSearchKey.RecipientSearchKey]
     * Data: [ContactSearchData.Story]
     * Model: [ContactSearchAdapter.StoryModel]
     */
    data class Stories(
      val groupStories: Set<ContactSearchData.Story> = emptySet(),
      override val includeHeader: Boolean,
      override val headerAction: HeaderAction? = null,
      override val expandConfig: ExpandConfig? = null
    ) : Section(SectionKey.STORIES)

    /**
     * Recent contacts
     *
     * Key: [ContactSearchKey.RecipientSearchKey]
     * Data: [ContactSearchData.KnownRecipient]
     * Model: [ContactSearchAdapter.RecipientModel]
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
     * 1:1 Recipients with whom the user has started a conversation.
     *
     * Key: [ContactSearchKey.RecipientSearchKey]
     * Data: [ContactSearchData.KnownRecipient]
     * Model: [ContactSearchAdapter.RecipientModel]
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
     *
     * Key: [ContactSearchKey.RecipientSearchKey]
     * Data: [ContactSearchData.KnownRecipient]
     * Model: [ContactSearchAdapter.RecipientModel]
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

    /**
     * A set of arbitrary rows, in the order given in the builder. Usage requires
     * an implementation of [ArbitraryRepository] to be passed into [ContactSearchMediator]
     *
     * Key: [ContactSearchKey.Arbitrary]
     * Data: [ContactSearchData.Arbitrary]
     * Model: To be provided by an instance of [ArbitraryRepository]
     */
    data class Arbitrary(
      val types: Set<String>
    ) : Section(SectionKey.ARBITRARY) {
      override val includeHeader: Boolean = false
      override val expandConfig: ExpandConfig? = null
    }

    /**
     * Individuals who you have not started a conversation with, but are members of shared
     * groups.
     *
     * Key: [ContactSearchKey.RecipientSearchKey]
     * Data: [ContactSearchData.KnownRecipient]
     * Model: [ContactSearchAdapter.RecipientModel]
     */
    data class GroupMembers(
      override val includeHeader: Boolean = true,
      override val expandConfig: ExpandConfig? = null
    ) : Section(SectionKey.GROUP_MEMBERS)

    /**
     * Includes a list of groups with members whose search name match the search query.
     * This section will only be rendered if there is a non-null, non-empty query present.
     *
     * Key: [ContactSearchKey.GroupWithMembers]
     * Data: [ContactSearchData.GroupWithMembers]
     * Model: [ContactSearchAdapter.GroupWithMembersModel]
     */
    data class GroupsWithMembers(
      override val includeHeader: Boolean = true,
      override val expandConfig: ExpandConfig? = null
    ) : Section(SectionKey.GROUPS_WITH_MEMBERS)

    /**
     * 1:1 and Group chat search results, whose data contains
     * a ThreadRecord. Only displayed when there is a search query.
     *
     * Key: [ContactSearchKey.Thread]
     * Data: [ContactSearchData.Thread]
     * Model: [ContactSearchAdapter.ThreadModel]
     */
    data class Chats(
      val isUnreadOnly: Boolean = false,
      override val includeHeader: Boolean = true,
      override val expandConfig: ExpandConfig? = null
    ) : Section(SectionKey.CHATS)

    /**
     * Message search results, only displayed when there
     * is a search query.
     *
     * Key: [ContactSearchKey.Message]
     * Data: [ContactSearchData.Message]
     * Model: [ContactSearchAdapter.MessageModel]
     */
    data class Messages(
      override val includeHeader: Boolean = true,
      override val expandConfig: ExpandConfig? = null
    ) : Section(SectionKey.MESSAGES)

    /**
     * Contacts that the user has shared profile key data with or
     * that exist in system contacts, but that do not have an associated
     * thread.
     *
     * Key: [ContactSearchKey.RecipientSearchKey]
     * Data: [ContactSearchData.KnownRecipient]
     * Model: [ContactSearchAdapter.RecipientModel]
     */
    data class ContactsWithoutThreads(
      override val includeHeader: Boolean = true,
      override val expandConfig: ExpandConfig? = null
    ) : Section(SectionKey.CONTACTS_WITHOUT_THREADS)

    data class Username(val newRowMode: NewRowMode) : Section(SectionKey.USERNAME) {
      override val includeHeader: Boolean = false
      override val expandConfig: ExpandConfig? = null
    }

    data class PhoneNumber(val newRowMode: NewRowMode) : Section(SectionKey.PHONE_NUMBER) {
      override val includeHeader: Boolean = false
      override val expandConfig: ExpandConfig? = null
    }
  }

  /**
   * Describes a given section. Useful for labeling sections and managing expansion state.
   */
  enum class SectionKey {
    /**
     * A generic empty item
     */
    EMPTY,

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
     * Section Key for [Section.GroupsWithMembers]
     */
    GROUPS_WITH_MEMBERS,

    /**
     * Section Key for [Section.ContactsWithoutThreads]
     */
    CONTACTS_WITHOUT_THREADS,

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
    MESSAGES,

    /**
     * A row representing the search query as a phone number
     */
    PHONE_NUMBER,

    /**
     * A row representing the search query as a username
     */
    USERNAME
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

  /**
   * Describes the mode for 'Username' or 'PhoneNumber'
   */
  enum class NewRowMode {
    NEW_CALL,
    NEW_CONVERSATION,
    BLOCK,
    ADD_TO_GROUP
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

  private class EmptyStateBuilder : Builder {
    private val sections: MutableList<Section> = mutableListOf()

    override var query: String? = null

    override fun addSection(section: Section) {
      sections.add(section)
    }

    override fun withEmptyState(emptyStateBuilderFn: Builder.() -> Unit) {
      error("Unsupported operation: Already in empty state.")
    }

    fun build(): List<Section> {
      return sections
    }
  }

  /**
   * Internal builder class with build method.
   */
  private class ConfigurationBuilder : Builder {
    private val sections: MutableList<Section> = mutableListOf()
    private val emptyState = EmptyStateBuilder()

    override var query: String? = null

    override fun addSection(section: Section) {
      sections.add(section)
    }

    override fun withEmptyState(emptyStateBuilderFn: Builder.() -> Unit) {
      emptyState.emptyStateBuilderFn()
    }

    fun build(): ContactSearchConfiguration {
      return ContactSearchConfiguration(
        query = query,
        sections = sections,
        emptyStateSections = emptyState.build()
      )
    }
  }

  /**
   * Exposed Builder interface without build method.
   */
  interface Builder {
    var query: String?

    fun arbitrary(first: String, vararg rest: String) {
      addSection(Section.Arbitrary(setOf(first) + rest.toSet()))
    }

    fun username(newRowMode: NewRowMode) {
      addSection(Section.Username(newRowMode))
    }

    fun phone(newRowMode: NewRowMode) {
      addSection(Section.PhoneNumber(newRowMode))
    }

    fun withEmptyState(emptyStateBuilderFn: Builder.() -> Unit)

    fun addSection(section: Section)
  }
}
