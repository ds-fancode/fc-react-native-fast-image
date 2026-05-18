package com.dylanvann.fastimage;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.request.Request;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.facebook.react.bridge.ReadableMap;
import com.dylanvann.fastimage.events.FastImageErrorEvent;
import com.dylanvann.fastimage.events.FastImageLoadStartEvent;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIManagerHelper;
import com.facebook.react.uimanager.events.EventDispatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.os.Build;
import android.util.Log;

class FastImageViewWithUrl extends AppCompatImageView {
    private static final String TAG = "FastImageViewWithUrl";
    private boolean mNeedsReload = false;
    private ReadableMap mSource = null;
    private Drawable mDefaultSource = null;
    private int mBlurRadius = 0;
    private int mBlurRadiusPrevious = 0;

    /**
     * URL of the most recently STARTED Glide request (set right before clearView+into).
     * Used to skip redundant cancel-restart cycles caused by React Native re-renders that
     * recreate the source object even when the URL and SR flag haven't changed.
     */
    @Nullable
    private String mActiveLoadUrl = null;
    /** SR flag that was active for the most recently started Glide request. */
    private boolean mActiveLoadSr = false;

    /** Once: explain why transform/infer never appear when model is READY. */
    private static volatile boolean sLoggedWhyNoSrOnSource;

    public GlideUrl glideUrl;
    private String mTransition = "none"; // "none" | "fade"

    public FastImageViewWithUrl(Context context) {
        super(context);
    }

    public void setSource(@Nullable ReadableMap source) {
        mNeedsReload = true;
        mSource = source;
    }

    public void setDefaultSource(@Nullable Drawable source) {
        mNeedsReload = true;
        mDefaultSource = source;
    }

    public void setBlurRadius(@Nullable Integer blurRadius) {
        mNeedsReload = true;
        mBlurRadiusPrevious = mBlurRadius;
        mBlurRadius = blurRadius == null ? 0 : blurRadius;
    }

    public void setTransition(@Nullable String transition) {
        mNeedsReload = true;
        if (transition == null) {
            mTransition = "none";
        } else {
            mTransition = transition;
        }
    }

    private boolean isNullOrEmpty(final String url) {
        return url == null || url.trim().isEmpty();
    }

    @SuppressLint("CheckResult")
    public void onAfterUpdate(
            @NonNull FastImageViewManager manager,
            @Nullable RequestManager requestManager,
            @NonNull Map<String, List<FastImageViewWithUrl>> viewsForUrlsMap) {
        FastImageSrLog.i("load_dbg", "onAfterUpdate needsReload=" + mNeedsReload
                + " hasSource=" + (mSource != null)
                + " requestMgr=" + (requestManager != null));
        if (!mNeedsReload)
            return;

        if ((mSource == null ||
                !mSource.hasKey("uri") ||
                isNullOrEmpty(mSource.getString("uri"))) &&
                mDefaultSource == null) {

            // Cancel existing requests.
            clearView(requestManager);

            if (glideUrl != null) {
                FastImageOkHttpProgressGlideModule.forget(glideUrl.toStringUrl());
            }

            // Clear the image.
            setImageDrawable(null);

            ThemedReactContext context = (ThemedReactContext) getContext();
            EventDispatcher dispatcher = UIManagerHelper.getEventDispatcherForReactTag(context, getId());
            int surfaceId = UIManagerHelper.getSurfaceId(this);
            FastImageErrorEvent event = new FastImageErrorEvent(surfaceId, getId(), mSource);
            if (dispatcher != null) {
                dispatcher.dispatchEvent(event);
            }
            return;
        }

        //final GlideUrl glideUrl = FastImageViewConverter.getGlideUrl(view.getContext(), mSource);
        final FastImageSource imageSource = FastImageViewConverter.getImageSource(getContext(), mSource);

        if (imageSource != null && imageSource.getUri().toString().length() == 0) {
            ThemedReactContext context = (ThemedReactContext) getContext();
            EventDispatcher dispatcher = UIManagerHelper.getEventDispatcherForReactTag(context, getId());
            int surfaceId = UIManagerHelper.getSurfaceId(this);
            FastImageErrorEvent event = new FastImageErrorEvent(surfaceId, getId(), mSource);

            if (dispatcher != null) {
                dispatcher.dispatchEvent(event);
            }
            // Cancel existing requests.
            clearView(requestManager);

            if (glideUrl != null) {
                FastImageOkHttpProgressGlideModule.forget(glideUrl.toStringUrl());
            }
            // Clear the image.
            setImageDrawable(null);
            return;
        }

        // `imageSource` may be null and we still continue, if `defaultSource` is not null
        final GlideUrl glideUrl = imageSource == null ? null : imageSource.getGlideUrl();
        String key = glideUrl == null ? null : glideUrl.toStringUrl();
        boolean applySr = FastImageViewConverter.shouldApplySuperResolution(mSource);

        // Skip cancel-restart when URL and SR flag are unchanged. React Native re-renders
        // caused by state changes (setLoading, setImageSize, onLoadEnd) recreate the source
        // object on every render, triggering setSource → onAfterUpdate even when nothing
        // meaningful changed. Without this guard, the in-flight SR request (~500ms) would be
        // cancelled and clearView would blank the ImageView, making images appear permanently blank.
        if (key != null && key.equals(mActiveLoadUrl) && applySr == mActiveLoadSr) {
            FastImageSrLog.i("load_dbg", "skip restart — url/sr unchanged");
            mNeedsReload = false;
            return;
        }
        // Cancel existing request (URL or SR flag actually changed).
        this.glideUrl = glideUrl;
        clearView(requestManager); // resets mActiveLoadUrl; set it again below after clearing

        // Record new active URL/SR so subsequent re-renders (triggered by onLoadStart /
        // onLoadEnd state changes) hit the skip-guard above and don't cancel this request.
        mActiveLoadUrl = key;
        mActiveLoadSr = applySr;

        if (glideUrl != null) {
            FastImageOkHttpProgressGlideModule.expect(key, manager);
            List<FastImageViewWithUrl> viewsForKey = viewsForUrlsMap.get(key);
            if (viewsForKey != null && !viewsForKey.contains(this)) {
                viewsForKey.add(this);
            } else if (viewsForKey == null) {
                List<FastImageViewWithUrl> newViewsForKeys = new ArrayList<>(Collections.singletonList(this));
                viewsForUrlsMap.put(key, newViewsForKeys);
            }
        }

        ThemedReactContext context = (ThemedReactContext) getContext();
        if (imageSource != null) {
            // This is an orphan even without a load/loadend when only loading a placeholder
            // This is an orphan event without a load/loadend when only loading a placeholder
            EventDispatcher dispatcher = UIManagerHelper.getEventDispatcherForReactTag(context, getId());
            int surfaceId = UIManagerHelper.getSurfaceId(this);
            FastImageLoadStartEvent event = new FastImageLoadStartEvent(surfaceId, getId());

            if (dispatcher != null) {
                dispatcher.dispatchEvent(event);
            }
        }

        if (requestManager != null) {
            RequestBuilder<? extends Drawable> builder;
            Map<String, Object> builderOptions = new HashMap<>();
            builderOptions.put("view", this);
            builderOptions.put("blurRadius", mBlurRadius);
            builderOptions.put("blurRadiusShouldClean", mBlurRadiusPrevious > 0 && mBlurRadius <= 0);

            try {
                builder = requestManager
                        .load(imageSource == null ? null : imageSource.getSourceForLoad())
                        .apply(FastImageViewConverter
                                .getOptions(context, imageSource, mSource, builderOptions)
                                .placeholder(mDefaultSource) // show until loaded
                                .fallback(mDefaultSource)); // null will not be treated as error

                String shortUrl = FastImageSrUiFeedback.truncateForUi(key, 80);
                if (applySr) {
                    FastImageSrLog.i("load", "SR=ON  url=" + shortUrl);
                    builder = builder.transform(new SuperResolutionTransformation(key));
                } else {
                    FastImageSrLog.i("load", "SR=OFF url=" + shortUrl);
                }

                if (key != null) {
                    builder.listener(new FastImageRequestListener(key));
                }

                if ("fade".equals(mTransition)) {
                    builder = builder.transition(DrawableTransitionOptions.withCrossFade());
                }

                builder.into(this);
                maybeLogWhyNoSrTransform();
            } catch (Exception e) {
                Log.e(TAG, String.format("Error detecting image type for URI: %s. Exception: %s",
                imageSource != null ? imageSource.getUri().toString() : "null", e.getMessage()), e);
            }
        }
    }

    /**
     * If you only see init/pipeline logs but never transform/infer: JS must pass
     * {@code superResolution: true} on the FastImage source. That only happens when the
     * intelligent-image URL actually changes (see fc-ui-components Image).
     */
    private void maybeLogWhyNoSrTransform() {
        if (sLoggedWhyNoSrOnSource) {
            return;
        }
        if (!FastImageSuperResolution.getInstance().isAvailable()) {
            return;
        }
        if (mSource == null) {
            return;
        }
        if (FastImageViewConverter.shouldApplySuperResolution(mSource)) {
            return;
        }
        sLoggedWhyNoSrOnSource = true;
        try {
            if (!mSource.hasKey("superResolution")) {
                FastImageSrLog.w(
                        "load",
                        "Model READY but source has no superResolution key — "
                                + "no transform/infer. JS sets it only when intelligent-image URL "
                                + "rewrites (optimised URL ≠ original). Check image host vs imgDefaultPatterns.");
            } else if (!mSource.getBoolean("superResolution")) {
                FastImageSrLog.i("load", "superResolution=false on source — SR transform skipped");
            }
        } catch (Exception ignored) {
        }
    }

    public void clearView(@Nullable RequestManager requestManager) {
        // requestManager.clear(view) is always safe to call, even with no pending request.
        // The old condition `getTag() instanceof Request` is always false in Glide 4.12+
        // because Glide uses view.setTag(R.id.glide_custom_view_target_tag, …), not the
        // default tag slot — so cancellation was silently skipped.
        if (requestManager != null) {
            FastImageSrLog.i("load_dbg",
                    "clearView — cancelling glide request for viewId=" + getId()
                    + " activeUrl=" + (mActiveLoadUrl != null ? mActiveLoadUrl.substring(0, Math.min(60, mActiveLoadUrl.length())) : "null"));
            requestManager.clear(this);
        }
        mActiveLoadUrl = null;
        mActiveLoadSr = false;
    }
}
