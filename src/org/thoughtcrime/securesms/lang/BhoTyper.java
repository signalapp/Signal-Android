package org.thoughtcrime.securesms.lang;

import java.util.ArrayList;

import org.ironrabbit.TibConvert;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

public class BhoTyper {
	
	private String bhoTag = "******************* LANG SERVICE **************";
	
	
	Typeface bho;
	ArrayList<TextView> textViews = new ArrayList<TextView>();
	Context c;
	
	public BhoTyper(Context c, View root) {
		this.c = c;
		bho = Typeface.createFromAsset(this.c.getAssets(), Constants.FONT);
	    
	    parseForTextViews(root);

	    for(View v : textViews) {
	    	if(!(v instanceof android.widget.EditText)) {
	    		
	    		String oldText = ((TextView) v).getText().toString();
	    		String bhoText = TibConvert.convertUnicodeToPrecomposedTibetan(oldText);
	    		
	    		((TextView) v).setTypeface(bho, TextUtils.CAP_MODE_CHARACTERS);
	    		((TextView) v).setText(bhoText);
	    	} else {
	    		String oldHint = ((EditText) v).getHint().toString();
	    		String bhoHint = TibConvert.convertUnicodeToPrecomposedTibetan(oldHint);
	    		
	    		((EditText) v).setTypeface(bho, TextUtils.CAP_MODE_CHARACTERS);
	    		((EditText) v).setHint(bhoHint);
	    	}
	    }
	}
	
	private void parseForTextViews(View view) {
		try {
			if(view instanceof android.widget.TextView)
				textViews.add((TextView) view);
			else {
				ViewGroup vg = (ViewGroup) view;
				for(int v=0; v< vg.getChildCount(); v++)
					parseForTextViews((View) vg.getChildAt(v));
			}
		} catch(ClassCastException e) {}
	}
	
	public static class Constants {
		public static String FONT = "monlambodyig.ttf"; 
	}
}
