package ml.docilealligator.infinityforreddit.bookmarkedsubreddit;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface BookmarkedSubredditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BookmarkedSubredditData bookmarkedSubredditData);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<BookmarkedSubredditData> bookmarkedSubredditDataList);

    @Query("DELETE FROM bookmarked_subreddits WHERE name = :subredditName COLLATE NOCASE AND username = :accountName COLLATE NOCASE")
    void deleteBookmarkedSubreddit(String subredditName, String accountName);

    @Query("SELECT * from bookmarked_subreddits WHERE username = :accountName AND name LIKE '%' || :searchQuery || '%' ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<BookmarkedSubredditData>> getAllBookmarkedSubredditsWithSearchQuery(String accountName, String searchQuery);

    @Query("SELECT * from bookmarked_subreddits WHERE name = :subredditName COLLATE NOCASE AND username = :accountName COLLATE NOCASE LIMIT 1")
    BookmarkedSubredditData getBookmarkedSubreddit(String subredditName, String accountName);

    @Query("DELETE FROM bookmarked_subreddits")
    void deleteAllBookmarkedSubreddits();
}
