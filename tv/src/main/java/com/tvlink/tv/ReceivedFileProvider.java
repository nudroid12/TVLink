package com.tvlink.tv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

public final class ReceivedFileProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private File resolve(Uri uri) throws IOException {
        if (uri.getPathSegments().size() == 2 && "updates".equals(uri.getPathSegments().get(0))
                && ReleaseCheck.UPDATE_FILE.equals(uri.getLastPathSegment())) {
            return new File(getContext().getCacheDir(), ReleaseCheck.UPDATE_FILE);
        }
        if (uri.getPathSegments().size() == 2 && "storage".equals(uri.getPathSegments().get(0))) {
            File file = new File(uri.getLastPathSegment());
            if (file.isFile() && TvSettings.canBrowse(getContext(), file)) return file;
            throw new IOException("Invalid storage file");
        }
        if (uri.getPathSegments().size() != 1) throw new IOException("Invalid URI");
        return TransferServer.file(TvSettings.directory(getContext()), uri.getLastPathSegment());
    }

    @Override public String getType(Uri uri) {
        String name = new File(uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment()).getName();
        if (ReleaseCheck.UPDATE_FILE.equals(name)) return "application/vnd.android.package-archive";
        int dot = name == null ? -1 : name.lastIndexOf('.');
        String mime = dot < 0 ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                name.substring(dot + 1).toLowerCase(Locale.ROOT));
        return mime == null ? "application/octet-stream" : mime;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        try { return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY); }
        catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        String[] columns = projection != null ? projection :
                new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns);
        try {
            File file = resolve(uri);
            if (file.isFile()) {
                MatrixCursor.RowBuilder row = cursor.newRow();
                for (String column : columns) {
                    if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add(file.getName());
                    else if (OpenableColumns.SIZE.equals(column)) row.add(file.length());
                    else row.add(null);
                }
            }
        } catch (IOException ignored) {}
        return cursor;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
