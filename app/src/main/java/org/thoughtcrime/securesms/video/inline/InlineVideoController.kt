/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.inline

import android.view.View
import androidx.annotation.MainThread
import androidx.core.view.children
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.RecyclerView
import org.signal.core.util.logging.Log
import org.signal.video.exo.ExoPlayerPool
import org.thoughtcrime.securesms.dependencies.AppDependencies

/**
 * Plays video in the [maxSlots] cells of a [RecyclerView] nearest its vertical center, moving a small, reused set of
 * [InlineVideoSlot]s between cells as the list scrolls.
 */
@MainThread
class InlineVideoController private constructor(
  private val recyclerView: RecyclerView,
  private val maxSlots: Int,
  private val pool: ExoPlayerPool<ExoPlayer>
) : DefaultLifecycleObserver, RecyclerView.OnChildAttachStateChangeListener, View.OnLayoutChangeListener, InlineVideoSlot.Listener {

  companion object {
    private val TAG = Log.tag(InlineVideoController::class)

    @JvmStatic
    @JvmOverloads
    fun attach(
      recyclerView: RecyclerView,
      lifecycleOwner: LifecycleOwner,
      maxSlots: Int,
      pool: ExoPlayerPool<ExoPlayer> = AppDependencies.exoPlayerPool
    ): InlineVideoController {
      val controller = InlineVideoController(recyclerView, maxSlots, pool)

      recyclerView.addOnScrollListener(controller.scrollListener)
      recyclerView.addOnChildAttachStateChangeListener(controller)
      recyclerView.addOnLayoutChangeListener(controller)
      recyclerView.adapter?.registerAdapterDataObserver(controller.dataObserver)
      lifecycleOwner.lifecycle.addObserver(controller)

      return controller
    }

    @JvmStatic
    fun attachForConversation(recyclerView: RecyclerView, lifecycleOwner: LifecycleOwner): InlineVideoController {
      val pool = AppDependencies.exoPlayerPool
      return attach(recyclerView, lifecycleOwner, pool.getPoolStats().maxUnreserved / 3, pool)
    }
  }

  private val slots = mutableListOf<InlineVideoSlot>()
  private val hostedBy = mutableMapOf<InlineVideoSlot, Hosting>()

  /** Least recently used first. */
  private val idle = linkedSetOf<InlineVideoSlot>()

  private var started = false
  private var passPosted = false

  private val held = mutableSetOf<InlineVideoCell>()

  private val pass = Runnable {
    passPosted = false
    performPass()
  }

  private val scrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = requestPass()
  }

  private val dataObserver = object : RecyclerView.AdapterDataObserver() {
    override fun onChanged() = requestPass()
    override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = requestPass()
    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = requestPass()
    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = requestPass()
    override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = requestPass()
  }

  override fun onResume(owner: LifecycleOwner) {
    started = true

    slots.toList().forEach { slot ->
      val player = pool.get(TAG)
      if (player != null) {
        slot.bindPlayer(player)
      } else {
        Log.w(TAG, "Could not re-acquire a player, dropping a slot.")
        discard(slot)
      }
    }

    requestPass()
  }

  override fun onPause(owner: LifecycleOwner) {
    started = false
    held.clear()
    recyclerView.removeCallbacks(pass)
    passPosted = false

    hostedBy.values.forEach { it.cell.showStill() }
    slots.forEach { slot -> slot.unbindPlayer()?.let { pool.pool(it) } }
  }

  override fun onDestroy(owner: LifecycleOwner) {
    recyclerView.removeOnScrollListener(scrollListener)
    recyclerView.removeOnChildAttachStateChangeListener(this)
    recyclerView.removeOnLayoutChangeListener(this)
    recyclerView.adapter?.unregisterAdapterDataObserver(dataObserver)

    slots.toList().forEach { discard(it) }
  }

  /**
   * Stops [cell]'s video and keeps it on its still until the next pause, e.g. during a shared element transition.
   */
  fun hold(cell: InlineVideoCell) {
    held += cell
    hostedBy.entries.firstOrNull { it.value.cell === cell }?.let { release(it.key) }
  }

  override fun onChildViewAttachedToWindow(view: View) = requestPass()

  override fun onChildViewDetachedFromWindow(view: View) {
    val cell = cellFor(view) ?: return
    hostedBy.entries.firstOrNull { it.value.cell === cell }?.let { release(it.key) }
    requestPass()
  }

  override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
    requestPass()
  }

  override fun onFirstFrameRendered(slot: InlineVideoSlot) {
    hostedBy[slot]?.cell?.hideStill()
  }

  override fun onPlaybackEnded(slot: InlineVideoSlot) {
    hostedBy[slot]?.cell?.showStill()
  }

  private fun requestPass() {
    if (started && !passPosted) {
      passPosted = true
      recyclerView.postOnAnimation(pass)
    }
  }

  private fun performPass() {
    if (!started) {
      return
    }

    val playbackSet = selectPlaybackSet()
    val playingCells = playbackSet.mapTo(mutableSetOf()) { it.cell }

    hostedBy.entries
      .filter { (_, hosting) -> hosting.cell !in playingCells }
      .map { it.key }
      .forEach { release(it) }

    for (candidate in playbackSet) {
      val slot = candidate.host.slot ?: acquireFor(candidate.mediaItem)?.also { host(it, candidate) } ?: continue
      play(slot, candidate)
    }
  }

  private fun cellFor(view: View): InlineVideoCell? {
    return recyclerView.getChildViewHolder(view) as? InlineVideoCell ?: view as? InlineVideoCell
  }

  private fun selectPlaybackSet(): List<Candidate> {
    val candidates = recyclerView.children
      .filter { it.bottom > 0 && it.top < recyclerView.height }
      .mapNotNull { view ->
        val cell = cellFor(view) ?: return@mapNotNull null
        if (cell in held || !cell.canPlayContent()) {
          return@mapNotNull null
        }

        val host = cell.surfaceHost ?: return@mapNotNull null
        val mediaItem = cell.mediaItem ?: return@mapNotNull null
        Candidate(cell, host, mediaItem, center = view.top + view.translationY + view.height / 2f)
      }
      .toList()

    val viewportCenter = (recyclerView.paddingTop + recyclerView.height - recyclerView.paddingBottom) / 2f
    return InlineVideoPlaybackPlan.selectNearestCenter(candidates, viewportCenter, maxSlots) { it.center }
  }

  private fun acquireFor(mediaItem: MediaItem): InlineVideoSlot? {
    return InlineVideoPlaybackPlan.pickIdleSlot(idle, mediaItem) { it.mediaItem } ?: createSlot()
  }

  private fun createSlot(): InlineVideoSlot? {
    if (slots.size >= maxSlots) {
      return null
    }

    val player = pool.get(TAG)
    if (player == null) {
      Log.d(TAG, "No player available, leaving cell still.")
      return null
    }

    return InlineVideoSlot(recyclerView.context).also { slot ->
      slot.listener = this
      slot.bindPlayer(player)
      slots += slot
    }
  }

  private fun host(slot: InlineVideoSlot, candidate: Candidate) {
    idle -= slot
    candidate.host.attach(slot)
    slot.setLoopPolicy(candidate.cell.loopPolicy)
    hostedBy[slot] = Hosting(candidate.cell, candidate.host)
  }

  private fun play(slot: InlineVideoSlot, candidate: Candidate) {
    slot.setMedia(candidate.mediaItem)

    if (slot.hasPlaybackEnded) {
      if (!candidate.cell.isPlaybackRequested) {
        candidate.cell.showStill()
        return
      }

      slot.replay(candidate.cell.loopPolicy)
    }

    if (slot.hasRenderedFirstFrame) {
      candidate.cell.hideStill()
    } else {
      candidate.cell.showStill()
    }

    slot.play()
  }

  private fun release(slot: InlineVideoSlot) {
    hostedBy.remove(slot)?.let { (cell, host) ->
      cell.showStill()
      host.detach()
    }

    slot.pause()
    idle -= slot
    idle += slot
  }

  private fun discard(slot: InlineVideoSlot) {
    hostedBy.remove(slot)?.let { (cell, host) ->
      cell.showStill()
      host.detach()
    }

    idle -= slot
    slots -= slot

    slot.release()?.let { pool.pool(it) }
  }

  private class Candidate(val cell: InlineVideoCell, val host: InlineVideoHost, val mediaItem: MediaItem, val center: Float)

  private data class Hosting(val cell: InlineVideoCell, val host: InlineVideoHost)
}
