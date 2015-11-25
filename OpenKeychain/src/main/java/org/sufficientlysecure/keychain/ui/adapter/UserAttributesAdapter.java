package org.sufficientlysecure.keychain.ui.adapter;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.CursorAdapter;
import android.view.View;

import org.sufficientlysecure.keychain.provider.KeychainContract.UserPackets;

public abstract class UserAttributesAdapter extends CursorAdapter {
    public static final String[] USER_PACKETS_PROJECTION = new String[]{
            UserPackets._ID,
            UserPackets.TYPE,
            UserPackets.USER_ID,
            UserPackets.ATTRIBUTE_DATA,
            UserPackets.RANK,
            UserPackets.VERIFIED,
            UserPackets.IS_PRIMARY,
            UserPackets.IS_REVOKED
    };
    public static final int INDEX_ID = 0;
    public static final int INDEX_TYPE = 1;
    public static final int INDEX_USER_ID = 2;
    public static final int INDEX_ATTRIBUTE_DATA = 3;
    public static final int INDEX_RANK = 4;
    public static final int INDEX_VERIFIED = 5;
    public static final int INDEX_IS_PRIMARY = 6;
    public static final int INDEX_IS_REVOKED = 7;

    // c: The cursor from which to get the data.
    // flags: Flags used to determine the behavior of the adapter; may be any
    // combination of FLAG_AUTO_REQUERY and FLAG_REGISTER_CONTENT_OBSERVER.
    public UserAttributesAdapter(Context context, Cursor c, int flags) {
        super(context, c, flags);
    }

    // Bind an existing view to the data pointed to by cursor.
    @Override
    public abstract void bindView(View view, Context context, Cursor cursor);

    // getString(int): Returns the value of the requested column as a String.
    // getInt(int): Returns the value of the requested column as an int.
    // Miért csak erre a 3-ra van getter a fentiek közül?

    public String getUserId(int position) {
        mCursor.moveToPosition(position);
        return mCursor.getString(INDEX_USER_ID);
    }

    public boolean getIsRevoked(int position) {
        mCursor.moveToPosition(position);
        return mCursor.getInt(INDEX_IS_REVOKED) > 0;
    }

    public int getIsVerified(int position) {
        mCursor.moveToPosition(position);
        return mCursor.getInt(INDEX_VERIFIED);
    }

}
