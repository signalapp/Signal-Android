package org.thoughtcrime.securesms.components.voice;

import android.net.Uri;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;

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
  public MediaDescriptionCompat getMediaDescription(int position) {
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

  void add(MediaDescriptionCompat description) {
    descriptions.add(description);
  }

  int indexOf(@NonNull Uri uri) {
    for (int i = 0; i < descriptions.size(); i++) {
      if (Objects.equals(uri, descriptions.get(i).getMediaUri())) {
        return i;
      }
    }

    return -1;
  }

  void clear() {
    descriptions.clear();
  }
}
