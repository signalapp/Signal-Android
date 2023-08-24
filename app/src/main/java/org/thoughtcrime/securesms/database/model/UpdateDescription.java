package org.thoughtcrime.securesms.database.model;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contains a list of people mentioned in an update message and a function to create the update message.
 */
public final class UpdateDescription {

  public interface SpannableFactory {
    Spannable create();
  }

  private final Collection<ACI>  mentioned;
  private final SpannableFactory stringFactory;
  private final Spannable        staticString;
  private final int              lightIconResource;
  private final int              lightTint;
  private final int              darkTint;

  private UpdateDescription(@NonNull Collection<ACI> mentioned,
                            @Nullable SpannableFactory stringFactory,
                            @Nullable Spannable staticString,
                            @DrawableRes int iconResource,
                            @ColorInt int lightTint,
                            @ColorInt int darkTint)
  {
    if (staticString == null && stringFactory == null) {
      throw new AssertionError();
    }
    this.mentioned         = mentioned;
    this.stringFactory     = stringFactory;
    this.staticString      = staticString;
    this.lightIconResource = iconResource;
    this.lightTint         = lightTint;
    this.darkTint          = darkTint;
  }

  /**
   * Create an update description which has a string value created by a supplied factory method that
   * will be run on a background thread.
   *
   * @param mentioned     UUIDs of recipients that are mentioned in the string.
   * @param stringFactory The background method for generating the string.
   */
  public static UpdateDescription mentioning(@NonNull Collection<ACI> mentioned,
                                             @NonNull SpannableFactory stringFactory,
                                             @DrawableRes int iconResource)
  {
    return new UpdateDescription(mentioned.stream().filter(ACI::isValid).collect(Collectors.toList()),
                                 stringFactory,
                                 null,
                                 iconResource,
                                 0,
                                 0);
  }

  /**
   * Create an update description that's string value is fixed.
   */
  public static UpdateDescription staticDescription(@NonNull String staticString,
                                                    @DrawableRes int iconResource)
  {
    return new UpdateDescription(Collections.emptyList(), null, new SpannableString(staticString), iconResource, 0, 0);
  }

  /**
   * Create an update description that's string value is fixed.
   */
  public static UpdateDescription staticDescription(@NonNull Spannable staticString,
                                                    @DrawableRes int iconResource)
  {
    return new UpdateDescription(Collections.emptyList(), null, staticString, iconResource, 0, 0);
  }

  /**
   * Create an update description that's string value is fixed with a specific tint color.
   */
  public static UpdateDescription staticDescription(@NonNull String staticString,
                                                    @DrawableRes int iconResource,
                                                    @ColorInt int lightTint,
                                                    @ColorInt int darkTint)
  {
    return new UpdateDescription(Collections.emptyList(), null, new SpannableString(staticString), iconResource, lightTint, darkTint);
  }

  public boolean isStringStatic() {
    return staticString != null;
  }

  @AnyThread
  public @NonNull Spannable getStaticSpannable() {
    if (staticString == null) {
      throw new UnsupportedOperationException();
    }

    return staticString;
  }

  @WorkerThread
  public @NonNull Spannable getSpannable() {
    if (staticString != null) {
      return staticString;
    }

    //noinspection ConstantConditions
    return stringFactory.create();
  }

  @AnyThread
  public @NonNull Collection<ACI> getMentioned() {
    return mentioned;
  }

  public @DrawableRes int getIconResource() {
    return lightIconResource;
  }

  public @ColorInt int getLightTint() {
    return lightTint;
  }

  public @ColorInt int getDarkTint() {
    return darkTint;
  }

  public static UpdateDescription concatWithNewLines(@NonNull List<UpdateDescription> updateDescriptions) {
    if (updateDescriptions.size() == 0) {
      throw new AssertionError();
    }

    if (updateDescriptions.size() == 1) {
      return updateDescriptions.get(0);
    }

    if (allAreStatic(updateDescriptions)) {
      return UpdateDescription.staticDescription(concatStaticLines(updateDescriptions),
                                                 updateDescriptions.get(0).getIconResource()
      );
    }

    Set<ACI> allMentioned = new HashSet<>();

    for (UpdateDescription updateDescription : updateDescriptions) {
      allMentioned.addAll(updateDescription.getMentioned());
    }

    return UpdateDescription.mentioning(allMentioned,
                                        () -> concatLines(updateDescriptions),
                                        updateDescriptions.get(0).getIconResource());
  }

  private static boolean allAreStatic(@NonNull Collection<UpdateDescription> updateDescriptions) {
    for (UpdateDescription description : updateDescriptions) {
      if (!description.isStringStatic()) {
        return false;
      }
    }

    return true;
  }

  @WorkerThread
  private static Spannable concatLines(@NonNull List<UpdateDescription> updateDescriptions) {
    SpannableStringBuilder result = new SpannableStringBuilder();

    for (int i = 0; i < updateDescriptions.size(); i++) {
      if (i > 0) result.append('\n');
      result.append(updateDescriptions.get(i).getSpannable());
    }

    return result;
  }

  @AnyThread
  private static Spannable concatStaticLines(@NonNull List<UpdateDescription> updateDescriptions) {
    SpannableStringBuilder result = new SpannableStringBuilder();

    for (int i = 0; i < updateDescriptions.size(); i++) {
      if (i > 0) result.append('\n');
      result.append(updateDescriptions.get(i).getStaticSpannable());
    }

    return result;
  }
}
