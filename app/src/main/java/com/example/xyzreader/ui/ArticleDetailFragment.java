package com.example.xyzreader.ui;

import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ShareCompat;
import android.support.v7.graphics.Palette;
import android.text.Html;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.example.xyzreader.R;
import com.example.xyzreader.data.Article;
import com.example.xyzreader.ui.activities.ArticleDetailActivity;
import com.example.xyzreader.ui.activities.ArticleListActivity;

import org.markdown4j.Markdown4jProcessor;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * A fragment representing a single Article detail screen. This fragment is
 * either contained in a {@link ArticleListActivity} in two-pane mode (on
 * tablets) or a {@link ArticleDetailActivity} on handsets.
 */
public class ArticleDetailFragment extends Fragment {
    private static final String TAG = "ArticleDetailFragment";
    private static final String ARTICLE = "article";
    private static final int BODY_CHUNK_SIZE = 1024;
    private static final int PRE_LOAD_TRIGGER_OFFSET = 256;

    private static final float PARALLAX_FACTOR = 1.25f;

    private Article mArticle;
    private int mBodyChunkId;
    private View mRootView;
    private int mMutedColor = 0xFF333333;
    private ColorDrawable mStatusBarColorDrawable;
    private int mTopInset;

    private int mScrollY;
    private boolean mIsCard = false;
    private int mStatusBarFullOpacityBottom;

    @BindView(R.id.photo_container) View mPhotoContainerView;
    @BindView(R.id.scrollview) ObservableScrollView mScrollView;
    @BindView(R.id.photo) ImageView mPhotoView;
    @BindView(R.id.draw_insets_frame_layout) DrawInsetsFrameLayout mDrawInsetsFrameLayout;
    @BindView(R.id.article_title) TextView mTitleView;
    @BindView(R.id.article_byline) TextView mByLineView;
    @BindView(R.id.body_chunk_loader_progressbar) ProgressBar mBodyChunkLoader;
    @BindView(R.id.article_body) TextView mBodyView;
    @BindView(R.id.share_fab) FloatingActionButton mShareFab;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ArticleDetailFragment() {
    }

    public static ArticleDetailFragment newInstance(@NonNull Article article) {
        Bundle arguments = new Bundle();
        ArticleDetailFragment fragment;

        fragment = new ArticleDetailFragment();
        arguments.putParcelable(ARTICLE, article);
        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(ARTICLE)) {
            mBodyChunkId = 0;
            mArticle = getArguments().getParcelable(ARTICLE);
        }

        mIsCard = getResources().getBoolean(R.bool.detail_is_card);
        mStatusBarFullOpacityBottom = getResources().getDimensionPixelSize(
                R.dimen.detail_card_top_margin);
        setHasOptionsMenu(true);
    }

    public ArticleDetailActivity getActivityCast() {
        return (ArticleDetailActivity) getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_article_detail, container, false);
        ButterKnife.bind(this, mRootView);

        // Fine tune views
        mByLineView.setMovementMethod(new LinkMovementMethod());
        mBodyView.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "literata-regular.ttf"));
        mStatusBarColorDrawable = new ColorDrawable(0);

        // Load article
        mTitleView.setText(mArticle.getTitle());
        Date publishedDate = parsePublishedDate();
        if (!publishedDate.before(START_OF_EPOCH.getTime())) {
            mByLineView.setText(Html.fromHtml(
                    DateUtils.getRelativeTimeSpanString(
                            publishedDate.getTime(),
                            System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_ALL).toString()
                            + " by <font color='#ffffff'>"
                            + mArticle.getAuthor()
                            + "</font>"));

        } else {
            // If date is before 1902, just show the string
            mByLineView.setText(Html.fromHtml(
                    outputFormat.format(publishedDate) + " by <font color='#ffffff'>"
                            + mArticle.getAuthor()
                            + "</font>"));

        }
        if (loadMarkdownTextChunkIntoBody(mArticle.getBody(), mBodyChunkId) == false) {
            mBodyChunkLoader.setVisibility(View.GONE);
        }
        mBodyChunkId++;
        ImageLoaderHelper.getInstance(getActivity()).getImageLoader()
                .get(mArticle.getPhotoUrl(), new ImageLoader.ImageListener() {
                    @Override
                    public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b) {
                        Bitmap bitmap = imageContainer.getBitmap();
                        if (bitmap != null) {
                            Palette p = Palette.generate(bitmap, 12);
                            mMutedColor = p.getDarkMutedColor(0xFF333333);
                            mPhotoView.setImageBitmap(imageContainer.getBitmap());
                            mRootView.findViewById(R.id.meta_bar)
                                    .setBackgroundColor(mMutedColor);
                            updateStatusBar();
                        }
                    }

                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
                        //No-Op
                    }
                });

        // Callbacks region
        mDrawInsetsFrameLayout.setOnInsetsCallback(new DrawInsetsFrameLayout.OnInsetsCallback() {
            @Override
            public void onInsetsChanged(Rect insets) {
                mTopInset = insets.top;
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mScrollView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                @Override
                public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                    if (oldScrollY - scrollY < 0) mShareFab.hide();
                    else mShareFab.show();
                }
            });
        }
        mShareFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(Intent.createChooser(ShareCompat.IntentBuilder.from(getActivity())
                        .setType("text/plain")
                        .setText("Some sample text")
                        .getIntent(), getString(R.string.action_share)));
            }
        });
        mScrollView.setCallbacks(new ObservableScrollView.Callbacks() {
            @Override
            public void onScrollChanged() {
                mScrollY = mScrollView.getScrollY();
                getActivityCast().onUpButtonFloorChanged(mArticle.getId(), ArticleDetailFragment.this);
                mPhotoContainerView.setTranslationY((int) (mScrollY - mScrollY / PARALLAX_FACTOR));
                updateStatusBar();

                // Check if we require to load a new body chunk
                if (mBodyChunkLoader.isShown() && mBodyChunkLoader.getTop() <= (mScrollY + mBodyView.getTop() + PRE_LOAD_TRIGGER_OFFSET)) {
                    if (loadMarkdownTextChunkIntoBody(mArticle.getBody(), mBodyChunkId) == false) {
                        mBodyChunkLoader.setVisibility(View.GONE);
                    } else {
                        Log.d(TAG, String.format("Loaded body chunk %d", mBodyChunkId));
                        mBodyChunkId++;
                    }
                }
            }
        });

        return mRootView;
    }

    private void updateStatusBar() {
        int color = 0;
        if (mPhotoView != null && mTopInset != 0 && mScrollY > 0) {
            float f = progress(mScrollY,
                    mStatusBarFullOpacityBottom - mTopInset * 3,
                    mStatusBarFullOpacityBottom - mTopInset);
            color = Color.argb((int) (255 * f),
                    (int) (Color.red(mMutedColor) * 0.9),
                    (int) (Color.green(mMutedColor) * 0.9),
                    (int) (Color.blue(mMutedColor) * 0.9));
        }
        mStatusBarColorDrawable.setColor(color);
        mDrawInsetsFrameLayout.setInsetBackground(mStatusBarColorDrawable);
    }

    static float progress(float v, float min, float max) {
        return constrain((v - min) / (max - min), 0, 1);
    }

    static float constrain(float val, float min, float max) {
        if (val < min) {
            return min;
        } else if (val > max) {
            return max;
        } else {
            return val;
        }
    }

    private Date parsePublishedDate() {
        try {
            String date = mArticle.getPublishedDate();
            return dateFormat.parse(date);
        } catch (ParseException ex) {
            Log.e(TAG, ex.getMessage());
            Log.i(TAG, "passing today's date");
            return new Date();
        }
    }

    public int getUpButtonFloor() {
        if (mPhotoContainerView == null || mPhotoView.getHeight() == 0) {
            return Integer.MAX_VALUE;
        }

        // account for parallax
        return mIsCard
                ? (int) mPhotoContainerView.getTranslationY() + mPhotoView.getHeight() - mScrollY
                : mPhotoView.getHeight() - mScrollY;
    }

    private boolean loadMarkdownTextChunkIntoBody(@NonNull String fullText, int chunkId) {
        boolean areThereStillChunkLeft = true;
        Markdown4jProcessor mdProcessor;
        int textStartPosition;
        String textToAdd;
        Spanned htmlSpannedText;

        mdProcessor = new Markdown4jProcessor();
        textStartPosition = chunkId * BODY_CHUNK_SIZE;
        textToAdd = fullText.substring(textStartPosition, textStartPosition + BODY_CHUNK_SIZE);
        try {
            // TODO: treat cross-chunk markdown formatting
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                htmlSpannedText = Html.fromHtml(mdProcessor.process(textToAdd), Html.FROM_HTML_MODE_COMPACT);
            } else {
                htmlSpannedText = Html.fromHtml(mdProcessor.process(textToAdd));
            }

            // TODO: eliminate the new line inserted after loading a chunk. It could be that
            // FROM_HTML_MODE_COMPACT resolves this.
            mBodyView.append(htmlSpannedText);
            areThereStillChunkLeft = (mBodyView.getText().length() != fullText.length());
        } catch (IOException ignored) {
        }

        return areThereStillChunkLeft;
    }
}
