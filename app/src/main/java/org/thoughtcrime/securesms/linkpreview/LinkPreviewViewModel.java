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

import org.signal.core.util.ThreadUtil;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.net.RequestController;
import org.thoughtcrime.securesms.util.Debouncer;

import java.util.Collections;
import java.util.List;
import java.util.Optional;


public class LinkPreviewViewModel extends ViewModel {

  private final LinkPreviewRepository             repository;
  private final MutableLiveData<LinkPreviewState> linkPreviewState;
  private final LiveData<LinkPreviewState>        linkPreviewSafeState;

  private String            activeUrl;
  private RequestController activeRequest;
  private boolean           userCanceled;
  private Debouncer         debouncer;
  private boolean           enabled;

  private final boolean enablePlaceholder;

  private LinkPreviewViewModel(@NonNull LinkPreviewRepository repository, boolean enablePlaceholder) {
    this.repository           = repository;
    this.enablePlaceholder    = enablePlaceholder;
    this.linkPreviewState     = new MutableLiveData<>();
    this.debouncer            = new Debouncer(250);
    this.enabled              = SignalStore.settings().isLinkPreviewsEnabled();
    this.linkPreviewSafeState = Transformations.map(linkPreviewState, state -> cleanseState(state));
  }

  public LiveData<LinkPreviewState> getLinkPreviewState() {
    return linkPreviewSafeState;
  }

  /**
   * Gets the current state for use in the UI, then resets local state to prepare for the next message send.
   */
  public @NonNull List<LinkPreview> onSend() {
    final LinkPreviewState currentState = linkPreviewSafeState.getValue();

    if (activeRequest != null) {
      activeRequest.cancel();
      activeRequest = null;
    }

    userCanceled = false;
    activeUrl    = null;

    debouncer.clear();
    linkPreviewState.setValue(LinkPreviewState.forNoLinks());

    if (currentState == null || !currentState.linkPreview.isPresent()) {
      return Collections.emptyList();
    } else {
      return Collections.singletonList(currentState.linkPreview.get());
    }
  }

  /**
   * Gets the current state for use in the UI, then resets local state to prepare for the next message send.
   */
  public @NonNull List<LinkPreview> onSendWithErrorUrl() {
    final LinkPreviewState currentState = linkPreviewSafeState.getValue();

    if (activeRequest != null) {
      activeRequest.cancel();
      activeRequest = null;
    }

    userCanceled = false;
    activeUrl    = null;

    debouncer.clear();
    linkPreviewState.setValue(LinkPreviewState.forNoLinks());

    if (currentState == null) {
      return Collections.emptyList();
    } else if (currentState.linkPreview.isPresent()) {
      return Collections.singletonList(currentState.linkPreview.get());
    } else if (currentState.activeUrlForError != null) {
      String       topLevelDomain = LinkPreviewUtil.getTopLevelDomain(currentState.activeUrlForError);
      AttachmentId attachmentId   = null;

      return Collections.singletonList(new LinkPreview(currentState.activeUrlForError,
                                                       topLevelDomain != null ? topLevelDomain : currentState.activeUrlForError,
                                                       null,
                                                       -1L,
                                                       attachmentId));
    } else {
      return Collections.emptyList();
    }
  }

  public void onTextChanged(@NonNull String text, int cursorStart, int cursorEnd) {
    if (!enabled && !enablePlaceholder) return;

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
      activeRequest = enabled ? performRequest(activeUrl) : createPlaceholder(activeUrl);
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

  private @Nullable RequestController createPlaceholder(String url) {
    ThreadUtil.runOnMain(() -> {
      if (!userCanceled) {
        if (activeUrl != null && activeUrl.equals(url)) {
          linkPreviewState.setValue(LinkPreviewState.forLinksWithNoPreview(url, LinkPreviewRepository.Error.PREVIEW_NOT_AVAILABLE));
        } else {
          linkPreviewState.setValue(LinkPreviewState.forNoLinks());
        }
      }

      activeRequest = null;
    });

    return null;
  }

  private @Nullable RequestController performRequest(String url) {
    return repository.getLinkPreview(AppDependencies.getApplication(), url, new LinkPreviewRepository.Callback() {
      @Override
      public void onSuccess(@NonNull LinkPreview linkPreview) {
        ThreadUtil.runOnMain(() -> {
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
        ThreadUtil.runOnMain(() -> {
          if (!userCanceled) {
            if (activeUrl != null) {
              linkPreviewState.setValue(LinkPreviewState.forLinksWithNoPreview(activeUrl, error));
            } else {
              linkPreviewState.setValue(LinkPreviewState.forNoLinks());
            }
          }
          activeRequest = null;
        });
      }
    });
  }

  private @NonNull LinkPreviewState cleanseState(@NonNull LinkPreviewState state) {
    if (enabled) {
      return state;
    }

    if (enablePlaceholder) {
      return state.linkPreview
          .map(linkPreview -> LinkPreviewState.forLinksWithNoPreview(linkPreview.getUrl(), LinkPreviewRepository.Error.PREVIEW_NOT_AVAILABLE))
          .orElse(state);
    }

    return LinkPreviewState.forNoLinks();
  }

  public static class Factory extends ViewModelProvider.NewInstanceFactory {

    private final LinkPreviewRepository repository;
    private final boolean               enablePlaceholder;

    public Factory(@NonNull LinkPreviewRepository repository) {
      this(repository, false);
    }

    public Factory(@NonNull LinkPreviewRepository repository, boolean enablePlaceholder) {
      this.repository        = repository;
      this.enablePlaceholder = enablePlaceholder;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return modelClass.cast(new LinkPreviewViewModel(repository, enablePlaceholder));
    }
  }
}
