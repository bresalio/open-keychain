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

package org.sufficientlysecure.keychain.linked;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.MediaStore;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.pgp.WrappedUserAttribute;
import org.sufficientlysecure.keychain.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * The instances of this class represent photo attributes.
 */
public class PhotoAttribute {

    private final Bitmap mBitmap;
    private final byte[] mByteData;
    // The ENCODED byte data of the bitmap
    private int mStatus;
    // verification status, see below
    private final Context mContext;

    // possible verification statuses
    public static final int STATUS_REVOKED = 0;
    public static final int STATUS_VERIFIED = 1;
    public static final int STATUS_UNVERIFIED = 2;
    public static final int STATUS_INVALID = 3;

    // With how many percent to darken the thumbnails of revoked (or invalid) photo atts
    private static final int DARKEN_PERCENT = 70;

    private static final Uri EXTERNAL_CONTENT_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

    protected PhotoAttribute(byte[] byteData, int status, Context context) {
        mBitmap = BitmapFactory.decodeByteArray(byteData, 0, byteData.length);
        mByteData = byteData;
        mStatus = status;
        mContext = context;
    }

    public PhotoAttribute(InputStream inputStream, int status, Context context) throws IOException {
        this(inputStreamToByteArray(inputStream), status, context);
    }

    // http://www.gregbugaj.com/?p=283
    private static byte[] inputStreamToByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer, 0, buffer.length)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();
        return outputStream.toByteArray();
    }

    /**
     * @param data The attribute data (BLOB)
     * @param status The verification status
     * @return The array of photo attributes produced from the attribute data.
     * @throws IOException if no attributes were found in the attribute data.
     */
    public static PhotoAttribute[] fromAttributeData(byte[] data, int status, Context context) throws IOException {
        WrappedUserAttribute att = WrappedUserAttribute.fromData(data);

        List<byte[]> imageAttributeData = att.getImageAttributeData();
        if (imageAttributeData != null && imageAttributeData.size() > 0) {
            PhotoAttribute[] results = new PhotoAttribute[imageAttributeData.size()];
            for (int i = 0; i < imageAttributeData.size(); i++) {
                results[i] = new PhotoAttribute(imageAttributeData.get(i), status, context);
            }
            return results;
        }

        throw new IOException("No photo attributes found in the attribute data!");
    }

    public WrappedUserAttribute toWrappedUserAttribute() {
        return WrappedUserAttribute.fromSubpacket(WrappedUserAttribute.UAT_IMAGE, mByteData);
    }

    /**
     * @return a darkened bitmap if this attribute is revoked or invalid
     */
    public Bitmap getBitmap() {
        if(isRevokedOrInvalid()) {
            return darkenBitmap(mBitmap, DARKEN_PERCENT);
        }
        return mBitmap;
    }

    /**
     * Darkens a bitmap pixel to pixel, with the given percent.
     */
    private Bitmap darkenBitmap(Bitmap original, int percent) {
        int width = original.getWidth();
        int height = original.getHeight();

        Bitmap result = Bitmap.createBitmap(width, height, original.getConfig());

        for(int x = 0; x < width; x++) {
            for(int y = 0; y < height; y++) {
                int originalColor = original.getPixel(x, y);
                int darkenedColor = darkenColor(originalColor, percent);
                result.setPixel(x, y, darkenedColor);
            }
        }

        return result;
    }

    /**
     * Converts the color to HSV (hue, saturation, value), so that hue is not modified.
     */
    private int darkenColor(int originalColor, int percent) {
        float[] hsv = new float[3];
        Color.colorToHSV(originalColor, hsv);
        float s = hsv[1];
        float v = hsv[2];
        s = s * (100 + percent) / 100;
        s = (s < 1) ? s : 1;
        v = v * (100 - percent) / 100;
        v = (v > 0) ? v : 0;
        hsv[1] = s;
        hsv[2] = v;
        return Color.HSVToColor(hsv);
    }

    /**
     * Concatenates subpacket size and pixel size.
     */
    public String getDescription() {
        String widthAndHeight = mBitmap.getWidth() + "x" + mBitmap.getHeight() + " pixels";
        return "Image of subpacket size " + mByteData.length + ", " + widthAndHeight;
        // TODO: strings shouldn't be hardcoded, move it to strings.xml!
    }

    public int getStatus() {
        return mStatus;
    }

    public boolean isRevokedOrInvalid() {
        return mStatus == STATUS_REVOKED || mStatus == STATUS_INVALID;
    }

    /**
     * @return an intent that views the photo when started.
     */
    public Intent getViewIntent() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        // String path = MediaStore.Images.Media.insertImage(mContext.getContentResolver(),
        //         mBitmap, DESCRIPTION_TAG, getDescription());
        Uri uri = getBitmapUri();
        intent.setData(uri);
        return intent;
    }

    // based on MediaStore.Images.Media.InsertImage(ContentResolver, Bitmap, String title, String descr)
    // The above method first creates a thumbnail of the photo,
    // and returns the URI of that thumbnail only.
    // Thus, artificially spoils the quality of the viewed image.
    // This method eliminates those elements.
    private Uri getBitmapUri() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "Photo attribute");
        values.put(MediaStore.Images.Media.DESCRIPTION, getDescription());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        Uri uri = null;
        ContentResolver cr = mContext.getContentResolver();

        try {
            uri = cr.insert(EXTERNAL_CONTENT_URI, values);

            if (mBitmap != null) {
                OutputStream imageOut = cr.openOutputStream(uri);
                try {
                    mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, imageOut);
                } finally {
                    imageOut.close();
                }

                // In the original method, here at first a thumbnail is generated, and
                // later only the thumbnail is shared.
                // This is not enough for us, we want to share the original bitmap.
            } else {
                cr.delete(uri, null, null);
                uri = null;
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "Failed to insert image", e);
            if (uri != null) {
                cr.delete(uri, null, null);
                uri = null;
            }
        }

        return uri;
    }
}