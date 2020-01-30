package org.thoughtcrime.securesms.megaphone;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.model.MegaphoneRecord;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.FeatureFlags;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Creating a new megaphone:
 * - Add an enum to {@link Event}
 * - Return a megaphone in {@link #forRecord(MegaphoneRecord)}
 * - Include the event in {@link #buildDisplayOrder()}
 *
 * Common patterns:
 * - For events that have a snooze-able recurring display schedule, use a {@link RecurringSchedule}.
 * - For events guarded by feature flags, set a {@link ForeverSchedule} with false in
 *   {@link #buildDisplayOrder()}.
 * - For events that change, return different megaphones in {@link #forRecord(MegaphoneRecord)}
 *   based on whatever properties you're interested in.
 */
public final class Megaphones {

  private Megaphones() {}

  static @Nullable Megaphone getNextMegaphone(@NonNull Context context, @NonNull Map<Event, MegaphoneRecord> records) {
    long currentTime = System.currentTimeMillis();

    List<Megaphone> megaphones = Stream.of(buildDisplayOrder())
                                       .filter(e -> {
                                         MegaphoneRecord   record = Objects.requireNonNull(records.get(e.getKey()));
                                         MegaphoneSchedule schedule = e.getValue();

                                         return !record.isFinished() && schedule.shouldDisplay(record.getSeenCount(), record.getLastSeen(), record.getFirstVisible(), currentTime);
                                       })
                                       .map(Map.Entry::getKey)
                                       .map(records::get)
                                       .map(Megaphones::forRecord)
                                       .toList();

    boolean hasOptional  = Stream.of(megaphones).anyMatch(m -> !m.isMandatory());
    boolean hasMandatory = Stream.of(megaphones).anyMatch(Megaphone::isMandatory);

    if (hasOptional && hasMandatory) {
      megaphones = Stream.of(megaphones).filter(Megaphone::isMandatory).toList();
    }

    if (megaphones.size() > 0) {
      return megaphones.get(0);
    } else {
      return null;
    }
  }

  /**
   * This is when you would hide certain megaphones based on {@link FeatureFlags}. You could
   * conditionally set a {@link ForeverSchedule} set to false for disabled features.
   */
  private static Map<Event, MegaphoneSchedule> buildDisplayOrder() {
    return new LinkedHashMap<Event, MegaphoneSchedule>() {{
      put(Event.REACTIONS, new ForeverSchedule(FeatureFlags.reactionSending()));
    }};
  }

  private static @NonNull Megaphone forRecord(@NonNull MegaphoneRecord record) {
    switch (record.getEvent()) {
      case REACTIONS:
        return buildReactionsMegaphone();
      default:
        throw new IllegalArgumentException("Event not handled!");
    }
  }

  private static @NonNull Megaphone buildReactionsMegaphone() {
    return new Megaphone.Builder(Event.REACTIONS, Megaphone.Style.REACTIONS)
                        .setMaxAppearances(Megaphone.UNLIMITED)
                        .setMandatory(false)
                        .build();
  }

  public enum Event {
    REACTIONS("reactions");

    private final String key;

    Event(@NonNull String key) {
      this.key = key;
    }

    public @NonNull String getKey() {
      return key;
    }

    public static Event fromKey(@NonNull String key) {
      for (Event event : values()) {
        if (event.getKey().equals(key)) {
          return event;
        }
      }
      throw new IllegalArgumentException("No event for key: " + key);
    }
  }
}
