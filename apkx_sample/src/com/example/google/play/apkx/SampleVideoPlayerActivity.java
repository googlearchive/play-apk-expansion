
package com.example.google.play.apkx;

/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * limitations under the License.
 */

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Unlike most of the rest of this example, this class is only really guaranteed
 * to work well on Froyo devices or above. This class tries to do the right
 * thing with a video player that is constrained to landscape even if its parent
 * is not so constrained. This can lead to some interesting state transitions in
 * Android.
 */
public class SampleVideoPlayerActivity extends Activity implements OnPreparedListener {

    private Uri mMediaUri;
    private String mMediaTitle;
    private VideoView mVideoView;
    private View mPlayerLayout;
    private Method mSetSystemUiVisibility;
    private Method mSetSystemUiVisibilityListener;
    private int mSavedPosition;
    private boolean mIgnoreSystemUIChange;
    private boolean mResumeVideoPlaybackOnResume;
    private boolean mStartVideoOnCreate;
    private boolean mHasFocus;
    private boolean mSystemKeysShowing;
    private Handler mHandler;
    private MediaController mMediaController;

    static final private String SAVE_VIDEO_POSITION = "POS";
    static final private String RESUME_VIDEO_ON_RESUME = "RES";
    protected static final int MSG_HIDE_SYSTEM_UI = 1;
    static final private String LOG_TAG = "SampleVideo";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /**
         * We create a handler here so that we can delay hiding the system Ui
         * for a fraction of a second.
         */
        mHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_HIDE_SYSTEM_UI:
                        hideSystemUi();
                        break;
                }
                super.handleMessage(msg);
            }

        };

        /**
         * We use a combination of getLastNonConfigurationInstance and saved
         * instance state to make sure we resume under a variety of cases.
         */
        if (null == savedInstanceState) {
            savedInstanceState = (Bundle) getLastNonConfigurationInstance();
        }
        if (null != savedInstanceState) {
            mResumeVideoPlaybackOnResume = savedInstanceState.getBoolean(RESUME_VIDEO_ON_RESUME,
                    false);
            mStartVideoOnCreate = false;
        } else {
            mStartVideoOnCreate = true;
            mSavedPosition = -1;
        }
        setContentView(R.layout.videoplayer);

        mPlayerLayout = this.findViewById(R.id.PlayerLayout);

        mMediaUri = this.getIntent().getData();
        mMediaTitle = this.getIntent().getStringExtra(Intent.EXTRA_TITLE);
        ((TextView) findViewById(R.id.NowPlaying)).setText(mMediaTitle);
        mVideoView = (VideoView) findViewById(R.id.VideoView);

        /**
         * This code will hide the system UI when the Media Controller is hidden
         * on ICS devices.
         */
        mMediaController = new MediaController(this) {

            /**
             * Whenever we hide the MediaController, we also hide the system UI
             * a fraction of a second later.
             */
            @Override
            public void hide() {
                super.hide();
                postHideSystemUi();
            }

            /**
             * If we are on an ICS device, we don't want the back key to just
             * dismiss the media controller, because that will also hide the
             * system keys, so the user effectively cannot back out of the app
             * using the back key. Since the media controller times out (or
             * exits when the user taps on a part of the screen that is not the
             * media controller) This seems to be a reasonable compromise
             */
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (mSystemKeysShowing && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    finish();
                }
                return super.dispatchKeyEvent(event);
            }

        };

        /**
         * Set up the use of reflection to hide system UI and use a Proxy to get
         * notified when the visibility changes. Once we are playing the video,
         * we hide the system UI.
         */
        try {
            mSetSystemUiVisibility = View.class.getMethod("setSystemUiVisibility", int.class);
            Class<?> onChangeListener = Class
                    .forName("android.view.View$OnSystemUiVisibilityChangeListener");
            mSetSystemUiVisibilityListener = View.class.getMethod(
                    "setOnSystemUiVisibilityChangeListener", onChangeListener);
            ClassLoader cl = onChangeListener.getClassLoader();
            InvocationHandler h = new InvocationHandler() {

                /**
                 * This gets called when the view calls the
                 * OnSystemUiVisibilityChangeListener. Since there is only one
                 * method in this class, we don't bother to look at the Method
                 * parameter.
                 */
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if (mIgnoreSystemUIChange) {
                        mIgnoreSystemUIChange = false;
                        return null;
                    }
                    switch (((Integer) args[0]).intValue()) {
                        case View.SYSTEM_UI_FLAG_LOW_PROFILE:
                            mMediaController.show();
                            mSystemKeysShowing = true;
                            break;
                        case View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LOW_PROFILE:
                        case View.SYSTEM_UI_FLAG_HIDE_NAVIGATION:
                            mSystemKeysShowing = false;
                            break;
                    }
                    return null;
                }
            };

            Object o = Proxy.newProxyInstance(cl, new Class[] {
                    onChangeListener
            }, h);
            mSetSystemUiVisibilityListener.invoke(mPlayerLayout, o);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        mVideoView.setVideoURI(mMediaUri);

        mVideoView.setMediaController(mMediaController);
        mVideoView.requestFocus();
        mVideoView.setOnPreparedListener(this);

        hideSystemUi();
    }

    /**
     * We both resume and start the video, depending on the state.
     */
    private void resumeVideoIfNecessary() {
        if (mResumeVideoPlaybackOnResume && mHasFocus) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
                mVideoView.resume();
            }
            if (!mVideoView.isPlaying()) {
                mVideoView.start();
            }
            mResumeVideoPlaybackOnResume = false;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        mHasFocus = hasFocus;
        resumeVideoIfNecessary();
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    protected void onPause() {
        if (mHasFocus) {
            mResumeVideoPlaybackOnResume = mVideoView.isPlaying();
        }
        int savedPosition = mVideoView.getCurrentPosition();
        // if not active, video view will return 0 for the saved position
        if (0 != savedPosition) {
            mSavedPosition = savedPosition;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            mVideoView.suspend();
        }
        super.onPause();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        Bundle outState = new Bundle();
        saveInstance(outState);
        return super.onRetainNonConfigurationInstance();
    }

    private void saveInstance(Bundle outState) {
        int videoPosition = mVideoView.getCurrentPosition();
        if (-1 == mSavedPosition) {
            mSavedPosition = videoPosition;
        }
        outState.putInt(SAVE_VIDEO_POSITION, mSavedPosition);
        outState.putBoolean(RESUME_VIDEO_ON_RESUME, mResumeVideoPlaybackOnResume);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveInstance(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        resumeVideoIfNecessary();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler = null;
        mMediaController = null;
        if (null != mSetSystemUiVisibilityListener) {
            try {
                mSetSystemUiVisibilityListener.invoke(mPlayerLayout, new Object[] {
                        null
                });
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    /**
     * We want to avoid a race condition where the system UI doesn't end up
     * getting hidden because the touch event gets there before the call to hide
     * the UI gets there.
     */
    private void postHideSystemUi() {
        if (null != mSetSystemUiVisibility && null != mHandler) {
            mHandler.sendEmptyMessageDelayed(MSG_HIDE_SYSTEM_UI, 100);
        }
    }

    /**
     * Use reflection to hide the system UI.
     */
    private void hideSystemUi() {
        Log.d(LOG_TAG, "Hiding System Ui");
        if (null != mSetSystemUiVisibility) {
            try {
                mSetSystemUiVisibility.invoke(mPlayerLayout, View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
                mIgnoreSystemUIChange = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * If we are being restarted from a paused state, don't start playing.
     */
    public void onPrepared(MediaPlayer mp) {
        mp.start();
        if (!mStartVideoOnCreate) {
            mp.pause();
        }
        if (-1 != mSavedPosition) {
            mp.seekTo(mSavedPosition);
            mSavedPosition = -1;
        }
        hideSystemUi();
    }
}
