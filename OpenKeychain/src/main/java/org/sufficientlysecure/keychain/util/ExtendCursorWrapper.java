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

package org.sufficientlysecure.keychain.util;

import android.database.Cursor;
import android.database.CursorWrapper;

/**
 * In some cases a cursor has to behave as if it has many upper-level
 * rows related to / originated from one database-level row.
 * This behavior is necessary mostly when many upper-level data elements (e.g. future list items)
 * are produced from a set of data that appears as only one row in the database.
 * <p>
 * This class defines a cursor which assigns 0, 1 or more rows for one original (database-level) row.
 */

public abstract class ExtendCursorWrapper extends CursorWrapper {
    // mIndex: shows how many upper-level rows are related to the database-level row
    // referred by each array index.
    private int[] mIndex;
    private int mCount = 0;
    private int mPos = 0;

    /**
     * @param cursor already moved to the correct position
     * @return The number of rows that should be assigned to the current original row of the cursor.
     */
    public abstract int relatedRowCount(Cursor cursor);

    public ExtendCursorWrapper(Cursor cursor) {
        super(cursor);
        mCount = super.getCount();
        mIndex = new int[mCount];
        for (int i = 0; i < mCount; i++) {
            super.moveToPosition(i);
            int relatedRowCount = relatedRowCount(cursor);
            mIndex[i] = relatedRowCount;
            mPos += relatedRowCount;
        }
        mCount = mPos;
        mPos = 0;
        super.moveToFirst();
    }

    @Override
    public boolean move(int offset) {
        return this.moveToPosition(mPos + offset);
    }

    @Override
    public boolean moveToNext() {
        return this.moveToPosition(mPos + 1);
    }

    @Override
    public boolean moveToPrevious() {
        return this.moveToPosition(mPos - 1);
    }

    @Override
    public boolean moveToFirst() {
        return this.moveToPosition(0);
    }

    @Override
    public boolean moveToLast() {
        return this.moveToPosition(mCount - 1);
    }

    @Override
    public boolean moveToPosition(int position) {
        try {
            mPos = position;
            int unextendedPosition = getExtendedPosition(position)[0];
            return super.moveToPosition(unextendedPosition);
        } catch(IllegalArgumentException e) {
            return false;
        }
    }

    /** Returns an array the 0th element of which shows the unextended position,
     * and the 1st element of which shows the offset inside the assigned rows of that original row.
     */
    public int[] getExtendedPosition(int position) {
        if (position >= mCount || position < 0) {
            throw new IllegalArgumentException("The position must be less than the cursor's count, and >= 0");
        }

        int unextendedPosition = 0, passedPosition = 0;
        while(passedPosition < position) {
            passedPosition += mIndex[unextendedPosition];
            unextendedPosition++;
        }

        int[] resultArray = new int[2];
        resultArray[0] = unextendedPosition;
        resultArray[1] = passedPosition - position; // offset

        return resultArray;
    }

    @Override
    public int getCount() {
        return mCount;
    }

    @Override
    public int getPosition() {
        return mPos;
    }
}