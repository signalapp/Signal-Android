package org.thoughtcrime.securesms.contacts;
/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.database.AbstractCursor;
import android.database.CursorWindow;

import java.lang.System;
import java.util.ArrayList;

/**
 * A convenience class that presents a two-dimensional ArrayList
 * as a Cursor.
 */
public class ArrayListCursor extends AbstractCursor {
    private String[] mColumnNames;
    private ArrayList<Object>[] mRows;

    @SuppressWarnings({"unchecked"})
    public ArrayListCursor(String[] columnNames, ArrayList<ArrayList> rows) {
        int colCount = columnNames.length;
        boolean foundID = false;
        // Add an _id column if not in columnNames
        for (int i = 0; i < colCount; ++i) {
            if (columnNames[i].compareToIgnoreCase("_id") == 0) {
                mColumnNames = columnNames;
                foundID = true;
                break;
            }
        }

        if (!foundID) {
            mColumnNames = new String[colCount + 1];
            System.arraycopy(columnNames, 0, mColumnNames, 0, columnNames.length);
            mColumnNames[colCount] = "_id";
        }

        int rowCount = rows.size();
        mRows = new ArrayList[rowCount];

        for (int i = 0; i < rowCount; ++i) {
            mRows[i] = rows.get(i);
            if (!foundID) {
                mRows[i].add(i);
            }
        }
    }

    @Override
    public void fillWindow(int position, CursorWindow window) {
        if (position < 0 || position > getCount()) {
            return;
        }

        window.acquireReference();
        try {
            int oldpos = mPos;
            mPos = position - 1;
            window.clear();
            window.setStartPosition(position);
            int columnNum = getColumnCount();
            window.setNumColumns(columnNum);
            while (moveToNext() && window.allocRow()) {
                for (int i = 0; i < columnNum; i++) {
                    final Object data = mRows[mPos].get(i);
                    if (data != null) {
                        if (data instanceof byte[]) {
                            byte[] field = (byte[]) data;
                            if (!window.putBlob(field, mPos, i)) {
                                window.freeLastRow();
                                break;
                            }
                        } else {
                            String field = data.toString();
                            if (!window.putString(field, mPos, i)) {
                                window.freeLastRow();
                                break;
                            }
                        }
                    } else {
                        if (!window.putNull(mPos, i)) {
                            window.freeLastRow();
                            break;
                        }
                    }
                }
            }

            mPos = oldpos;
        } catch (IllegalStateException e){
            // simply ignore it
        } finally {
            window.releaseReference();
        }
    }

    @Override
    public int getCount() {
        return mRows.length;
    }

    public boolean deleteRow() {
        return false;
    }

    @Override
    public String[] getColumnNames() {
        return mColumnNames;
    }

    @Override
    public byte[] getBlob(int columnIndex) {
        return (byte[]) mRows[mPos].get(columnIndex);
    }

    @Override
    public String getString(int columnIndex) {
        Object cell = mRows[mPos].get(columnIndex);
        return (cell == null) ? null : cell.toString();
    }

    @Override
    public short getShort(int columnIndex) {
        Number num = (Number) mRows[mPos].get(columnIndex);
        return num.shortValue();
    }

    @Override
    public int getInt(int columnIndex) {
        Number num = (Number) mRows[mPos].get(columnIndex);
        return num.intValue();
    }

    @Override
    public long getLong(int columnIndex) {
        Number num = (Number) mRows[mPos].get(columnIndex);
        return num.longValue();
    }

    @Override
    public float getFloat(int columnIndex) {
        Number num = (Number) mRows[mPos].get(columnIndex);
        return num.floatValue();
    }

    @Override
    public double getDouble(int columnIndex) {
        Number num = (Number) mRows[mPos].get(columnIndex);
        return num.doubleValue();
    }

    @Override
    public boolean isNull(int columnIndex) {
        return mRows[mPos].get(columnIndex) == null;
    }
}
