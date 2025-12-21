package ml.docilealligator.infinityforreddit.bookmarkedsubreddit;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;

import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;

public class BookmarkedSubredditViewModel extends ViewModel {
    private final BookmarkedSubredditRepository mBookmarkedSubredditRepository;
    private final LiveData<List<BookmarkedSubredditData>> mAllBookmarkedSubreddits;
    private final MutableLiveData<String> searchQueryLiveData;

    public BookmarkedSubredditViewModel(RedditDataRoomDatabase redditDataRoomDatabase, String accountName) {
        mBookmarkedSubredditRepository = new BookmarkedSubredditRepository(redditDataRoomDatabase, accountName);
        searchQueryLiveData = new MutableLiveData<>("");

        mAllBookmarkedSubreddits = Transformations.switchMap(searchQueryLiveData, mBookmarkedSubredditRepository::getAllBookmarkedSubredditsWithSearchQuery);
    }

    public LiveData<List<BookmarkedSubredditData>> getAllBookmarkedSubreddits() {
        return mAllBookmarkedSubreddits;
    }

    public void setSearchQuery(String searchQuery) {
        searchQueryLiveData.postValue(searchQuery);
    }

    public static class Factory extends ViewModelProvider.NewInstanceFactory {
        private final RedditDataRoomDatabase mRedditDataRoomDatabase;
        private final String mAccountName;

        public Factory(RedditDataRoomDatabase redditDataRoomDatabase, String accountName) {
            this.mRedditDataRoomDatabase = redditDataRoomDatabase;
            this.mAccountName = accountName;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new BookmarkedSubredditViewModel(mRedditDataRoomDatabase, mAccountName);
        }
    }
}
