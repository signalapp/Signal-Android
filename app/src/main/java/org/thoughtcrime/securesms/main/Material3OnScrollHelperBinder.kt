package org.thoughtcrime.securesms.main

import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView

interface Material3OnScrollHelperBinder {
  fun bindScrollHelper(recyclerView: RecyclerView, lifecycleOwner: LifecycleOwner)
  fun bindScrollHelper(recyclerView: RecyclerView, lifecycleOwner: LifecycleOwner, chatFolders: RecyclerView, setChatFolder: (Int) -> Unit)
}
