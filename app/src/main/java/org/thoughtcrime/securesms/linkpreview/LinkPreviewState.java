/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.linkpreview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

public class LinkPreviewState {
  private final String                      activeUrlForError;
  private final boolean                     isLoading;
  private final boolean                     hasLinks;
  private final Optional<LinkPreview>       linkPreview;
  private final LinkPreviewRepository.Error error;

  private LinkPreviewState(@Nullable String activeUrlForError,
                           boolean isLoading,
                           boolean hasLinks,
                           Optional<LinkPreview> linkPreview,
                           @Nullable LinkPreviewRepository.Error error)
  {
    this.activeUrlForError = activeUrlForError;
    this.isLoading         = isLoading;
    this.hasLinks          = hasLinks;
    this.linkPreview       = linkPreview;
    this.error             = error;
  }

  public static LinkPreviewState forLoading() {
    return new LinkPreviewState(null, true, false, Optional.empty(), null);
  }

  public static LinkPreviewState forPreview(@NonNull LinkPreview linkPreview) {
    return new LinkPreviewState(null, false, true, Optional.of(linkPreview), null);
  }

  public static LinkPreviewState forLinksWithNoPreview(@Nullable String activeUrlForError, @NonNull LinkPreviewRepository.Error error) {
    return new LinkPreviewState(activeUrlForError, false, true, Optional.empty(), error);
  }

  public static LinkPreviewState forNoLinks() {
    return new LinkPreviewState(null, false, false, Optional.empty(), null);
  }

  public @Nullable String getActiveUrlForError() {
    return activeUrlForError;
  }

  public boolean isLoading() {
    return isLoading;
  }

  public boolean hasLinks() {
    return hasLinks;
  }

  public Optional<LinkPreview> getLinkPreview() {
    return linkPreview;
  }

  public @Nullable LinkPreviewRepository.Error getError() {
    return error;
  }

  public boolean hasContent() {
    return isLoading || hasLinks;
  }
}
