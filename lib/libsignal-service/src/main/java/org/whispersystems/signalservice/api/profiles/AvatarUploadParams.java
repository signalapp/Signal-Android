package org.whispersystems.signalservice.api.profiles;

import org.whispersystems.signalservice.api.util.StreamDetails;

/**
 * A model to represent the attributes of an avatar upload.
 */
public class AvatarUploadParams {
  /** Whether or not you should keep the avatar the same on the server. */
  public final boolean keepTheSame;

  /** Whether or not you want an avatar. */
  public final boolean hasAvatar;

  /** A stream representing the content of the avatar to be uploaded. */
  public final StreamDetails stream;

  /**
   * Indicates that you want to leave the avatar as it already is on the server.
   * @param hasAvatar Whether or not you have an avatar. If true, the avatar you have on the server will remain as it is.
   *                  If this is false, it *will* delete the avatar. Weird to have this with the 'unchanged' bit, I know,
   *                  but this boolean already existed before we added the 'keepTheSame' functionality, so we gotta deal
   *                  with it.
   */
  public static AvatarUploadParams unchanged(boolean hasAvatar) {
    return new AvatarUploadParams(true, hasAvatar, null);
  }

  /**
   * Indicates that you'd like set the contents of this stream as your avatar. If null, the avatar will be removed.
   */
  public static AvatarUploadParams forAvatar(StreamDetails avatarStream) {
    return new AvatarUploadParams(false, avatarStream != null, avatarStream);
  }

  private AvatarUploadParams(boolean keepTheSame, boolean hasAvatar, StreamDetails stream) {
    this.keepTheSame = keepTheSame;
    this.hasAvatar   = hasAvatar;
    this.stream      = stream;
  }
}
