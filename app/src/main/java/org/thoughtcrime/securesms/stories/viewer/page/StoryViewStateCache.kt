package org.thoughtcrime.securesms.stories.viewer.page

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.util.ParcelUtil

/**
 * Activity-bounds ViewModel which tracks the viewed state for stories.
 */
class StoryViewStateCache() : Parcelable {

  private val viewStateMap: MutableMap<Long, Boolean> = mutableMapOf()

  constructor(parcel: Parcel) : this() {
    synchronized(this) {
      val entries: Collection<Entry> = ParcelUtil.readParcelableCollection(parcel, Entry::class.java)
      entries.forEach {
        viewStateMap[it.storyId] = it.hasSelfViewed
      }
    }
  }

  fun putAll(cache: StoryViewStateCache) {
    synchronized(this) {
      viewStateMap.putAll(cache.viewStateMap)
    }
  }

  /**
   * If storyId is in our map, return its value. Otherwise, insert and return the given state.
   */
  fun getOrPut(storyId: Long, hasSelfViewed: Boolean): Boolean {
    synchronized(this) {
      return if (viewStateMap.containsKey(storyId)) {
        viewStateMap[storyId]!!
      } else {
        viewStateMap[storyId] = hasSelfViewed
        hasSelfViewed
      }
    }
  }

  override fun writeToParcel(parcel: Parcel, flags: Int) {
    ParcelUtil.writeParcelableCollection(parcel, viewStateMap.map { Entry(it.key, it.value) })
  }

  override fun describeContents(): Int {
    return 0
  }

  companion object CREATOR : Parcelable.Creator<StoryViewStateCache> {
    override fun createFromParcel(parcel: Parcel): StoryViewStateCache {
      return StoryViewStateCache(parcel)
    }

    override fun newArray(size: Int): Array<StoryViewStateCache?> {
      return arrayOfNulls(size)
    }
  }

  @Parcelize
  data class Entry(
    val storyId: Long,
    val hasSelfViewed: Boolean
  ) : Parcelable
}
