package ml.docilealligator.infinityforreddit.bookmarkedsubreddit;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

import ml.docilealligator.infinityforreddit.account.Account;

@Entity(tableName = "bookmarked_subreddits", primaryKeys = {"id", "username"},
        foreignKeys = @ForeignKey(entity = Account.class, parentColumns = "username",
                childColumns = "username", onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = "username")})
public class BookmarkedSubredditData {
    @NonNull
    @ColumnInfo(name = "id")
    private final String id;
    @ColumnInfo(name = "name")
    private final String name;
    @ColumnInfo(name = "icon")
    private final String iconUrl;
    @NonNull
    @ColumnInfo(name = "username")
    private String username;

    public BookmarkedSubredditData(@NonNull String id, String name, String iconUrl, @NonNull String username) {
        this.id = id;
        this.name = name;
        this.iconUrl = iconUrl;
        this.username = username;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    public void setUsername(@NonNull String username) {
        this.username = username;
    }
}
