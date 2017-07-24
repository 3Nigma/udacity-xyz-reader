package com.example.xyzreader.ui;

import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import android.support.v4.widget.NestedScrollView;
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
import android.widget.LinearLayout;
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

    private Article mArticle;
    private int mBodyChunkId;
    private View mRootView;
    private int mMutedColor = 0xFF333333;

    @BindView(R.id.article_detail_scrollview) NestedScrollView mScrollView;
    @BindView(R.id.photo) ImageView mPhotoView;
    @BindView(R.id.share_fab) FloatingActionButton mShareFab;
    @BindView(R.id.article_detail_content_holder) LinearLayout mContentHolder;
    @BindView(R.id.article_title) TextView mTitleView;
    @BindView(R.id.article_byline) TextView mByLineView;
    @BindView(R.id.article_body) TextView mBodyView;
    @BindView(R.id.body_chunk_loader_progressbar) ProgressBar mBodyChunkLoader;

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
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_article_detail, container, false);
        ButterKnife.bind(this, mRootView);

        // Fine tune views
        mByLineView.setMovementMethod(new LinkMovementMethod());
        mBodyView.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "literata-regular.ttf"));

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
                        }
                    }

                    @Override
                    public void onErrorResponse(VolleyError volleyError) {
                        //No-Op
                    }
                });

        // Callbacks region
        mShareFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(Intent.createChooser(ShareCompat.IntentBuilder.from(getActivity())
                        .setType("text/plain")
                        .setText("Some sample text")
                        .getIntent(), getString(R.string.action_share)));
            }
        });
        mScrollView.setOnScrollChangeListener(new NestedScrollView.OnScrollChangeListener() {
            @Override
            public void onScrollChange(NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                // Check if we require to load a new body chunk
                if (mBodyChunkLoader.isShown() && mBodyChunkLoader.getTop() - scrollY - mScrollView.getHeight() < PRE_LOAD_TRIGGER_OFFSET) {
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
