package org.thoughtcrime.securesms.components.mp02anim;

import android.view.ViewGroup;

public class ItemAnimViewController<T extends ViewGroup> {
  protected AnimyAction animyAction;

  public ItemAnimViewController(T layoutView, int textSize, int itemHeight, int maginTop) {
    animyAction = new ItemAnimView<>(layoutView, textSize, itemHeight, maginTop);
  }

  public void actionUpIn(String title1, String title2) {
    animyAction.upIn(title1, title2);
  }

  public void actionDownIn(String title1, String title2) {
    animyAction.downIn(title1, title2);
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

  public void setViewTitleText(String text) {
    animyAction.setItemText(text);
  }


  public interface AnimyAction {
    void upIn(String title1, String title2);

    void downIn(String title1, String title2);

    void downInWithEdt(String title1, String title2);

    void editTextChange(String txt);

    void setInVisibility(boolean isGone);

    void setOutVisibility(boolean isGone);

    void setEdtVisibility(boolean isGone);

    void setItemVisibility(boolean isGone);

    void setItemText(String text);
  }
}