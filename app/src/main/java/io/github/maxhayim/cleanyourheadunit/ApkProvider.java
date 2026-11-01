package io.github.maxhayim.cleanyourheadunit;

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
 * Serves exactly one file, the verified download, read-only, to Android's package installer.
 * Not exported; access is granted per intent. (A tiny stand-in for AndroidX FileProvider, so the
 * app keeps no libraries.)
 */
public final class ApkProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.maxhayim.cleanyourheadunit.apk";

    public static Uri uri(Context c) {
        return Uri.parse("content://" + AUTHORITY + "/download.apk");
    }

    private File file() {
        return new File(new File(getContext().getCacheDir(), "apk"), "download.apk");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"download.apk".equals(uri.getLastPathSegment()) || !"r".equals(mode)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file(), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{"download.apk", file().length()});
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
