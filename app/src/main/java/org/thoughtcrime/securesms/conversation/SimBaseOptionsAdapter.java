package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import org.thoughtcrime.securesms.R;

public class SimBaseOptionsAdapter extends RecyclerView.Adapter<SimBaseOptionsAdapter.ViewHolder>{
    private static final String TAG = SimBaseOptionsAdapter.class.getSimpleName();
    Context mContext;
    private List<String> mList;

    private View.OnFocusChangeListener mOnFocusChangeListener=null;
    private View.OnClickListener mClickListener=null;
    private boolean mTagName=false;
    static class ViewHolder extends RecyclerView.ViewHolder{
        public ViewHolder(View view) {
            super(view);
        }
    }

    public SimBaseOptionsAdapter(Context mContext,List<String> list,View.OnFocusChangeListener mOnFocusChangeListener,
                                 View.OnClickListener clickListener) {
        this.mList = list;
        this.mContext = mContext;
        this.mOnFocusChangeListener = mOnFocusChangeListener;
        this.mClickListener = clickListener;
    }

    public void setTagName(boolean on){
        mTagName = on;
    }

    public void setlist(List<String> list){
        this.mList = list;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        TextView mText = (TextView) LayoutInflater.from(parent.getContext()).inflate(
                R.layout.sim_textview_item, parent, false);

        ViewHolder holder1 = new ViewHolder(mText);
        if(mClickListener!=null){
            mText.setOnClickListener(mClickListener);
        }
        if(mOnFocusChangeListener!=null){
            mText.setOnFocusChangeListener(mOnFocusChangeListener);
        }
        return holder1;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        TextView itemText = (TextView) holder.itemView;
        String Text = mList.get(position);
        itemText.setText(Text);
        if(mTagName){
            itemText.setTag(Text);
        }else{
            itemText.setTag(position);
        }
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }
}