package ml.docilealligator.infinityforreddit.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;

import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Named;

import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.adapters.BookmarkedSubredditsRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.bookmarkedsubreddit.BookmarkedSubredditViewModel;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed;
import ml.docilealligator.infinityforreddit.databinding.FragmentBookmarkedSubredditsListingBinding;
import ml.docilealligator.infinityforreddit.utils.Utils;

public class BookmarkedSubredditsListingFragment extends Fragment implements FragmentCommunicator {

    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;
    @Inject
    Executor mExecutor;
    public BookmarkedSubredditViewModel mBookmarkedSubredditViewModel;
    private BaseActivity mActivity;
    private RequestManager mGlide;
    private LinearLayoutManagerBugFixed mLinearLayoutManager;
    private FragmentBookmarkedSubredditsListingBinding binding;

    public BookmarkedSubredditsListingFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentBookmarkedSubredditsListingBinding.inflate(inflater, container, false);

        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);

        applyTheme();

        if ((mActivity.isImmersiveInterfaceRespectForcedEdgeToEdge())) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, false, mActivity.isForcedImmersiveInterface());
                    binding.recyclerViewBookmarkedSubredditsListingFragment.setPadding(
                            0, 0, 0, allInsets.bottom
                    );
                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        // Disable swipe refresh for bookmarks (no API sync needed)
        binding.swipeRefreshLayoutBookmarkedSubredditsListingFragment.setEnabled(false);

        mGlide = Glide.with(this);

        mLinearLayoutManager = new LinearLayoutManagerBugFixed(mActivity);
        binding.recyclerViewBookmarkedSubredditsListingFragment.setLayoutManager(mLinearLayoutManager);

        BookmarkedSubredditsRecyclerViewAdapter adapter = new BookmarkedSubredditsRecyclerViewAdapter(
                mActivity, mExecutor, mRedditDataRoomDatabase, mCustomThemeWrapper, mActivity.accountName);

        binding.recyclerViewBookmarkedSubredditsListingFragment.setAdapter(adapter);
        new FastScrollerBuilder(binding.recyclerViewBookmarkedSubredditsListingFragment).useMd2Style().build();

        mBookmarkedSubredditViewModel = new ViewModelProvider(this,
                new BookmarkedSubredditViewModel.Factory(mRedditDataRoomDatabase, mActivity.accountName))
                .get(BookmarkedSubredditViewModel.class);

        mBookmarkedSubredditViewModel.getAllBookmarkedSubreddits().observe(getViewLifecycleOwner(), bookmarkedSubredditData -> {
            if (bookmarkedSubredditData == null || bookmarkedSubredditData.size() == 0) {
                binding.recyclerViewBookmarkedSubredditsListingFragment.setVisibility(View.GONE);
                binding.noBookmarksLinearLayoutBookmarkedSubredditsListingFragment.setVisibility(View.VISIBLE);
                mGlide.load(R.drawable.error_image).into(binding.noBookmarksImageViewBookmarkedSubredditsListingFragment);
            } else {
                binding.noBookmarksLinearLayoutBookmarkedSubredditsListingFragment.setVisibility(View.GONE);
                binding.recyclerViewBookmarkedSubredditsListingFragment.setVisibility(View.VISIBLE);
                mGlide.clear(binding.noBookmarksImageViewBookmarkedSubredditsListingFragment);
            }

            adapter.setBookmarkedSubreddits(bookmarkedSubredditData);
        });

        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (BaseActivity) context;
    }

    @Override
    public void stopRefreshProgressbar() {
        // No refresh for bookmarks
    }

    @Override
    public void applyTheme() {
        binding.swipeRefreshLayoutBookmarkedSubredditsListingFragment.setProgressBackgroundColorSchemeColor(mCustomThemeWrapper.getCircularProgressBarBackground());
        binding.swipeRefreshLayoutBookmarkedSubredditsListingFragment.setColorSchemeColors(mCustomThemeWrapper.getColorAccent());
        binding.errorTextViewBookmarkedSubredditsListingFragment.setTextColor(mCustomThemeWrapper.getSecondaryTextColor());
        if (mActivity.typeface != null) {
            binding.errorTextViewBookmarkedSubredditsListingFragment.setTypeface(mActivity.contentTypeface);
        }
    }

    public void goBackToTop() {
        if (mLinearLayoutManager != null) {
            mLinearLayoutManager.scrollToPositionWithOffset(0, 0);
        }
    }

    public void changeSearchQuery(String searchQuery) {
        mBookmarkedSubredditViewModel.setSearchQuery(searchQuery);
    }
}
