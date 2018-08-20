package org.thoughtcrime.securesms.profiles;


import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.Address;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

public class AvatarHelper {

  private static final String AVATAR_DIRECTORY = "avatars";

  public static InputStream getInputStreamFor(@NonNull Context context, @NonNull Address address)
      throws IOException
  {
    return new FileInputStream(getAvatarFile(context, address));
  }

  public static List<File> getAvatarFiles(@NonNull Context context) {
    File   avatarDirectory = new File(context.getFilesDir(), AVATAR_DIRECTORY);
    File[] results         = avatarDirectory.listFiles();

    if (results == null) return new LinkedList<>();
    else                 return Stream.of(results).toList();
  }

  public static void delete(@NonNull Context context, @NonNull Address address) {
    getAvatarFile(context, address).delete();
  }

  public static @NonNull File getAvatarFile(@NonNull Context context, @NonNull Address address) {
    File avatarDirectory = new File(context.getFilesDir(), AVATAR_DIRECTORY);
    avatarDirectory.mkdirs();

    return new File(avatarDirectory, new File(address.serialize()).getName());
  }

  public static void setAvatar(@NonNull Context context, @NonNull Address address, @Nullable byte[] data)
    throws IOException
  {
    if (data == null)  {
      delete(context, address);
    } else {
      FileOutputStream out = new FileOutputStream(getAvatarFile(context, address));
      out.write(data);
      out.close();
    }
  }

}
