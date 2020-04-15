package org.thoughtcrime.securesms.loki.redesign.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.LoaderManager
import android.support.v4.content.Loader
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.contact_selection_list_fragment.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader
import org.thoughtcrime.securesms.loki.redesign.activities.ContactClickListener
import org.thoughtcrime.securesms.loki.redesign.activities.ContactSelectionListAdapter
import org.thoughtcrime.securesms.loki.redesign.activities.ContactSelectionListLoader
import org.thoughtcrime.securesms.loki.redesign.activities.ContactSelectionListLoaderItem
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.recipients.Recipient

class ContactSelectionListFragment : Fragment(), LoaderManager.LoaderCallbacks<List<ContactSelectionListLoaderItem>>, ContactClickListener {

  companion object {
    @JvmField val DISPLAY_MODE = "display_mode"
    @JvmField val MULTI_SELECT = "multi_select"
    @JvmField val REFRESHABLE = "refreshable"
  }

  val selectedContacts: List<String>
    get() = listAdapter.selectedContacts.map { it.address.serialize() }

  private var items = listOf<ContactSelectionListLoaderItem>()
    set(value) { field = value; listAdapter.items = value }

  private val listAdapter by lazy {
    val result = ContactSelectionListAdapter(activity!!, isMulti)
    result.glide = GlideApp.with(this)
    result.contactClickListener = this
    result
  }

  private val isMulti: Boolean by lazy {
    activity!!.intent.getBooleanExtra(MULTI_SELECT, false)
  }

  private var cursorFilter: String? = null

  var onContactSelectedListener: OnContactSelectedListener? = null

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)

    recyclerView.layoutManager = LinearLayoutManager(activity)
    recyclerView.adapter = listAdapter
    swipeRefresh.isEnabled = activity!!.intent.getBooleanExtra(REFRESHABLE, true)
  }

  override fun onStart() {
    super.onStart()
    LoaderManager.getInstance(this).initLoader(0, null, this)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.contact_selection_list_fragment, container, false)
  }

  fun setQueryFilter(filter: String?) {
    cursorFilter = filter
    this.loaderManager.restartLoader<List<ContactSelectionListLoaderItem>>(0, null, this)
  }

  fun resetQueryFilter() {
    setQueryFilter(null)
    swipeRefresh.isRefreshing = false
  }

  fun setRefreshing(refreshing: Boolean) {
    swipeRefresh.isRefreshing = refreshing
  }

  fun setOnRefreshListener(onRefreshListener: OnRefreshListener?) {
    this.swipeRefresh.setOnRefreshListener(onRefreshListener)
  }

  override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<ContactSelectionListLoaderItem>> {
    return ContactSelectionListLoader(activity!!,
            activity!!.intent.getIntExtra(DISPLAY_MODE, ContactsCursorLoader.DisplayMode.FLAG_ALL),
            cursorFilter)
  }

  override fun onLoadFinished(loader: Loader<List<ContactSelectionListLoaderItem>>, items: List<ContactSelectionListLoaderItem>) {
    update(items)
  }

  override fun onLoaderReset(loader: Loader<List<ContactSelectionListLoaderItem>>) {
    update(listOf())
  }

  private fun update(items: List<ContactSelectionListLoaderItem>) {
    this.items = items
    mainContentContainer.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    emptyStateContainer.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    val useFastScroller = items.count() > 20
    recyclerView.isVerticalScrollBarEnabled = !useFastScroller
    if (useFastScroller) {
      fastScroller.visibility = View.VISIBLE
      fastScroller.setRecyclerView(recyclerView)
    } else {
      fastScroller.visibility = View.GONE
    }
  }

  override fun onContactClick(contact: Recipient) {
    listAdapter.onContactClick(contact)
  }

  override fun onContactSelected(contact: Recipient) {
    onContactSelectedListener?.onContactSelected(contact.address.serialize())
  }

  override fun onContactDeselected(contact: Recipient) {
    onContactSelectedListener?.onContactDeselected(contact.address.serialize())
  }

  interface OnContactSelectedListener {
    fun onContactSelected(number: String?)
    fun onContactDeselected(number: String?)
  }
}