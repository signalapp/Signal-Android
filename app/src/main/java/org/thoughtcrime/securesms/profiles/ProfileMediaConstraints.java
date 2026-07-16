package org.thoughtcrime.securesms.profiles;


import org.thoughtcrime.securesms.jobs.AttachmentUploadJob;
import org.signal.mediasend.MediaConstraints;
import org.thoughtcrime.securesms.video.TranscodingConfig;

import java.util.Collections;
import java.util.List;

public class ProfileMediaConstraints extends MediaConstraints {
  @Override
  public int getImageMaxWidth() {
    return 640;
  }

  @Override
  public int getImageMaxHeight() {
    return 640;
  }

  @Override
  public int getImageMaxSize() {
    return 5 * 1024 * 1024;
  }

  @Override
  public int[] getImageDimensionTargets() {
    return new int[] { getImageMaxWidth() };
  }

  @Override
  public long getGifMaxSize() {
    return 0;
  }

  @Override
  public long getVideoMaxSize() {
    return 0;
  }

  @Override
  public long getAudioMaxSize() {
    return 0;
  }

  @Override
  public long getDocumentMaxSize() {
    return 0;
  }

  @Override
  public long getMaxAttachmentSize() {
    return AttachmentUploadJob.getMaxPlaintextSize();
  }

  @Override
  public List<TranscodingConfig.QualityTier> getVideoTranscodingSettings() {
    return Collections.emptyList();
  }
}
