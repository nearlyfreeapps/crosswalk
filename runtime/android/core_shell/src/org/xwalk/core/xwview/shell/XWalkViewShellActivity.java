// Copyright (c) 2013-2014 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.core.xwview.shell;

import java.util.HashMap;

import android.app.Activity;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.drawable.ClipDrawable;
import android.os.Bundle;
import android.os.Looper;
import android.os.MessageQueue;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import org.chromium.base.BaseSwitches;
import org.chromium.base.CommandLine;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.content.browser.TracingControllerAndroid;
import org.xwalk.core.XWalkNavigationHistory;
import org.xwalk.core.XWalkPreferences;
import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;

public class XWalkViewShellActivity extends Activity {
    public static final String COMMAND_LINE_FILE = "/data/local/tmp/xwview-shell-command-line";
    private static final String TAG = XWalkViewShellActivity.class.getName();
    public static final String COMMAND_LINE_ARGS_KEY = "commandLineArgs";
    private static final long COMPLETED_PROGRESS_TIMEOUT_MS = 200;
    private static final String ACTION_LAUNCH_URL = "org.xwalk.core.xwview.shell.launch";

    private XWalkView mActiveView;
    private TracingControllerAndroid mTracingController;
    private BroadcastReceiver mReceiver;

    TracingControllerAndroid getTracingController() {
        if (mTracingController == null) {
            mTracingController = new TracingControllerAndroid(this);
        }
        return mTracingController;
    }

    private void registerTracingReceiverWhenIdle() {
        // Delay tracing receiver registration until the main loop is idle.
        Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
            @Override
            public boolean queueIdle() {
                // Will retry if the native library is not initialized yet.
                if (!LibraryLoader.isInitialized()) return true;
                try {
                    getTracingController().registerReceiver(XWalkViewShellActivity.this);
                } catch (SecurityException e) {
                    Log.w(TAG, "failed to register tracing receiver: " + e.getMessage());
                }
                return false;
            }
        });
    }

    private void unregisterTracingReceiver() {
        try {
            getTracingController().unregisterReceiver(this);
        } catch (SecurityException e) {
            Log.w(TAG, "failed to unregister tracing receiver: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "failed to unregister tracing receiver: " + e.getMessage());
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerTracingReceiverWhenIdle();

        if (!CommandLine.isInitialized()) {
            CommandLine.initFromFile(COMMAND_LINE_FILE);
            String[] commandLineParams = getCommandLineParamsFromIntent(getIntent());
            if (commandLineParams != null) {
                CommandLine.getInstance().appendSwitchesAndArguments(commandLineParams);
            }
        }

        waitForDebuggerIfNeeded();

        setContentView(R.layout.testshell_activity);

        mActiveView = (XWalkView) findViewById(R.id.xwalkview);
        mActiveView.load("http://google.ca", null);

        XWalkPreferences.setValue(XWalkPreferences.REMOTE_DEBUGGING, true);

        IntentFilter intentFilter = new IntentFilter(ACTION_LAUNCH_URL);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle bundle = intent.getExtras();
                if (bundle == null)
                    return;

                if (bundle.containsKey("url")) {
                    String extra = bundle.getString("url");
                    if (mActiveView != null)
                        mActiveView.load(extra, null);
                }
            }
        };
        registerReceiver(mReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        unregisterTracingReceiver();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mActiveView != null) mActiveView.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (mActiveView != null) {
            if (!mActiveView.onNewIntent(intent)) super.onNewIntent(intent);
        }
    }

    private void waitForDebuggerIfNeeded() {
        if (CommandLine.getInstance().hasSwitch(BaseSwitches.WAIT_FOR_JAVA_DEBUGGER)) {
            Log.e(TAG, "Waiting for Java debugger to connect...");
            android.os.Debug.waitForDebugger();
            Log.e(TAG, "Java debugger connected. Resuming execution.");
        }
    }

    private static String[] getCommandLineParamsFromIntent(Intent intent) {
        return intent != null ? intent.getStringArrayExtra(COMMAND_LINE_ARGS_KEY) : null;
    }
}
