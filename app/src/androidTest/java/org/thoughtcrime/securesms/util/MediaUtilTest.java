package org.thoughtcrime.securesms.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.thoughtcrime.securesms.util.MediaUtil.getSaveTargetPath;

public class MediaUtilTest {

  @Test
  public void testSaveAttachmentImage() {
    String actual   = getSaveTargetPath("signal_img.jpg:image/jpeg");
    String expected = "/storage/emulated/0/Pictures/signal_img.jpg";
    assertEquals(expected, actual);
  }

  @Test
  public void testSaveAttachmentVideo() {
    String actual   = getSaveTargetPath("signal_video.mp4:video/mp4");
    String expected = "/storage/emulated/0/Movies/signal_video.mp4";
    assertEquals(expected, actual);
  }

  @Test
  public void testSaveAttachmentDocument() {
    String actual   = getSaveTargetPath("signal_document.pdf:application/pdf");
    String expected = "/storage/emulated/0/Download/signal_document.pdf";
    assertEquals(expected, actual);
  }

  @Test
  public void testSaveAttachmentUnknownType() {
    String actual   = getSaveTargetPath("signal_unknown.bin:application/octet-stream");
    String expected = "/storage/emulated/0/Download/signal_unknown.bin";
    assertEquals(expected, actual);
  }
}