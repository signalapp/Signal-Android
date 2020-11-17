package org.thoughtcrime.securesms.components.voice;

import android.net.Uri;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ext.mediasession.TimelineQueueEditor;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * DataAdapter which maintains the current queue of MediaDescriptionCompat objects.
 */
final class VoiceNoteQueueDataAdapter implements TimelineQueueEditor.QueueDataAdapter {

  private final List<MediaDescriptionCompat> descriptions = new LinkedList<>();

  @Override
  public synchronized MediaDescriptionCompat getMediaDescription(int position) {
    return descriptions.get(position);
  }

  @Override
  public synchronized void add(int position, MediaDescriptionCompat description) {
    descriptions.add(position, description);
  }

  @Override
  public synchronized void remove(int position) {
    descriptions.remove(position);
  }

  @Override
  public synchronized void move(int from, int to) {
    MediaDescriptionCompat description = descriptions.remove(from);
    descriptions.add(to, description);
  }

  synchronized int size() {
    return descriptions.size();
  }

  synchronized int indexOf(@NonNull Uri uri) {
    for (int i = 0; i < descriptions.size(); i++) {
      if (Objects.equals(uri, descriptions.get(i).getMediaUri())) {
        return i;
      }
    }

    return -1;
  }

  synchronized int indexAfter(@NonNull MediaDescriptionCompat target) {
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

  synchronized boolean isEmpty() {
    return descriptions.isEmpty();
  }

  synchronized void clear() {
    descriptions.clear();
  }
}
