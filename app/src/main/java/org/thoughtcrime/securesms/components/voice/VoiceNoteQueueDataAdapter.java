package org.thoughtcrime.securesms.components.voice;

import android.net.Uri;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.google.android.exoplayer2.ext.mediasession.TimelineQueueEditor;

import org.signal.core.util.logging.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * DataAdapter which maintains the current queue of MediaDescriptionCompat objects.
 */
@MainThread
final class VoiceNoteQueueDataAdapter implements TimelineQueueEditor.QueueDataAdapter {

  private static final String TAG = Log.tag(VoiceNoteQueueDataAdapter.class);

  public static final MediaDescriptionCompat EMPTY = new MediaDescriptionCompat.Builder().build();

  private final List<MediaDescriptionCompat> descriptions = new LinkedList<>();

  @Override
  public MediaDescriptionCompat getMediaDescription(int position) {
    if (descriptions.size() <= position) {
      Log.i(TAG, "getMediaDescription: Returning EMPTY MediaDescriptionCompat for index " + position);
      return EMPTY;
    }

    return descriptions.get(position);
  }

  @Override
  public void add(int position, MediaDescriptionCompat description) {
    descriptions.add(position, description);
  }

  @Override
  public void remove(int position) {
    descriptions.remove(position);
  }

  @Override
  public void move(int from, int to) {
    MediaDescriptionCompat description = descriptions.remove(from);
    descriptions.add(to, description);
  }

  int size() {
    return descriptions.size();
  }

  int indexOf(@NonNull Uri uri) {
    for (int i = 0; i < descriptions.size(); i++) {
      if (Objects.equals(uri, descriptions.get(i).getMediaUri())) {
        return i;
      }
    }

    return -1;
  }

  int indexAfter(@NonNull MediaDescriptionCompat target) {
    if (isEmpty()) {
      return 0;
    }

    long targetMessageId = target.getExtras().getLong(VoiceNoteMediaDescriptionCompatFactory.EXTRA_MESSAGE_ID);
    for (int i = 0; i < descriptions.size(); i++) {
      long descriptionMessageId = descriptions.get(i).getExtras().getLong(VoiceNoteMediaDescriptionCompatFactory.EXTRA_MESSAGE_ID);

      if (descriptionMessageId > targetMessageId) {
        return i;
      }
    }

    return descriptions.size();
  }

  boolean isEmpty() {
    return descriptions.isEmpty();
  }

  void clear() {
    descriptions.clear();
  }
}
