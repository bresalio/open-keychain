package org.sufficientlysecure.keychain.ui.adapter;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.linked.PhotoAttribute;
import org.sufficientlysecure.keychain.ui.PhotoAttViewHolder;

import java.util.List;

public class PhotoAttsAddedAdapter extends ArrayAdapter<PhotoAttribute> {
    private LayoutInflater mInflater;

    // "hold a private reference to the underlying data List"
    private List<PhotoAttribute> mData;

    public PhotoAttsAddedAdapter(Activity activity, List<PhotoAttribute> data) {
        super(activity, -1, data);
        mInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mData = data;
    }

    public List<PhotoAttribute> getData() {
        return mData;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        PhotoAttViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.photo_attribute_item, null);
            holder = new PhotoAttViewHolder(convertView, this, false, false, true);
            convertView.setTag(holder);
        } else {
            holder = (PhotoAttViewHolder) convertView.getTag();
        }
        holder.setData(getItem(position), getContext());
        return convertView;
    }
}