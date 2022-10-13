package org.thoughtcrime.securesms.contacts.paged.collections

import androidx.collection.SparseArrayCompat
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import kotlin.math.min

/**
 * Generic contact search collection.
 */
open class ContactSearchCollection<ContactRecord>(
  private val section: ContactSearchConfiguration.Section,
  private val records: ContactSearchIterator<ContactRecord>,
  private val recordPredicate: ((ContactRecord) -> Boolean)? = null,
  private val recordMapper: (ContactRecord) -> ContactSearchData,
  private val activeContactCount: Int
) {

  protected open val contentSize: Int = if (recordPredicate != null) {
    records.asSequence().filter(recordPredicate).count()
  } else {
    records.getCount()
  }

  private val aggregateData: SparseArrayCompat<ContactSearchData> = SparseArrayCompat()

  init {
    records.moveToPosition(-1)
  }

  fun getSize(): Int {
    val contentMaximum = section.expandConfig?.let {
      if (it.isExpanded) Int.MAX_VALUE else (it.maxCountWhenNotExpanded(activeContactCount) + 1)
    } ?: Int.MAX_VALUE

    val contentAndExpanded = min(contentMaximum, contentSize)

    return contentAndExpanded + (if (contentAndExpanded > 0 && section.includeHeader) 1 else 0)
  }

  fun getSublist(start: Int, end: Int): List<ContactSearchData> {
    if (start == end) {
      return emptyList()
    }

    val results = mutableListOf<ContactSearchData>()

    val startOffset = if (start == 0 && section.includeHeader) {
      results.add(ContactSearchData.Header(section.sectionKey, section.headerAction))
      1
    } else {
      0
    }

    val (expand, endOffset) = if (end == getSize() && shouldDisplayExpandRow()) {
      ContactSearchData.Expand(section.sectionKey) to 1
    } else {
      null to 0
    }

    val windowOffset = start + startOffset - if (section.includeHeader) 1 else 0
    val windowLimit = end - windowOffset - if (section.includeHeader) 1 else 0

    fillDataWindow(windowOffset, windowLimit)

    for (i in (start + startOffset) until (end - endOffset)) {
      val correctedIndex = if (section.includeHeader) i - 1 else i
      results.add(getItemAtCorrectedIndex(correctedIndex))
    }

    if (expand != null) {
      results.add(expand)
    }

    return results
  }

  open fun getItemAtCorrectedIndex(correctedIndex: Int): ContactSearchData {
    return if (recordPredicate == null) {
      records.moveToPosition(correctedIndex - 1)
      recordMapper.invoke(records.next())
    } else {
      aggregateData.get(correctedIndex)!!
    }
  }

  open fun fillDataWindow(offset: Int, limit: Int) {
    if (recordPredicate == null) {
      return
    }

    if (isAggregateDataFilled(offset, limit)) {
      return
    }

    var key = offset
    records.moveToPosition(-1)
    records.asSequence().filter(recordPredicate).drop(offset).take(limit).forEach {
      aggregateData.put(key, recordMapper.invoke(it))
      key++
    }

    if (!isAggregateDataFilled(offset, limit)) {
      error("Data integrity failure: ${section.sectionKey} requesting $offset , $limit")
    }
  }

  private fun isAggregateDataFilled(startOffset: Int, limit: Int): Boolean {
    return (startOffset until (startOffset + limit)).all { aggregateData.containsKey(it) }
  }

  private fun shouldDisplayExpandRow(): Boolean {
    val expandConfig = section.expandConfig
    return when {
      expandConfig == null || expandConfig.isExpanded -> false
      else -> contentSize > expandConfig.maxCountWhenNotExpanded(activeContactCount) + 1
    }
  }
}
