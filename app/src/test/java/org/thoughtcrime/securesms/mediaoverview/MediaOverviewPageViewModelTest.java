package org.thoughtcrime.securesms.mediaoverview;

import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import androidx.lifecycle.Lifecycle.Event;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.Observer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import org.thoughtcrime.securesms.database.MediaDatabase.Sorting;
import org.thoughtcrime.securesms.database.loaders.MediaLoader.MediaType;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public final class MediaOverviewPageViewModelTest {
  @Mock
  private LifecycleOwner    lifecycleOwner;
  private LifecycleRegistry lifecycleRegistry;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    lifecycleRegistry = new LifecycleRegistry(lifecycleOwner);
    lifecycleRegistry.handleLifecycleEvent(Event.ON_CREATE);
    lifecycleRegistry.handleLifecycleEvent(Event.ON_START);
    when(lifecycleOwner.getLifecycle()).thenReturn(lifecycleRegistry);
  }

  @Test
  public void update_actionModeTitle_triggers_observer_with_showFileSize() {
    internal_updateActionModeTitle(Sorting.Largest, true, MediaType.ALL,
        new ActionModeTitleData(100, 12345, true));
  }

  @Test
  public void update_actionModeTitle_triggers_observer_with_fileSize_because_mediaType() {
    internal_updateActionModeTitle(Sorting.Newest, false, MediaType.ALL,
        new ActionModeTitleData(200, 12345, true));
  }

  @Test
  public void update_actionModeTitle_triggers_observer_with_fileSize_because_detailLayout() {
    internal_updateActionModeTitle(Sorting.Newest, true, MediaType.GALLERY,
        new ActionModeTitleData(300, 12345, true));
  }

  @Test
  public void update_actionModeTitle_triggers_observer_with_noFileSize() {
    internal_updateActionModeTitle(Sorting.Newest, false, MediaType.GALLERY,
        new ActionModeTitleData(400, 12345, false));
  }

  private void internal_updateActionModeTitle(Sorting sortOrder, boolean detailLayout, MediaType mediaType,
                                              ActionModeTitleData expected) {
    MediaOverviewPageViewModel viewModel   = new MediaOverviewPageViewModel();
    Observer<ActionModeTitleData> observer = mock(Observer.class);
    viewModel.getActionModeTitleData().observe(lifecycleOwner, observer);

    reset(observer);
    viewModel.updateActionModeTitle(expected.getMediaCount(), expected.getTotalMediaSize(),
        detailLayout, sortOrder, mediaType);
    verify(observer).onChanged(argThat(new ActionModeTitleDataMatcher(expected)));
  }
}

final class ActionModeTitleDataMatcher implements ArgumentMatcher<ActionModeTitleData> {
  private ActionModeTitleData expected;
  ActionModeTitleDataMatcher(ActionModeTitleData expected) {
    this.expected = expected;
  }

  @Override
  public boolean matches(ActionModeTitleData actual) {
    return expected.isShowFileSize()    == actual.isShowFileSize() &&
           expected.getMediaCount()     == actual.getMediaCount() &&
           expected.getTotalMediaSize() == actual.getTotalMediaSize();
  }
}