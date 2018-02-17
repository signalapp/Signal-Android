package org.thoughtcrime.securesms;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import org.thoughtcrime.securesms.androidtagview.TagContainerLayout;
import org.thoughtcrime.securesms.androidtagview.TagView;
import org.thoughtcrime.securesms.database.MutedWordsDatabase;

public class MutedWordsActivity extends PassphraseRequiredActionBarActivity {
    @Override
    public void onCreate(Bundle bundle, boolean ready) {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Muted Words");
        initFragment(android.R.id.content, new MutedWordsFragment());
    }


    public static class MutedWordsFragment extends Fragment {

        private TagContainerLayout tagContainerLayout;
        private MutedWordsDatabase mutedWordsDatabase;
        private EditText wordEditText;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mutedWordsDatabase = new MutedWordsDatabase(getActivity());
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
            View view = inflater.inflate(R.layout.activity_muted_words, container, false);
            wordEditText = view.findViewById(R.id.text_tag);
            setUpTabContainerLayout(view);
            setUpAddwordButton(view);
            return view;
        }

        private void setUpTabContainerLayout(View view) {
            tagContainerLayout = view.findViewById(R.id.tagcontainerLayout);
            tagContainerLayout.setTags(mutedWordsDatabase.getWords());
            tagContainerLayout.setOnTagClickListener(new TagView.OnTagClickListener() {

                @Override
                public void onTagClick(int position, String text) {

                }

                @Override
                public void onTagLongClick(int position, String text) {

                }

                @Override
                public void onTagCrossClick(int position) {
                    mutedWordsDatabase.removeWord(tagContainerLayout.getTags().get(position));
                    tagContainerLayout.removeTag(position);

                }
            });
        }

        private void setUpAddwordButton(View view) {
            Button addWordButton = view.findViewById(R.id.btn_add_tag);
            addWordButton.setOnClickListener(v -> {
                String word = wordEditText.getText().toString();
                tagContainerLayout.addTag(word);
                mutedWordsDatabase.insertWord(word);
                wordEditText.setText("");
            });
        }

    }
}
