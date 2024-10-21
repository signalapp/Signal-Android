package org.thoughtcrime.securesms.main

import androidx.recyclerview.widget.RecyclerView

interface Material3OnScrollHelperBinder {
  fun bindScrollHelper(recyclerView: RecyclerView)
  fun bindScrollHelper(recyclerView: RecyclerView, chatFolders: RecyclerView, setChatFolder: (Int) -> Unit)
}
