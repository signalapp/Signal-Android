package org.thoughtcrime.securesms.lang;

import org.thoughtcrime.securesms.contacts.RecipientsEditor;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;

public class BhoRecipientsEditor extends RecipientsEditor {
	Context c;
	private static Typeface t;
	
	public BhoRecipientsEditor(Context context, AttributeSet attrs) {
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
