package org.thoughtcrime.securesms.database;

import android.net.Uri;

import org.thoughtcrime.securesms.TextSecureTestCase;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.crypto.MasterSecret;

import java.io.FileNotFoundException;
import java.io.InputStream;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AttachmentDatabaseTest extends TextSecureTestCase {
  private static final long ROW_ID    = 1L;
  private static final long UNIQUE_ID = 2L;

  private AttachmentDatabase database;

  @Override
  public void setUp() {
    super.setUp();
    database = spy(DatabaseFactory.getAttachmentDatabase(getInstrumentation().getTargetContext()));
  }

  public void testTaskNotRunWhenThumbnailExists() throws Exception {
    final AttachmentId attachmentId = new AttachmentId(ROW_ID, UNIQUE_ID);

    when(database.getAttachment(attachmentId)).thenReturn(getMockAttachment("x/x"));

    doReturn(mock(InputStream.class)).when(database).getDataStream(any(MasterSecret.class), any(AttachmentId.class), eq("thumbnail"));
    database.getThumbnailStream(mock(MasterSecret.class), attachmentId);

    // XXX - I don't think this is testing anything? The thumbnail would be updated asynchronously.
    verify(database, never()).updateAttachmentThumbnail(any(MasterSecret.class), any(AttachmentId.class), any(InputStream.class), anyFloat());
  }

  public void testTaskRunWhenThumbnailMissing() throws Exception {
    final AttachmentId attachmentId = new AttachmentId(ROW_ID, UNIQUE_ID);

    when(database.getAttachment(attachmentId)).thenReturn(getMockAttachment("image/png"));
    doReturn(null).when(database).getDataStream(any(MasterSecret.class), any(AttachmentId.class), eq("thumbnail"));
    doNothing().when(database).updateAttachmentThumbnail(any(MasterSecret.class), any(AttachmentId.class), any(InputStream.class), anyFloat());

    try {
      database.new ThumbnailFetchCallable(mock(MasterSecret.class), attachmentId).call();
      throw new AssertionError("didn't try to generate thumbnail");
    } catch (FileNotFoundException fnfe) {
      // success
    }
  }

  private DatabaseAttachment getMockAttachment(String contentType) {
    DatabaseAttachment attachment = mock(DatabaseAttachment.class);
    when(attachment.getContentType()).thenReturn(contentType);
    when(attachment.getDataUri()).thenReturn(Uri.EMPTY);

    return attachment;
  }
}
