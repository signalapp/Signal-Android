package org.thoughtcrime.securesms.conversation;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;

import java.util.ArrayList;
import java.util.List;

public class ConversationSubMemuActivity extends Activity implements ConversationSubMenuAdapter.ItemClickListener {
    private ConversationSubMenuAdapter menuAdapter;
    private RecyclerView subMenuRcy;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation_sub_menu_view);
        subMenuRcy = findViewById(R.id.conversation_sub_menu_recy);
        List<String> data = new ArrayList<>();
        data.add(getResources().getString(R.string.sub_menu_reply_message));
        data.add(getResources().getString(R.string.conversation_context__menu_forward_message));
        menuAdapter = new ConversationSubMenuAdapter(this,this,data);
        RecyclerView.LayoutManager layout = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL,false);
        subMenuRcy.setLayoutManager(layout);
        subMenuRcy.setAdapter(menuAdapter);
    }

    @Override
    public void onItemClick(TextView tv) {
        if(tv.getText().equals(getResources().getString(R.string.sub_menu_reply_message))){
            setResult(100);
            finish();
        }else if(tv.getText().equals(getResources().getString(R.string.conversation_context__menu_forward_message))){
            setResult(200);
            finish();
        }
    }
}
