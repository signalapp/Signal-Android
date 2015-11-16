package org.thoughtcrime.securesms;

import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class DeviceLinkFragment extends Fragment implements View.OnClickListener {

  private LinearLayout        container;
  private LinkClickedListener linkClickedListener;
  private Uri                 uri;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
    this.container = (LinearLayout) inflater.inflate(R.layout.device_link_fragment, container, false);
    this.container.findViewById(R.id.link_device).setOnClickListener(this);

    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
      container.setOrientation(LinearLayout.HORIZONTAL);
    } else {
      container.setOrientation(LinearLayout.VERTICAL);
    }

    return this.container;
  }

  @Override
  public void onConfigurationChanged(Configuration newConfiguration) {
    if (newConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      container.setOrientation(LinearLayout.HORIZONTAL);
    } else {
      container.setOrientation(LinearLayout.VERTICAL);
    }
  }

  public void setLinkClickedListener(Uri uri, LinkClickedListener linkClickedListener) {
    this.uri                 = uri;
    this.linkClickedListener = linkClickedListener;
  }

  @Override
  public void onClick(View v) {
    if (linkClickedListener != null) {
      linkClickedListener.onLink(uri);
    }
  }

  public interface LinkClickedListener {
    public void onLink(Uri uri);
  }
}
