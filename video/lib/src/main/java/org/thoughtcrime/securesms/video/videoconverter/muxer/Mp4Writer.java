/*
 * Copyright (C) https://github.com/sannies/mp4parser/blob/master/LICENSE
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * https://github.com/sannies/mp4parser/blob/4ed724754cde751c3f27fdda51f288df4f4c5db5/streaming/src/main/java/org/mp4parser/streaming/output/mp4/StandardMp4Writer.java
 *
 * This file has been modified by Signal.
 */
package org.thoughtcrime.securesms.video.videoconverter.muxer;

import androidx.annotation.NonNull;

import org.mp4parser.Box;
import org.mp4parser.boxes.iso14496.part12.ChunkOffsetBox;
import org.mp4parser.boxes.iso14496.part12.CompositionTimeToSample;
import org.mp4parser.boxes.iso14496.part12.FileTypeBox;
import org.mp4parser.boxes.iso14496.part12.MediaHeaderBox;
import org.mp4parser.boxes.iso14496.part12.MovieBox;
import org.mp4parser.boxes.iso14496.part12.MovieHeaderBox;
import org.mp4parser.boxes.iso14496.part12.SampleSizeBox;
import org.mp4parser.boxes.iso14496.part12.SampleTableBox;
import org.mp4parser.boxes.iso14496.part12.SampleToChunkBox;
import org.mp4parser.boxes.iso14496.part12.SyncSampleBox;
import org.mp4parser.boxes.iso14496.part12.TimeToSampleBox;
import org.mp4parser.boxes.iso14496.part12.TrackBox;
import org.mp4parser.boxes.iso14496.part12.TrackHeaderBox;
import org.mp4parser.streaming.StreamingSample;
import org.mp4parser.streaming.StreamingTrack;
import org.mp4parser.streaming.extensions.CompositionTimeSampleExtension;
import org.mp4parser.streaming.extensions.CompositionTimeTrackExtension;
import org.mp4parser.streaming.extensions.SampleFlagsSampleExtension;
import org.mp4parser.streaming.extensions.TrackIdTrackExtension;
import org.mp4parser.streaming.output.SampleSink;
import org.mp4parser.streaming.output.mp4.DefaultBoxes;
import org.mp4parser.tools.Mp4Arrays;
import org.mp4parser.tools.Mp4Math;
import org.mp4parser.tools.Path;
import org.signal.core.util.logging.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

import static org.mp4parser.tools.CastUtils.l2i;

/**
 * Creates an MP4 file with ftyp, mdat+, moov order.
 * A very special property of this variant is that it written sequentially. You can start transferring the
 * data while the <code>sink</code> receives it. (in contrast to typical implementations which need random
 * access to write length fields at the beginning of the file)
 */
final class Mp4Writer extends DefaultBoxes implements SampleSink {

  private static final String TAG = "Mp4Writer";
  private static final Long UInt32_MAX = (1L << 32) - 1;

  private final WritableByteChannel  sink;
  private final List<StreamingTrack> source;
  private final Date                 creationTime = new Date();

  private boolean hasWrittenMdat = false;

  /**
   * Contains the start time of the next segment in line that will be created.
   */
  private final Map<StreamingTrack, Long>                  nextChunkCreateStartTime = new ConcurrentHashMap<>();
  /**
   * Contains the start time of the next segment in line that will be written.
   */
  private final Map<StreamingTrack, Long>                  nextChunkWriteStartTime  = new ConcurrentHashMap<>();
  /**
   * Contains the next sample's start time.
   */
  private final Map<StreamingTrack, Long>                  nextSampleStartTime      = new HashMap<>();
  /**
   * Buffers the samples per track until there are enough samples to form a Segment.
   */
  private final Map<StreamingTrack, List<StreamingSample>> sampleBuffers            = new HashMap<>();
  private final Map<StreamingTrack, TrackBox>              trackBoxes               = new HashMap<>();
  /**
   * Buffers segments until it's time for a segment to be written.
   */
  private final Map<StreamingTrack, Queue<ChunkContainer>> chunkBuffers             = new ConcurrentHashMap<>();
  private final Map<StreamingTrack, Long>                  chunkNumbers             = new HashMap<>();
  private final Map<StreamingTrack, Long>                  sampleNumbers            = new HashMap<>();
  private       long                                       bytesWritten             = 0;

  private long mMDatTotalContentLength = 0;
  
  Mp4Writer(final @NonNull List<StreamingTrack> source, final @NonNull WritableByteChannel sink) throws IOException {
    this.source = new ArrayList<>(source);
    this.sink   = sink;

    final HashSet<Long> trackIds = new HashSet<>();
    for (StreamingTrack streamingTrack : source) {
      streamingTrack.setSampleSink(this);
      chunkNumbers.put(streamingTrack, 1L);
      sampleNumbers.put(streamingTrack, 1L);
      nextSampleStartTime.put(streamingTrack, 0L);
      nextChunkCreateStartTime.put(streamingTrack, 0L);
      nextChunkWriteStartTime.put(streamingTrack, 0L);
      sampleBuffers.put(streamingTrack, new ArrayList<>());
      chunkBuffers.put(streamingTrack, new LinkedList<>());
      if (streamingTrack.getTrackExtension(TrackIdTrackExtension.class) != null) {
        final TrackIdTrackExtension trackIdTrackExtension = streamingTrack.getTrackExtension(TrackIdTrackExtension.class);
        if (trackIds.contains(trackIdTrackExtension.getTrackId())) {
          throw new MuxingException("There may not be two tracks with the same trackID within one file");
        }
        trackIds.add(trackIdTrackExtension.getTrackId());
      }
    }
    for (StreamingTrack streamingTrack : source) {
      if (streamingTrack.getTrackExtension(TrackIdTrackExtension.class) == null) {
        long maxTrackId = 0;
        for (Long trackId : trackIds) {
          maxTrackId = Math.max(trackId, maxTrackId);
        }
        final TrackIdTrackExtension tiExt = new TrackIdTrackExtension(maxTrackId + 1);
        trackIds.add(tiExt.getTrackId());
        streamingTrack.addTrackExtension(tiExt);
      }
    }

    final List<String> minorBrands = new LinkedList<>();
    minorBrands.add("isom");
    minorBrands.add("mp42");
    write(sink, new FileTypeBox("mp42", 0, minorBrands));
  }

  public void close() throws IOException {
    for (StreamingTrack streamingTrack : source) {
      writeChunkContainer(createChunkContainer(streamingTrack));
      streamingTrack.close();
    }
    write(sink, createMoov());
    hasWrittenMdat = false;
  }

  public long getTotalMdatContentLength() {
    return mMDatTotalContentLength;
  }

  private Box createMoov() {
    final MovieBox movieBox = new MovieBox();

    final MovieHeaderBox mvhd = createMvhd();
    movieBox.addBox(mvhd);

    // update durations
    for (StreamingTrack streamingTrack : source) {
      final TrackBox       tb   = trackBoxes.get(streamingTrack);
      final MediaHeaderBox mdhd = Path.getPath(tb, "mdia[0]/mdhd[0]");
      mdhd.setCreationTime(creationTime);
      mdhd.setModificationTime(creationTime);
      final Long mediaHeaderDuration = Objects.requireNonNull(nextSampleStartTime.get(streamingTrack));
      if (mediaHeaderDuration >= UInt32_MAX) {
        mdhd.setVersion(1);
      }
      mdhd.setDuration(mediaHeaderDuration);
      mdhd.setTimescale(streamingTrack.getTimescale());
      mdhd.setLanguage(streamingTrack.getLanguage());
      movieBox.addBox(tb);

      final TrackHeaderBox tkhd     = Path.getPath(tb, "tkhd[0]");
      final double         duration = (double) mediaHeaderDuration / streamingTrack.getTimescale();
      tkhd.setCreationTime(creationTime);
      tkhd.setModificationTime(creationTime);
      final long trackHeaderDuration = (long) (mvhd.getTimescale() * duration);
      if (trackHeaderDuration >= UInt32_MAX) {
        tkhd.setVersion(1);
      }
      tkhd.setDuration(trackHeaderDuration);
    }

    // metadata here
    return movieBox;
  }

  private void sortTracks() {
    Collections.sort(source, (o1, o2) -> {
      // compare times and account for timestamps!
      final long a = Objects.requireNonNull(nextChunkWriteStartTime.get(o1)) * o2.getTimescale();
      final long b = Objects.requireNonNull(nextChunkWriteStartTime.get(o2)) * o1.getTimescale();
      return (int) Math.signum(a - b);
    });
  }

  @Override
  protected MovieHeaderBox createMvhd() {
    final MovieHeaderBox mvhd = new MovieHeaderBox();
    mvhd.setVersion(1);
    mvhd.setCreationTime(creationTime);
    mvhd.setModificationTime(creationTime);


    long[] timescales = new long[0];
    long   maxTrackId = 0;
    double duration   = 0;
    for (StreamingTrack streamingTrack : source) {
      duration   = Math.max((double) Objects.requireNonNull(nextSampleStartTime.get(streamingTrack)) / streamingTrack.getTimescale(), duration);
      timescales = Mp4Arrays.copyOfAndAppend(timescales, streamingTrack.getTimescale());
      maxTrackId = Math.max(streamingTrack.getTrackExtension(TrackIdTrackExtension.class).getTrackId(), maxTrackId);
    }


    long chosenTimescale = Mp4Math.lcm(timescales);
    Log.d(TAG, "chosenTimescale = " + chosenTimescale);
    final long MAX_UNSIGNED_INT = 0xFFFFFFFFL;
    if (chosenTimescale > MAX_UNSIGNED_INT) {
      int nRatio = (int)(chosenTimescale / MAX_UNSIGNED_INT);
      Log.d(TAG, "chosenTimescale exceeds 32-bit range " + nRatio + " times !");
      int nDownscaleFactor = 1;
      if (nRatio < 10) {
        nDownscaleFactor = 10;
      } else if (nRatio < 100) {
        nDownscaleFactor = 100;
      } else if (nRatio < 1000) {
        nDownscaleFactor = 1000;
      } else if (nRatio < 10000) {
        nDownscaleFactor = 10000;
      }
      chosenTimescale /= nDownscaleFactor;
      Log.d(TAG, "chosenTimescale is scaled down by factor of " + nDownscaleFactor + " to value " + chosenTimescale);
    }

    double fDurationTicks = chosenTimescale * duration;
    Log.d(TAG, "fDurationTicks = chosenTimescale * duration = " + fDurationTicks);
    final double MAX_UNSIGNED_64_BIT_VALUE = 18446744073709551615.0;
    if (fDurationTicks > MAX_UNSIGNED_64_BIT_VALUE) {
      // Highly unlikely, as duration (number of seconds)
      // would need to be larger than MAX_UNSIGNED_INT
      // to produce fDuration = chosenTimescale * duration
      // which whould exceed 64-bit storage
      Log.d(TAG, "Numeric overflow !!!");
    }
    mvhd.setTimescale(chosenTimescale);
    mvhd.setDuration((long) (fDurationTicks));
    // find the next available trackId
    mvhd.setNextTrackId(maxTrackId + 1);
    return mvhd;
  }

  private void write(final @NonNull WritableByteChannel out, Box... boxes) throws IOException {
    for (Box box1 : boxes) {
      box1.getBox(out);
      bytesWritten += box1.getSize();
    }
  }

  /**
   * Tests if the currently received samples for a given track
   * are already a 'chunk' as we want to have it. The next
   * sample will not be part of the chunk
   * will be added to the fragment buffer later.
   *
   * @param streamingTrack track to test
   * @param next           the lastest samples
   * @return true if a chunk is to b e created.
   */
  private boolean isChunkReady(StreamingTrack streamingTrack, StreamingSample next) {
    final long ts   = Objects.requireNonNull(nextSampleStartTime.get(streamingTrack));
    final long cfst = Objects.requireNonNull(nextChunkCreateStartTime.get(streamingTrack));

    return (ts >= cfst + 2 * streamingTrack.getTimescale());
    // chunk interleave of 2 seconds
  }

  private void writeChunkContainer(ChunkContainer chunkContainer) throws IOException {
    final TrackBox       tb   = trackBoxes.get(chunkContainer.streamingTrack);
    final ChunkOffsetBox stco = Objects.requireNonNull(Path.getPath(tb, "mdia[0]/minf[0]/stbl[0]/stco[0]"));
    final int extraChunkOffset = hasWrittenMdat ? 0 : 8;
    stco.setChunkOffsets(Mp4Arrays.copyOfAndAppend(stco.getChunkOffsets(), bytesWritten + extraChunkOffset));
    chunkContainer.mdat.includeHeader = !hasWrittenMdat;
    write(sink, chunkContainer.mdat);

    mMDatTotalContentLength += chunkContainer.mdat.getSize();

    if (!hasWrittenMdat) {
      hasWrittenMdat = true;
    }
  }

  public void acceptSample(
          final @NonNull StreamingSample streamingSample,
          final @NonNull StreamingTrack streamingTrack) throws IOException
  {
    if (streamingSample.getContent().limit() == 0) {
      //
      // For currently unknown reason, the STSZ table of AAC audio stream
      // related to the very last chunk comes with the extra table elements
      // whose value is zero.
      //
      // The ISO MP4 spec does not absolutely prohibit such a case, but strongly
      // stipulates that the stream has to have the inner logic to support
      // the zero length audio frames (QCELP happens to be one such example).
      //
      // Spec excerpt:
      // ----------------------------------------------------------------------
      // 8.7.3 Sample Size Boxes
      // 8.7.3.1 Definition
      // ...
      // NOTE A sample size of zero is not prohibited in general, but it
      // must be valid and defined for the coding system, as defined by
      // the sample entry, that the sample belongs to
      // ----------------------------------------------------------------------
      //
      // In all other cases, having zero STSZ table values is very illogical
      // and may pose the problems down the road. Here we will eliminate such
      // samples from all the related bookkeeping
      //
      Log.d(TAG, "skipping zero-sized sample");
      return;
    }
    TrackBox tb = trackBoxes.get(streamingTrack);
    if (tb == null) {
      tb = new TrackBox();
      tb.addBox(createTkhd(streamingTrack));
      tb.addBox(createMdia(streamingTrack));
      trackBoxes.put(streamingTrack, tb);
    }

    if (isChunkReady(streamingTrack, streamingSample)) {

      final ChunkContainer chunkContainer = createChunkContainer(streamingTrack);
      //System.err.println("Creating fragment for " + streamingTrack);
      Objects.requireNonNull(sampleBuffers.get(streamingTrack)).clear();
      nextChunkCreateStartTime.put(streamingTrack, Objects.requireNonNull(nextChunkCreateStartTime.get(streamingTrack)) + chunkContainer.duration);
      final Queue<ChunkContainer> chunkQueue = Objects.requireNonNull(chunkBuffers.get(streamingTrack));
      chunkQueue.add(chunkContainer);
      if (source.get(0) == streamingTrack) {

        Queue<ChunkContainer> tracksFragmentQueue;
        StreamingTrack        currentStreamingTrack;
        // This will write AT LEAST the currently created fragment and possibly a few more
        while (!(tracksFragmentQueue = chunkBuffers.get((currentStreamingTrack = this.source.get(0)))).isEmpty()) {
          final ChunkContainer currentFragmentContainer = tracksFragmentQueue.remove();
          writeChunkContainer(currentFragmentContainer);
          Log.d(TAG, "write chunk " + currentStreamingTrack.getHandler() + ". duration " + (double) currentFragmentContainer.duration / currentStreamingTrack.getTimescale());
          final long ts = Objects.requireNonNull(nextChunkWriteStartTime.get(currentStreamingTrack)) + currentFragmentContainer.duration;
          nextChunkWriteStartTime.put(currentStreamingTrack, ts);
          Log.d(TAG, currentStreamingTrack.getHandler() + " track advanced to " + (double) ts / currentStreamingTrack.getTimescale());
          sortTracks();
        }
      } else {
        Log.d(TAG, streamingTrack.getHandler() + " track delayed, queue size is " + chunkQueue.size());
      }
    }

    Objects.requireNonNull(sampleBuffers.get(streamingTrack)).add(streamingSample);
    nextSampleStartTime.put(streamingTrack, Objects.requireNonNull(nextSampleStartTime.get(streamingTrack)) + streamingSample.getDuration());

  }

  private ChunkContainer createChunkContainer(final @NonNull StreamingTrack streamingTrack) {

    final List<StreamingSample> samples     = Objects.requireNonNull(sampleBuffers.get(streamingTrack));
    final long                  chunkNumber = Objects.requireNonNull(chunkNumbers.get(streamingTrack));
    chunkNumbers.put(streamingTrack, chunkNumber + 1);
    final ChunkContainer cc = new ChunkContainer();
    cc.streamingTrack = streamingTrack;
    cc.mdat           = new Mdat(samples);
    cc.duration       = Objects.requireNonNull(nextSampleStartTime.get(streamingTrack)) - Objects.requireNonNull(nextChunkCreateStartTime.get(streamingTrack));
    final TrackBox         tb   = trackBoxes.get(streamingTrack);
    final SampleTableBox   stbl = Objects.requireNonNull(Path.getPath(tb, "mdia[0]/minf[0]/stbl[0]"));
    final SampleToChunkBox stsc = Objects.requireNonNull(Path.getPath(stbl, "stsc[0]"));
    if (stsc.getEntries().isEmpty()) {
      final List<SampleToChunkBox.Entry> entries = new ArrayList<>();
      stsc.setEntries(entries);
      entries.add(new SampleToChunkBox.Entry(chunkNumber, samples.size(), 1));
    } else {
      final SampleToChunkBox.Entry e = stsc.getEntries().get(stsc.getEntries().size() - 1);
      if (e.getSamplesPerChunk() != samples.size()) {
        stsc.getEntries().add(new SampleToChunkBox.Entry(chunkNumber, samples.size(), 1));
      }
    }
    long sampleNumber = Objects.requireNonNull(sampleNumbers.get(streamingTrack));

    final SampleSizeBox     stsz = Objects.requireNonNull(Path.getPath(stbl, "stsz[0]"));
    final TimeToSampleBox   stts = Objects.requireNonNull(Path.getPath(stbl, "stts[0]"));
    SyncSampleBox           stss = Path.getPath(stbl, "stss[0]");
    CompositionTimeToSample ctts = Path.getPath(stbl, "ctts[0]");
    if (streamingTrack.getTrackExtension(CompositionTimeTrackExtension.class) != null) {
      if (ctts == null) {
        ctts = new CompositionTimeToSample();
        ctts.setEntries(new ArrayList<>());

        final ArrayList<Box> bs = new ArrayList<>(stbl.getBoxes());
        bs.add(bs.indexOf(stts), ctts);
      }
    }

    final long[] sampleSizes = new long[samples.size()];
    int          i           = 0;
    for (StreamingSample sample : samples) {
      sampleSizes[i++] = sample.getContent().limit();

      if (ctts != null) {
        ctts.getEntries().add(new CompositionTimeToSample.Entry(1, l2i(sample.getSampleExtension(CompositionTimeSampleExtension.class).getCompositionTimeOffset())));
      }

      if (stts.getEntries().isEmpty()) {
        final ArrayList<TimeToSampleBox.Entry> entries = new ArrayList<>(stts.getEntries());
        entries.add(new TimeToSampleBox.Entry(1, sample.getDuration()));
        stts.setEntries(entries);
      } else {
        final TimeToSampleBox.Entry sttsEntry = stts.getEntries().get(stts.getEntries().size() - 1);
        if (sttsEntry.getDelta() == sample.getDuration()) {
          sttsEntry.setCount(sttsEntry.getCount() + 1);
        } else {
          stts.getEntries().add(new TimeToSampleBox.Entry(1, sample.getDuration()));
        }
      }
      final SampleFlagsSampleExtension sampleFlagsSampleExtension = sample.getSampleExtension(SampleFlagsSampleExtension.class);
      if (sampleFlagsSampleExtension != null && sampleFlagsSampleExtension.isSyncSample()) {
        if (stss == null) {
          stss = new SyncSampleBox();
          stbl.addBox(stss);
        }
        stss.setSampleNumber(Mp4Arrays.copyOfAndAppend(stss.getSampleNumber(), sampleNumber));
      }
      sampleNumber++;

    }
    stsz.setSampleSizes(Mp4Arrays.copyOfAndAppend(stsz.getSampleSizes(), sampleSizes));

    sampleNumbers.put(streamingTrack, sampleNumber);
    samples.clear();
    Log.d(TAG, "chunk container created for " + streamingTrack.getHandler() + ". mdat size: " + cc.mdat.size + ". chunk duration is " + (double) cc.duration / streamingTrack.getTimescale());
    return cc;
  }

  protected @NonNull Box createMdhd(final @NonNull StreamingTrack streamingTrack) {
    final MediaHeaderBox mdhd = new MediaHeaderBox();
    mdhd.setCreationTime(creationTime);
    mdhd.setModificationTime(creationTime);
    //mdhd.setDuration(nextSampleStartTime.get(streamingTrack)); will update at the end, in createMoov
    mdhd.setTimescale(streamingTrack.getTimescale());
    mdhd.setLanguage(streamingTrack.getLanguage());
    return mdhd;
  }

  @Override
  protected Box createTkhd(StreamingTrack streamingTrack) {
    TrackHeaderBox tkhd = (TrackHeaderBox) super.createTkhd(streamingTrack);
    tkhd.setEnabled(true);
    tkhd.setInMovie(true);
    return tkhd;
  }

  private class Mdat implements Box {
    final ArrayList<StreamingSample> samples;
    long size;

    boolean includeHeader;

    Mdat(final @NonNull List<StreamingSample> samples) {
      this.samples = new ArrayList<>(samples);
      size         = 8;

      for (StreamingSample sample : samples) {
        size += sample.getContent().limit();
      }
    }

    @Override
    public String getType() {
      return "mdat";
    }

    @Override
    public long getSize() {
      if (includeHeader) {
        return size;
      } else {
        return size - 8;
      }
    }

    @Override
    public void getBox(WritableByteChannel writableByteChannel) throws IOException {
      if (includeHeader) {
        // When we include the header, we specify the declared size as 1, indicating the size is from here until the end of the file
        writableByteChannel.write(ByteBuffer.wrap(new byte[] {
            0, 0, 0, 0, // size (4 bytes)
            109, 100, 97, 116, // 'm' 'd' 'a' 't'
        }));
      }

      for (StreamingSample sample : samples) {
        writableByteChannel.write((ByteBuffer) sample.getContent().rewind());
      }
    }
  }

  private class ChunkContainer {
    Mdat           mdat;
    StreamingTrack streamingTrack;
    long           duration;
  }
}
