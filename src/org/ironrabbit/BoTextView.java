package org.ironrabbit;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;
import android.widget.TextView.BufferType;

public class BoTextView extends TextView {

    Context context;
    private static Typeface font;

    public BoTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;

        
       init();
    }

    private void init() {
    	
    	if (font == null)
    		 font = Typeface.createFromAsset(context.getAssets(),  "monlambodyig.ttf");
    	
        setTypeface(font);
        
    }
    

	@Override
    public void setTypeface(Typeface tf) {

        super.setTypeface(tf);
        
    }

}