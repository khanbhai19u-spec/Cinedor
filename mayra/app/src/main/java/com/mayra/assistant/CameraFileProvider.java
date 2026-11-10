package com.mayra.assistant;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Minimal file provider so the camera app can write a full-resolution photo into MAYRA's
 * private cache. Only the single file "camera/photo.jpg" is served.
 */
public class CameraFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.mayra.assistant.camera";

    public static File photoFile(Context c) {
        File dir = new File(c.getCacheDir(), "camera");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "photo.jpg");
    }

    public static Uri photoUri() {
        return Uri.parse("content://" + AUTHORITY + "/photo.jpg");
    }

    @Override public boolean onCreate() { return true; }

    private File fileFor(Uri uri) throws FileNotFoundException {
        if (!"photo.jpg".equals(uri.getLastPathSegment())) throw new FileNotFoundException(uri.toString());
        return photoFile(getContext());
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        try {
            File f = fileFor(uri);
            c.addRow(new Object[]{"camera.jpg", f.length()});
        } catch (FileNotFoundException ignored) { }
        return c;
    }

    @Override public String getType(Uri uri) { return "image/jpeg"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
