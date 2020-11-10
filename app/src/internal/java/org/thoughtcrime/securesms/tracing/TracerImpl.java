package org.thoughtcrime.securesms.tracing;

import android.os.SystemClock;
import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.trace.TraceProtos;
import org.thoughtcrime.securesms.trace.TraceProtos.Trace;
import org.thoughtcrime.securesms.trace.TraceProtos.TracePacket;
import org.thoughtcrime.securesms.trace.TraceProtos.TrackDescriptor;
import org.thoughtcrime.securesms.trace.TraceProtos.TrackEvent;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A class to create Perfetto-compatible traces. Currently keeps the entire trace in memory to
 * avoid weirdness with synchronizing to disk.
 *
 * Some general info on how the Perfetto format works:
 * - The file format is just a Trace proto (see Trace.proto)
 * - The Trace proto is just a series of TracePackets
 * - TracePackets can describe:
 *   - Threads
 *   - Start of a method
 *   - End of a method
 *   - (And a bunch of other stuff that's not relevant to use at this point)
 *
 * We keep a circular buffer of TracePackets for method calls, and we keep a separate list of
 * TracePackets for threads so we don't lose any of those.
 *
 * Serializing is just a matter of throwing all the TracePackets we have into a proto.
 *
 * Note: This class aims to be largely-thread-safe, but prioritizes speed and memory efficiency
 * above all else. These methods are going to be called very quickly from every thread imaginable,
 * and we want to create as little overhead as possible. The idea being that it's ok if we don't,
 * for example, keep a perfect circular buffer size if it allows us to reduce overhead. The only
 * cost of screwing up would be dropping a trace packet or something, which, while sad, won't affect
 * how the app functions.
 */
public final class TracerImpl implements Tracer {

  private static final int    TRUSTED_SEQUENCE_ID    = 1;
  private static final byte[] SYNCHRONIZATION_MARKER = UuidUtil.toByteArray(UUID.fromString("82477a76-b28d-42ba-81dc-33326d57a079"));

  private final Clock                  clock;
  private final Map<Long, TracePacket> threadPackets;
  private final Queue<TracePacket>     eventPackets;
  private final AtomicInteger          eventCount;

  TracerImpl() {
    this.clock         = SystemClock::elapsedRealtimeNanos;
    this.threadPackets = new ConcurrentHashMap<>();
    this.eventPackets  = new ConcurrentLinkedQueue<>();
    this.eventCount    = new AtomicInteger(0);
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void start(@NonNull String methodName) {
    long   time          = clock.getTimeNanos();
    Thread currentThread = Thread.currentThread();

    if (!threadPackets.containsKey(currentThread.getId())) {
      threadPackets.put(currentThread.getId(), forThread(currentThread));
    }

    addPacket(forMethodStart(methodName, time, currentThread.getId()));
  }

  @Override
  public void start(@NonNull String methodName, @NonNull String key, @NonNull String value) {
    long   time          = clock.getTimeNanos();
    Thread currentThread = Thread.currentThread();

    if (!threadPackets.containsKey(currentThread.getId())) {
      threadPackets.put(currentThread.getId(), forThread(currentThread));
    }

    addPacket(forMethodStart(methodName, time, currentThread.getId(), key, value));
  }

  @Override
  public void end(@NonNull String methodName) {
    addPacket(forMethodEnd(methodName, clock.getTimeNanos(), Thread.currentThread().getId()));
  }

  public @NonNull byte[] serialize() {
    Trace.Builder trace = Trace.newBuilder();

    for (TracePacket thread : threadPackets.values()) {
      trace.addPacket(thread);
    }

    for (TracePacket event : eventPackets) {
      trace.addPacket(event);
    }

    trace.addPacket(forSynchronization(clock.getTimeNanos()));

    return trace.build().toByteArray();
  }

  /**
   * Attempts to add a packet to our list while keeping the size of our circular buffer in-check.
   * The tracking of the event count is not perfectly thread-safe, but doing it in a thread-safe
   * way would likely involve adding a lock, which we really don't want to do, since it'll add
   * unnecessary overhead.
   *
   * Note that we keep track of the event count separately because
   * {@link ConcurrentLinkedQueue#size()} is NOT a constant-time operation.
   */
  private void addPacket(@NonNull TracePacket packet) {
    eventPackets.add(packet);

    int size = eventCount.incrementAndGet();

    for (int i = size; i > BuildConfig.TRACE_EVENT_MAX; i--) {
      eventPackets.poll();
      eventCount.decrementAndGet();
    }
  }

  private static TracePacket forThread(@NonNull Thread thread) {
    return TracePacket.newBuilder()
                      .setTrustedPacketSequenceId(TRUSTED_SEQUENCE_ID)
                      .setTrackDescriptor(TrackDescriptor.newBuilder()
                                                         .setUuid(thread.getId())
                                                         .setName(thread.getName()))
                      .build();

  }

  private static TracePacket forMethodStart(@NonNull String name, long time, long threadId) {
    return TracePacket.newBuilder()
                      .setTrustedPacketSequenceId(TRUSTED_SEQUENCE_ID)
                      .setTimestamp(time)
                      .setTrackEvent(TrackEvent.newBuilder()
                                               .setTrackUuid(threadId)
                                               .setName(name)
                                               .setType(TrackEvent.Type.TYPE_SLICE_BEGIN))
                      .build();
  }

  private static TracePacket forMethodStart(@NonNull String name, long time, long threadId, @NonNull String key, @NonNull String value) {
    return TracePacket.newBuilder()
                      .setTrustedPacketSequenceId(TRUSTED_SEQUENCE_ID)
                      .setTimestamp(time)
                      .setTrackEvent(TrackEvent.newBuilder()
                                               .setTrackUuid(threadId)
                                               .setName(name)
                                               .setType(TrackEvent.Type.TYPE_SLICE_BEGIN)
                                               .addDebugAnnotations(TraceProtos.DebugAnnotation.newBuilder()
                                                                                               .setName(key)
                                                                                               .setStringValue(value)
                                                                                               .build()))
                      .build();
  }

  private static TracePacket forMethodEnd(@NonNull String name, long time, long threadId) {
    return TracePacket.newBuilder()
                      .setTrustedPacketSequenceId(TRUSTED_SEQUENCE_ID)
                      .setTimestamp(time)
                      .setTrackEvent(TrackEvent.newBuilder()
                                               .setTrackUuid(threadId)
                                               .setName(name)
                                               .setType(TrackEvent.Type.TYPE_SLICE_END))
                      .build();
  }

  private static TracePacket forSynchronization(long time) {
    return TracePacket.newBuilder()
                      .setTrustedPacketSequenceId(TRUSTED_SEQUENCE_ID)
                      .setTimestamp(time)
                      .setSynchronizationMarker(ByteString.copyFrom(SYNCHRONIZATION_MARKER))
                      .build();
  }

  private interface Clock {
    long getTimeNanos();
  }
}
