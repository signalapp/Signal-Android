package org.thoughtcrime.securesms.components.camera;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.nineoldandroids.animation.ObjectAnimator;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.InputAwareLayout.InputView;
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout;
import org.thoughtcrime.securesms.components.camera.CameraView.CameraViewListener;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

public class QuickAttachmentDrawer extends ViewGroup implements InputView {
  private static final String TAG = QuickAttachmentDrawer.class.getSimpleName();

  private final ViewDragHelper dragHelper;

  private CameraView                cameraView;
  private int                       coverViewPosition;
  private KeyboardAwareLinearLayout container;
  private View                      coverView;
  private View                      controls;
  private ImageButton               fullScreenButton;
  private ImageButton               swapCameraButton;
  private ImageButton               shutterButton;
  private int                       slideOffset;
  private float                     initialMotionX;
  private float                     initialMotionY;
  private int                       rotation;
  private AttachmentDrawerListener  listener;
  private int                       halfExpandedHeight;
  private ObjectAnimator            animator;

  private DrawerState drawerState      = DrawerState.COLLAPSED;
  private Rect        drawChildrenRect = new Rect();
  private boolean     paused           = false;

  public QuickAttachmentDrawer(Context context) {
    this(context, null);
  }

  public QuickAttachmentDrawer(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public QuickAttachmentDrawer(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    dragHelper = ViewDragHelper.create(this, 1.f, new ViewDragHelperCallback());
    initializeView();
    updateHalfExpandedAnchorPoint();
    onConfigurationChanged();
  }

  private void initializeView() {
    inflate(getContext(), R.layout.quick_attachment_drawer, this);
    cameraView = ViewUtil.findById(this, R.id.quick_camera);
    updateControlsView();

    coverViewPosition = getChildCount();
    controls.setVisibility(GONE);
    cameraView.setVisibility(GONE);
  }

  public static boolean isDeviceSupported(Context context) {
    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA) &&
           Camera.getNumberOfCameras() > 0;
  }

  @Override
  public boolean isShowing() {
    return drawerState.isVisible();
  }

  @Override
  public void hide(boolean immediate) {
    setDrawerStateAndUpdate(DrawerState.COLLAPSED, immediate);
  }

  @Override
  public void show(int height, boolean immediate) {
    setDrawerStateAndUpdate(DrawerState.HALF_EXPANDED, immediate);
  }

  public void onConfigurationChanged() {
    int rotation = ServiceUtil.getWindowManager(getContext()).getDefaultDisplay().getRotation();
    final boolean rotationChanged = this.rotation != rotation;
    this.rotation = rotation;
    if (rotationChanged) {
      if (isShowing()) {
        cameraView.onPause();
      }
      updateControlsView();
      setDrawerStateAndUpdate(drawerState, true);
    }
  }

  private void updateControlsView() {
    Log.w(TAG, "updateControlsView()");
    View controls = LayoutInflater.from(getContext()).inflate(isLandscape() ? R.layout.quick_camera_controls_land
                                                                            : R.layout.quick_camera_controls,
                                                              this, false);
    shutterButton    = (ImageButton) controls.findViewById(R.id.shutter_button);
    swapCameraButton = (ImageButton) controls.findViewById(R.id.swap_camera_button);
    fullScreenButton = (ImageButton) controls.findViewById(R.id.fullscreen_button);
    if (cameraView.isMultiCamera()) {
      swapCameraButton.setVisibility(View.VISIBLE);
      swapCameraButton.setImageResource(cameraView.isRearCamera() ? R.drawable.quick_camera_front
                                                                  : R.drawable.quick_camera_rear);
      swapCameraButton.setOnClickListener(new CameraFlipClickListener());
    }
    shutterButton.setOnClickListener(new ShutterClickListener());
    fullScreenButton.setOnClickListener(new FullscreenClickListener());
    ViewUtil.swapChildInPlace(this, this.controls, controls, indexOfChild(cameraView) + 1);
    this.controls = controls;
  }

  private boolean isLandscape() {
    return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
  }

  private View getCoverView() {
    if (coverView == null) coverView = getChildAt(coverViewPosition);
    return coverView;
  }

  private KeyboardAwareLinearLayout getContainer() {
    if (container == null) container = (KeyboardAwareLinearLayout)getParent();
    return container;
  }

  private void updateHalfExpandedAnchorPoint() {
    if (getContainer() != null) {
      halfExpandedHeight = getContainer().getKeyboardHeight();
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    if (changed && drawerState == DrawerState.FULL_EXPANDED) {
      setSlideOffset(getMeasuredHeight());
    }

    final int paddingLeft = getPaddingLeft();
    final int paddingTop  = getPaddingTop();

    for (int i = 0; i < getChildCount(); i++) {
      final View child       = getChildAt(i);
      final int  childHeight = child.getMeasuredHeight();

      int childTop  = paddingTop;
      int childLeft = paddingLeft;
      int childBottom;

      if (child == cameraView) {
        childTop    = computeCameraTopPosition(slideOffset);
        childBottom = childTop + childHeight;
        if (cameraView.getMeasuredWidth() < getMeasuredWidth())
          childLeft = (getMeasuredWidth() - cameraView.getMeasuredWidth()) / 2 + paddingLeft;
      } else if (child == controls) {
        childBottom = getMeasuredHeight();
      } else {
        childTop    = computeCoverTopPosition(slideOffset);
        childBottom = childTop + childHeight;
      }
      final int childRight = childLeft + child.getMeasuredWidth();

      child.layout(childLeft, childTop, childRight, childBottom);
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    final int widthMode  = MeasureSpec.getMode(widthMeasureSpec);
    final int widthSize  = MeasureSpec.getSize(widthMeasureSpec);
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
    if (child == coverView) {
      drawChildrenRect.bottom = Math.min(drawChildrenRect.bottom, child.getBottom());
    } else if (coverView != null) {
      drawChildrenRect.top = Math.max(drawChildrenRect.top, coverView.getBottom());
    }
    canvas.clipRect(drawChildrenRect);
    result = super.drawChild(canvas, child, drawingTime);
    canvas.restoreToCount(save);
    return result;
  }

  @Override
  public void computeScroll() {
    if (dragHelper.continueSettling(true)) {
      ViewCompat.postInvalidateOnAnimation(this);
    }

    if (slideOffset == 0 && cameraView.isStarted()) {
      cameraView.onPause();
      controls.setVisibility(GONE);
      cameraView.setVisibility(GONE);
    } else if (slideOffset != 0 && !cameraView.isStarted() & !paused) {
      controls.setVisibility(VISIBLE);
      cameraView.setVisibility(VISIBLE);
      cameraView.onResume();
    }
  }

  private void setDrawerState(DrawerState drawerState) {
    switch (drawerState) {
    case COLLAPSED:
      fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
      break;
    case HALF_EXPANDED:
      if (isLandscape()) {
        setDrawerState(DrawerState.FULL_EXPANDED);
        return;
      }
      fullScreenButton.setImageResource(R.drawable.quick_camera_fullscreen);
      break;
    case FULL_EXPANDED:
      fullScreenButton.setImageResource(isLandscape() ? R.drawable.quick_camera_hide
                                                      : R.drawable.quick_camera_exit_fullscreen);
      break;
    }

    if (listener != null && drawerState != this.drawerState) {
      this.drawerState = drawerState;
      listener.onAttachmentDrawerStateChanged(drawerState);
    }
  }

  @SuppressWarnings("unused")
  public void setSlideOffset(int slideOffset) {
    this.slideOffset = slideOffset;
    requestLayout();
  }

  public int getTargetSlideOffset() {
    switch (drawerState) {
    case FULL_EXPANDED: return getMeasuredHeight();
    case HALF_EXPANDED: return halfExpandedHeight;
    default:            return 0;
    }
  }

  public void setDrawerStateAndUpdate(final DrawerState requestedDrawerState) {
    setDrawerStateAndUpdate(requestedDrawerState, false);
  }

  public void setDrawerStateAndUpdate(final DrawerState requestedDrawerState, boolean instant) {
    DrawerState oldDrawerState = this.drawerState;
    setDrawerState(requestedDrawerState);
    if (oldDrawerState != drawerState) {
      updateHalfExpandedAnchorPoint();
      slideTo(getTargetSlideOffset(), instant);
    }
  }

  public void setListener(AttachmentDrawerListener listener) {
    this.listener = listener;
    if (cameraView != null) cameraView.setListener(listener);
  }

  public interface AttachmentDrawerListener extends CameraViewListener {
    void onAttachmentDrawerStateChanged(DrawerState drawerState);
  }

  private class ViewDragHelperCallback extends ViewDragHelper.Callback {

    @Override
    public boolean tryCaptureView(View child, int pointerId) {
      return child == controls;
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
      slideOffset = Util.clamp(slideOffset - dy, 0, getMeasuredHeight());
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
          boolean halfExpand = (slideOffset > halfExpandedHeight && !isLandscape());
          drawerState = halfExpand ? DrawerState.HALF_EXPANDED : DrawerState.COLLAPSED;
        } else if (!isLandscape()) {
          if (slideOffset >= (halfExpandedHeight + getMeasuredHeight()) / 2) {
            drawerState = DrawerState.FULL_EXPANDED;
          } else if (slideOffset >= halfExpandedHeight / 2) {
            drawerState = DrawerState.HALF_EXPANDED;
          }
        }

        setDrawerState(drawerState);
        int slideOffset = getTargetSlideOffset();
        dragHelper.captureChildView(coverView, 0);
        dragHelper.settleCapturedViewAt(coverView.getLeft(), computeCoverTopPosition(slideOffset));
        dragHelper.captureChildView(cameraView, 0);
        dragHelper.settleCapturedViewAt(cameraView.getLeft(), computeCameraTopPosition(slideOffset));
        ViewCompat.postInvalidateOnAnimation(QuickAttachmentDrawer.this);
      }
    }

    @Override
    public int getViewVerticalDragRange(View child) {
      return getMeasuredHeight();
    }

    @Override
    public int clampViewPositionVertical(View child, int top, int dy) {
      return top;
    }
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
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

  @Override
  public boolean onTouchEvent(@NonNull MotionEvent event) {
    dragHelper.processTouchEvent(event);
    return true;
  }

  // NOTE: Android Studio bug misreports error, squashing the warning.
  // https://code.google.com/p/android/issues/detail?id=175977
  @SuppressWarnings("ResourceType")
  private boolean isDragViewUnder(int x, int y) {
    int[] viewLocation = new int[2];
    cameraView.getLocationOnScreen(viewLocation);
    int[] parentLocation = new int[2];
    this.getLocationOnScreen(parentLocation);
    int screenX = parentLocation[0] + x;
    int screenY = parentLocation[1] + y;
    return screenX >= viewLocation[0] && screenX < viewLocation[0] + cameraView.getWidth() &&
           screenY >= viewLocation[1] && screenY < viewLocation[1] + cameraView.getHeight();
  }

  private int computeCameraTopPosition(int slideOffset) {
    if (VERSION.SDK_INT < VERSION_CODES.ICE_CREAM_SANDWICH) {
      return getPaddingTop();
    }

    final int   baseCameraTop = (cameraView.getMeasuredHeight() - halfExpandedHeight) / 2;
    final int   baseOffset    = getMeasuredHeight() - slideOffset - baseCameraTop;
    final float slop          = Util.clamp((float)(slideOffset - halfExpandedHeight) / (getMeasuredHeight() - halfExpandedHeight),
                                           0f,
                                           1f);
    return baseOffset + (int)(slop * baseCameraTop);
  }

  private int computeCoverTopPosition(int slideOffset) {
    return getMeasuredHeight() - getPaddingBottom() - slideOffset - getCoverView().getMeasuredHeight();
  }

  private void slideTo(int slideOffset, boolean forceInstant) {
    if (animator != null) {
      animator.cancel();
      animator = null;
    }

    if (!forceInstant) {
      animator = ObjectAnimator.ofInt(this, "slideOffset", this.slideOffset, slideOffset);
      animator.setInterpolator(new FastOutSlowInInterpolator());
      animator.setDuration(400);
      animator.start();
      ViewCompat.postInvalidateOnAnimation(this);
    } else {
      this.slideOffset = slideOffset;
      requestLayout();
      invalidate();
    }
  }

  public void onPause() {
    paused = true;
    cameraView.onPause();
  }

  public void onResume() {
    paused = false;
    if (drawerState.isVisible()) cameraView.onResume();
  }

  public enum DrawerState {
    COLLAPSED, HALF_EXPANDED, FULL_EXPANDED;

    public boolean isVisible() {
      return this != COLLAPSED;
    }
  }

  private class ShutterClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      boolean crop        = drawerState != DrawerState.FULL_EXPANDED;
      int     imageHeight = crop ? getContainer().getKeyboardHeight() : cameraView.getMeasuredHeight();
      Rect    previewRect = new Rect(0, 0, cameraView.getMeasuredWidth(), imageHeight);
      cameraView.takePicture(previewRect);
    }
  }

  private class CameraFlipClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      cameraView.flipCamera();
      swapCameraButton.setImageResource(cameraView.isRearCamera() ? R.drawable.quick_camera_front
                                                                  : R.drawable.quick_camera_rear);
    }
  }

  private class FullscreenClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      if (drawerState != DrawerState.FULL_EXPANDED) {
        setDrawerStateAndUpdate(DrawerState.FULL_EXPANDED);
      } else if (isLandscape()) {
        setDrawerStateAndUpdate(DrawerState.COLLAPSED);
      } else {
        setDrawerStateAndUpdate(DrawerState.HALF_EXPANDED);
      }
    }
  }
}
