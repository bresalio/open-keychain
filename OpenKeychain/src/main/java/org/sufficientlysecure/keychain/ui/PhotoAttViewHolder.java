package org.sufficientlysecure.keychain.ui;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.linked.PhotoAttribute;
import org.sufficientlysecure.keychain.ui.util.KeyFormattingUtils;
import org.sufficientlysecure.keychain.util.Log;

public class PhotoAttViewHolder {
    final public ImageView vPhoto;
    final public TextView vDescription;
    final public ImageView vIsVerified;
    final public ImageView vEditImage;
    final public ImageView vDeleteButton;

    final private boolean mShowStatusImage;
    final private boolean mShowEditImage;
    final private boolean mShowDeleteButton;

    // adapter of added photos;
    // should be null if the view holder is not being used from added photos adapter
    public ArrayAdapter<PhotoAttribute> mAdapter;
    public PhotoAttribute mModel;

    public PhotoAttViewHolder(View view, ArrayAdapter<PhotoAttribute> adapter,
                              boolean showStatusImage, boolean showEditImage, boolean showDeleteButton) {
        vPhoto = (ImageView) view.findViewById(R.id.photo_att_item_image);
        vDescription = (TextView) view.findViewById(R.id.photo_att_item_description);
        vIsVerified = (ImageView) view.findViewById(R.id.photo_att_item_certified);
        vEditImage = (ImageView) view.findViewById(R.id.photo_att_item_edit_image);
        vDeleteButton = (ImageView) view.findViewById(R.id.photo_att_item_delete_button);

        mAdapter = adapter;

        mShowStatusImage = showStatusImage;
        mShowEditImage = showEditImage;
        mShowDeleteButton = showDeleteButton;
    }

    public void setData(PhotoAttribute att, Context context) {
        mModel = att;
        vPhoto.setImageBitmap(att.getBitmap());
        vDescription.setText(att.getDescription());
        if (att.isRevokedOrInvalid()) {
            Log.d("ADDPHOTO", "view holder, disable text: " + att.getDescription());
            vDescription.setEnabled(false);
        }

        if (!mShowStatusImage) {
            vIsVerified.setVisibility(View.GONE);
        } else {
            setVerifiedIcon(context);
        }
        if (!mShowEditImage) {
            vEditImage.setVisibility(View.GONE);
        }
        if (!mShowDeleteButton) {
            vDeleteButton.setVisibility(View.GONE);
        } else if (mAdapter != null) {
            vDeleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mAdapter.remove(mModel);
                }
            });
        }
    }

    private void setVerifiedIcon(Context context) {
        switch (mModel.getStatus()) {
            case PhotoAttribute.STATUS_REVOKED: {
                KeyFormattingUtils.setStatusImage(context, vIsVerified, null,
                        KeyFormattingUtils.State.REVOKED, R.color.key_flag_gray);
                break;
            }
            case PhotoAttribute.STATUS_VERIFIED: {
                KeyFormattingUtils.setStatusImage(context, vIsVerified, null,
                        KeyFormattingUtils.State.VERIFIED, KeyFormattingUtils.DEFAULT_COLOR);
                break;
            }
            case PhotoAttribute.STATUS_UNVERIFIED: {
                KeyFormattingUtils.setStatusImage(context, vIsVerified, null,
                        KeyFormattingUtils.State.UNVERIFIED, KeyFormattingUtils.DEFAULT_COLOR);
                break;
            }
            case PhotoAttribute.STATUS_INVALID: {
                KeyFormattingUtils.setStatusImage(context, vIsVerified, null,
                        KeyFormattingUtils.State.INVALID, KeyFormattingUtils.DEFAULT_COLOR);
                break;
            }
        }
    }
}