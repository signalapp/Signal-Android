package org.thoughtcrime.securesms.sharing.interstitial;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.dd.CircularProgressButton;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.LinkPreviewView;
import org.thoughtcrime.securesms.components.SelectionAwareEmojiEditText;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sharing.MultiShareArgs;
import org.thoughtcrime.securesms.sharing.MultiShareDialogs;
import org.thoughtcrime.securesms.sharing.ShareFlowConstants;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;

import java.util.Objects;

/**
 * Handles display and editing of a text message (with possible link preview) before it is forwarded
 * to multiple users.
 */
public class ShareInterstitialActivity extends PassphraseRequiredActivity {

  private static final String ARGS = "args";

  private ShareInterstitialViewModel viewModel;
  private LinkPreviewViewModel       linkPreviewViewModel;
  private TextView                   confirm;
  private RecyclerView               contactsRecycler;
  private Toolbar                    toolbar;
  private LinkPreviewView            preview;

  public static int mFocusHeight;
  public static int mNormalHeight;
  public static int mFocusTextSize;
  public static int mNormalTextSize;
  public static int mNormalPaddingX;
  public static int mFocusPaddingX;

  private final DynamicTheme                      dynamicTheme = new DynamicNoActionBarTheme();
  private final ShareInterstitialSelectionAdapter adapter      = new ShareInterstitialSelectionAdapter();

  public static Intent createIntent(@NonNull Context context, @NonNull MultiShareArgs multiShareArgs) {
    Intent intent = new Intent(context, ShareInterstitialActivity.class);

    intent.putExtra(ARGS, multiShareArgs);

    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    dynamicTheme.onCreate(this);
    setContentView(R.layout.share_interstitial_activity);

    MultiShareArgs args = getIntent().getParcelableExtra(ARGS);

    initializeViewModels(args);
    initializeViews(args);
    initializeObservers();
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  private void initializeViewModels(@NonNull MultiShareArgs args) {
    ShareInterstitialRepository        repository = new ShareInterstitialRepository();
    ShareInterstitialViewModel.Factory factory    = new ShareInterstitialViewModel.Factory(args, repository);

    viewModel = ViewModelProviders.of(this, factory).get(ShareInterstitialViewModel.class);

    LinkPreviewRepository        linkPreviewRepository       = new LinkPreviewRepository();
    LinkPreviewViewModel.Factory linkPreviewViewModelFactory = new LinkPreviewViewModel.Factory(linkPreviewRepository);

    linkPreviewViewModel = ViewModelProviders.of(this, linkPreviewViewModelFactory).get(LinkPreviewViewModel.class);

    boolean hasSms = Stream.of(args.getShareContactAndThreads())
                           .anyMatch(c -> {
                             Recipient recipient = Recipient.resolved(c.getRecipientId());
                             return !recipient.isRegistered() || recipient.isForceSmsSelection();
                           });

    if (hasSms) {
      linkPreviewViewModel.onTransportChanged(hasSms);
    }
  }

  private void initializeViews(@NonNull MultiShareArgs args) {
    confirm = findViewById(R.id.share_confirm);
    toolbar = findViewById(R.id.toolbar);
    preview = findViewById(R.id.link_preview);

    confirm.setOnClickListener(unused -> onConfirm());

    SelectionAwareEmojiEditText text = findViewById(R.id.text);

    toolbar.setNavigationOnClickListener(unused -> finish());

    text.addTextChangedListener(new AfterTextChanged(editable -> {
      linkPreviewViewModel.onTextChanged(this, editable.toString(), text.getSelectionStart(), text.getSelectionEnd());
      viewModel.onDraftTextChanged(editable.toString());
    }));

    //noinspection CodeBlock2Expr
    text.setOnSelectionChangedListener(((selStart, selEnd) -> {
      linkPreviewViewModel.onTextChanged(this, text.getText().toString(), text.getSelectionStart(), text.getSelectionEnd());
    }));

    preview.setCloseClickedListener(linkPreviewViewModel::onUserCancel);

    int defaultRadius = getResources().getDimensionPixelSize(R.dimen.thumbnail_default_radius);
    preview.setCorners(defaultRadius, defaultRadius);

    text.setText(args.getDraftText());
    ViewUtil.focusAndMoveCursorToEndAndOpenKeyboard(text);

    contactsRecycler = findViewById(R.id.selected_list);
    contactsRecycler.setAdapter(adapter);

    RecyclerView.ItemAnimator itemAnimator = Objects.requireNonNull(contactsRecycler.getItemAnimator());
    ShareFlowConstants.applySelectedContactsRecyclerAnimationSpeeds(itemAnimator);

    /*confirm.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      int pad = Math.abs(v.getWidth() + ViewUtil.dpToPx(16));
      ViewUtil.setPaddingEnd(contactsRecycler, pad);
    });*/

    Resources res = getResources();
    mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
    mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);

    mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
    mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);

    mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
    mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);

    confirm.setOnFocusChangeListener(new View.OnFocusChangeListener() {
      @Override
      public void onFocusChange(View v, boolean hasFocus) {
        updateFocusView(confirm,confirm,hasFocus);
      }
    });
  }

  private void updateFocusView(View parent, TextView tv, boolean itemFocus) {
    ValueAnimator va;
    if (itemFocus) {
      va = ValueAnimator.ofFloat(0, 1);
    } else {
      va = ValueAnimator.ofFloat(1, 0);
    }

    va.addUpdateListener(valueAnimator -> {
      float scale = (float) valueAnimator.getAnimatedValue();
      float height = ((float) (mFocusHeight - mNormalHeight)) * (scale) + (float) mNormalHeight;
      float textsize = ((float) (mFocusTextSize - mNormalTextSize)) * (scale)/2 + mNormalTextSize;
      int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (scale));
      int color = alpha * 0x1000000 + 0xffffff;

      tv.setTextColor(color);
      tv.setTextSize((int) textsize);
      tv.getLayoutParams().height = (int) height;
      parent.getLayoutParams().height = (int) (height);
    });

    FastOutLinearInInterpolator FastOutLinearInInterpolator = new FastOutLinearInInterpolator();
    va.setInterpolator(FastOutLinearInInterpolator);
    if (itemFocus) {
      va.setDuration(270);
      va.start();
    } else {
      va.setDuration(270);
      va.start();
    }
  }

  private void initializeObservers() {
    viewModel.getRecipients().observe(this, models -> adapter.submitList(models,
                                                                         () -> contactsRecycler.scrollToPosition(models.size() - 1)));
    viewModel.hasDraftText().observe(this, this::handleHasDraftText);

    linkPreviewViewModel.getLinkPreviewState().observe(this, linkPreviewState -> {
      preview.setVisibility(View.VISIBLE);
      if (linkPreviewState.getError() != null) {
        preview.setNoPreview(linkPreviewState.getError());
        viewModel.onLinkPreviewChanged(null);
      } else if (linkPreviewState.isLoading()) {
        preview.setLoading();
        viewModel.onLinkPreviewChanged(null);
      } else if (linkPreviewState.getLinkPreview().isPresent()) {
        preview.setLinkPreview(GlideApp.with(this), linkPreviewState.getLinkPreview().get(), true);
        viewModel.onLinkPreviewChanged(linkPreviewState.getLinkPreview().get());
      } else if (!linkPreviewState.hasLinks()) {
        preview.setVisibility(View.GONE);
        viewModel.onLinkPreviewChanged(null);
      }
    });
  }

  private void handleHasDraftText(boolean hasDraftText) {
    confirm.setEnabled(hasDraftText);
    confirm.setAlpha(hasDraftText ? 1f : 0.5f);
  }

  private void onConfirm() {
    confirm.setClickable(false);
//    confirm.setIndeterminateProgressMode(true);
//    confirm.setProgress(50);

    viewModel.send(results -> {
      MultiShareDialogs.displayResultDialog(this, results, () -> {
        setResult(RESULT_OK);
        finish();
      });
    });
  }
}
