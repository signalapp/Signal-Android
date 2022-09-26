package org.thoughtcrime.securesms.main

import android.widget.ImageView
import org.thoughtcrime.securesms.components.Material3SearchToolbar
import org.thoughtcrime.securesms.util.views.Stub

interface SearchBinder {
  fun getSearchAction(): ImageView

  fun getSearchToolbar(): Stub<Material3SearchToolbar>

  fun onSearchOpened()

  fun onSearchClosed()
}
