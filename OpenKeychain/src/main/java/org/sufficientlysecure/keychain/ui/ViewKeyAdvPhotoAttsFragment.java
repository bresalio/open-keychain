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

package org.sufficientlysecure.keychain.ui;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.ui.adapter.PhotoAttributesAdapter;
import org.sufficientlysecure.keychain.util.Log;

/**
 * This fragment is designed to be a tab in the view pager of extended (advanced) key view.
 * Has a similar role as ViewKeyAdvShareFragment, ViewKeyAdvUserIdsFragment,
 * ViewKeyAdvSubkeysFragment and ViewKeyAdvCertsFragment.
 */
public class ViewKeyAdvPhotoAttsFragment extends LoaderFragment implements
        LoaderManager.LoaderCallbacks<Cursor> {

    public static final String ARG_DATA_URI = "data_uri";

    private ListView mPhotoAttributes;
    private PhotoAttributesAdapter mPhotoAttributesAdapter;
    private TextView mNoPhotos;
    // The text to display when there are no photos to show.

    private Uri mDataUri;

    public static ViewKeyAdvPhotoAttsFragment newInstance(Uri dataUri) {
        ViewKeyAdvPhotoAttsFragment frag = new ViewKeyAdvPhotoAttsFragment();

        Bundle args = new Bundle();
        args.putParcelable(ARG_DATA_URI, dataUri);

        frag.setArguments(args);
        return frag;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup superContainer, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, superContainer, savedInstanceState);
        View view = inflater.inflate(R.layout.view_key_adv_photo_atts_fragment, getContainer());

        mPhotoAttributes = (ListView) view.findViewById(R.id.photo_atts);
        mNoPhotos = (TextView) view.findViewById(R.id.no_photos);

        mPhotoAttributes.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showPhotoAttribute(position);
            }
        });

        return root;
    }

    private void showPhotoAttribute(final int position) {
        mPhotoAttributesAdapter.startViewIntent(position);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Uri dataUri = getArguments().getParcelable(ARG_DATA_URI);
        if (dataUri == null) {
            Log.e(Constants.TAG, "Data missing. Should be Uri of key!");
            getActivity().finish();
            return;
        }

        loadData(dataUri);
    }

    private void loadData(Uri dataUri) {
        mDataUri = dataUri;
        Log.i(Constants.TAG, "mDataUri: " + mDataUri.toString());

        mPhotoAttributesAdapter = new PhotoAttributesAdapter(getActivity(), null, 0, true, true);
        mPhotoAttributes.setAdapter(mPhotoAttributesAdapter);

        // Prepare the loaders. Either re-connect with an existing ones,
        // or start new ones.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        setContentShown(false);
        return PhotoAttributesAdapter.createLoader(getActivity(), mDataUri);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mPhotoAttributesAdapter.swapCursor(data);

        if(mPhotoAttributesAdapter.isPhotoListEmpty()){
            mPhotoAttributes.setVisibility(View.GONE);
            mNoPhotos.setVisibility(View.VISIBLE);
        } else {
            mPhotoAttributes.setVisibility(View.VISIBLE);
            mNoPhotos.setVisibility(View.GONE);
        }

        setContentShown(true);
    }

    /**
     * "This is called when the last Cursor provided to onLoadFinished() above is about to be closed.
     * We need to make sure we are no longer using it."
     */
    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mPhotoAttributesAdapter.swapCursor(null);
    }
}