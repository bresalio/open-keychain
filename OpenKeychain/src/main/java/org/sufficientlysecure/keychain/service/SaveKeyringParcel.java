/*
 * Copyright (C) 2014 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2014 Vincent Breitmoser <v.breitmoser@mugenguild.com>
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

package org.sufficientlysecure.keychain.service;

import android.os.Parcel;
import android.os.Parcelable;

import org.sufficientlysecure.keychain.linked.PhotoAttribute;
import org.sufficientlysecure.keychain.pgp.WrappedUserAttribute;
import org.sufficientlysecure.keychain.util.Passphrase;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * This class is a a transferable representation for a collection of changes
 * to be done on a keyring.
 * <p/>
 * This class should include all types of operations supported in the backend.
 * <p/>
 * All changes are done in a differential manner. Besides the two key
 * identification attributes, all attributes may be null, which indicates no
 * change to the keyring. This is also the reason why boxed values are used
 * instead of primitives in the subclasses.
 * <p/>
 * Application of operations in the backend should be fail-fast, which means an
 * error in any included operation (for example revocation of a non-existent
 * subkey) will cause the operation as a whole to fail.
 */
public class SaveKeyringParcel implements Parcelable {

    // the master key id to be edited. if this is null, a new one will be created
    public Long mMasterKeyId;
    // the key fingerprint, for safety. MUST be null for a new key.
    public byte[] mFingerprint;

    public ChangeUnlockParcel mNewUnlock;

    public ArrayList<String> mAddUserIds;
    public ArrayList<WrappedUserAttribute> mAddUserAttributes;
    public ArrayList<SubkeyAdd> mAddSubKeys;

    // The added photos are stored as photo attributes here.
    // Storing them as PhotoAttribute objects is inevitable
    // for display reasons: the view holder of the add-list
    // is designed to receive and display photo attribute objects.
    // But when the modify/save method processes this save parcel
    // in PgpKeyOperation (see modifySecretKeyRing() and createSecretKeyRing()),
    // they have to be converted to WrappedUserAttributes and added to that list.
    public ArrayList<PhotoAttribute> mAddPhotos;

    public ArrayList<SubkeyChange> mChangeSubKeys;
    public String mChangePrimaryUserId;

    public ArrayList<String> mRevokeUserIds;
    public ArrayList<WrappedUserAttribute> mRevokeUserAttributes;
    public ArrayList<Long> mRevokeSubKeys;

    public ArrayList<PhotoAttribute> mRevokePhotos;

    // if these are non-null, PINs will be changed on the card
    public Passphrase mCardPin;
    public Passphrase mCardAdminPin;

    // private because they have to be set together with setUpdateOptions
    private boolean mUpload;
    private boolean mUploadAtomic;
    private String mKeyserver;

    public SaveKeyringParcel() {
        reset();
    }

    public SaveKeyringParcel(long masterKeyId, byte[] fingerprint) {
        this();
        mMasterKeyId = masterKeyId;
        mFingerprint = fingerprint;
    }

    public void reset() {
        mNewUnlock = null;
        mAddUserIds = new ArrayList<>();
        mAddUserAttributes = new ArrayList<>();
        mAddSubKeys = new ArrayList<>();
        mAddPhotos = new ArrayList<>();
        mChangePrimaryUserId = null;
        mChangeSubKeys = new ArrayList<>();
        mRevokeUserIds = new ArrayList<>();
        mRevokeUserAttributes = new ArrayList<>();
        mRevokeSubKeys = new ArrayList<>();
        mRevokePhotos = new ArrayList<>();
        mCardPin = null;
        mCardAdminPin = null;
        mUpload = false;
        mUploadAtomic = false;
        mKeyserver = null;
    }

    public void setUpdateOptions(boolean upload, boolean uploadAtomic, String keysever) {
        mUpload = upload;
        mUploadAtomic = uploadAtomic;
        mKeyserver = keysever;
    }

    public boolean isUpload() {
        return mUpload;
    }

    public boolean isUploadAtomic() {
        return mUploadAtomic;
    }

    public String getUploadKeyserver() {
        return mKeyserver;
    }

    public boolean isEmpty() {
        return isRestrictedOnly() && mChangeSubKeys.isEmpty();
    }

    /** Returns true if this parcel does not contain any operations which require a passphrase. */
    public boolean isRestrictedOnly() {
        if (mNewUnlock != null || mChangePrimaryUserId != null
                || !mAddUserIds.isEmpty() || !mAddUserAttributes.isEmpty()
                || !mAddSubKeys.isEmpty() || !mAddPhotos.isEmpty()
                || !mRevokeUserIds.isEmpty() || !mRevokeUserAttributes.isEmpty()
                || !mRevokeSubKeys.isEmpty() || !mRevokePhotos.isEmpty()) {
            return false;
        }

        for (SubkeyChange change : mChangeSubKeys) {
            if (change.mRecertify || change.mFlags != null || change.mExpiry != null
                    || change.mMoveKeyToCard) {
                return false;
            }
        }

        return true;
    }

    // performance gain for using Parcelable here would probably be negligible,
    // use Serializable instead.
    public static class SubkeyAdd implements Serializable {
        public Algorithm mAlgorithm;
        public Integer mKeySize;
        public Curve mCurve;
        public int mFlags;
        public Long mExpiry;

        public SubkeyAdd(Algorithm algorithm, Integer keySize, Curve curve, int flags, Long expiry) {
            mAlgorithm = algorithm;
            mKeySize = keySize;
            mCurve = curve;
            mFlags = flags;
            mExpiry = expiry;
        }

        @Override
        public String toString() {
            String out = "mAlgorithm: " + mAlgorithm + ", ";
            out += "mKeySize: " + mKeySize + ", ";
            out += "mCurve: " + mCurve + ", ";
            out += "mFlags: " + mFlags;
            out += "mExpiry: " + mExpiry;

            return out;
        }
    }

    public static class SubkeyChange implements Serializable {
        public final long mKeyId;
        public Integer mFlags;
        // this is a long unix timestamp, in seconds (NOT MILLISECONDS!)
        public Long mExpiry;
        // if this flag is true, the key will be recertified even if all above
        // values are no-ops
        public boolean mRecertify;
        // if this flag is true, the subkey should be changed to a stripped key
        public boolean mDummyStrip;
        // if this flag is true, the subkey should be moved to a card
        public boolean mMoveKeyToCard;
        // if this is non-null, the subkey will be changed to a divert-to-card
        // key for the given serial number
        public byte[] mDummyDivert;

        public SubkeyChange(long keyId) {
            mKeyId = keyId;
        }

        public SubkeyChange(long keyId, boolean recertify) {
            mKeyId = keyId;
            mRecertify = recertify;
        }

        public SubkeyChange(long keyId, Integer flags, Long expiry) {
            mKeyId = keyId;
            mFlags = flags;
            mExpiry = expiry;
        }

        public SubkeyChange(long keyId, boolean dummyStrip, boolean moveKeyToCard) {
            this(keyId, null, null);

            // these flags are mutually exclusive!
            if (dummyStrip && moveKeyToCard) {
                throw new AssertionError(
                        "cannot set strip and keytocard flags at the same time - this is a bug!");
            }
            mDummyStrip = dummyStrip;
            mMoveKeyToCard = moveKeyToCard;
        }

        @Override
        public String toString() {
            String out = "mKeyId: " + mKeyId + ", ";
            out += "mFlags: " + mFlags + ", ";
            out += "mExpiry: " + mExpiry + ", ";
            out += "mDummyStrip: " + mDummyStrip + ", ";
            out += "mMoveKeyToCard: " + mMoveKeyToCard + ", ";
            out += "mDummyDivert: [" + (mDummyDivert == null ? 0 : mDummyDivert.length) + " bytes]";

            return out;
        }
    }

    public SubkeyChange getSubkeyChange(long keyId) {
        for (SubkeyChange subkeyChange : mChangeSubKeys) {
            if (subkeyChange.mKeyId == keyId) {
                return subkeyChange;
            }
        }
        return null;
    }

    public SubkeyChange getOrCreateSubkeyChange(long keyId) {
        SubkeyChange foundSubkeyChange = getSubkeyChange(keyId);
        if (foundSubkeyChange != null) {
            return foundSubkeyChange;
        } else {
            // else, create a new one
            SubkeyChange newSubkeyChange = new SubkeyChange(keyId);
            mChangeSubKeys.add(newSubkeyChange);
            return newSubkeyChange;
        }
    }

    public void convertPhotosToWrappedUserAttributes() {
        for (PhotoAttribute photo : mAddPhotos) {
            mAddUserAttributes.add(photo.toWrappedUserAttribute());
        }
        for (PhotoAttribute photo : mRevokePhotos) {
            mRevokeUserAttributes.add(photo.toWrappedUserAttribute());
        }
    }

    @SuppressWarnings("unchecked") // we verify the reads against writes in writeToParcel
    public SaveKeyringParcel(Parcel source) {
        mMasterKeyId = source.readInt() != 0 ? source.readLong() : null;
        mFingerprint = source.createByteArray();

        mNewUnlock = source.readParcelable(getClass().getClassLoader());

        mAddUserIds = source.createStringArrayList();
        mAddUserAttributes = (ArrayList<WrappedUserAttribute>) source.readSerializable();
        mAddSubKeys = (ArrayList<SubkeyAdd>) source.readSerializable();
        mAddPhotos = (ArrayList<PhotoAttribute>) source.readSerializable();

        mChangeSubKeys = (ArrayList<SubkeyChange>) source.readSerializable();
        mChangePrimaryUserId = source.readString();

        mRevokeUserIds = source.createStringArrayList();
        mRevokeUserAttributes = (ArrayList<WrappedUserAttribute>) source.readSerializable();
        mRevokeSubKeys = (ArrayList<Long>) source.readSerializable();
        mRevokePhotos = (ArrayList<PhotoAttribute>) source.readSerializable();

        mCardPin = source.readParcelable(Passphrase.class.getClassLoader());
        mCardAdminPin  = source.readParcelable(Passphrase.class.getClassLoader());

        mUpload = source.readByte() != 0;
        mUploadAtomic = source.readByte() != 0;
        mKeyserver = source.readString();
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeInt(mMasterKeyId == null ? 0 : 1);
        if (mMasterKeyId != null) {
            destination.writeLong(mMasterKeyId);
        }
        destination.writeByteArray(mFingerprint);

        // yes, null values are ok for parcelables
        destination.writeParcelable(mNewUnlock, flags);

        destination.writeStringList(mAddUserIds);
        destination.writeSerializable(mAddUserAttributes);
        destination.writeSerializable(mAddSubKeys);
        destination.writeSerializable(mAddPhotos);

        destination.writeSerializable(mChangeSubKeys);
        destination.writeString(mChangePrimaryUserId);

        destination.writeStringList(mRevokeUserIds);
        destination.writeSerializable(mRevokeUserAttributes);
        destination.writeSerializable(mRevokeSubKeys);
        destination.writeSerializable(mRevokePhotos);

        destination.writeParcelable(mCardPin, flags);
        destination.writeParcelable(mCardAdminPin, flags);

        destination.writeByte((byte) (mUpload ? 1 : 0));
        destination.writeByte((byte) (mUploadAtomic ? 1 : 0));
        destination.writeString(mKeyserver);
    }

    public static final Creator<SaveKeyringParcel> CREATOR = new Creator<SaveKeyringParcel>() {
        public SaveKeyringParcel createFromParcel(final Parcel source) {
            return new SaveKeyringParcel(source);
        }

        public SaveKeyringParcel[] newArray(final int size) {
            return new SaveKeyringParcel[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        String out = "mMasterKeyId: " + mMasterKeyId + "\n";
        out += "mNewUnlock: " + mNewUnlock + "\n";
        out += "mAddUserIds: " + mAddUserIds + "\n";
        out += "mAddUserAttributes: " + mAddUserAttributes + "\n";
        out += "mAddSubKeys: " + mAddSubKeys + "\n";
        out += "mAddPhotos: " + mAddPhotos + "\n";
        out += "mChangeSubKeys: " + mChangeSubKeys + "\n";
        out += "mChangePrimaryUserId: " + mChangePrimaryUserId + "\n";
        out += "mRevokeUserIds: " + mRevokeUserIds + "\n";
        out += "mRevokeUserAttributes: " + mRevokeUserAttributes + "\n";
        out += "mRevokeSubKeys: " + mRevokeSubKeys + "\n";
        out += "mRevokePhotos: " + mRevokePhotos + "\n";
        out += "mCardPin: " + mCardPin + "\n";
        out += "mCardAdminPin: " + mCardAdminPin;

        return out;
    }

    // All supported algorithms
    public enum Algorithm {
        RSA, DSA, ELGAMAL, ECDSA, ECDH
    }

    // All curves defined in the standard
    // http://www.bouncycastle.org/wiki/pages/viewpage.action?pageId=362269
    public enum Curve {
        NIST_P256, NIST_P384, NIST_P521,

        // these are supported by gpg, but they are not in rfc6637 and not supported by BouncyCastle yet
        // (adding support would be trivial though -> JcaPGPKeyConverter.java:190)
        // BRAINPOOL_P256, BRAINPOOL_P384, BRAINPOOL_P512
    }

    /** This subclass contains information on how the passphrase should be changed.
     *
     * If no changes are to be made, this class should NOT be used!
     *
     * At this point, there must be *exactly one* non-null value here, which specifies the type
     * of unlocking mechanism to use.
     *
     */
    public static class ChangeUnlockParcel implements Parcelable {

        // The new passphrase to use
        public final Passphrase mNewPassphrase;
        // A new pin to use. Must only contain [0-9]+
        public final Passphrase mNewPin;

        public ChangeUnlockParcel(Passphrase newPassphrase) {
            this(newPassphrase, null);
        }
        public ChangeUnlockParcel(Passphrase newPassphrase, Passphrase newPin) {
            if (newPassphrase == null && newPin == null) {
                throw new RuntimeException("Cannot set both passphrase and pin. THIS IS A BUG!");
            }
            mNewPassphrase = newPassphrase;
            mNewPin = newPin;
        }

        public ChangeUnlockParcel(Parcel source) {
            mNewPassphrase = source.readParcelable(Passphrase.class.getClassLoader());
            mNewPin = source.readParcelable(Passphrase.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel destination, int flags) {
            destination.writeParcelable(mNewPassphrase, flags);
            destination.writeParcelable(mNewPin, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<ChangeUnlockParcel> CREATOR = new Creator<ChangeUnlockParcel>() {
            public ChangeUnlockParcel createFromParcel(final Parcel source) {
                return new ChangeUnlockParcel(source);
            }

            public ChangeUnlockParcel[] newArray(final int size) {
                return new ChangeUnlockParcel[size];
            }
        };

        public String toString() {
            return mNewPassphrase != null
                    ? ("passphrase (" + mNewPassphrase + ")")
                    : ("pin (" + mNewPin + ")");
        }

    }

}
