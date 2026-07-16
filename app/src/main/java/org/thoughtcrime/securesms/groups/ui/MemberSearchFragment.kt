package org.thoughtcrime.securesms.groups.ui

import android.os.Bundle
import android.view.View
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.LocalFragmentManager
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.util.logging.Log
import org.signal.core.util.requireParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ArbitraryRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearch
import org.thoughtcrime.securesms.contacts.paged.ContactSearchAdapter
import org.thoughtcrime.securesms.contacts.paged.ContactSearchCallbacks
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.contacts.paged.ContactSearchPagedDataSourceRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.contacts.paged.ContactSearchViewModel
import org.thoughtcrime.securesms.conversation.RecipientSearchBar
import org.thoughtcrime.securesms.conversation.mutiselect.forward.SearchConfigurationProvider
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.groups.ui.MemberSearchFragment.AddMembersModel
import org.thoughtcrime.securesms.groups.ui.MemberSearchFragment.DividerModel
import org.thoughtcrime.securesms.groups.ui.MemberSearchFragment.InviteViaModel
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.ui.sharablegrouplink.GroupLinkBottomSheetDialogFragment
import org.thoughtcrime.securesms.search.SearchRepository
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.compose.MappingEntryProvider
import org.thoughtcrime.securesms.util.adapter.mapping.compose.rememberMappingEntryProvider
import org.thoughtcrime.securesms.util.fragments.findListener

/**
 * Fragment that shows all members in a group (including self)
 */
class MemberSearchFragment : ComposeFragment(), RecipientBottomSheetDialogFragment.Callback {

  companion object {

    private val TAG = Log.tag(MemberSearchFragment::class.java)
    private const val ARG_GROUP_ID = "group_id"
    private const val ARG_CAN_ADD = "can_add"
    private const val ARG_HAS_GROUP_LINK = "has_group_link"

    const val RESULT_ADD_MEMBERS = "result_add"

    fun newInstance(groupId: GroupId.V2, canAdd: Boolean, hasGroupLink: Boolean): MemberSearchFragment {
      return MemberSearchFragment().apply {
        arguments = Bundle().apply {
          putParcelable(ARG_GROUP_ID, groupId)
          putBoolean(ARG_CAN_ADD, canAdd)
          putBoolean(ARG_HAS_GROUP_LINK, hasGroupLink)
        }
      }
    }
  }

  private val groupId: GroupId.V2 by lazy {
    requireArguments().requireParcelableCompat(ARG_GROUP_ID, GroupId.V2::class.java)
  }

  private val contactViewModel: ContactSearchViewModel by viewModels {
    ContactSearchViewModel.Factory(
      selectionLimits = SelectionLimits(0, 0),
      isMultiSelect = false,
      repository = ContactSearchRepository(),
      performSafetyNumberChecks = false,
      arbitraryRepository = findListener<SearchConfigurationProvider>()?.getArbitraryRepository() ?: MemberArbitraryRepository(),
      searchRepository = SearchRepository(requireContext().getString(R.string.Recipient_you)),
      contactSearchPagedDataSourceRepository = ContactSearchPagedDataSourceRepository(requireContext(), requireContext().getString(R.string.Recipient_you))
    )
  }

  private val memberSearchViewModel: MemberSearchViewModel by viewModels()

  override fun onRecipientBottomSheetDismissed() {
    contactViewModel.refreshGroupData()
  }

  override fun onMessageClicked() = Unit

  @Composable
  override fun FragmentContent() {
    val memberFilter by memberSearchViewModel.memberFilter.collectAsStateWithLifecycle()
    val canAdd = requireArguments().getBoolean(ARG_CAN_ADD)
    val hasGroupLink = requireArguments().getBoolean(ARG_HAS_GROUP_LINK)
    CompositionLocalProvider(LocalFragmentManager provides childFragmentManager) {
      MemberSearchScreen(
        contactViewModel = contactViewModel,
        memberFilter = memberFilter,
        onFilterSelected = { memberSearchViewModel.setFilter(it) },
        mapStateToConfiguration = { state -> getConfiguration(state, memberFilter, canAdd, hasGroupLink) },
        contactSearchCallbacks = remember {
          SearchCallbacks(
            fragmentManager = childFragmentManager,
            groupId = groupId,
            onAddMembers = {
              setFragmentResult(RESULT_ADD_MEMBERS, Bundle.EMPTY)
              findNavController().popBackStack()
            }
          )
        }
      )
    }
  }

  class SearchCallbacks(
    private val fragmentManager: FragmentManager,
    private val groupId: GroupId.V2,
    private val onAddMembers: () -> Unit
  ) : ContactSearchCallbacks.Simple() {
    override fun onBeforeContactsSelected(view: View?, contactSearchKeys: Set<ContactSearchKey>): Set<ContactSearchKey> {
      val recipientId = contactSearchKeys.filterIsInstance<ContactSearchKey.RecipientSearchKey>().firstOrNull()?.recipientId
      if (recipientId != null) {
        RecipientBottomSheetDialogFragment.show(fragmentManager, recipientId, groupId)
      }
      return emptySet()
    }

    fun onAdd() {
      onAddMembers()
    }

    fun onInvite() {
      GroupLinkBottomSheetDialogFragment.show(fragmentManager, groupId)
    }
  }

  private fun getConfiguration(contactSearchState: ContactSearchState, memberFilter: MemberSearchViewModel.MemberFilter, canAdd: Boolean, hasGroupLink: Boolean): ContactSearchConfiguration {
    return findListener<SearchConfigurationProvider>()?.getSearchConfiguration(childFragmentManager, contactSearchState) ?: ContactSearchConfiguration.build {
      query = contactSearchState.query

      addSection(
        ContactSearchConfiguration.Section.GroupMembers(
          includeHeader = false,
          includeLetterHeaders = true,
          groupId = groupId,
          showGroupsInCommon = false,
          showSelfAsYou = true,
          roleFilter = when (memberFilter) {
            MemberSearchViewModel.MemberFilter.ALL -> ContactSearchConfiguration.MemberRole.ALL
            MemberSearchViewModel.MemberFilter.ADMINS -> ContactSearchConfiguration.MemberRole.ADMINS
            MemberSearchViewModel.MemberFilter.CONTACTS -> ContactSearchConfiguration.MemberRole.CONTACTS
          }
        )
      )

      if (canAdd || hasGroupLink) {
        val set = if (canAdd && hasGroupLink) {
          setOf(MemberArbitraryRepository.TYPE_DIVIDER, MemberArbitraryRepository.TYPE_ADD_MEMBERS, MemberArbitraryRepository.TYPE_INVITE_VIA)
        } else if (canAdd) {
          setOf(MemberArbitraryRepository.TYPE_DIVIDER, MemberArbitraryRepository.TYPE_ADD_MEMBERS)
        } else {
          setOf(MemberArbitraryRepository.TYPE_DIVIDER, MemberArbitraryRepository.TYPE_INVITE_VIA)
        }
        addSection(ContactSearchConfiguration.Section.Arbitrary(set))
      }

      withEmptyState {
        addSection(ContactSearchConfiguration.Section.Empty)
        addSection(
          ContactSearchConfiguration.Section.Arbitrary(
            setOf(MemberArbitraryRepository.TYPE_DIVIDER, MemberArbitraryRepository.TYPE_ADD_MEMBERS, MemberArbitraryRepository.TYPE_INVITE_VIA)
          )
        )
      }
    }
  }

  class DividerModel : MappingModel<DividerModel> {
    override fun areItemsTheSame(newItem: DividerModel) = true
    override fun areContentsTheSame(newItem: DividerModel) = true
  }

  class AddMembersModel : MappingModel<AddMembersModel> {
    override fun areItemsTheSame(newItem: AddMembersModel) = true
    override fun areContentsTheSame(newItem: AddMembersModel) = true
  }

  class InviteViaModel : MappingModel<InviteViaModel> {
    override fun areItemsTheSame(newItem: InviteViaModel) = true
    override fun areContentsTheSame(newItem: InviteViaModel) = true
  }

  class MemberArbitraryRepository : ArbitraryRepository {
    override fun getSize(section: ContactSearchConfiguration.Section.Arbitrary, query: String?) = section.types.size

    override fun getData(section: ContactSearchConfiguration.Section.Arbitrary, query: String?, startIndex: Int, endIndex: Int, totalSearchSize: Int): List<ContactSearchData.Arbitrary> {
      return section.types.toList().subList(startIndex, endIndex).map { ContactSearchData.Arbitrary(it) }
    }

    override fun getMappingModel(arbitrary: ContactSearchData.Arbitrary): MappingModel<*> {
      return when (arbitrary.type) {
        TYPE_DIVIDER -> DividerModel()
        TYPE_ADD_MEMBERS -> AddMembersModel()
        TYPE_INVITE_VIA -> InviteViaModel()
        else -> throw IllegalArgumentException("Unknown type: ${arbitrary.type}")
      }
    }

    companion object {
      const val TYPE_DIVIDER = "divider"
      const val TYPE_ADD_MEMBERS = "add-members"
      const val TYPE_INVITE_VIA = "invite-via"
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberSearchScreen(
  contactViewModel: ContactSearchViewModel,
  memberFilter: MemberSearchViewModel.MemberFilter,
  onFilterSelected: (MemberSearchViewModel.MemberFilter) -> Unit,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  contactSearchCallbacks: MemberSearchFragment.SearchCallbacks
) {
  val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  var showFilterDialog by remember { mutableStateOf(false) }

  if (showFilterDialog) {
    val filters = MemberSearchViewModel.MemberFilter.entries

    Dialogs.RadioListDialog(
      onDismissRequest = { showFilterDialog = false },
      title = stringResource(R.string.MemberSearchFragment__filter),
      labels = stringArrayResource(R.array.filter_search_entries),
      values = filters.map { it.name }.toTypedArray(),
      selectedIndex = filters.indexOf(memberFilter),
      onSelected = { index -> onFilterSelected(filters[index]) }
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(R.string.MemberSearchFragment__search_members)) },
        navigationIcon = {
          IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
            Icon(
              imageVector = SignalIcons.ArrowStart.imageVector,
              contentDescription = stringResource(R.string.DSLSettingsToolbar__navigate_up)
            )
          }
        },
        actions = {
          val filterActive = memberFilter != MemberSearchViewModel.MemberFilter.ALL
          IconButton(onClick = { showFilterDialog = true }) {
            Icon(
              imageVector = ImageVector.vectorResource(R.drawable.symbol_filter_20),
              tint = if (filterActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
              contentDescription = stringResource(R.string.MemberSearchFragment__filter),
              modifier = Modifier
                .size(26.dp)
                .background(color = if (filterActive) MaterialTheme.colorScheme.primary else Color.Transparent, shape = CircleShape)
                .padding(3.dp)
            )
          }
        }
      )
    }
  ) {
    MemberSearchContent(
      contactViewModel = contactViewModel,
      memberFilter = memberFilter,
      mapStateToConfiguration = mapStateToConfiguration,
      contactSearchCallbacks = contactSearchCallbacks,
      modifier = Modifier.padding(it)
    )
  }
}

@Composable
private fun MemberSearchContent(
  contactViewModel: ContactSearchViewModel,
  memberFilter: MemberSearchViewModel.MemberFilter,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  modifier: Modifier = Modifier,
  contactSearchCallbacks: MemberSearchFragment.SearchCallbacks
) {
  val focusRequester = remember { FocusRequester() }
  val additionalEntries: MappingEntryProvider<Any> = rememberMappingEntryProvider {
    entry<DividerModel> { Dividers.Default() }

    entry<AddMembersModel> {
      Rows.TextRow(
        text = { Text(text = stringResource(R.string.AddMembersActivity__add_members)) },
        icon = {
          Icon(
            imageVector = ImageVector.vectorResource(org.signal.core.ui.R.drawable.symbol_plus_24),
            tint = MaterialTheme.colorScheme.onSurface,
            contentDescription = null,
            modifier = Modifier
              .size(40.dp)
              .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
              .padding(8.dp)
          )
        },
        onClick = contactSearchCallbacks::onAdd
      )
    }

    entry<InviteViaModel> {
      Rows.TextRow(
        text = { Text(text = stringResource(R.string.MemberSearchFragment__invite_via)) },
        icon = {
          Icon(
            imageVector = ImageVector.vectorResource(org.signal.core.ui.R.drawable.symbol_link_24),
            tint = MaterialTheme.colorScheme.onSurface,
            contentDescription = null,
            modifier = Modifier
              .size(40.dp)
              .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
              .padding(8.dp)
          )
        },
        onClick = contactSearchCallbacks::onInvite
      )
    }
  }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  val currentMapStateToConfiguration by rememberUpdatedState(mapStateToConfiguration)
  LaunchedEffect(memberFilter) {
    contactViewModel.setConfiguration(
      currentMapStateToConfiguration(contactViewModel.configurationState.value)
    )
  }

  Column(
    modifier = modifier.fillMaxSize()
  ) {
    val query by contactViewModel.query.collectAsStateWithLifecycle()
    val hint = when (memberFilter) {
      MemberSearchViewModel.MemberFilter.ALL -> stringResource(R.string.MemberSearchFragment__search_members)
      MemberSearchViewModel.MemberFilter.ADMINS -> stringResource(R.string.MemberSearchFragment__search_admins)
      MemberSearchViewModel.MemberFilter.CONTACTS -> stringResource(R.string.MemberSearchFragment__search_contacts)
    }
    RecipientSearchBar(
      hint = hint,
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp, horizontal = 16.dp)
        .focusRequester(focusRequester),
      query = query ?: "",
      onQueryChange = { contactViewModel.setQuery(it) },
      onSearch = { contactViewModel.setQuery(it) }
    )

    ContactSearch(
      viewModel = contactViewModel,
      mapStateToConfiguration = mapStateToConfiguration,
      displayOptions = remember {
        ContactSearchAdapter.DisplayOptions(
          displaySecondaryInformation = ContactSearchAdapter.DisplaySecondaryInformation.NEVER
        )
      },
      additionalEntries = additionalEntries,
      callbacks = contactSearchCallbacks,
      modifier = Modifier.weight(1f)
    )
  }
}
