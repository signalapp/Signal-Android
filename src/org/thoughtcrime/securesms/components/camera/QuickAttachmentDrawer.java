package org.thoughtcrime.securesms.components.camera;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Build.VERSION;
import android.support.annotation.NonNull;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.widget.ImageButton;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.camera.QuickCamera.QuickCameraListener;
import org.thoughtcrime.securesms.util.SoftKeyboardUtil;

public class QuickAttachmentDrawer extends ViewGroup {
  private static final String TAG                        = QuickAttachmentDrawer.class.getSimpleName();
  private static final float  FULL_EXPANDED_ANCHOR_POINT = 1.f;
  private static final float  COLLAPSED_ANCHOR_POINT     = 0.f;

  private final ViewDragHelper dragHelper;

  private QuickCamera              quickCamera;
  private int                      coverViewPosition;
  private View                     coverView;
  private View                     controls;
  private ImageButton              fullScreenButton;
  private ImageButton              swapCameraButton;
  private ImageButton              shutterButton;
  private float                    slideOffset;
  private float                    initialMotionX;
  private float                    initialMotionY;
  private int                      rotation;
  private int                      slideRange;
  private int                      baseHalfHeight;
  private AttachmentDrawerListener listener;

  private DrawerState drawerState             = DrawerState.COLLAPSED;
  private float       halfExpandedAnchorPoint = COLLAPSED_ANCHOR_POINT;
  private boolean     halfModeUnsupported     = VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH;
  private Rect        drawChildrenRect        = new Rect();
  private boolean     paused                  = false;

  public QuickAttachmentDrawer(Context context) {
    this(context, null);
  }

  public QuickAttachmentDrawer(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public QuickAttachmentDrawer(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    baseHalfHeight = SoftKeyboardUtil.getKeyboardHeight(getContext());
    dragHelper     = ViewDragHelper.create(this, 1.f, new ViewDragHelperCallback());
    initializeView();
    updateHalfExpandedAnchorPoint();
    onConfigurationChanged();
  }

  private void initializeView() {
    inflate(getContext(), R.layout.quick_attachment_drawer, this);
    quickCamera = (QuickCamera) findViewById(R.id.quick_camera);
    updateControlsView();

    coverViewPosition = getChildCount();
  }

  private WindowManager getWindowManager() {
    return (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
  }

  public static boolean isDeviceSupported(Context context) {
    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA) &&
           Camera.getNumberOfCameras() > 0;
  }

  public boolean isOpen() {
    return drawerState.isVisible();
  }

  public void close() {
    setDrawerStateAndAnimate(DrawerState.COLLAPSED);
  }

  public void open() {
    setDrawerStateAndAnimate(DrawerState.HALF_EXPANDED);
  }

  public void onConfigurationChanged() {
    int rotation = getWindowManager().getDefaultDisplay().getRotation();
    final boolean rotationChanged = this.rotation != rotation;
    this.rotation = rotation;
    if (rotationChanged) {
      if (isOpen()) {
        quickCamera.onPause();
      }
      updateControlsView();
      setDrawerStateAndAnimate(drawerState);
    }
  }

  private void updateControlsView() {
    int controlsIndex = indexOfChild(controls);
    if (controlsIndex > -1) removeView(controls);
    controls = LayoutInflater.from(getContext()).inflate(isLandscape() ? R.layout.quick_camera_controls_land
                                                                       : R.layout.quick_camera_controls,
                                                         this, false);
    shutterButton    = (ImageButton) controls.findViewById(R.id.shutter_button);
    swapCameraButton = (ImageButton) controls.findViewById(R.id.swap_camera_button);
    fullScreenButton = (ImageButton) controls.findViewById(R.id.fullscreen_button);
    if (quickCamera.isMultipleCameras()) {
      swapCameraButton.setVisibility(View.VISIBLE);
      swapCameraButton.setOnClickListener(new CameraFlipClickListener());
    }
    shutterButton.setOnClickListener(new ShutterClickListener());
    fullScreenButton.setOnClickListener(new FullscreenClickListener());
    controls.setVisibility(INVISIBLE);
    addView(controls, controlsIndex > -1 ? controlsIndex : indexOfChild(quickCamera) + 1);
  }

  private boolean isLandscape() {
    return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
  }

  private boolean isFullscreenOnly() {
    return isLandscape() || halfModeUnsupported;
  }

  private void updateHalfExpandedAnchorPoint() {
    Log.w(TAG, "updateHalfExpandedAnchorPoint()");
    getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
      @SuppressWarnings("deprecation") @Override public void onGlobalLayout() {
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
          getViewTreeObserver().removeOnGlobalLayoutListener(this);
        } else {
          getViewTreeObserver().removeGlobalOnLayoutListener(this);
        }

        coverView               = getChildAt(coverViewPosition);
        slideRange              = getMeasuredHeight();
        halfExpandedAnchorPoint = computeSlideOffsetFromCoverBottom(slideRange - baseHalfHeight);
        requestLayout();
        invalidate();
        Log.w(TAG, "updated halfExpandedAnchorPoint!");
      }
    });
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    final int paddingLeft = getPaddingLeft();
    final int paddingTop  = getPaddingTop();

    for (int i = 0; i < getChildCount(); i++) {
      final View child       = getChildAt(i);
      final int  childHeight = child.getMeasuredHeight();

      int childTop  = paddingTop;
      int childLeft = paddingLeft;
      int childBottom;

      if (child == quickCamera) {
        childTop    = computeCameraTopPosition(slideOffset);
        childBottom = childTop + childHeight;
        if (quickCamera.getMeasuredWidth() < getMeasuredWidth())
          childLeft = (getMeasuredWidth() - quickCamera.getMeasuredWidth()) / 2 + paddingLeft;
      } else if (child == controls) {
        childBottom = getMeasuredHeight();
      } else {
        childBottom = computeCoverBottomPosition(slideOffset);
        childTop    = childBottom - childHeight;
      }
      final int childRight = childLeft + child.getMeasuredWidth();

      child.layout(childLeft, childTop, childRight, childBottom);
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

    if (widthMode != MeasureSpec.EXACTLY) {
      throw new IllegalStateException("Width must have an exact value or MATCH_PARENT");
    } else if (heightMode != MeasureSpec.EXACTLY) {
      throw new IllegalStateException("Height must have an exact value or MATCH_PARENT");
    }

    int layoutHeight = heightSize - getPaddingTop() - getPaddingBottom();

    for (int i = 0; i < getChildCount(); i++) {
      final View child = getChildAt(i);
      final LayoutParams lp = child.getLayoutParams();

      if (child.getVisibility() == GONE && i == 0) {
        continue;
      }

      int childWidthSpec;
      switch (lp.width) {
        case LayoutParams.WRAP_CONTENT:
          childWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.AT_MOST);
          break;
        case LayoutParams.MATCH_PARENT:
          childWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
          break;
        default:
          childWidthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
          break;
      }

      int childHeightSpec;
      switch (lp.height) {
        case LayoutParams.WRAP_CONTENT:
          childHeightSpec = MeasureSpec.makeMeasureSpec(layoutHeight, MeasureSpec.AT_MOST);
          break;
        case LayoutParams.MATCH_PARENT:
          childHeightSpec = MeasureSpec.makeMeasureSpec(layoutHeight, MeasureSpec.EXACTLY);
          break;
        default:
          childHeightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
          break;
    }

      child.measure(childWidthSpec, childHeightSpec);
    }

    setMeasuredDimension(widthSize, heightSize);
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (h != oldh) updateHalfExpandedAnchorPoint();
  }

  @Override
  protected boolean drawChild(@NonNull Canvas canvas, @NonNull View child, long drawingTime) {
    boolean result;
    final int save = canvas.save(Canvas.CLIP_SAVE_FLAG);

    canvas.getClipBounds(drawChildrenRect);
    if (child == coverView)
      drawChildrenRect.bottom = Math.min(drawChildrenRect.bottom, child.getBottom());
    else if (coverView != null)
      drawChildrenRect.top = Math.max(drawChildrenRect.top, coverView.getBottom());
    canvas.clipRect(drawChildrenRect);
    result = super.drawChild(canvas, child, drawingTime);
    canvas.restoreToCount(save);
    return result;
  }

  @Override
  public void computeScroll() {
    if (dragHelper != null && dragHelper.continueSettling(true)) {
      ViewCompat.postInvalidateOnAnimation(this);
    }

    if (slideOffset == COLLAPSED_ANCHOR_POINT && quickCamera.isStarted()) {
      quickCamera.onPause();
      controls.setVisibility(INVISIBLE);
      quickCamera.setVisibility(INVISIBLE);
    } else if (slideOffset != COLLAPSED_ANCHOR_POINT && !quickCamera.isStarted() & !paused) {
      controls.setVisibility(VISIBLE);
      quickCamera.setVisibility(VISIBLE);
      quickCamera.onResume();
    }
  }

  private void setDrawerState(DrawerState drawerState) {
    switch (drawerState) {
    case COLLAPSED:
      fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
      if (listener != null) listener.onAttachmentDrawerClosed();
      break;
    case HALF_EXPANDED:
      if (isFullscreenOnly()) {
        setDrawerState(DrawerState.FULL_EXPANDED);
        return;
      }
      fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
      if (listener != null) listener.onAttachmentDrawerOpened();
      break;
    case FULL_EXPANDED:
      fullScreenButton.setImageResource(isFullscreenOnly() ? R.drawable.quick_camera_hide
                                                           : R.drawable.quick_camera_exit_fullscreen);
      if (listener != null) listener.onAttachmentDrawerOpened();
      break;
    }
    this.drawerState = drawerState;
  }

  public float getTargetSlideOffset() {
    switch (drawerState) {
    case FULL_EXPANDED: return FULL_EXPANDED_ANCHOR_POINT;
    case HALF_EXPANDED: return halfExpandedAnchorPoint;
    default: return COLLAPSED_ANCHOR_POINT;
    }
  }

  public void setDrawerStateAndAnimate(final DrawerState requestedDrawerState) {
    DrawerState oldDrawerState = this.drawerState;
    setDrawerState(requestedDrawerState);
    if (oldDrawerState != drawerState) {
      slideTo(getTargetSlideOffset());
    }
  }

  public void setListener(AttachmentDrawerListener listener) {
    this.listener = listener;
    if (quickCamera != null) quickCamera.setQuickCameraListener(listener);
  }

  public interface AttachmentDrawerListener extends QuickCameraListener {
    void onAttachmentDrawerClosed();
    void onAttachmentDrawerOpened();
  }

  private class ViewDragHelperCallback extends ViewDragHelper.Callback {

    @Override
    public boolean tryCaptureView(View child, int pointerId) {
      return child == controls && !halfModeUnsupported;
    }

    @Override
    public void onViewDragStateChanged(int state) {
      if (dragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE) {
        setDrawerState(drawerState);
        slideOffset = getTargetSlideOffset();
        requestLayout();
      }
    }

    @Override
    public void onViewCaptured(View capturedChild, int activePointerId) {
    }

    @Override
    public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
      final int expandedTop  = computeCoverBottomPosition(FULL_EXPANDED_ANCHOR_POINT) - coverView.getHeight();
      final int collapsedTop = computeCoverBottomPosition(COLLAPSED_ANCHOR_POINT)     - coverView.getHeight();
      final int newTop       = Math.min(Math.max(coverView.getTop() + dy, expandedTop), collapsedTop);
      slideOffset = computeSlideOffsetFromCoverBottom(newTop + coverView.getHeight());
      requestLayout();
    }

    @Override
    public void onViewReleased(View releasedChild, float xvel, float yvel) {
      if (releasedChild == controls) {
        float direction = -yvel;
        DrawerState drawerState = DrawerState.COLLAPSED;

        if (direction > 1) {
          drawerState = DrawerState.FULL_EXPANDED;
        } else if (direction < -1) {
          boolean halfExpand = (slideOffset > halfExpandedAnchorPoint && !isLandscape());
          drawerState = halfExpand ? DrawerState.HALF_EXPANDED : DrawerState.COLLAPSED;
        } else if (!isLandscape()) {
          if (halfExpandedAnchorPoint != 1 && slideOffset >= (1.f + halfExpandedAnchorPoint) / 2) {
            drawerState = DrawerState.FULL_EXPANDED;
          } else if (halfExpandedAnchorPoint == 1 && slideOffset >= 0.5f) {
            drawerState = DrawerState.FULL_EXPANDED;
          } else if (halfExpandedAnchorPoint != 1 && slideOffset >= halfExpandedAnchorPoint) {
            drawerState = DrawerState.HALF_EXPANDED;
          } else if (halfExpandedAnchorPoint != 1 && slideOffset >= halfExpandedAnchorPoint / 2) {
            drawerState = DrawerState.HALF_EXPANDED;
          }
        }

        setDrawerState(drawerState);
        float slideOffset = getTargetSlideOffset();
        dragHelper.captureChildView(coverView, 0);
        Log.w(TAG, String.format("setting cover at %d",  computeCoverBottomPosition(slideOffset) - coverView.getHeight()));
        dragHelper.settleCapturedViewAt(coverView.getLeft(), computeCoverBottomPosition(slideOffset) - coverView.getHeight());
        dragHelper.captureChildView(quickCamera, 0);
        dragHelper.settleCapturedViewAt(quickCamera.getLeft(), computeCameraTopPosition(slideOffset));
        ViewCompat.postInvalidateOnAnimation(QuickAttachmentDrawer.this);
      }
    }

    @Override
    public int getViewVerticalDragRange(View child) {
      return slideRange;
    }

    @Override
    public int clampViewPositionVertical(View child, int top, int dy) {
      return top;
    }
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
    if (dragHelper != null) {
      final int action = MotionEventCompat.getActionMasked(event);

      if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
        dragHelper.cancel();
        return false;
      }

      final float x = event.getX();
      final float y = event.getY();

      switch (action) {
      case MotionEvent.ACTION_DOWN:
        initialMotionX = x;
        initialMotionY = y;
        break;

      case MotionEvent.ACTION_MOVE:
        final float adx = Math.abs(x - initialMotionX);
        final float ady = Math.abs(y - initialMotionY);
        final int dragSlop = dragHelper.getTouchSlop();

        if (adx > dragSlop && ady < dragSlop) {
          return super.onInterceptTouchEvent(event);
        }

        if ((ady > dragSlop && adx > ady) || !isDragViewUnder((int) initialMotionX, (int) initialMotionY)) {
          dragHelper.cancel();
          return false;
        }
        break;
      }
      return dragHelper.shouldInterceptTouchEvent(event);
    }
    return super.onInterceptTouchEvent(event);
  }

  @Override
  public boolean onTouchEvent(@NonNull MotionEvent event) {
    if (dragHelper != null) {
      dragHelper.processTouchEvent(event);
      return true;
    }
    return super.onTouchEvent(event);
  }

  // NOTE: Android Studio bug misreports error, squashing the warning.
  // https://code.google.com/p/android/issues/detail?id=175977
  @SuppressWarnings("ResourceType")
  private boolean isDragViewUnder(int x, int y) {
    int[] viewLocation = new int[2];
    quickCamera.getLocationOnScreen(viewLocation);
    int[] parentLocation = new int[2];
    this.getLocationOnScreen(parentLocation);
    int screenX = parentLocation[0] + x;
    int screenY = parentLocation[1] + y;
    return screenX >= viewLocation[0] && screenX < viewLocation[0] + quickCamera.getWidth() &&
           screenY >= viewLocation[1] && screenY < viewLocation[1] + quickCamera.getHeight();
  }

  private int computeCameraTopPosition(float slideOffset) {
    float clampedOffset = slideOffset - halfExpandedAnchorPoint;
    if (clampedOffset < COLLAPSED_ANCHOR_POINT) {
      clampedOffset = COLLAPSED_ANCHOR_POINT;
    } else {
      clampedOffset = clampedOffset / (FULL_EXPANDED_ANCHOR_POINT - halfExpandedAnchorPoint);
    }
    float slidePixelOffset = slideOffset * slideRange +
                             (quickCamera.getMeasuredHeight() - baseHalfHeight) / 2 *
                             (FULL_EXPANDED_ANCHOR_POINT - clampedOffset);
    float marginPixelOffset = (getMeasuredHeight() - quickCamera.getMeasuredHeight()) / 2 * clampedOffset;
    return (int) (getMeasuredHeight() - slidePixelOffset + marginPixelOffset);
  }

  private int computeCoverBottomPosition(float slideOffset) {
    int slidePixelOffset = (int) (slideOffset * slideRange);
    return getMeasuredHeight() - getPaddingBottom() - slidePixelOffset;
  }

  private void slideTo(float slideOffset) {
    if (dragHelper != null && !halfModeUnsupported) {
      dragHelper.smoothSlideViewTo(coverView, coverView.getLeft(), computeCoverBottomPosition(slideOffset) - coverView.getHeight());
      dragHelper.smoothSlideViewTo(quickCamera, quickCamera.getLeft(), computeCameraTopPosition(slideOffset));
      ViewCompat.postInvalidateOnAnimation(this);
    } else {
      Log.w(TAG, "quick sliding to " + slideOffset);
      this.slideOffset = slideOffset;
      requestLayout();
      invalidate();
    }
  }

  private float computeSlideOffsetFromCoverBottom(int topPosition) {
    final int topBoundCollapsed = computeCoverBottomPosition(0);
    return (float) (topBoundCollapsed - topPosition) / slideRange;
  }

  public void onPause() {
    paused = true;
    quickCamera.onPause();
  }

  public void onResume() {
    paused = false;
    if (drawerState.isVisible()) quickCamera.onResume();
  }

  public enum DrawerState {
    COLLAPSED, HALF_EXPANDED, FULL_EXPANDED;

    public boolean isVisible() {
      return this == HALF_EXPANDED || this == FULL_EXPANDED;
    }
  }

  private class ShutterClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      boolean crop        = drawerState != DrawerState.FULL_EXPANDED;
      int     imageHeight = crop ? baseHalfHeight : quickCamera.getMeasuredHeight();
      Rect    previewRect = new Rect(0, 0, quickCamera.getMeasuredWidth(), imageHeight);
      quickCamera.takePicture(previewRect);
    }
  }

  private class CameraFlipClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      quickCamera.swapCamera();
      swapCameraButton.setImageResource(quickCamera.isRearCamera() ? R.drawable.quick_camera_front
                                                                   : R.drawable.quick_camera_rear);
    }
  }

  private class FullscreenClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      if (drawerState != DrawerState.FULL_EXPANDED) {
        setDrawerStateAndAnimate(DrawerState.FULL_EXPANDED);
      } else if (isFullscreenOnly()) {
        setDrawerStateAndAnimate(DrawerState.COLLAPSED);
      } else {
        setDrawerStateAndAnimate(DrawerState.HALF_EXPANDED);
      }
    }
  }
}
