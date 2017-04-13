package org.thoughtcrime.securesms.video.exo;


import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.mms.PartUriParser;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class PartDataSource implements DataSource {

  private final @NonNull  Context      context;
  private final @NonNull  MasterSecret masterSecret;
  private final @Nullable TransferListener<? super PartDataSource> listener;

  private Uri         uri;
  private InputStream inputSteam;

  public PartDataSource(@NonNull Context context,
                        @NonNull MasterSecret masterSecret,
                        @Nullable TransferListener<? super PartDataSource> listener)
  {
    this.context      = context.getApplicationContext();
    this.masterSecret = masterSecret;
    this.listener     = listener;
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    this.uri = dataSpec.uri;

    AttachmentDatabase attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context);
    PartUriParser      partUri            = new PartUriParser(uri);
    Attachment         attachment         = attachmentDatabase.getAttachment(masterSecret, partUri.getPartId());

    if (attachment == null) throw new IOException("Attachment not found");

    this.inputSteam = attachmentDatabase.getAttachmentStream(masterSecret, partUri.getPartId());

    if (inputSteam == null) throw new IOException("InputStream not foudn");

    long skipped = this.inputSteam.skip(dataSpec.position);

    if (skipped != dataSpec.position) throw new IOException("Skip failed!");

    if (listener != null) {
      listener.onTransferStart(this, dataSpec);
    }

    if (attachment.getSize() - dataSpec.position <= 0) throw new EOFException("No more data");

    return attachment.getSize() - dataSpec.position;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    int read = inputSteam.read(buffer, offset, readLength);

    if (read > 0 && listener != null) {
      listener.onBytesTransferred(this, read);
    }

    return read;
  }

  @Override
  public Uri getUri() {
    return uri;
  }

  @Override
  public void close() throws IOException {
    inputSteam.close();
  }
}
