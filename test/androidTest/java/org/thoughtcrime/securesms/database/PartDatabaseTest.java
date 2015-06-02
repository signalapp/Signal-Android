package org.thoughtcrime.securesms.database;

import android.net.Uri;

import org.thoughtcrime.securesms.TextSecureTestCase;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.PartDatabase.PartId;

import java.io.FileNotFoundException;
import java.io.InputStream;

import ws.com.google.android.mms.pdu.PduPart;

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

public class PartDatabaseTest extends TextSecureTestCase {
  private static final long ROW_ID    = 1L;
  private static final long UNIQUE_ID = 2L;

  private PartDatabase database;

  @Override
  public void setUp() {
    database = spy(DatabaseFactory.getPartDatabase(getInstrumentation().getTargetContext()));
  }

  public void testTaskNotRunWhenThumbnailExists() throws Exception {
    final PartId partId = new PartId(ROW_ID, UNIQUE_ID);

    when(database.getPart(partId)).thenReturn(getPduPartSkeleton("x/x"));
    doReturn(mock(InputStream.class)).when(database).getDataStream(any(MasterSecret.class), any(PartId.class), eq("thumbnail"));
    database.getThumbnailStream(null, partId);

    verify(database, never()).updatePartThumbnail(any(MasterSecret.class), any(PartId.class), any(PduPart.class), any(InputStream.class), anyFloat());
  }

  public void testTaskRunWhenThumbnailMissing() throws Exception {
    final PartId partId = new PartId(ROW_ID, UNIQUE_ID);

    when(database.getPart(partId)).thenReturn(getPduPartSkeleton("image/png"));
    doReturn(null).when(database).getDataStream(any(MasterSecret.class), any(PartId.class), eq("thumbnail"));
    doNothing().when(database).updatePartThumbnail(any(MasterSecret.class), any(PartId.class), any(PduPart.class), any(InputStream.class), anyFloat());

    try {
      database.new ThumbnailFetchCallable(mock(MasterSecret.class), partId).call();
      throw new AssertionError("didn't try to generate thumbnail");
    } catch (FileNotFoundException fnfe) {
      // success
    }
  }

  private PduPart getPduPartSkeleton(String contentType) {
    PduPart part = new PduPart();
    part.setContentType(contentType.getBytes());
    part.setDataUri(Uri.EMPTY);
    return part;
  }
}
