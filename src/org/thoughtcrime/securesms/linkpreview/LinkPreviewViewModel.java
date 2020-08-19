package org.thoughtcrime.securesms.linkpreview;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import android.content.Context;
import androidx.annotation.NonNull;
import android.text.TextUtils;

import org.thoughtcrime.securesms.net.RequestController;
import org.thoughtcrime.securesms.util.Debouncer;
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

  public @NonNull List<LinkPreview> getActiveLinkPreviews() {
    final LinkPreviewState state = linkPreviewState.getValue();

    if (state == null || !state.getLinkPreview().isPresent()) {
      return Collections.emptyList();
    } else {
      return Collections.singletonList(state.getLinkPreview().get());
    }
  }

  public void onTextChanged(@NonNull Context context, @NonNull String text, int cursorStart, int cursorEnd) {
    debouncer.publish(() -> {
      if (TextUtils.isEmpty(text)) {
        userCanceled = false;
      }

      if (userCanceled) {
        return;
      }

      List<Link>     links = LinkPreviewUtil.findWhitelistedUrls(text);
      Optional<Link> link  = links.isEmpty() ? Optional.absent() : Optional.of(links.get(0));

      if (link.isPresent() && link.get().getUrl().equals(activeUrl)) {
        return;
      }

      if (activeRequest != null) {
        activeRequest.cancel();
        activeRequest = null;
      }

      if (!link.isPresent() || !isCursorPositionValid(text, link.get(), cursorStart, cursorEnd)) {
        activeUrl = null;
        linkPreviewState.setValue(LinkPreviewState.forEmpty());
        return;
      }

      linkPreviewState.setValue(LinkPreviewState.forLoading());

      activeUrl     = link.get().getUrl();
      activeRequest = repository.getLinkPreview(context, link.get().getUrl(), lp -> {
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
