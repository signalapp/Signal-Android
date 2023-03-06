package org.thoughtcrime.securesms.video.exo

import android.content.Context
import androidx.annotation.MainThread
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.util.MimeTypes
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.net.ContentProxySelector
import org.thoughtcrime.securesms.util.AppForegroundObserver
import org.thoughtcrime.securesms.util.DeviceProperties
import kotlin.time.Duration.Companion.seconds

/**
 * ExoPlayerPool concrete instance which helps to manage a pool of SimpleExoPlayer objects
 */
class SimpleExoPlayerPool(context: Context) : ExoPlayerPool<ExoPlayer>(MAXIMUM_RESERVED_PLAYERS) {
  private val context: Context = context.applicationContext
  private val okHttpClient = ApplicationDependencies.getOkHttpClient().newBuilder().proxySelector(ContentProxySelector()).build()
  private val dataSourceFactory: DataSource.Factory = SignalDataSource.Factory(ApplicationDependencies.getApplication(), okHttpClient, null)
  private val mediaSourceFactory: MediaSource.Factory = DefaultMediaSourceFactory(dataSourceFactory)

  init {
    ApplicationDependencies.getAppForegroundObserver().addListener(this)
  }

  /**
   * Tries to get the max number of instances that can be played back on the screen at a time, based off of
   * the device API level and decoder info.
   */
  override fun getMaxSimultaneousPlayback(): Int {
    val maxInstances = try {
      val info = MediaCodecUtil.getDecoderInfo(MimeTypes.VIDEO_H264, false, false)
      if (info != null && info.maxSupportedInstances > 0) {
        info.maxSupportedInstances
      } else {
        0
      }
    } catch (ignored: DecoderQueryException) {
      0
    }

    if (maxInstances > 0) {
      return maxInstances
    }

    return if (DeviceProperties.isLowMemoryDevice(ApplicationDependencies.getApplication())) {
      MAXIMUM_SUPPORTED_PLAYBACK_PRE_23_LOW_MEM
    } else {
      MAXIMUM_SUPPORTED_PLAYBACK_PRE_23
    }
  }

  @MainThread
  override fun createPlayer(): ExoPlayer {
    return ExoPlayer.Builder(context)
      .setMediaSourceFactory(mediaSourceFactory)
      .setSeekBackIncrementMs(SEEK_INTERVAL.inWholeMilliseconds)
      .setSeekForwardIncrementMs(SEEK_INTERVAL.inWholeMilliseconds)
      .build()
  }

  companion object {
    private const val MAXIMUM_RESERVED_PLAYERS = 1
    private const val MAXIMUM_SUPPORTED_PLAYBACK_PRE_23 = 6
    private const val MAXIMUM_SUPPORTED_PLAYBACK_PRE_23_LOW_MEM = 3
    private val SEEK_INTERVAL = 15.seconds
  }
}

/**
 * ExoPlayer pool which allows for the quick and efficient reuse of ExoPlayer instances instead of creating and destroying them
 * as needed. This class will, if added as an AppForegroundObserver.Listener, evict players when the app is backgrounded to try to
 * make sure it is a good citizen on the device.
 *
 * This class also supports reserving a number of players, which count against its total specified by getMaxSimultaneousPlayback. These
 * players will be returned first when a player is requested via require.
 */
abstract class ExoPlayerPool<T : ExoPlayer>(
  private val maximumReservedPlayers: Int
) : AppForegroundObserver.Listener {

  companion object {
    private val TAG = Log.tag(ExoPlayerPool::class.java)
  }

  private val pool: MutableMap<T, PoolState> = mutableMapOf()

  /**
   * Try to get a player from the non-reserved pool.
   *
   * @return A player if one is available, otherwise null
   */
  @MainThread
  fun get(tag: String): T? {
    return get(allowReserved = false, tag = tag)
  }

  /**
   * Get a player, preferring reserved players.
   *
   * @return A non-null player instance. If one is not available, an exception is thrown.
   * @throws IllegalStateException if no player is available.
   */
  @MainThread
  fun require(tag: String): T {
    return checkNotNull(get(allowReserved = true, tag = tag)) { "Required exoPlayer could not be acquired for $tag! :: ${poolStats()}" }
  }

  /**
   * Returns a player to the pool. If the player is not from the pool, an exception is thrown.
   *
   * @throws IllegalArgumentException if the player passed is not in the pool
   */
  @MainThread
  fun pool(exoPlayer: T) {
    val poolState = pool[exoPlayer]
    if (poolState != null) {
      pool[exoPlayer] = poolState.copy(available = true, tag = null)
    } else {
      throw IllegalArgumentException("Tried to return unknown ExoPlayer to pool :: ${poolStats()}")
    }
  }

  @MainThread
  private fun get(allowReserved: Boolean, tag: String): T? {
    val player = findAvailablePlayer(allowReserved)
    val toReturn = if (player == null && pool.size < getMaximumAllowed(allowReserved)) {
      val newPlayer = createPlayer()
      val poolState = createPoolStateForNewEntry(allowReserved, tag)
      pool[newPlayer] = poolState
      newPlayer
    } else if (player != null) {
      val poolState = pool[player]!!.copy(available = false, tag = tag)
      pool[player] = poolState
      player
    } else {
      Log.d(TAG, "Failed to get an ExoPlayer instance for tag: $tag :: ${poolStats()}")
      null
    }

    return toReturn?.apply {
      configureForVideoPlayback()
    }
  }

  private fun getMaximumAllowed(allowReserved: Boolean): Int {
    return if (allowReserved) getMaxSimultaneousPlayback() else getMaxSimultaneousPlayback() - maximumReservedPlayers
  }

  private fun createPoolStateForNewEntry(allowReserved: Boolean, tag: String?): PoolState {
    return if (allowReserved && pool.none { (_, v) -> v.reserved }) {
      PoolState(available = false, reserved = true, tag = tag)
    } else {
      PoolState(available = false, reserved = false, tag = tag)
    }
  }

  private fun findAvailablePlayer(allowReserved: Boolean): T? {
    return if (allowReserved) {
      findFirstReservedAndAvailablePlayer() ?: findFirstUnreservedAndAvailablePlayer()
    } else {
      findFirstUnreservedAndAvailablePlayer()
    }
  }

  private fun findFirstReservedAndAvailablePlayer(): T? {
    return pool.filter { (_, v) -> v.reservedAndAvailable }.keys.firstOrNull()
  }

  private fun findFirstUnreservedAndAvailablePlayer(): T? {
    return pool.filter { (_, v) -> v.unreservedAndAvailable }.keys.firstOrNull()
  }

  protected abstract fun createPlayer(): T

  @MainThread
  override fun onBackground() {
    val playersToRelease = pool.filter { (_, v) -> v.available }.keys
    pool -= playersToRelease

    playersToRelease.forEach { it.release() }
  }

  private fun poolStats(): String {
    return getPoolStats().toString()
  }

  fun getPoolStats(): PoolStats {
    val poolStats = PoolStats(
      created = pool.size,
      maxUnreserved = getMaxSimultaneousPlayback() - maximumReservedPlayers,
      maxReserved = maximumReservedPlayers,
      owners = emptyList()
    )

    return pool.values.fold(poolStats) { acc, state ->
      Log.d(TAG, "$state")
      acc.copy(
        unreservedAndAvailable = acc.unreservedAndAvailable + if (state.unreservedAndAvailable) 1 else 0,
        reservedAndAvailable = acc.reservedAndAvailable + if (state.reservedAndAvailable) 1 else 0,
        unreserved = acc.unreserved + if (!state.reserved) 1 else 0,
        reserved = acc.reserved + if (state.reserved) 1 else 0,
        owners = if (!state.available) acc.owners + OwnershipInfo(state.tag!!, state.reserved) else acc.owners
      )
    }
  }

  protected abstract fun getMaxSimultaneousPlayback(): Int

  data class PoolStats(
    val created: Int = 0,
    val maxUnreserved: Int = 0,
    val maxReserved: Int = 0,
    val unreservedAndAvailable: Int = 0,
    val reservedAndAvailable: Int = 0,
    val unreserved: Int = 0,
    val reserved: Int = 0,
    val owners: List<OwnershipInfo>
  )

  data class OwnershipInfo(
    val tag: String,
    val isReserved: Boolean
  )

  private data class PoolState(
    val available: Boolean,
    val reserved: Boolean,
    val tag: String?
  ) {
    val unreservedAndAvailable = available && !reserved
    val reservedAndAvailable = available && reserved
  }
}
