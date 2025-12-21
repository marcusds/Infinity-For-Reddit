package ml.docilealligator.infinityforreddit;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class ForkMigrations {

    public static final Migration MIGRATION_32_33 = new Migration(32, 33) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Devices that went through our old MIGRATION_31_32 (bookmarks) skipped
            // upstream's MIGRATION_31_32 (read_posts restructure), so apply it here if needed.
            boolean hasReadPostType = false;
            try (Cursor cursor = database.query("PRAGMA table_info(read_posts)")) {
                int nameIdx = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    if ("read_post_type".equals(cursor.getString(nameIdx))) {
                        hasReadPostType = true;
                        break;
                    }
                }
            }
            if (!hasReadPostType) {
                database.execSQL("CREATE TABLE read_posts_new"
                        + "(username TEXT NOT NULL, id TEXT NOT NULL, time INTEGER DEFAULT 0 NOT NULL, "
                        + "read_post_type INTEGER DEFAULT 0 NOT NULL, PRIMARY KEY(username, id, read_post_type), "
                        + "FOREIGN KEY(username) REFERENCES accounts(username) ON DELETE CASCADE)");
                database.execSQL("INSERT INTO read_posts_new (username, id, time) SELECT username, id, time FROM read_posts");
                database.execSQL("DROP TABLE read_posts");
                database.execSQL("ALTER TABLE read_posts_new RENAME TO read_posts");
            }

            database.execSQL("CREATE TABLE IF NOT EXISTS bookmarked_subreddits " +
                    "(id TEXT NOT NULL, name TEXT, icon TEXT, username TEXT NOT NULL, " +
                    "PRIMARY KEY(id, username), " +
                    "FOREIGN KEY(username) REFERENCES accounts(username) ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarked_subreddits_username ON bookmarked_subreddits(username)");
        }
    };
}
