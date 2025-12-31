package org.signal.paging;

import java.util.List;

/**
 * An abstraction over different types of ways the paging lib can provide data, e.g. Observables vs LiveData.
 */
interface DataStream<Data> {
  void next(List<Data> data);
}
