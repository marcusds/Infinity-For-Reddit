package ml.docilealligator.infinityforreddit.adapters;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import me.zhanghai.android.fastscroll.PopupTextProvider;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.activities.ViewSubredditDetailActivity;
import ml.docilealligator.infinityforreddit.bookmarkedsubreddit.BookmarkedSubredditData;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemSubscribedThingBinding;

public class BookmarkedSubredditsRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements PopupTextProvider {
    private final BaseActivity mActivity;
    private final Executor mExecutor;
    private final RedditDataRoomDatabase mRedditDataRoomDatabase;
    private List<BookmarkedSubredditData> mBookmarkedSubredditData;
    private final RequestManager glide;
    private final String accountName;
    private final int primaryTextColor;
    private final int secondaryTextColor;

    public BookmarkedSubredditsRecyclerViewAdapter(BaseActivity activity, Executor executor,
                                                   RedditDataRoomDatabase redditDataRoomDatabase,
                                                   CustomThemeWrapper customThemeWrapper,
                                                   @NonNull String accountName) {
        mActivity = activity;
        mExecutor = executor;
        glide = Glide.with(activity);
        mRedditDataRoomDatabase = redditDataRoomDatabase;
        this.accountName = accountName;
        this.mBookmarkedSubredditData = new ArrayList<>();
        primaryTextColor = customThemeWrapper.getPrimaryTextColor();
        secondaryTextColor = customThemeWrapper.getSecondaryTextColor();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        return new BookmarkedSubredditViewHolder(ItemSubscribedThingBinding
                .inflate(LayoutInflater.from(viewGroup.getContext()), viewGroup, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        if (viewHolder instanceof BookmarkedSubredditViewHolder) {
            BookmarkedSubredditData subreddit = mBookmarkedSubredditData.get(position);
            String name = subreddit.getName();
            String iconUrl = subreddit.getIconUrl();

            ((BookmarkedSubredditViewHolder) viewHolder).binding.thingNameTextViewItemSubscribedThing.setText(name);
            ((BookmarkedSubredditViewHolder) viewHolder).binding.thingNameTextViewItemSubscribedThing.setTextColor(primaryTextColor);

            if (iconUrl != null && !iconUrl.isEmpty()) {
                glide.load(iconUrl)
                        .apply(RequestOptions.bitmapTransform(new RoundedCornersTransformation(72, 0)))
                        .error(glide.load(R.drawable.subreddit_default_icon)
                                .apply(RequestOptions.bitmapTransform(new RoundedCornersTransformation(72, 0))))
                        .into(((BookmarkedSubredditViewHolder) viewHolder).binding.thingIconGifImageViewItemSubscribedThing);
            } else {
                glide.load(R.drawable.subreddit_default_icon)
                        .apply(RequestOptions.bitmapTransform(new RoundedCornersTransformation(72, 0)))
                        .into(((BookmarkedSubredditViewHolder) viewHolder).binding.thingIconGifImageViewItemSubscribedThing);
            }

            // Show bookmark icon (filled) since these are bookmarked items
            ((BookmarkedSubredditViewHolder) viewHolder).binding.favoriteImageViewItemSubscribedThing.setImageResource(R.drawable.ic_bookmark_day_night_24dp);
            ((BookmarkedSubredditViewHolder) viewHolder).binding.favoriteImageViewItemSubscribedThing.setColorFilter(primaryTextColor);

            // Remove bookmark when clicked
            ((BookmarkedSubredditViewHolder) viewHolder).binding.favoriteImageViewItemSubscribedThing.setOnClickListener(view -> {
                int adapterPosition = viewHolder.getBindingAdapterPosition();
                if (adapterPosition >= 0 && adapterPosition < mBookmarkedSubredditData.size()) {
                    BookmarkedSubredditData toRemove = mBookmarkedSubredditData.get(adapterPosition);
                    mExecutor.execute(() -> {
                        mRedditDataRoomDatabase.bookmarkedSubredditDao().deleteBookmarkedSubreddit(toRemove.getName(), accountName);
                    });
                    mBookmarkedSubredditData.remove(adapterPosition);
                    notifyItemRemoved(adapterPosition);
                }
            });

            // Open subreddit on item click
            viewHolder.itemView.setOnClickListener(view -> {
                Intent intent = new Intent(mActivity, ViewSubredditDetailActivity.class);
                intent.putExtra(ViewSubredditDetailActivity.EXTRA_SUBREDDIT_NAME_KEY, name);
                mActivity.startActivity(intent);
            });

            // Open subreddit on long click
            viewHolder.itemView.setOnLongClickListener(view -> {
                Intent intent = new Intent(mActivity, ViewSubredditDetailActivity.class);
                intent.putExtra(ViewSubredditDetailActivity.EXTRA_SUBREDDIT_NAME_KEY, name);
                mActivity.startActivity(intent);
                return true;
            });
        }
    }

    @Override
    public int getItemCount() {
        return mBookmarkedSubredditData == null ? 0 : mBookmarkedSubredditData.size();
    }

    @NonNull
    @Override
    public String getPopupText(@NonNull View view, int position) {
        if (mBookmarkedSubredditData != null && position >= 0 && position < mBookmarkedSubredditData.size()) {
            String name = mBookmarkedSubredditData.get(position).getName();
            if (name != null && !name.isEmpty()) {
                return name.substring(0, 1).toUpperCase();
            }
        }
        return "";
    }

    public void setBookmarkedSubreddits(List<BookmarkedSubredditData> bookmarkedSubreddits) {
        this.mBookmarkedSubredditData = bookmarkedSubreddits == null ? new ArrayList<>() : bookmarkedSubreddits;
        notifyDataSetChanged();
    }

    class BookmarkedSubredditViewHolder extends RecyclerView.ViewHolder {
        ItemSubscribedThingBinding binding;

        BookmarkedSubredditViewHolder(@NonNull ItemSubscribedThingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
