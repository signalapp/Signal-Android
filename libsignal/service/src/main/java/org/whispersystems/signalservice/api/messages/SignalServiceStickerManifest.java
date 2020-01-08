package org.whispersystems.signalservice.api.messages;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SignalServiceStickerManifest {

  private final Optional<String>      title;
  private final Optional<String>      author;
  private final Optional<StickerInfo> cover;
  private final List<StickerInfo>     stickers;

  public SignalServiceStickerManifest(String title, String author, StickerInfo cover, List<StickerInfo> stickers) {
    this.title    = Optional.fromNullable(title);
    this.author   = Optional.fromNullable(author);
    this.cover    = Optional.fromNullable(cover);
    this.stickers = (stickers == null) ? Collections.<StickerInfo>emptyList() : new ArrayList<>(stickers);
  }

  public Optional<String> getTitle() {
    return title;
  }

  public Optional<String> getAuthor() {
    return author;
  }

  public Optional<StickerInfo> getCover() {
    return cover;
  }

  public List<StickerInfo> getStickers() {
    return stickers;
  }

  public static final class StickerInfo {
    private final int    id;
    private final String emoji;

    public StickerInfo(int id, String emoji) {
      this.id    = id;
      this.emoji = emoji;
    }

    public int getId() {
      return id;
    }

    public String getEmoji() {
      return emoji;
    }
  }
}
