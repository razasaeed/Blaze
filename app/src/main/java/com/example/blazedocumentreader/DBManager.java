package com.example.blazedocumentreader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.Toast;

import com.example.blazedocumentreader.Models.FavModel;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class DBManager {

    public String DATABASE_NAME = "blaze.sqlite";
    static final int DATABASE_VERSION = 2;
    final Context context;
    SQLiteDatabase db;
    DatabaseHelper DBHelper;

    public DBManager(Context ctx) {
        this.context = ctx;
        DBHelper = new DatabaseHelper(context);
    }

    private class DatabaseHelper extends SQLiteOpenHelper {


        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);

        }

        @Override
        public void onCreate(SQLiteDatabase db) {

        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            super.onOpen(db);
            db.disableWriteAheadLogging();
        }

    }

    // ---opens the database---
    public DBManager open() throws SQLException {
        db = DBHelper.getWritableDatabase();
        return this;
    }

    // ---closes the database---
    public void close() {
        DBHelper.close();
    }

    public void createDataBase() throws IOException {

        boolean mDataBaseExist = checkDataBase();

        if (mDataBaseExist) {
            // do nothing - database already exist

        } else {

            DBHelper.getReadableDatabase();
            DBHelper.close();

            try {
                copyDataBase();
            } catch (IOException e) {
                throw new Error("Error copying database");
            }
        }
    }

    /**
     * This method checks whether database is exists or not
     **/
    private boolean checkDataBase() {
        SQLiteDatabase checkDB = null;

        try {
            String myPath = context.getDatabasePath(DATABASE_NAME).getPath()
                    .toString();

            checkDB = SQLiteDatabase.openDatabase(myPath, null,
                    SQLiteDatabase.OPEN_READONLY);
        } catch (SQLiteException e) {
            // database does't exist yet.
        }

        if (checkDB != null) {
            checkDB.close();
        }

        return checkDB != null ? true : false;
    }

    public long copyDataBase() throws IOException {
        String DB_PATH = context.getDatabasePath(DATABASE_NAME).getPath()
                .toString();

        // Open your local db as the input stream
        InputStream myInput = context.getAssets().open(DATABASE_NAME);

        // Path to the just created empty db
        String outFileName = DB_PATH;

        // Open the empty db as the output stream
        OutputStream myOutput = new FileOutputStream(outFileName);

        // transfer bytes from the inputfile to the outputfile
        byte[] buffer = new byte[1024];
        int length;
        while ((length = myInput.read(buffer)) > 0) {
            myOutput.write(buffer, 0, length);
        }

        // Close the streams
        myOutput.flush();
        myOutput.close();
        myInput.close();
        return length;
    }

    public List<FavModel> getFavourites(String name, String type) {
        List<FavModel> data = new ArrayList<>();
        String[] selection = {name, type};
        String query = "select * from favourite where name = ? and type = ?";
        Cursor cursor = db.rawQuery(query, selection);
        if (data.size() > 0) {
            data.clear();
        }
        if (cursor.moveToFirst()) {
            do {
                FavModel favModel = new FavModel();
                favModel.setId(cursor.getInt(0));
                favModel.setType(cursor.getString(1));
                favModel.setName(cursor.getString(2));
                data.add(favModel);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return data;
    }

    public void addFav(String type, String name){
        ContentValues cv = new ContentValues();
        cv.put("type", type);
        cv.put("name", name);
        long l = db.insert("favourite", null, cv);
        if (l>0){
            Toast.makeText(context, "favourited", Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(context, "un-favourited", Toast.LENGTH_SHORT).show();
        }
    }

    public void deleteFav(String id, String type){
        ContentValues cv = new ContentValues();
        cv.put("id", id);
        cv.put("type", type);
        long l = db.update("favourite", cv, "id = ? and type = ?", new String[] {id, type});
        if (l>0){
            Toast.makeText(context, "favourited", Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(context, "un-favourited", Toast.LENGTH_SHORT).show();
        }
    }
}


