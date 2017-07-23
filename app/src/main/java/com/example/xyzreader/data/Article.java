package com.example.xyzreader.data;

import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

public class Article implements Parcelable {
    private long mId;
    private String mTitle;
    private String mPublishedDate;
    private String mAuthor;
    private String mThumbUrl;
    private String mPhotoUrl;
    private float mAspectRatio;
    private String mBodyText;

    private Article() {
        // No-Op
    }
    private Article(Parcel in) {
        this.mId = in.readLong();
        this.mTitle = in.readString();
        this.mPublishedDate = in.readString();
        this.mAuthor = in.readString();
        this.mThumbUrl = in.readString();
        this.mPhotoUrl = in.readString();
        this.mAspectRatio = in.readFloat();
        this.mBodyText = in.readString();
    }

    public static Article getFromCursor(@NonNull Cursor cursor) {
        Article article = new Article();

        article.mId = cursor.getLong(ArticleLoader.Query._ID);
        article.mTitle = cursor.getString(ArticleLoader.Query.TITLE);
        article.mPublishedDate = cursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
        article.mAuthor = cursor.getString(ArticleLoader.Query.AUTHOR);
        article.mThumbUrl = cursor.getString(ArticleLoader.Query.THUMB_URL);
        article.mPhotoUrl = cursor.getString(ArticleLoader.Query.PHOTO_URL);
        article.mAspectRatio = cursor.getFloat(ArticleLoader.Query.ASPECT_RATIO);
        article.mBodyText = cursor.getString(ArticleLoader.Query.BODY);

        return article;
    }

    public long getId() {
        return mId;
    }
    public String getPublishedDate() {
        return mPublishedDate;
    }
    public String getTitle() {
        return mTitle;
    }
    public String getAuthor() {
        return mAuthor;
    }

    public String getPhotoUrl() {
        return mPhotoUrl;
    }
    public String getBody() {
        return mBodyText;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mId);
        dest.writeString(mTitle);
        dest.writeString(mPublishedDate);
        dest.writeString(mAuthor);
        dest.writeString(mThumbUrl);
        dest.writeString(mPhotoUrl);
        dest.writeFloat(mAspectRatio);
        dest.writeString(mBodyText);
    }

    public static final Parcelable.Creator CREATOR = new Parcelable.Creator<Article>() {

        @Override
        public Article createFromParcel(Parcel source) {
            return new Article(source);
        }

        @Override
        public Article[] newArray(int size) {
            return new Article[size];
        }
    };
}
