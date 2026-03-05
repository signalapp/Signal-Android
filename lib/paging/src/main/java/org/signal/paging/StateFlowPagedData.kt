package org.signal.paging

import kotlinx.coroutines.flow.StateFlow

/**
 * An implementation of [PagedData] that will provide data as a [StateFlow].
 */
class StateFlowPagedData<Key, Data>(
  val data: StateFlow<List<Data>>,
  controller: PagingController<Key>
) : PagedData<Key>(controller)
