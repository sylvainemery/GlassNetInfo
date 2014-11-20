package com.hackncheese.glassnetinfo;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

/**
 * Toggles the WiFi state
 */
public class ToggleWifiActivity extends Activity {

    private CardScrollView mCardScroller;

    private View mView;

    private boolean wifiState;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        toggleWifiState();
        mView = buildView();

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
                toggleWifiState();
                mView = buildView();
                mCardScroller.getAdapter().notifyDataSetChanged();
                // play a nice sound
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.playSoundEffect(Sounds.SUCCESS);
            }
        });
        setContentView(mCardScroller);

        // play a nice sound
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.playSoundEffect(Sounds.SUCCESS);

    }

    @Override
    protected void onResume() {
        super.onResume();
        mCardScroller.activate();
    }

    @Override
    protected void onPause() {
        mCardScroller.deactivate();
        super.onPause();
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
        CardBuilder card = new CardBuilder(this, CardBuilder.Layout.TEXT);

        String txt;
        if (wifiState) {
            txt = "WiFi is now enabled";
        } else {
            txt = "WiFi is now disabled";
        }

        card.setText(txt);
        return card.getView();
    }

}
