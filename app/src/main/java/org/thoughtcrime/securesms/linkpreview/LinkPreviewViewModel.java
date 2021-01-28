package org.thoughtcrime.securesms.linkpreview;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.net.RequestController;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;
import java.util.List;


public class LinkPreviewViewModel extends ViewModel {

  private final LinkPreviewRepository             repository;
  private final MutableLiveData<LinkPreviewState> linkPreviewState;
  private final LiveData<LinkPreviewState>        linkPreviewSafeState;

  private String            activeUrl;
  private RequestController activeRequest;
  private boolean           userCanceled;
  private Debouncer         debouncer;
  private boolean           enabled;

  private LinkPreviewViewModel(@NonNull LinkPreviewRepository repository) {
    this.repository           = repository;
    this.linkPreviewState     = new MutableLiveData<>();
    this.debouncer            = new Debouncer(250);
    this.enabled              = SignalStore.settings().isLinkPreviewsEnabled();
    this.linkPreviewSafeState = Transformations.map(linkPreviewState, state -> enabled ? state : LinkPreviewState.forNoLinks());
  }

  public LiveData<LinkPreviewState> getLinkPreviewState() {
    return linkPreviewSafeState;
  }

  public boolean hasLinkPreview() {
    return linkPreviewSafeState.getValue() != null && linkPreviewSafeState.getValue().getLinkPreview().isPresent();
  }

  public boolean hasLinkPreviewUi() {
    return linkPreviewSafeState.getValue() != null && linkPreviewSafeState.getValue().hasContent();
  }

  public @NonNull List<LinkPreview> getActiveLinkPreviews() {
    final LinkPreviewState state = linkPreviewSafeState.getValue();

    if (state == null || !state.getLinkPreview().isPresent()) {
      return Collections.emptyList();
    } else {
      return Collections.singletonList(state.getLinkPreview().get());
    }
  }

  public void onTextChanged(@NonNull Context context, @NonNull String text, int cursorStart, int cursorEnd) {
    if (!enabled) return;

    debouncer.publish(() -> {
      if (TextUtils.isEmpty(text)) {
        userCanceled = false;
      }

      if (userCanceled) {
        return;
      }

      Optional<Link> link = LinkPreviewUtil.findValidPreviewUrls(text)
                                           .findFirst();

      if (link.isPresent() && link.get().getUrl().equals(activeUrl)) {
        return;
      }

      if (activeRequest != null) {
        activeRequest.cancel();
        activeRequest = null;
      }

      if (!link.isPresent() || !isCursorPositionValid(text, link.get(), cursorStart, cursorEnd)) {
        activeUrl = null;
        linkPreviewState.setValue(LinkPreviewState.forNoLinks());
        return;
      }

      linkPreviewState.setValue(LinkPreviewState.forLoading());

      activeUrl     = link.get().getUrl();
      activeRequest = repository.getLinkPreview(context, link.get().getUrl(), new LinkPreviewRepository.Callback() {
          @Override
          public void onSuccess(@NonNull LinkPreview linkPreview) {
            Util.runOnMain(() -> {
              if (!userCanceled) {
                if (activeUrl != null && activeUrl.equals(linkPreview.getUrl())) {
                  linkPreviewState.setValue(LinkPreviewState.forPreview(linkPreview));
                } else {
                  linkPreviewState.setValue(LinkPreviewState.forNoLinks());
                }
              }
              activeRequest = null;
            });
          }

        @Override
        public void onError(@NonNull LinkPreviewRepository.Error error) {
          Util.runOnMain(() -> {
            if (!userCanceled) {
              linkPreviewState.setValue(LinkPreviewState.forLinksWithNoPreview(error));
            }
            activeRequest = null;
          });
        }
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
    linkPreviewState.setValue(LinkPreviewState.forNoLinks());
  }

  public void onTransportChanged(boolean isSms) {
    enabled = SignalStore.settings().isLinkPreviewsEnabled() && !isSms;

    if (!enabled) {
      onUserCancel();
    }
  }

  public void onSend() {
    if (activeRequest != null) {
      activeRequest.cancel();
      activeRequest = null;
    }

    userCanceled = false;
    activeUrl    = null;

    debouncer.clear();
    linkPreviewState.setValue(LinkPreviewState.forNoLinks());
  }

  public void onEnabled() {
    userCanceled = false;
    enabled      = SignalStore.settings().isLinkPreviewsEnabled();
  }

  @Override
  protected void onCleared() {
    if (activeRequest != null) {
      activeRequest.cancel();
    }

    debouncer.clear();
  }

  private boolean isCursorPositionValid(@NonNull String text, @NonNull Link link, int cursorStart, int cursorEnd) {
    if (cursorStart != cursorEnd) {
      return true;
    }

    if (text.endsWith(link.getUrl()) && cursorStart == link.getPosition() + link.getUrl().length()) {
      return true;
    }

    return cursorStart < link.getPosition() || cursorStart > link.getPosition() + link.getUrl().length();
  }

  public static class LinkPreviewState {
    private final boolean                     isLoading;
    private final boolean                     hasLinks;
    private final Optional<LinkPreview>       linkPreview;
    private final LinkPreviewRepository.Error error;

    private LinkPreviewState(boolean isLoading,
                             boolean hasLinks,
                             Optional<LinkPreview> linkPreview,
                             @Nullable LinkPreviewRepository.Error error)
    {
      this.isLoading   = isLoading;
      this.hasLinks    = hasLinks;
      this.linkPreview = linkPreview;
      this.error       = error;
    }

    private static LinkPreviewState forLoading() {
      return new LinkPreviewState(true, false, Optional.absent(), null);
    }

    private static LinkPreviewState forPreview(@NonNull LinkPreview linkPreview) {
      return new LinkPreviewState(false, true, Optional.of(linkPreview), null);
    }

    private static LinkPreviewState forLinksWithNoPreview(@NonNull LinkPreviewRepository.Error error) {
      return new LinkPreviewState(false, true, Optional.absent(), error);
    }

    private static LinkPreviewState forNoLinks() {
      return new LinkPreviewState(false, false, Optional.absent(), null);
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

    boolean hasContent() {
      return isLoading || hasLinks;
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
