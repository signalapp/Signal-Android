package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ViewProfilePhotoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String fileUri = intent.getExtras().getString(MediaStore.EXTRA_OUTPUT);
        if(fileUri != null){
            Toast.makeText(getApplicationContext(), fileUri,Toast.LENGTH_LONG).show();
        }else{
            Toast.makeText(getApplicationContext(),"fileUri is null",Toast.LENGTH_LONG).show();
        }
    }
}
