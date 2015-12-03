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
import android.provider.MediaStore;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.pgp.WrappedUserAttribute;
import org.sufficientlysecure.keychain.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.List;

/**
 * The instances of this class represent photo attributes.
 */
public class PhotoAttribute implements Serializable {

    private transient Bitmap mBitmap;
    private byte[] mByteData;
    // The ENCODED byte data of the bitmap
    private int mStatus;
    // verification status, see below
    private transient final Context mContext;

    // possible verification statuses
    public static final int STATUS_REVOKED = 0;
    public static final int STATUS_VERIFIED = 1;
    public static final int STATUS_UNVERIFIED = 2;
    public static final int STATUS_INVALID = 3;

    private static final Uri EXTERNAL_CONTENT_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

    // With how many percent to darken the thumbnails of revoked photo atts
    private static final int DARKEN_PERCENT = 70;
    private static final int MAX_PIXELS = 256 * 256;
    private static final int MIN_PIXELS = 64 * 64;
    private static final int MAX_BYTES = 20 * 1024;
    private static final int MIN_QUALITY = 80; // (0 < x < lim_quality)
    private static final int INTERMEDIATE_LIM_QUALITY = 90; // (min_quality < x < 100)
    private static final int PIXEL_DECREASE_PERCENT = 1; // (1 < x < 99)

    protected PhotoAttribute(byte[] byteData, int status, Context context) {
        mBitmap = BitmapFactory.decodeByteArray(byteData, 0, byteData.length);
        mByteData = byteData;
        mStatus = status;
        mContext = context;
    }

    // for creating an invalid attribute with no valid byte data
    protected PhotoAttribute(Context context) {
        mStatus = STATUS_INVALID;
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
     * @param data   The attribute data (BLOB)
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
                byte[] imageData = imageAttributeData.get(i);
                if (imageData != null) {
                    results[i] = new PhotoAttribute(imageData, status, context);
                } else {
                    results[i] = new PhotoAttribute(context);
                }
            }
            return results;
        }

        throw new IOException("No photo attributes found in the attribute data!");
    }

    // TODO: írjuk bele a dolgozatba, h ennek érdekében módosítva lett a fromSubpacket() !!!
    public WrappedUserAttribute toWrappedUserAttribute() {
        return WrappedUserAttribute.fromSubpacket(WrappedUserAttribute.UAT_IMAGE, mByteData);
    }

    public boolean ofSuitableSize() {
        return mBitmap.getWidth() * mBitmap.getHeight() <= MAX_PIXELS &&
                mByteData.length <= MAX_BYTES;
    }

    public String scalingMessage() {
        return mContext.getResources().getString(R.string.add_photo_actual_size) +
                actualSize() + ".\n" +
                mContext.getResources().getString(R.string.add_photo_max_size) +
                maxSize() + ".\n" +
                mContext.getResources().getString(R.string.add_photo_must_be_scaled) +
                "\n" +
                mContext.getResources().getString(R.string.add_photo_scale_now);
    }

    public void scale() {
        int intDestWidth = mBitmap.getWidth();
        int intDestHeight = mBitmap.getHeight();
        double doubleDestWidth = intDestWidth;
        double doubleDestHeight = intDestHeight;

        Log.d("SCALE", "Scale: original width = " + doubleDestWidth);
        Log.d("SCALE", "Scale: original height = " + doubleDestHeight);
        Log.d("SCALE", "Scale: original bytes = " + mByteData.length);

        // If the width-height product is greater than the maximum allowed,
        // we calculate a scaled width and height.
        if (intDestWidth * intDestHeight > MAX_PIXELS) {
            double rate = Math.sqrt(MAX_PIXELS / (double) (intDestWidth * intDestHeight));
            Log.d("SCALE", "pixel correction; rate = " + rate);
            doubleDestWidth *= rate;
            doubleDestHeight *= rate;
            intDestWidth = (int) doubleDestWidth;
            intDestHeight = (int) doubleDestHeight;
            Log.d("SCALE", "scaled width = " + doubleDestWidth + ", " + intDestWidth);
            Log.d("SCALE", "scaled height = " + doubleDestHeight + ", " + intDestHeight);
        }

        // If the condition of the previous if statement was false (if the original width-height
        // are correct), this method below does no actual scaling, only returns the original bitmap.
        // Else, the bitmap gets scaled.
        mBitmap = Bitmap.createScaledBitmap(mBitmap, intDestWidth, intDestHeight, true);

        // We compress the bitmap with an incrementally decreasing quality, until its byte length
        // gets less than the maximum allowed byte length, but not more than with a limes quality.
        int quality = 100;
        do {
            compress(quality);
            if (mByteData.length <= MAX_BYTES) {
                Log.d("SCALE", "returned quality = " + quality);
                return;
            }
            quality--;
        } while (quality >= INTERMEDIATE_LIM_QUALITY);

        // If the byte length is still too large, we start to decrease the pixel size and
        // the compression quality parallel, until the byte length gets less than the maximum allowed;
        // but we don't let neither pixel size nor quality sink under a certain minimum.
        quality = INTERMEDIATE_LIM_QUALITY;
        double rate = PIXEL_DECREASE_PERCENT / 100.0;
        double deltaWidth = doubleDestWidth * rate;
        double deltaHeight = doubleDestHeight * rate;
        do {
            doubleDestWidth -= deltaWidth;
            doubleDestHeight -= deltaHeight;
            intDestWidth = (int) doubleDestWidth;
            intDestHeight = (int) doubleDestHeight;
            if (intDestWidth * intDestHeight < MIN_PIXELS) {
                if (doubleDestWidth * doubleDestHeight < MIN_PIXELS) {
                    break;
                } else {
                    intDestWidth = (int) Math.ceil(doubleDestWidth);
                    intDestHeight = (int) Math.ceil(doubleDestHeight);
                }
            }

            mBitmap = Bitmap.createScaledBitmap(mBitmap, intDestWidth, intDestHeight, true);
            compress(quality);
            if (mByteData.length <= MAX_BYTES) {
                break;
            }

            quality--;
            compress(quality);
            if (mByteData.length <= MAX_BYTES) {
                break;
            }
        } while (quality > MIN_QUALITY);

        Log.d("SCALE", "Final quality = " + quality);
        Log.d("SCALE", "Final width = " + mBitmap.getWidth());
        Log.d("SCALE", "Final height = " + mBitmap.getHeight());
        Log.d("SCALE", "Final bytes = " + mByteData.length);
    }

    private void compress(int quality) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream);
        mByteData = byteArrayOutputStream.toByteArray();
    }

    /**
     * @return a darkened bitmap if this attribute is revoked
     */
    public Bitmap getBitmap() {
        if (mBitmap != null && mStatus == STATUS_REVOKED) {
            return darkenBitmap(mBitmap, DARKEN_PERCENT);
        } else {
            return mBitmap;
        }
    }

    /**
     * Darkens a bitmap pixel to pixel, with the given percent.
     */
    private Bitmap darkenBitmap(Bitmap original, int percent) {
        int width = original.getWidth();
        int height = original.getHeight();

        Bitmap result = Bitmap.createBitmap(width, height, original.getConfig());

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
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
        if (mBitmap == null || mStatus == STATUS_INVALID) {
            return mContext.getResources().getString(R.string.photo_status_invalid).toUpperCase();
        } else {
            return mContext.getResources().getString(R.string.photo_att_description) + size();
        }
    }

    private String actualSize() {
        return size(mByteData.length, mBitmap.getWidth() * mBitmap.getHeight());
    }

    private String maxSize() {
        return size(MAX_BYTES, MAX_PIXELS);
    }

    private String size(int bytes, int pixels) {
        return " " + bytes + ", " + pixels;
    }

    private String size() {
        return " " + mByteData.length + ", " + mBitmap.getWidth() + "x" + mBitmap.getHeight() + " pixels";
    }

    public int getStatus() {
        return mStatus;
    }

    public boolean isRevokedOrInvalid() {
        return mStatus == STATUS_REVOKED || mStatus == STATUS_INVALID;
    }

    public boolean isRevoked() {
        return mStatus == STATUS_REVOKED;
    }

    /**
     * @return an intent that views the photo when started.
     */
    public Intent getViewIntent() {
        if (mBitmap == null || mStatus == STATUS_INVALID) {
            return null;
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = getBitmapUri();
            intent.setData(uri);
            return intent;
        }
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