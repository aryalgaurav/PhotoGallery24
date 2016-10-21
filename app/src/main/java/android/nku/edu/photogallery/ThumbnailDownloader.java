package android.nku.edu.photogallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.LruCache;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by Gaurav on 10/19/2016.
 */
public class ThumbnailDownloader<T> extends HandlerThread {

    private static final String TAG = "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;
    private static final int MESSAGE_PRELOAD = 1;
    private boolean mHasQuit = false;

    private Handler mRequestHandler;
    private ConcurrentMap<T, String> mRequestMap = new ConcurrentHashMap<>();
    private Handler mResponseHandler;
    private ThumbnailDownloadListener<T> mThumbnailDownloadListener;
    private LruCache<String, Bitmap> mLruCache;
    private static final int CACHE_SIZE = 32 * 1024;

    public interface ThumbnailDownloadListener<T> {
        void onThumbnailDownloaded(T target, Bitmap bitmap);
    }

    public void setThumbnailDownloadListener(ThumbnailDownloadListener<T> listener) {
        mThumbnailDownloadListener = listener;
    }

    public ThumbnailDownloader(Handler responseHandler) {
        super(TAG);
        mResponseHandler = responseHandler;
        mLruCache = new LruCache<String, Bitmap>(CACHE_SIZE);
    }

    @Override
    protected void onLooperPrepared() {
        mRequestHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    T target = (T) msg.obj;
                    Log.i(TAG, "Got a request for URL: " + mRequestMap.get(target));
                    handleRequest(target);
                }
                else if (msg.what == MESSAGE_PRELOAD) {
                    String url = (String) msg.obj;
                    downloadImage(url);
                }
            }
        };
    }

    @Override
    public boolean quit() {
        mHasQuit = true;
        return super.quit();
    }

    public void queueThumbnail(T target, String url) {
        Log.i(TAG, "Got a URL: " + url);

        if (url == null) {
            mRequestMap.remove(target);
        }
        else {
            mRequestMap.put(target, url);
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD, target).sendToTarget();
        }
    }

    public void preloadImage(String url) {
        mRequestHandler.obtainMessage(MESSAGE_PRELOAD, url).sendToTarget();
    }

    public void clearCache() {
        mLruCache.evictAll();
    }

    public Bitmap getCachedImage(String url) {
        return mLruCache.get(url);
    }

    public void clearQueue() {
        mRequestHandler.removeMessages(MESSAGE_DOWNLOAD);
    }

    private void handleRequest(final T target) {
        final String url = mRequestMap.get(target);
        final Bitmap bitmap;
        if (url != null) {
        /*byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
            final Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);*/

            bitmap = downloadImage(url);
            Log.i(TAG, "Bitmap created");

            mResponseHandler.post(new Runnable() {
                @Override
                public void run() {
                    if ((mRequestMap.get(target) != url) || mHasQuit) {
                        return;
                    }
                    mRequestMap.remove(target);
                    mThumbnailDownloadListener.onThumbnailDownloaded(target, bitmap);
                }
            });
        }
    }

    private Bitmap downloadImage(String url) {
        Bitmap bitmap;

        if (url != null) {
            bitmap = mLruCache.get(url);

            if (bitmap == null) {
                try {
                    byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
                    bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
                    mLruCache.put(url, bitmap);
                    return bitmap;
                } catch (IOException ioe) {
                    Log.e(TAG, "Error downloading image.", ioe);
                    return null;
                }
            }
            else {
                return bitmap;
            }
        }
        return null;
    }
}