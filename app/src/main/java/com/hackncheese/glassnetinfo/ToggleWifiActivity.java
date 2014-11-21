package com.hackncheese.glassnetinfo;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.view.WindowUtils;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;
import com.google.android.glass.widget.Slider;

/**
 * Toggles the WiFi state
 * Shows a grace period slider before enabling/disabling WiFi
 */
public class ToggleWifiActivity extends Activity {

    // for logs
    private static final String TAG = ToggleWifiActivity.class.getSimpleName();

    private CardScrollView mCardScroller;

    private View mView;

    private Slider mSlider;
    private Slider.GracePeriod mGracePeriod;
    private final Slider.GracePeriod.Listener mGracePeriodListener = new Slider.GracePeriod.Listener() {
        @Override
        public void onGracePeriodEnd() {
            mGracePeriod = null;
            toggleWifiState();
            mView = buildView();
            mCardScroller.getAdapter().notifyDataSetChanged();
            // Play a SUCCESS sound to indicate the end of the grace period.
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.playSoundEffect(Sounds.SUCCESS);
        }

        @Override
        public void onGracePeriodCancel() {
            mGracePeriod = null;
            mView = buildView();
            mCardScroller.getAdapter().notifyDataSetChanged();
            // Play a DISMISS sound to indicate the cancellation of the grace period.
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.playSoundEffect(Sounds.DISMISSED);
        }
    };

    //current WiFi state : true = enabled, false = disabled
    private boolean wifiState;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // Request a voice menu
        getWindow().requestFeature(WindowUtils.FEATURE_VOICE_COMMANDS);

        // get the current WiFi state
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        wifiState = wifiManager.isWifiEnabled();

        mCardScroller = new CardScrollView(this);
        mCardScroller.setAdapter(new CardScrollAdapter() {
            @Override
            public int getCount() {
                return 1;
            }

            @Override
            public Object getItem(int position) {
                return mView;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return mView;
            }

            @Override
            public int getPosition(Object item) {
                if (mView.equals(item)) {
                    return 0;
                }
                return AdapterView.INVALID_POSITION;
            }
        });
        setContentView(mCardScroller);

        // when entering the activity, the WiFi toggles directly after the grace period
        mSlider = Slider.from(mCardScroller);
        mGracePeriod = mSlider.startGracePeriod(mGracePeriodListener);

        mView = buildView();
    }

    @Override
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        Log.d(TAG, "onCreatePanelMenu");
        if (featureId == WindowUtils.FEATURE_VOICE_COMMANDS ||
                featureId == Window.FEATURE_OPTIONS_PANEL) {
            getMenuInflater().inflate(R.menu.toggle_wifi, menu);
            return true;
        }

        // Pass through to super to setup touch menu.
        return super.onCreatePanelMenu(featureId, menu);
    }

    /**
     * onPreparePanel is called every time the menu is shown
     * Here, we change what's in the menu based on the current state of the activity:
     * - if in a grace period, we only show the Cancel item
     * - if not in a grace period, we offer the choice of toggling WiFi or returning to the previous activity
     */
    @Override
    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        Log.d(TAG, "onPreparePanel");
        if (featureId == WindowUtils.FEATURE_VOICE_COMMANDS ||
                featureId == Window.FEATURE_OPTIONS_PANEL) {
            if (mGracePeriod == null) {
                // not in a grace period
                menu.setGroupVisible(R.id.tw_toggling, false);
                menu.setGroupVisible(R.id.tw_not_toggling, true);
                // we change the "Toggle WiFi" menu label to be more precise
                if (wifiState) {
                    menu.findItem(R.id.tw_toggle_wifi).setTitle(R.string.toggle_wifi_disable_wifi);
                } else {

                    menu.findItem(R.id.tw_toggle_wifi).setTitle(R.string.toggle_wifi_enable_wifi);
                }
            } else {
                // in a grace period, we only show the cancel toggling menu item
                menu.setGroupVisible(R.id.tw_toggling, true);
                menu.setGroupVisible(R.id.tw_not_toggling, false);
            }
            return true;
        }
        // Pass through to super to setup touch menu.
        return super.onPreparePanel(featureId, view, menu);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            openOptionsMenu();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        Log.d(TAG, "onMenuItemSelected");
        if (featureId == WindowUtils.FEATURE_VOICE_COMMANDS ||
                featureId == Window.FEATURE_OPTIONS_PANEL) {
            switch (item.getItemId()) {
                case R.id.tw_toggle_wifi:
                    // we start a grace period
                    mGracePeriod = Slider.from(mCardScroller).startGracePeriod(mGracePeriodListener);
                    mView = buildView();
                    mCardScroller.getAdapter().notifyDataSetChanged();
                    break;
                case R.id.tw_close_activity:
                    // we go back to the previous activity (if any)
                    this.onBackPressed();
                    break;
                case R.id.tw_cancel_toggle:
                    // cancel toggle = cancel grace period
                    mGracePeriod.cancel();
                    mView = buildView();
                    mCardScroller.getAdapter().notifyDataSetChanged();
                    break;
            }
            return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCardScroller.activate();
    }

    @Override
    protected void onPause() {
        mCardScroller.deactivate();

        // hide the grace period slider, if it was showing
        if (mGracePeriod != null) {
            mGracePeriod.cancel();
            mGracePeriod = null;
        }

        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // If the Grace Period is running, cancel it instead of finishing the Activity.
        if (mGracePeriod != null) {
            mGracePeriod.cancel();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Toggles the state of WiFi
     */
    private void toggleWifiState() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        wifiState = !wifiManager.isWifiEnabled();
        wifiManager.setWifiEnabled(wifiState);
    }

    /**
     * Builds a Glass styled view showing the WiFi state.
     */
    private View buildView() {
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.MENU);

        String txt;

        if (mGracePeriod != null) {
            if (wifiState) {
                txt = "Turning WiFi off";
            } else {
                txt = "Turning WiFi on";
            }
        } else {
            if (wifiState) {
                txt = "WiFi is enabled";
            } else {
                txt = "WiFi is disabled";
            }
        }

        card.setText(txt);
        return card.getView();
    }

}
