/*
 * Copyright (C) 2014-2015 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2015 Vincent Breitmoser <v.breitmoser@mugenguild.com>
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

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.CursorLoader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.linked.LinkedAttribute;
import org.sufficientlysecure.keychain.linked.UriAttribute;
import org.sufficientlysecure.keychain.provider.KeychainContract.Certs;
import org.sufficientlysecure.keychain.provider.KeychainContract.UserPackets;
import org.sufficientlysecure.keychain.ui.linked.LinkedIdViewFragment;
import org.sufficientlysecure.keychain.ui.util.KeyFormattingUtils;
import org.sufficientlysecure.keychain.ui.util.KeyFormattingUtils.State;
import org.sufficientlysecure.keychain.ui.util.SubtleAttentionSeeker;
import org.sufficientlysecure.keychain.util.FilterCursorWrapper;

import java.io.IOException;
import java.util.WeakHashMap;

public class LinkedIdsAdapter extends UserAttributesAdapter {
    private final boolean mIsSecret;
    // Az isSecret értéke egyszerűen konstruktorból állítódik be.

    protected LayoutInflater mInflater;
    // Inflater: technikai célokat szolgál, nincs vele semmi különös.

    WeakHashMap<Integer,UriAttribute> mLinkedIdentityCache = new WeakHashMap<>();
    // WeakHashMap: úgy működik, mint egy cache: a nem elég gyakran használt
    // bejegyzések idővel törlődnek belőle (kitörli őket a GC).
    // Itt az Integer a rank, az UriAttribute pedig a hozzá tartozó linked id.

    private Cursor mUnfilteredCursor;
    // Unfiltered: nincs belőle semmi se kiszűrve.
    // Filtered (lásd swapCursor()): nem látszanak benne a nem LinkedAttribute elemek.

    private TextView mExpander;
    // Ez nem használt, nem kell vele foglalkozni!!

    public LinkedIdsAdapter(Context context, Cursor c, int flags,
            boolean isSecret, TextView expander) {
        super(context, c, flags);
        mInflater = LayoutInflater.from(context);
        mIsSecret = isSecret;

        // Vegyük észre, h ha az expander nem null, a visibility-je akkor is gone:
        // vagyis nem használjuk!!
        // (A szerepe az lett volna, h rákattintva a filtered elemeket is megmutatja.)
        if (expander != null) {
            expander.setVisibility(View.GONE);
            /* don't show an expander (maybe in some sort of advanced view?)
            mExpander = expander;
            mExpander.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showUnfiltered();
                }
            });
            */
        }
    }

    // Swap in a new Cursor, returning the old Cursor.
    // Unlike changeCursor(Cursor), the returned old Cursor is not closed.
    @Override
    public Cursor swapCursor(Cursor cursor) {
        if (cursor == null) {
            mUnfilteredCursor = null;
            return super.swapCursor(null);
        }
        // Az unfiltered cursor mindig a pm.-ként kapott cursor értékét kapja meg,
        // és a return statement is mindig a super.swapCursor(cursor).

        // A filtered cursor annyiban más vagy több, h az isVisible() metódusa
        // (amit a FilterCursorWrappertől örököl) lekéri az adott pozícióban levő
        // uri attribute-ot, és akkor tér vissza igazzal, ha az LinkedAttribute típusú.

        mUnfilteredCursor = cursor;
        FilterCursorWrapper filteredCursor = new FilterCursorWrapper(cursor) {
            @Override
            public boolean isVisible(Cursor cursor) {
                UriAttribute id = getItemAtPosition(cursor);
                return id instanceof LinkedAttribute;
            }
        };

        if (mExpander != null) {
            int hidden = filteredCursor.getHiddenCount();
            if (hidden == 0) {
                mExpander.setVisibility(View.GONE);
            } else {
                mExpander.setVisibility(View.VISIBLE);
                mExpander.setText(mContext.getResources().getQuantityString(
                        R.plurals.linked_id_expand, hidden));
            }
        }

        return super.swapCursor(filteredCursor);
    }

    private void showUnfiltered() {
        mExpander.setVisibility(View.GONE);
        super.swapCursor(mUnfilteredCursor);
    }

    // Bind an existing view to the data pointed to by cursor.
    // view 	Existing view, returned earlier by newView.
    // cursor 	The cursor from which to get the data, already moved to the correct position.
    @Override
    public void bindView(View view, Context context, Cursor cursor) {

        ViewHolder holder = (ViewHolder) view.getTag();

        // Ez a képekhez valszeg nem fog kelleni!
        if (!mIsSecret) {
            int isVerified = cursor.getInt(INDEX_VERIFIED);
            switch (isVerified) {
                case Certs.VERIFIED_SECRET:
                    KeyFormattingUtils.setStatusImage(mContext, holder.vVerified,
                            null, State.VERIFIED, KeyFormattingUtils.DEFAULT_COLOR);
                    break;
                case Certs.VERIFIED_SELF:
                    KeyFormattingUtils.setStatusImage(mContext, holder.vVerified,
                            null, State.UNVERIFIED, KeyFormattingUtils.DEFAULT_COLOR);
                    break;
                default:
                    KeyFormattingUtils.setStatusImage(mContext, holder.vVerified,
                            null, State.INVALID, KeyFormattingUtils.DEFAULT_COLOR);
                    break;
            }
        }

        // Ez fontos!
        UriAttribute id = getItemAtPosition(cursor);
        holder.setData(mContext, id);

        // Ez mi???
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setTransitionName(id.mUri.toString());
        }

    }

    public UriAttribute getItemAtPosition(Cursor cursor) {
        int rank = cursor.getInt(INDEX_RANK);
        Log.d(Constants.TAG, "requested rank: " + rank);
        // Ezt a részt majd másoljuk ki: a kiválasztás a rank alapján történik!
        // Ha a linked identity már cache-elve van, csak visszaadja a cache-ből a rank alapján.
        // Egyébként (lásd lentebb) most cache-eli.
        // Egyébként a try-blokkban történik a kiolvasás.

        UriAttribute ret = mLinkedIdentityCache.get(rank);
        if (ret != null) {
            Log.d(Constants.TAG, "cached!");
            return ret;
        }
        Log.d(Constants.TAG, "not cached!");

        try {
            byte[] data = cursor.getBlob(INDEX_ATTRIBUTE_DATA);
            ret = LinkedAttribute.fromAttributeData(data);
            mLinkedIdentityCache.put(rank, ret);
            return ret;
        } catch (IOException e) {
            Log.e(Constants.TAG, "could not read linked identity subpacket data", e);
            return null;
        }
    }

    @Override
    public UriAttribute getItem(int position) {
        Cursor cursor = getCursor();
        cursor.moveToPosition(position);
        return getItemAtPosition(cursor);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View v = mInflater.inflate(R.layout.linked_id_item, null);
        ViewHolder holder = new ViewHolder(v);
        v.setTag(holder);
        return v;
    }

    // don't show revoked user ids, irrelevant for average users
    public static final String LINKED_IDS_WHERE = UserPackets.IS_REVOKED + " = 0";

    public static CursorLoader createLoader(Activity activity, Uri dataUri) {
        Uri baseUri = UserPackets.buildLinkedIdsUri(dataUri);
        return new CursorLoader(activity, baseUri,
                UserIdsAdapter.USER_PACKETS_PROJECTION, LINKED_IDS_WHERE, null, null);
    }

    public LinkedIdViewFragment getLinkedIdFragment(Uri baseUri,
            int position, byte[] fingerprint) throws IOException {
        Cursor c = getCursor();
        c.moveToPosition(position);
        int rank = c.getInt(UserIdsAdapter.INDEX_RANK);

        Uri dataUri = UserPackets.buildLinkedIdsUri(baseUri);
        return LinkedIdViewFragment.newInstance(dataUri, rank, mIsSecret, fingerprint);
    }

    public static class ViewHolder {
        final public ImageView vVerified;
        final public ImageView vIcon;
        final public TextView vTitle;
        final public TextView vComment;

        public ViewHolder(View view) {
            vVerified = (ImageView) view.findViewById(R.id.linked_id_certified_icon);
            vIcon = (ImageView) view.findViewById(R.id.linked_id_type_icon);
            vTitle = (TextView) view.findViewById(R.id.linked_id_title);
            vComment = (TextView) view.findViewById(R.id.linked_id_comment);
        }

        public void setData(Context context, UriAttribute id) {

            vTitle.setText(id.getDisplayTitle(context));

            String comment = id.getDisplayComment(context);
            if (comment != null) {
                vComment.setVisibility(View.VISIBLE);
                vComment.setText(comment);
            } else {
                vComment.setVisibility(View.GONE);
            }

            vIcon.setImageResource(id.getDisplayIcon());

        }

        // Ez a fényképeshez nem kell majd, mert nem kell színezni a szöveget!
        public void seekAttention() {
            ObjectAnimator anim = SubtleAttentionSeeker.tintText(vComment, 1000);
            anim.setStartDelay(200);
            anim.start();
        }

    }

}
