package com.termux.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.autofill.AutofillManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.blockchain.ub.utils.httputils.BaseHttpUtils;
import com.blockchain.ub.utils.httputils.HttpResponseListenerBase;
import com.example.xh_lib.utils.UUtils;
import com.example.xh_lib.utils.UUtils2;
import com.google.gson.Gson;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.lzy.okgo.model.Response;
import com.termux.R;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalSessionClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.app.terminal.io.extrakeys.ExtraKeysView;
import com.termux.app.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.interact.DialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.app.utils.CrashUtils;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import com.termux.zerocore.activity.BackNewActivity;
import com.termux.zerocore.activity.FontActivity;
import com.termux.zerocore.activity.SwitchActivity;
import com.termux.zerocore.activity.adapter.BoomMinLAdapter;
import com.termux.zerocore.bean.ZDYDataBean;
import com.termux.zerocore.code.CodeString;
import com.termux.zerocore.dialog.BoomCommandDialog;
import com.termux.zerocore.dialog.DownLoadDialogBoom;
import com.termux.zerocore.dialog.EditDialog;
import com.termux.zerocore.dialog.LoadingDialog;
import com.termux.zerocore.dialog.SwitchDialog;
import com.termux.zerocore.popuwindow.MenuLeftPopuListWindow;
import com.termux.zerocore.url.FileUrl;
import com.termux.zerocore.utils.IsInstallCommand;
import com.termux.zerocore.utils.SendJoinUtils;
import com.termux.zerocore.utils.SmsUtils;
import com.termux.zerocore.utils.StartRunCommandUtils;
import com.termux.zerocore.view.BoomWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TermuxActivity extends Activity implements ServiceConnection, View.OnClickListener, MenuLeftPopuListWindow.ItemClickPopuListener, TerminalView.DoubleClickListener {

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    public static TerminalView mTerminalView;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxActivity}.
     */
    TermuxTerminalSessionClient mTermuxTerminalSessionClient;

    /**
     * Termux app shared preferences manager.
     */
    private TermuxAppSharedPreferences mPreferences;

    /**
     * Termux app shared properties manager, loaded from termux.properties
     */
    private TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TermuxActivity}.
     */
    TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TermuxActivity}.
     */
    View mTermuxActivityBottomSpaceView;

    /**
     * The terminal extra keys view.
     */
    ExtraKeysView mExtraKeysView;

    /**
     * The termux sessions list controller.
     */
    TermuxSessionsListViewController mTermuxSessionListViewController;

    /**
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private boolean mIsVisible;

    /**
     * If onResume() was called after onCreate().
     */
    private boolean isOnResumeAfterOnCreate = false;

    /**
     * The {@link TermuxActivity} is in an invalid state and must not be run.
     */
    private boolean mIsInvalidState;

    private int mNavBarHeight;

    private int mTerminalToolbarDefaultHeight;



    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_AUTOFILL_ID = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_HELP_ID = 7;
    private static final int CONTEXT_MENU_SETTINGS_ID = 8;
    private static final int CONTEXT_MENU_REPORT_ID = 9;

    private static final int REQUESTCODE_PERMISSION_STORAGE = 1234;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";

    private static final String LOG_TAG = "TermuxActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {

        Logger.logDebug(LOG_TAG, "onCreate");
        isOnResumeAfterOnCreate = true;

        // Check if a crash happened on last run of the app and show a
        // notification with the crash details if it did
        CrashUtils.notifyCrash(this, LOG_TAG);

        // Load termux shared properties
        mProperties = new TermuxAppSharedProperties(this);

        setActivityTheme();

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_termux);

        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running activity code
            mIsInvalidState = true;
            return;
        }

        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);

        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            mNavBarHeight = insets.getSystemWindowInsetBottom();
            return insets;
        });

        if (mProperties.isUsingFullScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setDrawerTheme();

        setTermuxTerminalViewAndClients();

        setTerminalToolbarView(savedInstanceState);

        setNewSessionButtonView();

        setToggleKeyboardView();

        registerForContextMenu(mTerminalView);

        // Start the {@link TermuxService} and make it run regardless of who is bound to it
        Intent serviceIntent = new Intent(this, TermuxService.class);
        startService(serviceIntent);

        // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
        // callback if it succeeds.
        if (!bindService(serviceIntent, this, 0))
            throw new RuntimeException("bindService() failed");

        // Send the {@link TermuxConstants#BROADCAST_TERMUX_OPENED} broadcast to notify apps that Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this);


        createFiles();
        initZeroView();
    }

    @Override
    public void onStart() {
        super.onStart();

        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState) return;

        mIsVisible = true;

        if (mTermuxTerminalSessionClient != null)
            mTermuxTerminalSessionClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();

        addTermuxActivityRootViewGlobalLayoutListener();

        registerTermuxActivityBroadcastReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState) return;

        if (mTermuxTerminalSessionClient != null)
            mTermuxTerminalSessionClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();

        isOnResumeAfterOnCreate = false;


        isShow();

    }

    @Override
    protected void onStop() {
        super.onStop();

        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState) return;

        mIsVisible = false;

        if (mTermuxTerminalSessionClient != null)
            mTermuxTerminalSessionClient.onStop();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();

        removeTermuxActivityRootViewGlobalLayoutListener();

        unregisterTermuxActivityBroadcastReceiever();
        getDrawer().closeDrawers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Logger.logDebug(LOG_TAG, "onDestroy");

        if (mIsInvalidState) return;

        if (mTermuxService != null) {
            // Do not leave service and session clients with references to activity.
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }

        try {
            unbindService(this);
        } catch (Exception e) {
            // ignore.
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
    }





    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {

        Logger.logDebug(LOG_TAG, "onServiceConnected");

        mTermuxService = ((TermuxService.LocalBinder) service).service;

        setTermuxSessionsListView();

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {

                    //  getDrawer().openDrawer(Gravity.RIGHT);
                    if (mTermuxService == null) return; // Activity might have been destroyed.
                    try {
                        Bundle bundle = getIntent().getExtras();
                        boolean launchFailsafe = false;
                        if (bundle != null) {
                            launchFailsafe = bundle.getBoolean(TERMUX_ACTIVITY.ACTION_FAILSAFE_SESSION, false);
                        }
                        mTermuxTerminalSessionClient.addNewSession(launchFailsafe, null);
                    } catch (WindowManager.BadTokenException e) {
                        // Activity finished - ignore.
                    }



                }, new Runnable() {
                    @Override
                    public void run() {
                        getDrawer().openDrawer(Gravity.LEFT);
                    }
                });
            } else {
                // The service connected while not in foreground - just bail out.
                finishActivityIfNotFinishing();
            }
        } else {
            Intent i = getIntent();
            if (i != null && Intent.ACTION_RUN.equals(i.getAction())) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                boolean isFailSafe = i.getBooleanExtra(TERMUX_ACTIVITY.ACTION_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionClient.setCurrentSession(mTermuxTerminalSessionClient.getCurrentStoredSessionOrLast());
            }
        }

        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionClient);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

        Logger.logDebug(LOG_TAG, "onServiceDisconnected");

        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }





    private void setActivityTheme() {
        if (mProperties.isUsingBlackUI()) {
            this.setTheme(R.style.Theme_Termux_Black);
        } else {
            this.setTheme(R.style.Theme_Termux);
        }
    }

    private void setDrawerTheme() {
        if (mProperties.isUsingBlackUI()) {
            findViewById(R.id.left_drawer).setBackgroundColor(ContextCompat.getColor(this,
                android.R.color.background_dark));
        }
    }



    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }



    private void setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        mTermuxTerminalSessionClient = new TermuxTerminalSessionClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionClient);

        // Set termux terminal view
        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onCreate();

        if (mTermuxTerminalSessionClient != null)
            mTermuxTerminalSessionClient.onCreate();
    }

    private void setTermuxSessionsListView() {
        ListView termuxSessionsListView = findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    }



    private void setTerminalToolbarView(Bundle savedInstanceState) {
        final ViewPager terminalToolbarViewPager = findViewById(R.id.terminal_toolbar_view_pager);
        if (mPreferences.shouldShowTerminalToolbar()) terminalToolbarViewPager.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        setTerminalToolbarHeight();

        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = findViewById(R.id.terminal_toolbar_view_pager);
        if (terminalToolbarViewPager == null) return;
        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = (int) Math.round(mTerminalToolbarDefaultHeight *
            (mProperties.getExtraKeysInfo() == null ? 0 : mProperties.getExtraKeysInfo().getMatrix().length) *
            mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public void toggleTerminalToolbar() {
        final ViewPager terminalToolbarViewPager = findViewById(R.id.terminal_toolbar_view_pager);
        if (terminalToolbarViewPager == null) return;

        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        terminalToolbarViewPager.setVisibility(showNow ? View.VISIBLE : View.GONE);
        if (showNow && terminalToolbarViewPager.getCurrentItem() == 1) {
            // Focus the text input view if just revealed.
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        final EditText textInputView =  findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }



    private void setNewSessionButtonView() {
        View newSessionButton = findViewById(R.id.new_session_button);
        newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionClient.addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            DialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionClient.addNewSession(false, text),
                R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionClient.addNewSession(true, text),
                -1, null, null);
            return true;
        });
    }

    private void setToggleKeyboardView() {
        findViewById(R.id.toggle_keyboard_button).setOnClickListener(v -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });

        findViewById(R.id.toggle_keyboard_button).setOnLongClickListener(v -> {
            toggleTerminalToolbar();
            return true;
        });
    }





    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().closeDrawers();
        } else {
            finishActivityIfNotFinishing();
        }
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!TermuxActivity.this.isFinishing()) {
            finish();
        }
    }

    /** Show a toast and dismiss the last one if still visible. */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }



    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        boolean addAutoFillMenu = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillManager autofillManager = getSystemService(AutofillManager.class);
            if (autofillManager != null && autofillManager.isEnabled()) {
                addAutoFillMenu = true;
            }
        }

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (addAutoFillMenu) menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_ID, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, getCurrentSession().getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
    }

    /** Hook system menu to show context menu instead. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();

        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_AUTOFILL_ID:
                requestAutoFill();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                resetSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_STYLING_ID:
                showStylingDialog();
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                return true;
            case CONTEXT_MENU_HELP_ID:
                startActivity(new Intent(this, HelpActivity.class));
                return true;
            case CONTEXT_MENU_SETTINGS_ID:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case CONTEXT_MENU_REPORT_ID:
                mTermuxTerminalViewClient.reportIssueFromTranscript();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void resetSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);
        }
    }

    private void showStylingDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install, (dialog, which) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL)))).setNegativeButton(android.R.string.cancel, null).show();
        }
    }
    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }

    private void requestAutoFill() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillManager autofillManager = getSystemService(AutofillManager.class);
            if (autofillManager != null && autofillManager.isEnabled()) {
                autofillManager.requestAutofill(mTerminalView);
            }
        }
    }



    /**
     * For processes to access shared internal storage (/sdcard) we need this permission.
     */
    public boolean ensureStoragePermissionGranted() {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            Logger.logDebug(LOG_TAG, "Storage permission not granted, requesting permission.");
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUESTCODE_PERMISSION_STORAGE);
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUESTCODE_PERMISSION_STORAGE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Logger.logDebug(LOG_TAG, "Storage permission granted by user on request.");
            TermuxInstaller.setupStorageSymlinks(this);
        } else {
            Logger.logDebug(LOG_TAG, "Storage permission denied by user on request.");
        }
    }



    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    public DrawerLayout getDrawer() {
        return (DrawerLayout) findViewById(R.id.drawer_layout);
    }

    public void termuxSessionListNotifyUpdated() {
        mTermuxSessionListViewController.notifyDataSetChanged();
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean isOnResumeAfterOnCreate() {
        return isOnResumeAfterOnCreate;
    }



    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionClient;
    }

    @Nullable
    public static TerminalSession getCurrentSession() {
        if (mTerminalView != null)
            return mTerminalView.getCurrentSession();
        else
            return null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }




    public static void updateTermuxActivityStyling(Context context) {
        // Make sure that terminal styling is always applied.
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        context.sendBroadcast(stylingIntent);
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);

        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter);
    }

    private void unregisterTermuxActivityBroadcastReceiever() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void fixTermuxActivityBroadcastReceieverIntent(Intent intent) {
        if (intent == null) return;

        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
        resBroadcastReceiever(extraReloadStyle);
    }




    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            if (mIsVisible) {
                fixTermuxActivityBroadcastReceieverIntent(intent);

                switch (intent.getAction()) {
                    case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                        if (ensureStoragePermissionGranted())
                            TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                        return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                        reloadActivityStyling();
                        return;
                    default:
                }
            }
        }
    }

    private void reloadActivityStyling() {
        if (mProperties!= null) {
            mProperties.loadTermuxPropertiesFromDisk();

            if (mExtraKeysView != null) {
                mExtraKeysView.reload(mProperties.getExtraKeysInfo());
            }
        }

        setTerminalToolbarHeight();

        if (mTermuxTerminalSessionClient != null)
            mTermuxTerminalSessionClient.onReload();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReload();

        if (mTermuxService != null)
            mTermuxService.setTerminalTranscriptRows();

        // To change the activity and drawer theme, activity needs to be recreated.
        // But this will destroy the activity, and will call the onCreate() again.
        // We need to investigate if enabling this is wise, since all stored variables and
        // views will be destroyed and bindService() will be called again. Extra keys input
        // text will we restored since that has already been implemented. Terminal sessions
        // and transcripts are also already preserved. Theme does change properly too.
        // TermuxActivity.this.recreate();
    }



    public static void startTermuxActivity(@NonNull final Context context) {
        context.startActivity(newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }


    /**
     *
     *
     * ZeroView
     *
     *
     */
    private LinearLayout code_ll;
    private LinearLayout rongqi;
    private LinearLayout back_res;
    private LinearLayout linux_online;
    private LinearLayout qemu;
    private LinearLayout cmd_command;
    private LinearLayout moe;
    private LinearLayout msg;
    private LinearLayout files_mulu;
    private TextView version;
    private TextView text_start;
    private LinearLayout title_mb;
    private LinearLayout github;
    private LinearLayout start_command;
    private LinearLayout xuanfu;
    private LinearLayout ziti;
    private LinearLayout zero_tier;
    private TextView service_status;

    /**
     *
     *
     * ZeroTermux
     *
     */

    private void initZeroView(){


        code_ll = findViewById(R.id.code_ll);
        rongqi = findViewById(R.id.rongqi);
        back_res = findViewById(R.id.back_res);
        linux_online = findViewById(R.id.linux_online);
        qemu = findViewById(R.id.qemu);
        cmd_command = findViewById(R.id.cmd_command);
        moe = findViewById(R.id.moe);
        msg = findViewById(R.id.msg);
        files_mulu = findViewById(R.id.files_mulu);
        version = findViewById(R.id.version);
        title_mb = findViewById(R.id.title_mb);
        github = findViewById(R.id.github);
        start_command = findViewById(R.id.start_command);
        text_start = findViewById(R.id.text_start);
        xuanfu = findViewById(R.id.xuanfu);
        ziti = findViewById(R.id.ziti);
        service_status = findViewById(R.id.service_status);
        zero_tier = findViewById(R.id.zero_tier);

        code_ll.setOnClickListener(this);
        rongqi.setOnClickListener(this);
        back_res.setOnClickListener(this);
        linux_online.setOnClickListener(this);
        qemu.setOnClickListener(this);
        cmd_command.setOnClickListener(this);
        moe.setOnClickListener(this);
        msg.setOnClickListener(this);
        files_mulu.setOnClickListener(this);
        github.setOnClickListener(this);
        start_command.setOnClickListener(this);
        xuanfu.setOnClickListener(this);
        ziti.setOnClickListener(this);
        zero_tier.setOnClickListener(this);

        mTerminalView.setDoubleClickListener(this);
        title_mb.setVisibility(View.GONE);
        version.setText(UUtils.getString(R.string.版本) + ":" + UUtils2.INSTANCE.getVersionName(UUtils.getContext()));


        getDrawer().addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull @NotNull View drawerView, float slideOffset) {




                    int i = (int) (slideOffset * 100);
                  /*  UUtils.showLog("状态变换[isDrawerVisible]:" + getDrawer().isDrawerVisible(Gravity.LEFT));
                    UUtils.showLog("状态变换[isDrawerOpen]:" + getDrawer().isDrawerOpen(Gravity.LEFT));*/
                if(getDrawer().isDrawerVisible(Gravity.LEFT)){
                    if(i < 50){
                        title_mb.setVisibility(View.GONE);
                    }else{
                        title_mb.setVisibility(View.VISIBLE);

                    }

                }




                UUtils.showLog("状态变换:" + slideOffset);

            }

            @Override
            public void onDrawerOpened(@NonNull @NotNull View drawerView) {
              //  title_mb.setVisibility(View.VISIBLE);
            }

            @Override
            public void onDrawerClosed(@NonNull @NotNull View drawerView) {
              //  title_mb.setVisibility(View.GONE);
            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        });

        refStartCommandStat();
    }


    @Override
    public void onClick(View v) {

        switch (v.getId()){

            /**
             * 源切换功能
             *
             *
             */
            case R.id.code_ll:
                ArrayList<MenuLeftPopuListWindow.MenuLeftPopuListData> menuLeftPopuListData = new ArrayList<>();

                //清华
                MenuLeftPopuListWindow.MenuLeftPopuListData qinghua = new MenuLeftPopuListWindow.MenuLeftPopuListData(R.mipmap.qinghua_ico, UUtils.getString(R.string.清华源), 1);
                menuLeftPopuListData.add(qinghua);
                //北京
                MenuLeftPopuListWindow.MenuLeftPopuListData beijing = new MenuLeftPopuListWindow.MenuLeftPopuListData(R.mipmap.beijing, UUtils.getString(R.string.北京源), 2);
                menuLeftPopuListData.add(beijing);
                //官方
                MenuLeftPopuListWindow.MenuLeftPopuListData guanfang = new MenuLeftPopuListWindow.MenuLeftPopuListData(R.mipmap.guanfang, UUtils.getString(R.string.官方源), 3);
                menuLeftPopuListData.add(guanfang);

                showMenuDialog(menuLeftPopuListData,code_ll);

                break;

            /**
             *
             * 容器
             *
             */
            case R.id.rongqi:
                getDrawer().closeDrawer(Gravity.LEFT);
                startActivity(new Intent(this, SwitchActivity.class));
                break;

            /**
             *
             * 备份恢复
             *
             */
            case R.id.back_res:
                getDrawer().closeDrawer(Gravity.LEFT);
                startActivity(new Intent(this, BackNewActivity.class));
                break;
            case R.id.linux_online:
                getDrawer().closeDrawer(Gravity.LEFT);
                UUtils.writerFile("linux/termux_toolx.sh",new File(FileUrl.INSTANCE.getMainHomeUrl(),"/linux.sh"));
                mTerminalView.sendTextToTerminal(CodeString.INSTANCE.getRunLinuxSh());
                break;
            case R.id.qemu:

                ArrayList<MenuLeftPopuListWindow.MenuLeftPopuListData> menuLeftPopuListData1 = new ArrayList<>();
                //官方
                MenuLeftPopuListWindow.MenuLeftPopuListData qemuData = new MenuLeftPopuListWindow.MenuLeftPopuListData(R.mipmap.qemu_ico_hai, UUtils.getString(R.string.海的QEMU), 5);
                menuLeftPopuListData1.add(qemuData);

                showMenuDialog(menuLeftPopuListData1,qemu);

                break;
            case R.id.cmd_command:


                BoomCommandDialog boomCommandDialog = new BoomCommandDialog(TermuxActivity.this);
                boomCommandDialog.show();
                boomCommandDialog.setCancelable(true);

                break;
            case R.id.moe:

                getDrawer().closeDrawer(Gravity.LEFT);
                mTerminalView.sendTextToTerminal(CodeString.INSTANCE.getRunMoeSh());

                break;

            case R.id.msg:
                ArrayList<MenuLeftPopuListWindow.MenuLeftPopuListData> menuphoneMsg = new ArrayList<>();

                MenuLeftPopuListWindow.MenuLeftPopuListData msg_phone = new MenuLeftPopuListWindow.MenuLeftPopuListData(R.mipmap.install_msg_phone, UUtils.getString(R.string.安装短信读取工具), 6);
                menuphoneMsg.add(msg_phone);

                showMenuDialog(menuphoneMsg,msg);
                break;

            case R.id.files_mulu:


                try {
                    Intent intent = new Intent();
                    intent.setAction("com.utermux.files.action");
                    startActivity(intent);
                }catch (Exception e){
                    e.printStackTrace();

                    try {
                        installApk(getAssets().open("apk/utermux_file_plug.ip"),"files");
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }

                }


                break;
            case R.id.github:
                SendJoinUtils.INSTANCE.sendJoin(this);

             /*   Intent intent = new Intent();
                intent.setData(Uri.parse("https://github.com/hanxinhao000/ZeroTermux"));//Url 就是你要打开的网址
                intent.setAction(Intent.ACTION_VIEW);
                startActivity(intent); //启动浏览器*/
                break;
            case R.id.start_command:


                //refStartCommandStat()
                if(StartRunCommandUtils.INSTANCE.isRun()){

                    StartRunCommandUtils.INSTANCE.endRun();
                }else{
                    StartRunCommandUtils.INSTANCE.startRun();
                }

                refStartCommandStat();

                break;
            case R.id.xuanfu:


                try {
                    Intent intent1 = new Intent();
                    intent1.setAction("com.zero_float.action.ENTER");
                    startActivity(intent1);
                }catch (Exception e){
                    e.printStackTrace();

                    try {
                        installApk(getAssets().open("apk/zero_float.ip"),"zeroFloat");
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }

                }


                break;

            case R.id.ziti:
                getDrawer().close();
                title_mb.setVisibility(View.GONE);
                startActivity(new Intent(this, FontActivity.class));
                break;

            case R.id.zero_tier:


                EditDialog editDialog = new EditDialog(this);

                EditText edit_text = editDialog.getEdit_text();

                editDialog.getCancel().setText(UUtils.getString(R.string.如何创建服务器));

                editDialog.getCancel().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                    }
                });

                String s = edit_text.getText().toString();

                if(s == null || s.isEmpty()){

                    s = "http://10.242.164.19";

                }
                String finalS = s;
                editDialog.getOk().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        editDialog.dismiss();
                        startHttp(finalS);


                    }
                });


                editDialog.show();


                break;


        }

    }


    private void showMenuDialog(ArrayList<MenuLeftPopuListWindow.MenuLeftPopuListData> arrayList,View showView){

        MenuLeftPopuListWindow menuLeftPopuListWindow = new MenuLeftPopuListWindow(this);
        menuLeftPopuListWindow.setItemClickPopuListener(this);
        menuLeftPopuListWindow.setListData(arrayList);
        menuLeftPopuListWindow.showAsDropDown(showView,250,-200);



    }

    /**
     *
     * 刷新状态
     *
     *
     */

    private void refStartCommandStat(){


        if(StartRunCommandUtils.INSTANCE.isRun()){

            text_start.setText(UUtils.getString(R.string.开机启动开));

        }else{

            text_start.setText(UUtils.getString(R.string.开机启动));

        }



    }


    @Override
    protected void onPause() {
        super.onPause();


        title_mb.setVisibility(View.GONE);
        getDrawer().close();

    }

    /**
     *
     * 菜单点击事件
     *
     * @param id
     * @param index
     */

    @Override
    public void itemClick(int id, int index,MenuLeftPopuListWindow mMenuLeftPopuListWindow) {
        mMenuLeftPopuListWindow.dismiss();
        switch(id){
            //清华
            case 1:

                SwitchDialog switchDialog = switchDialogShow(UUtils.getString(R.string.警告), UUtils.getString(R.string.该操作会覆盖您的文件记录));

                switchDialog.getCancel().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchDialog.dismiss();

                    }
                });
                switchDialog.getOk().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchDialog.dismiss();
                        mTerminalView.sendTextToTerminal(CodeString.INSTANCE.getQH());
                    }
                });


                break;
            //北京
            case 2:

                SwitchDialog switchDialog1 = switchDialogShow(UUtils.getString(R.string.警告), UUtils.getString(R.string.该操作会覆盖您的文件记录));

                switchDialog1.getCancel().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchDialog1.dismiss();

                    }
                });
                switchDialog1.getOk().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchDialog1.dismiss();
                        mTerminalView.sendTextToTerminal(CodeString.INSTANCE.getBJ());
                    }
                });
                break;
            //官方
            case 3:

                SwitchDialog switchDialog2 = switchDialogShow(UUtils.getString(R.string.警告), UUtils.getString(R.string.该操作会覆盖您的文件记录));

                switchDialog2.getCancel().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchDialog2.dismiss();

                    }
                });
                switchDialog2.getOk().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchDialog2.dismiss();
                        UUtils.writerFile("code/sources.list", new File(FileUrl.INSTANCE.getSourcesUrl()));
                        UUtils.writerFile("code/science.list", new File(FileUrl.INSTANCE.getScienceUrl()));
                        UUtils.writerFile("code/game.list", new File(FileUrl.INSTANCE.getGameUrl()));
                        mTerminalView.sendTextToTerminal(CodeString.INSTANCE.getUpDate());
                    }
                });


                break;
            //qemu
            case 5:
                getDrawer().closeDrawer(Gravity.LEFT);
                UUtils.writerFile("linux/termux_toolx.sh",new File(FileUrl.INSTANCE.getMainHomeUrl(),"/utqemu.sh"));
                mTerminalView.sendTextToTerminal(CodeString.INSTANCE.getRunQemuSh());
                break;
            case 6:
                getDrawer().closeDrawer(Gravity.LEFT);

                SwitchDialog msg = switchDialogShow(UUtils.getString(R.string.警告), UUtils.getString(R.string.该操作有风险));

                msg.getCancel().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        msg.dismiss();

                    }
                });
                msg.getOk().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        msg.dismiss();
                        File file = new File(FileUrl.INSTANCE.getSmsUrl());
                        if(file.exists()){

                            UUtils.showMsg(UUtils.getString(R.string.您已安装工具));

                        }else{


                            UUtils.writerFile("runcommand/smsread",new File(FileUrl.INSTANCE.getSmsUrl()));


                            TermuxActivity.mTerminalView.sendTextToTerminal(CodeString.INSTANCE.getRunsmsChomdSh());

                            UUtils.showMsg(UUtils.getString(R.string.安装完成));


                        }

                    }
                });

                break;
        }

    }


    private SwitchDialog switchDialogShow(String title,String msg){

        getDrawer().closeDrawer(Gravity.LEFT);
        SwitchDialog switchDialog = new SwitchDialog(this);

        switchDialog.getTitle().setText(title);
        switchDialog.getMsg().setText(msg);
        switchDialog.getOther().setVisibility(View.GONE);
        switchDialog.getOk().setText(UUtils.getString(R.string.确定));
        switchDialog.getCancel().setText(UUtils.getString(R.string.取消));

        switchDialog.show();


        return switchDialog;

    }




    @Override
    public void doubleClicke() {



        PopupWindow popupWindow = new PopupWindow();
        final BoomWindow[] boomWindow = {new BoomWindow()};



        popupWindow.setContentView(boomWindow[0].getView(new BoomMinLAdapter.CloseLiftListener() {
            @Override
            public void close() {
                popupWindow.dismiss();
            }
        },TermuxActivity.this,popupWindow));


        popupWindow.setOutsideTouchable(true);
        //  popupWindow.setAnimationStyle(R.style.Animation);
        popupWindow.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        popupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.showAsDropDown(mTerminalView,0,- boomWindow[0].getHigh());
        popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                boomWindow[0] = null;
            }
        });


        boomWindow[0].popu_windows_huihua.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mTermuxTerminalSessionClient.addNewSession(false, null);
                popupWindow.dismiss();

            }
        });
        boomWindow[0].popu_windows_jianpan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
                getDrawer().closeDrawers();

                popupWindow.dismiss();


            }
        });

    }


    private void resBroadcastReceiever(String msg){

        if(msg == null){
            return;
        }

        if(msg.equals("readsms")){

            boolean vim = IsInstallCommand.INSTANCE.isInstall(this, "vim", CodeString.INSTANCE.getRunsmsInstallSh());

            if(vim){

                XXPermissions.with(this)
                    .permission(Permission.READ_SMS)
                    .request(new OnPermissionCallback() {

                        @Override
                        public void onGranted(List<String> permissions, boolean all) {
                            if (all) {
                               // UUtils.showMsg("获取录音和日历权限成功");

                                String smsInPhone = SmsUtils.getSmsInPhone();
                                UUtils.setFileString(new File(FileUrl.INSTANCE.getSmsUrlFile()),smsInPhone);
                                UUtils.sleepSetRunMm(new Runnable() {
                                    @Override
                                    public void run() {
                                        TermuxActivity.mTerminalView.sendTextToTerminal("cd ~ && cd ~ && vim sms.txt \n");
                                    }
                                },100);

                            } else {
                               // UUtils.showMsg(("获取部分权限成功，但部分权限未正常授予"));
                                TermuxActivity.mTerminalView.sendTextToTerminal("echo 无权限读取! \n");
                            }
                        }

                        @Override
                        public void onDenied(List<String> permissions, boolean never) {
                            if (never) {
                                TermuxActivity.mTerminalView.sendTextToTerminal("echo 无权限读取! \n");
                                // 如果是被永久拒绝就跳转到应用权限系统设置页面
                                XXPermissions.startPermissionActivity(TermuxActivity.this, permissions);
                            } else {
                                TermuxActivity.mTerminalView.sendTextToTerminal("echo 无权限读取! \n");
                            }
                        }
                    });

            }

        }



    }


    private void installApk(InputStream inputStream,String fileName){


        SwitchDialog switchDialog1 = switchDialogShow(UUtils.getString(R.string.警告), UUtils.getString(R.string.您未安装该插件));

        switchDialog1.getCancel().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchDialog1.dismiss();

            }
        });
        switchDialog1.getOk().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchDialog1.dismiss();


                XXPermissions.with(TermuxActivity.this)
                    .permission(Permission.WRITE_EXTERNAL_STORAGE)
                    .permission(Permission.READ_EXTERNAL_STORAGE)
                    .permission(Permission.REQUEST_INSTALL_PACKAGES)
                    .request(new OnPermissionCallback() {

                        @Override
                        public void onGranted(List<String> permissions, boolean all) {
                            if (all) {
                                if(!FileUrl.INSTANCE.getZeroTermuxApk().exists()){
                                    FileUrl.INSTANCE.getZeroTermuxApk().mkdirs();
                                }
                                File file1 = new File(Environment.getExternalStorageDirectory(), "/xinhao/apk/"+ fileName +".apk");
                                LoadingDialog loadingDialog = new LoadingDialog(TermuxActivity.this);
                                loadingDialog.show();
                                UUtils.writerFileRawInput(file1,inputStream);

                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {

                                        try {
                                            Thread.sleep(2000);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }

                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                loadingDialog.dismiss();
                                                UUtils.installApk(UUtils.getContext(),file1.getAbsolutePath());
                                            }
                                        });

                                    }
                                }).start();


                            } else {

                                UUtils.showMsg("无权限");
                            }
                        }

                        @Override
                        public void onDenied(List<String> permissions, boolean never) {
                            if (never) {
                                UUtils.showMsg("无权限");
                                // 如果是被永久拒绝就跳转到应用权限系统设置页面
                                XXPermissions.startPermissionActivity(TermuxActivity.this, permissions);
                            } else {
                                UUtils.showMsg("无权限");
                            }
                        }
                    });


            }
        });



    }



    //创建目录

    private void createFiles(){

     /*   File file = new File(FileUrl.INSTANCE.getMainConfigUrl());
        File fileProperties = new File(FileUrl.INSTANCE.getMainConfigUrl(), "/termux.properties");

        if(!file.exists()){
            file.mkdirs();
        }
        if(!fileProperties.exists()){

            UUtils.writerFile("properties/termux.properties",fileProperties);
            reloadActivityStyling();
        }*/

        if(!FileUrl.INSTANCE.getZeroTermuxHome().exists()){

            SwitchDialog switchDialog2 = switchDialogShow(UUtils.getString(R.string.警告), UUtils.getString(R.string.需要在您的手机));

            switchDialog2.getCancel().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchDialog2.dismiss();

                    finish();
                }
            });
            switchDialog2.setCancelable(false);
            switchDialog2.getOk().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchDialog2.dismiss();

                    XXPermissions.with(TermuxActivity.this)
                        .permission(Permission.WRITE_EXTERNAL_STORAGE)
                        .permission(Permission.READ_EXTERNAL_STORAGE)
                        .request(new OnPermissionCallback() {

                            @Override
                            public void onGranted(List<String> permissions, boolean all) {
                                if (all) {

                                    if(!FileUrl.INSTANCE.getZeroTermuxHome().exists()){
                                        FileUrl.INSTANCE.getZeroTermuxHome().mkdirs();
                                    }
                                    if(!FileUrl.INSTANCE.getZeroTermuxData().exists()){
                                        FileUrl.INSTANCE.getZeroTermuxData().mkdirs();
                                    }
                                    if(!FileUrl.INSTANCE.getZeroTermuxApk().exists()){
                                        FileUrl.INSTANCE.getZeroTermuxApk().mkdirs();
                                    }
                                    if(!FileUrl.INSTANCE.getZeroTermuxWindows().exists()){
                                        FileUrl.INSTANCE.getZeroTermuxWindows().mkdirs();
                                    }
                                    if(!FileUrl.INSTANCE.getZeroTermuxCommand().exists()){
                                        FileUrl.INSTANCE.getZeroTermuxCommand().mkdirs();
                                    }
                                    if(!FileUrl.INSTANCE.getZeroTermuxFont().exists()){
                                        FileUrl.INSTANCE.getZeroTermuxFont().mkdirs();
                                    }
                                    if(!FileUrl.INSTANCE.getZeroTermuxIso().exists()){
                                        FileUrl.INSTANCE.getZeroTermuxIso().mkdirs();
                                    }
                                    if(!FileUrl.INSTANCE.getZeroTermuxMysql().exists()){
                                        FileUrl.INSTANCE.getZeroTermuxMysql().mkdirs();
                                    }
                                    if(!FileUrl.INSTANCE.getZeroTermuxOnlineSystem().exists()){
                                        FileUrl.INSTANCE.getZeroTermuxOnlineSystem().mkdirs();
                                    }
                                    if(!FileUrl.INSTANCE.getZeroTermuxQemu().exists()){
                                        FileUrl.INSTANCE.getZeroTermuxQemu().mkdirs();
                                    }
                                    if(!FileUrl.INSTANCE.getZeroTermuxServer().exists()){
                                        FileUrl.INSTANCE.getZeroTermuxServer().mkdirs();
                                    }
                                    if(!FileUrl.INSTANCE.getZeroTermuxShare().exists()){
                                        FileUrl.INSTANCE.getZeroTermuxShare().mkdirs();
                                    }
                                    if(!FileUrl.INSTANCE.getZeroTermuxSystem().exists()){
                                        FileUrl.INSTANCE.getZeroTermuxSystem().mkdirs();
                                    }
                                    if(!FileUrl.INSTANCE.getZeroTermuxWebConfig().exists()){
                                        FileUrl.INSTANCE.getZeroTermuxWebConfig().mkdirs();
                                    }
                                    UUtils.showMsg("ok");
                                } else {

                                    UUtils.showMsg("无权限");
                                }
                            }

                            @Override
                            public void onDenied(List<String> permissions, boolean never) {
                                if (never) {
                                    UUtils.showMsg("无权限");
                                    // 如果是被永久拒绝就跳转到应用权限系统设置页面
                                    XXPermissions.startPermissionActivity(TermuxActivity.this, permissions);
                                } else {
                                    UUtils.showMsg("无权限");
                                }
                            }
                        });


                }
            });

        }











    }

    /**
     *
     * 判断面板是否显示
     *
     *
     */


    private void isShow(){

        if(getDrawer().isOpen()){
            title_mb.setVisibility(View.VISIBLE);
        }else{
            title_mb.setVisibility(View.GONE);

        }



    }

    /**
     *
     *
     *
     * 连接到服务器
     *
     *
     */

    private void startHttp(String ip){

        LoadingDialog loadingDialog = new LoadingDialog(TermuxActivity.this);

        loadingDialog.getMsg().setText(UUtils.getString(R.string.正在连接到自定义服务器));

        loadingDialog.show();

        new BaseHttpUtils().getUrl(ip + "/repository/main.json", new HttpResponseListenerBase() {
            @Override
            public void onSuccessful(@NotNull Message msg, int mWhat) {
                loadingDialog.dismiss();
                UUtils.showLog("连接成功:" + msg.obj);

                try{
                    ZDYDataBean zdyDataBean = new Gson().fromJson((String) msg.obj, ZDYDataBean.class);

                    DownLoadDialogBoom downLoadDialogBoom = new DownLoadDialogBoom(TermuxActivity.this);
                    downLoadDialogBoom.setIP(ip + "/repository/main.json");
                    downLoadDialogBoom.show();
                    downLoadDialogBoom.setCancelable(true);
                }catch (Exception e){
                    e.printStackTrace();
                    UUtils.showMsg(UUtils.getString(R.string.服务器数据格式不正确));
                }

            }

            @Override
            public void onFailure(@org.jetbrains.annotations.Nullable Response<String> response, @NotNull String msg, int mWhat) {
                loadingDialog.dismiss();
                UUtils.showMsg(UUtils.getString(R.string.无法连接到自定义服务器));
            }
        },new HashMap<>(),5555);


    }


}
