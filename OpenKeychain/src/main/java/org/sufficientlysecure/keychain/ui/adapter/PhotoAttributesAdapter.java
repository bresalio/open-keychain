/*
 * Copyright (C) 2015 Bresalio Nagy <bresalio@yahoo.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.ui.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.content.CursorLoader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.linked.PhotoAttribute;
import org.sufficientlysecure.keychain.provider.KeychainContract;
import org.sufficientlysecure.keychain.ui.PhotoAttViewHolder;
import org.sufficientlysecure.keychain.util.ExtendCursorWrapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Cursor adapter for backing the list of photos attached to a key.
 */
public class PhotoAttributesAdapter extends UserAttributesAdapter {

    protected LayoutInflater mInflater;
    protected Context mContext;
    // Here either the status image is shown or the edit image.
    // So if this is false, the edit image is shown.
    protected boolean mShowStatusImage;
    protected boolean mShowRevokedItems;

    WeakHashMap<Integer, PhotoAttribute[]> mPhotoAttributeCache = new WeakHashMap<>();
    // Key: rank; value: the array of photos created from the attribute data of that cursor row

    /**
     * @param showStatusImages Whether to show the verification status icon in the list items
     * @param showRevokedItems Whether not to filter out revoked photos
     */
    public PhotoAttributesAdapter(Context context, Cursor c, int flags, boolean showStatusImages, boolean showRevokedItems) {
        super(context, c, flags);
        mInflater = LayoutInflater.from(context);
        mContext = context;
        mShowStatusImage = showStatusImages;
        mShowRevokedItems = showRevokedItems;
    }

    /**
     * Creates a cursor loader, based on the appropriate URI builder of the contract class.
     * @param dataUri The data uri of the key the photos of which we are interested in.
     */
    public static CursorLoader createLoader(Activity activity, Uri dataUri) {
        Log.d(Constants.TAG, "Photo Attributes Adapter cursor loader - dataUri: " + dataUri.toString());
        Uri baseUri = KeychainContract.UserPackets.buildPhotoAttributesUri(dataUri);
        Log.d(Constants.TAG, "baseUri: " + baseUri.toString());
        return new CursorLoader(activity, baseUri,
                PhotoAttributesAdapter.USER_PACKETS_PROJECTION, null, null, null);
    }

    /** "Swap in a new Cursor, returning the old Cursor.
     * Unlike changeCursor(Cursor), the returned old Cursor is not closed." (from the official documentation)
     * Here, the original cursor is swapped to the extended cursor.
     * After calling this method, the cursor of this adapter will be the extended cursor
     * produced from the one received as parameter.
     */
    @Override
    public Cursor swapCursor(Cursor cursor) {
        ExtendCursorWrapper extendedCursor;
        if (cursor == null) {
            extendedCursor = null;
        } else {
            extendedCursor = new ExtendCursorWrapper(cursor) {
                @Override
                public int relatedRowCount(Cursor cursor) {
                    PhotoAttribute[] photoAttributes = getItemArrayAtUnextendedPosition(cursor);
                    return photoAttributes.length;
                }
            };
        }
        return super.swapCursor(extendedCursor);
    }

    /**
     * Warning: this method uses the unextended position of the cursor!
     * @param cursor A cursor, already moved to the correct position.
     * @return The array of photo attributes produced from the attribute data
     * at that cursor position.
     */
    public PhotoAttribute[] getItemArrayAtUnextendedPosition(Cursor cursor) {
        int rank = cursor.getInt(INDEX_RANK);

        PhotoAttribute[] attributes = mPhotoAttributeCache.get(rank);
        if (attributes != null) {
            Log.d(Constants.TAG, "The requested photo attribute array is cached!");
            return attributes;
        }
        Log.d(Constants.TAG, "The requested photo attribute array is not cached!");

        try {
            byte[] data = cursor.getBlob(INDEX_ATTRIBUTE_DATA);
            int status = getStatus(cursor);
            attributes = PhotoAttribute.fromAttributeData(data, status, mContext);
            if(!mShowRevokedItems) {
                attributes = filterArray(attributes);
            }
            mPhotoAttributeCache.put(rank, attributes);
            return attributes;
        } catch (IOException e) {
            Log.e(Constants.TAG, "Could not read photo attribute subpacket data", e);
            return null;
        }
    }

    /**
     * Removes revoked and invalid photo attributes from a photo attribute array.
     */
    private PhotoAttribute[] filterArray(PhotoAttribute[] original) {
        List<PhotoAttribute> filtered = new ArrayList<>();
        for(PhotoAttribute att : original) {
            if(!att.isRevokedOrInvalid()) {
                filtered.add(att);
            }
        }
        return filtered.toArray(new PhotoAttribute[filtered.size()]);
    }

    /**
     * Get the verification status of the attribute at the current cursor position.
     * The method assumes that the cursor is moved to the correct position.
     */
    private int getStatus(Cursor cursor) {
        if (cursor.getInt(INDEX_IS_REVOKED) > 0) {
            return PhotoAttribute.STATUS_REVOKED;
        } else {
            int isVerified = cursor.getInt(INDEX_VERIFIED);
            switch (isVerified) {
                case KeychainContract.Certs.VERIFIED_SECRET:
                    return PhotoAttribute.STATUS_VERIFIED;
                case KeychainContract.Certs.VERIFIED_SELF:
                    return PhotoAttribute.STATUS_UNVERIFIED;
                default:
                    return PhotoAttribute.STATUS_INVALID;
            }
        }
    }

    /**
     * Inflate a new, empty view based on the layout descriptor,
     * and associate it with an empty view holder.
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = mInflater.inflate(R.layout.photo_attribute_item, null);
        PhotoAttViewHolder holder = new PhotoAttViewHolder(view, null, mShowStatusImage, !mShowStatusImage, false);
        view.setTag(holder);
        return view;
    }

    /**
     * Get the list item at an extended position.
     * @param position has to mean the position in the EXTENDED cursor!
     */
    @Override
    public PhotoAttribute getItem(int position) {
        ExtendCursorWrapper cursor = (ExtendCursorWrapper) getCursor();
        cursor.moveToPosition(position);
        PhotoAttribute[] attributes = getItemArrayAtUnextendedPosition(cursor);
        int offset = cursor.getExtendedPosition(position)[1];
        return attributes[offset];
    }

    /** "Bind an existing view to the data pointed to by cursor.
     * view 	Existing view, returned earlier by newView.
     * cursor 	The cursor from which to get the data, already moved to the correct position." (from doc)
     * This method is the bridge between newView() and getItem().
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        PhotoAttViewHolder holder = (PhotoAttViewHolder) view.getTag();
        PhotoAttribute photo = getItem(cursor.getPosition());
        holder.setData(photo, context);
    }

    public void startViewIntent(final int position) {
        Log.i(Constants.TAG, "PhotoAttributesAdapter.getViewIntent() called for position " + position);
        Intent viewIntent = getItem(position).getViewIntent();
        mContext.startActivity(viewIntent);
    }

    public boolean isPhotoListEmpty() {
        return getCursor().getCount() == 0;
    }
}