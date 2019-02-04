package org.thoughtcrime.securesms.linkpreview;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.UriAttachment;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.net.RequestController;
import org.thoughtcrime.securesms.providers.MemoryBlobProvider;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;
import java.util.List;


public class LinkPreviewViewModel extends ViewModel {

  private final LinkPreviewRepository             repository;
  private final MutableLiveData<LinkPreviewState> linkPreviewState;

  private String            activeUrl;
  private RequestController activeRequest;
  private boolean           userCanceled;
  private Debouncer         debouncer;

  private LinkPreviewViewModel(@NonNull LinkPreviewRepository repository) {
    this.repository       = repository;
    this.linkPreviewState = new MutableLiveData<>();
    this.debouncer        = new Debouncer(250);
  }

  public LiveData<LinkPreviewState> getLinkPreviewState() {
    return linkPreviewState;
  }

  public boolean hasLinkPreview() {
    return linkPreviewState.getValue() != null && linkPreviewState.getValue().getLinkPreview().isPresent();
  }

  public @NonNull List<LinkPreview> getPersistedLinkPreviews(@NonNull Context context) {
    final LinkPreviewState state = linkPreviewState.getValue();
    if (state == null || !state.getLinkPreview().isPresent()) {
      return Collections.emptyList();
    }

    if (!state.getLinkPreview().get().getThumbnail().isPresent() || state.getLinkPreview().get().getThumbnail().get().getDataUri() == null) {
      return Collections.singletonList(state.getLinkPreview().get());
    }

    LinkPreview originalPreview    = state.getLinkPreview().get();
    Attachment  originalAttachment = originalPreview.getThumbnail().get();
    Uri         memoryUri          = originalAttachment.getDataUri();
    byte[]      imageBlob          = MemoryBlobProvider.getInstance().getBlob(memoryUri);
    Uri         diskUri            = PersistentBlobProvider.getInstance(context).create(context, imageBlob, MediaUtil.IMAGE_JPEG, null);
    Attachment  newAttachment      = new UriAttachment(diskUri,
                                                       diskUri,
                                                       originalAttachment.getContentType(),
                                                       originalAttachment.getTransferState(),
                                                       originalAttachment.getSize(),
                                                       originalAttachment.getWidth(),
                                                       originalAttachment.getHeight(),
                                                       originalAttachment.getFileName(),
                                                       originalAttachment.getFastPreflightId(),
                                                       originalAttachment.isVoiceNote(),
                                                       originalAttachment.isQuote(),
                                                       originalAttachment.getCaption());

    MemoryBlobProvider.getInstance().delete(memoryUri);

    return Collections.singletonList(new LinkPreview(originalPreview.getUrl(), originalPreview.getTitle(), Optional.of(newAttachment)));
  }

  public void onTextChanged(@NonNull Context context, @NonNull String text) {
    debouncer.publish(() -> {
      if (TextUtils.isEmpty(text)) {
        userCanceled = false;
      }

      if (userCanceled) {
        return;
      }

      List<String>     urls = LinkPreviewUtil.findWhitelistedUrls(text);
      Optional<String> url  = urls.isEmpty() ? Optional.absent() : Optional.of(urls.get(0));

      if (url.isPresent() && url.get().equals(activeUrl)) {
        return;
      }

      if (activeRequest != null) {
        activeRequest.cancel();
        activeRequest = null;
      }

      if (!url.isPresent()) {
        activeUrl = null;
        linkPreviewState.setValue(LinkPreviewState.forEmpty());
        return;
      }

      linkPreviewState.setValue(LinkPreviewState.forLoading());

      activeUrl     = url.get();
      activeRequest = repository.getLinkPreview(context, url.get(), lp -> {
        Util.runOnMain(() -> {
          if (!userCanceled) {
            linkPreviewState.setValue(LinkPreviewState.forPreview(lp));
          }
          activeRequest = null;
        });
      });
    });
  }


  public void onUserCancel() {
    if (activeRequest != null) {
      activeRequest.cancel();
      activeRequest = null;
    }

    userCanceled = true;
    activeUrl    = null;

    debouncer.clear();
    linkPreviewState.setValue(LinkPreviewState.forEmpty());
  }

  public void onEnabled() {
    userCanceled = false;
  }

  @Override
  protected void onCleared() {
    if (activeRequest != null) {
      activeRequest.cancel();
    }

    debouncer.clear();
  }

  public static class LinkPreviewState {
    private final boolean               isLoading;
    private final Optional<LinkPreview> linkPreview;

    private LinkPreviewState(boolean isLoading, Optional<LinkPreview> linkPreview) {
      this.isLoading   = isLoading;
      this.linkPreview = linkPreview;
    }

    private static LinkPreviewState forLoading() {
      return new LinkPreviewState(true, Optional.absent());
    }

    private static LinkPreviewState forPreview(@NonNull Optional<LinkPreview> linkPreview) {
      return new LinkPreviewState(false, linkPreview);
    }

    private static LinkPreviewState forEmpty() {
      return new LinkPreviewState(false, Optional.absent());
    }

    public boolean isLoading() {
      return isLoading;
    }

    public Optional<LinkPreview> getLinkPreview() {
      return linkPreview;
    }
  }

  public static class Factory extends ViewModelProvider.NewInstanceFactory {

    private final LinkPreviewRepository repository;

    public Factory(@NonNull LinkPreviewRepository repository) {
      this.repository = repository;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return modelClass.cast(new LinkPreviewViewModel(repository));
    }
  }
}
