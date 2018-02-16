package org.thoughtcrime.securesms;


import android.os.Bundle;
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
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
            View view = inflater.inflate(R.layout.activity_muted_words, container, false);
            MutedWordsDatabase mutedWordsDatabase = new MutedWordsDatabase(getActivity());
            final EditText text = view.findViewById(R.id.text_tag);
            Button btnAddTag = view.findViewById(R.id.btn_add_tag);
            TagContainerLayout tagContainerLayout = view.findViewById(R.id.tagcontainerLayout);
            tagContainerLayout.setTags(mutedWordsDatabase.getWords());
            btnAddTag.setOnClickListener(v -> {
                String word = text.getText().toString();
                tagContainerLayout.addTag(word);
                mutedWordsDatabase.insertWord(word);
                text.setText("");
            });
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
            return view;
        }

    }
}
