package org.thoughtcrime.securesms.lang;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.EditText;

public class BhoEditText extends EditText {
	Context c;
	private static Typeface t;
	
	public BhoEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.c = context;
		
		if(t == null)
			t = Typeface.createFromAsset(this.c.getAssets(), BhoTyper.FONT);
		
		setTypeface(t);
		
	}
	
	@Override
	public void setTypeface(Typeface typeface) {
		super.setTypeface(typeface);
	}

}
