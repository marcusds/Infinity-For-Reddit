package ml.docilealligator.infinityforreddit.bookmarkedsubreddit;

import androidx.lifecycle.LiveData;

import java.util.List;

import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;

public class BookmarkedSubredditRepository {
    private final BookmarkedSubredditDao mBookmarkedSubredditDao;
    private final String mAccountName;

    BookmarkedSubredditRepository(RedditDataRoomDatabase redditDataRoomDatabase, String accountName) {
        mAccountName = accountName;
        mBookmarkedSubredditDao = redditDataRoomDatabase.bookmarkedSubredditDao();
    }

    LiveData<List<BookmarkedSubredditData>> getAllBookmarkedSubredditsWithSearchQuery(String searchQuery) {
        return mBookmarkedSubredditDao.getAllBookmarkedSubredditsWithSearchQuery(mAccountName, searchQuery);
    }
}
