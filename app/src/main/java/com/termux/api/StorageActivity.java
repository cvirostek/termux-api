package com.termux.api;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StorageActivity extends Activity {

    private String outputPath;
    private boolean resultReturned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        outputPath = getIntent().getStringExtra("file");

        // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file browser.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        // Filter to only show results that can be "opened", such as a
        // file (as opposed to a list of contacts or timezones)
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.setType("*/*");

        startActivityForResult(intent, 42);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (!resultReturned) {
            StorageResult result = new StorageResult();
            result.code = -2;
            postResult(result);
        }
        finishAndRemoveTask();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);

        StorageResult result = new StorageResult();

        if (resultCode == RESULT_OK) {
            Uri data = resultData.getData();
            result.filename = getFilenameFromUri(data);

            File outputFile = new File(outputPath);
            if (outputFile.exists() && outputFile.isDirectory()) {
                outputFile = new File(outputPath, result.filename);
            }
            try {
                try (InputStream in = getContentResolver().openInputStream(data)) {
                    try (OutputStream out = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[8192];
                        while (true) {
                            int read = in.read(buffer);
                            if (read <= 0) {
                                break;
                            } else {
                                out.write(buffer, 0, read);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                TermuxApiLogger.error("Error copying " + data + " to " + outputPath);
                result.code = -2;
                result.error = "Error: could not write to " + outputFile.getAbsolutePath();
            }
        } else {
            result.code = -2;
        }
        postResult(result);
        finishAndRemoveTask();
    }

    void postResult(final StorageResult result) {
        ResultReturner.returnData(this, getIntent(), new ResultReturner.ResultJsonWriter() {

            @Override
            public void writeJson(JsonWriter out) throws Exception {
                out.beginObject();

                out.name("code").value(result.code);
                out.name("filename").value(result.filename);

                if (!result.error.equals("")) {
                    out.name("error").value(result.error);
                }

                out.endObject();
                out.flush();
                resultReturned = true;
            }
        });
    }

    protected String getFilenameFromUri(Uri uri) {
        // https://stackoverflow.com/a/27926504
        String uriString = uri.toString();
        File myFile = new File(uriString);
        String displayName = null;

        if (uriString.startsWith("content://")) {
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    displayName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        } else if (uriString.startsWith("file://")) {
            displayName = myFile.getName();
        }
        return displayName;
    }

    class StorageResult {
        public String filename = "";
        public String error = "";
        public int code = 0;
    }
}
