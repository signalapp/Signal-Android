//package org.thoughtcrime.redphone.ui;
//
//import android.content.Context;
//import android.util.AttributeSet;
//import android.view.View;
//
//import com.actionbarsherlock.internal.view.View_HasStateListenerSupport;
//import com.actionbarsherlock.internal.view.View_OnAttachStateChangeListener;
//
///**
// * The ABS MenuPopupHelper will only attach to a view that implements View_HasStateListenerSupport.
// *
// * This view can be included in layouts to provide attachment points for PopupMenus.  We use it in
// * the InCallAudioButton to provide an attachment point for the audio routing selection popup.
// *
// * @author Stuart O. Anderson
//*/
//public class StateListenerView extends View implements View_HasStateListenerSupport {
//  public StateListenerView(Context context) {
//    super(context);
//  }
//
//  public StateListenerView(Context context, AttributeSet attrs) {
//    super(context, attrs);
//  }
//
//  public StateListenerView(Context context, AttributeSet attrs, int defStyle) {
//    super(context, attrs, defStyle);
//  }
//
//  @Override
//  public void addOnAttachStateChangeListener(View_OnAttachStateChangeListener listener) {
//  }
//
//  @Override
//  public void removeOnAttachStateChangeListener(View_OnAttachStateChangeListener listener) {
//  }
//}
