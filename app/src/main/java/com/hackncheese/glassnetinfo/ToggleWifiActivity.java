package com.hackncheese.glassnetinfo;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;
import com.google.android.glass.widget.Slider;
import com.google.android.glass.widget.Slider.GracePeriod;

/**
 * Toggles the WiFi state
 */
public class ToggleWifiActivity extends Activity {

    // for logs
    private static final String TAG = ToggleWifiActivity.class.getSimpleName();

    private CardScrollView mCardScroller;

    private View mView;

    private Slider mSlider;
    private Slider.GracePeriod mGracePeriod;
    private final GracePeriod.Listener mGracePeriodListener = new GracePeriod.Listener() {
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
    private boolean wifiState;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

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
        // Handle the TAP event.
        mCardScroller.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mGracePeriod = Slider.from(parent).startGracePeriod(mGracePeriodListener);
                mView = buildView();
                mCardScroller.getAdapter().notifyDataSetChanged();
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.playSoundEffect(Sounds.TAP);
            }
        });
        setContentView(mCardScroller);

        mSlider = Slider.from(mCardScroller);
        mGracePeriod = mSlider.startGracePeriod(mGracePeriodListener);

        mView = buildView();
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
        String footnote;

        if (mGracePeriod != null) {
            if (wifiState) {
                txt = "Turning WiFi off";
            } else {
                txt = "Turning WiFi on";
            }
            footnote = "Dismiss to cancel";
        } else {
            if (wifiState) {
                txt = "WiFi is enabled";
                footnote = "Tap to turn off";
            } else {
                txt = "WiFi is disabled";
                footnote = "Tap to turn on";
            }
        }

        card.setText(txt);
        card.setFootnote(footnote);
        return card.getView();
    }

}
