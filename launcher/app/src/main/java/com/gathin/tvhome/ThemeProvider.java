package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lets tools/theme.sh write the theme files into {@link Config#dir} on firmware where the adb shell is
 * locked out of Android/data (TCL G07, Android 11). Only config.json, wallpaper.jpg, theme_revision and
 * images in wallpapers/, and only for callers holding WRITE_SECURE_SETTINGS, which the adb shell has and
 * ordinary apps cannot get.
 *
 *   content write --uri content://com.gathin.tvhome.theme/config.json < /data/local/tmp/config.json
 *   content write --uri content://com.gathin.tvhome.theme/wallpapers/dusk.jpg < /data/local/tmp/dusk.jpg
 *   content delete --uri content://com.gathin.tvhome.theme/config.json
 *   content delete --uri content://com.gathin.tvhome.theme/wallpapers      (every image in the collection)
 */
public class ThemeProvider extends ContentProvider {

    private static final String WALLPAPERS = "wallpapers";
    /** The names tools/theme.sh accepts for a collection image, and the types WallpaperRotation reads. */
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);

    private File file(Uri uri) throws FileNotFoundException {
        List<String> path = uri.getPathSegments();
        File dir = Config.dir(getContext());
        if (dir != null && path.size() == 1) {
            String name = path.get(0);
            if (Config.FILE_NAME.equals(name) || "wallpaper.jpg".equals(name) || "theme_revision".equals(name)) {
                return new File(dir, name);
            }
        }
        if (dir != null && path.size() == 2 && WALLPAPERS.equals(path.get(0)) && IMAGE.matcher(path.get(1)).matches()) {
            File collection = new File(dir, WALLPAPERS);
            if (!collection.isDirectory() && !collection.mkdirs()) throw new FileNotFoundException(String.valueOf(uri));
            return new File(collection, path.get(1));
        }
        throw new FileNotFoundException(String.valueOf(uri));
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = file(uri);
        if (mode.contains("w")) {
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        List<String> path = uri.getPathSegments();
        File dir = Config.dir(getContext());
        if (dir != null && path.size() == 1 && WALLPAPERS.equals(path.get(0))) {
            File[] images = new File(dir, WALLPAPERS).listFiles((d, name) -> IMAGE.matcher(name).matches());
            int n = 0;
            if (images != null) for (File f : images) if (f.delete()) n++;
            return n;
        }
        try {
            return file(uri).delete() ? 1 : 0;
        } catch (FileNotFoundException e) {
            return 0;
        }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
