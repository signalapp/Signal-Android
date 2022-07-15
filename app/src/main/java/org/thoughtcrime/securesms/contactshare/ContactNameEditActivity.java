package org.thoughtcrime.securesms.contactshare;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;

import static org.thoughtcrime.securesms.contactshare.Contact.Name;

public class ContactNameEditActivity extends PassphraseRequiredActivity {

  public static final String KEY_NAME          = "name";
  public static final String KEY_CONTACT_INDEX = "contact_index";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  private ContactNameEditViewModel viewModel;

  static Intent getIntent(@NonNull Context context, @NonNull Name name, int contactPosition) {
    Intent intent = new Intent(context, ContactNameEditActivity.class);
    intent.putExtra(KEY_NAME, name);
    intent.putExtra(KEY_CONTACT_INDEX, contactPosition);
    return intent;
  }

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);

    if (getIntent() == null) {
      throw new IllegalStateException("You must supply extras to this activity. Please use the #getIntent() method.");
    }

    Name name = getIntent().getParcelableExtra(KEY_NAME);
    if (name == null) {
      throw new IllegalStateException("You must supply a name to this activity. Please use the #getIntent() method.");
    }

    setContentView(R.layout.activity_contact_name_edit);

    initializeToolbar();
    initializeViews(name);

    viewModel = new ViewModelProvider(this).get(ContactNameEditViewModel.class);
    viewModel.setName(name);
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  private void initializeToolbar() {
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    toolbar.setTitle("");
    toolbar.setNavigationOnClickListener(v -> {
      Intent resultIntent = new Intent();
      resultIntent.putExtra(KEY_NAME, viewModel.getName());
      resultIntent.putExtra(KEY_CONTACT_INDEX, getIntent().getIntExtra(KEY_CONTACT_INDEX, -1));
      setResult(RESULT_OK, resultIntent);
      finish();
    });
  }

  private void initializeViews(@NonNull Name name) {
    TextView givenName   = findViewById(R.id.name_edit_given_name);
    TextView familyName  = findViewById(R.id.name_edit_family_name);
    TextView middleName  = findViewById(R.id.name_edit_middle_name);
    TextView prefix      = findViewById(R.id.name_edit_prefix);
    TextView suffix      = findViewById(R.id.name_edit_suffix);

    givenName.setText(name.getGivenName());
    familyName.setText(name.getFamilyName());
    middleName.setText(name.getMiddleName());
    prefix.setText(name.getPrefix());
    suffix.setText(name.getSuffix());

    givenName.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      public void onTextChanged(String text) {
        viewModel.updateGivenName(text);
      }
    });

    familyName.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      public void onTextChanged(String text) {
        viewModel.updateFamilyName(text);
      }
    });

    middleName.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      public void onTextChanged(String text) {
        viewModel.updateMiddleName(text);
      }
    });

    prefix.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      public void onTextChanged(String text) {
        viewModel.updatePrefix(text);
      }
    });

    suffix.addTextChangedListener(new SimpleTextWatcher() {
      @Override
      public void onTextChanged(String text) {
        viewModel.updateSuffix(text);
      }
    });
  }
}
