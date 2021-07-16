package org.thoughtcrime.securesms.components.mp02anim;
import android.view.ViewGroup;
public class ItemAnimViewController2<T extends ViewGroup> {
  protected AnimyAction2 animyAction;
  public ItemAnimViewController2(T layoutView, int textSize, int itemHeight, int maginTop) {
    animyAction = new ItemAnimView2<>(layoutView, textSize, itemHeight, maginTop);
  }
  public void actionUpIn(String title01, String title02, String title1, String title2) {
    animyAction.upIn(title01, title02, title1, title2);
  }
  //    public void actionUpOut(String title){
//        animyAction.upOut( title );
//    }
  public void actionDownIn(String title01, String title02, String title1, String title2) {
    animyAction.downIn(title01, title02, title1, title2);
  }
  public void editTextChange(String txt) {
    animyAction.editTextChange(txt);
  }
  public void setInVisibility(boolean isGone) {
    animyAction.setInVisibility(isGone);
  }
  public void setOutVisibility(boolean isGone) {
    animyAction.setOutVisibility(isGone);
  }
  public void actionDownInWithEdt(String title1, String title2) {
    animyAction.downInWithEdt(title1, title2);
  }
  public void setItemVisibility(boolean isGone) {
    animyAction.setItemVisibility(isGone);
  }
  public void setViewTitleText(String text){
    animyAction.setItemText(text);
  }

  public void ref(){
    animyAction.ref();
  }

  public interface AnimyAction2 {
    public void upIn(String title01, String title02, String title1, String title2);
    public void downIn(String title01, String title02, String title1, String title2);
    public void downInWithEdt(String title1, String title2);
    public void editTextChange(String txt);
    public void setInVisibility(boolean isGone);
    public void setOutVisibility(boolean isGone);
    public void setEdtVisibility(boolean isGone);
    public void setItemVisibility(boolean isGone);
    public void setItemText(String text);
    public void ref();
  }
}