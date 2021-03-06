package com.hackncheese.glassnetinfo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.view.WindowUtils;
import com.google.android.glass.widget.CardScrollView;
import com.google.android.glass.widget.Slider;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.apache.http.conn.util.InetAddressUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;

/**
 * The main activity: retrieve network info and display it in a card
 */
public class MainActivity extends Activity {

    // for logs
    private static final String TAG = MainActivity.class.getSimpleName();

    /**
     * {@link CardScrollView} to use as the main content view.
     */
    private CardScrollView mCardScroller;


    /**
     * Contains all the info collected about the network state
     * I think we need a {@link Hashtable} because it is synchronized
     * and we will insert new entries in several threads.
     */
    private Hashtable<String, String> mInfoTable = new Hashtable<String, String>();

    private CardAdapter mCardAdapter;
    private Slider mSlider;
    private Slider.Indeterminate mIndSlider;

    private GetExternalIPTask mExtTask;
    private GetExternalIPInfoTask mExtInfoTask;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // Request a voice menu
        getWindow().requestFeature(WindowUtils.FEATURE_VOICE_COMMANDS);

        mCardAdapter = new CardAdapter(this, mInfoTable);
        mCardScroller = new CardScrollView(this);
        mCardScroller.setAdapter(mCardAdapter);
        setContentView(mCardScroller);

        mSlider = Slider.from(mCardScroller);
    }

    @Override
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        if (featureId == WindowUtils.FEATURE_VOICE_COMMANDS ||
                featureId == Window.FEATURE_OPTIONS_PANEL) {
            getMenuInflater().inflate(R.menu.main, menu);
            return true;
        }
        // Pass through to super to setup touch menu.
        return super.onCreatePanelMenu(featureId, menu);
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
        if (featureId == WindowUtils.FEATURE_VOICE_COMMANDS ||
                featureId == Window.FEATURE_OPTIONS_PANEL) {
            switch (item.getItemId()) {
                case R.id.refresh:
                    updateInfo();
                    break;
                case R.id.toggle_wifi:
                    startActivity(new Intent(this, ToggleWifiActivity.class));
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

        updateInfo();
    }

    @Override
    protected void onPause() {
        mCardScroller.deactivate();

        // hide the progress bar, if it was showing
        if (mIndSlider != null) {
            mIndSlider.hide();
            mIndSlider = null;
        }
        // cancel the async task if it exists
        if (mExtTask != null) {
            mExtTask.cancel(true); // true = force interruption
        }

        // cancel the async task if it exists
        if (mExtInfoTask != null) {
            mExtInfoTask.cancel(true); // true = force interruption
        }

        super.onPause();
    }

    private void updateInfo() {
        // empty the table to avoid info mismatch
        mInfoTable.clear();

        //get the local wlan ip address
        String wlanIPAddress = getWlanIPAddress();

        if (wlanIPAddress != null) {
            // we have an IP address, use it
            mInfoTable.put("wlan0", wlanIPAddress);
            // add the ssid we are connected to
            mInfoTable.put("ssid", getConnectedSSID());
        } else {
            // no IP address on wlan0, meaning we are not connected to WiFi
            mInfoTable.put("wlan0", getString(R.string.wlan_na));
        }

        // notify that the card UI must be redrawn
        mCardAdapter.notifyDataSetChanged();

        // get the external IP
        mExtTask = new GetExternalIPTask();
        mExtTask.execute();
    }

    /**
     * Loop through all the network interfaces to find the wlan interface
     * and retrieve the local IPv4 address
     *
     * @return the IP address
     */
    private String getWlanIPAddress() {
        NetworkInterface intf;
        String address;

        try {
            // go through all the network interfaces
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                intf = en.nextElement();

                if (intf.getName().equals("wlan0")) {

                    // go through all its IP addresses
                    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        address = inetAddress.getHostAddress();

                        // get only local IPv4 address that are not loopback
                        if (!inetAddress.isLoopbackAddress() && InetAddressUtils.isIPv4Address(address)) {
                            return address;
                        }
                    }

                }
            }
        } catch (SocketException ex) {
            Log.e(TAG, ex.getMessage());
        }

        return null;
    }

    /**
     * Get the SSID we are currently connected to
     *
     * @return the SSID name
     */
    private String getConnectedSSID() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String ssid = wifiInfo.getSSID();

        if (ssid == null || ssid.equals("0x")) {
            ssid = getString(R.string.ssid_na);
        } else if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            // ssid is often returned with surrounding double quotation marks. We take them off.
            ssid = ssid.substring(1, ssid.length() - 1);
        }

        return ssid;
    }

    /**
     * Retrieves the content of a URL
     *
     * @param url : the url of the web page
     * @return the content as a {@link String}
     */
    private String getDataFromUrl(String url) {
        OkHttpClient client = new OkHttpClient();
        String result;

        // don't wait more than 3 seconds total
        client.setConnectTimeout(1000, TimeUnit.MILLISECONDS);
        client.setWriteTimeout(1000, TimeUnit.MILLISECONDS);
        client.setReadTimeout(1000, TimeUnit.MILLISECONDS);

        Request request = new Request.Builder()
                .url(url)
                .build();

        try {
            Response response = client.newCall(request).execute();
            result = response.body().string();
        } catch (IOException e) {
            Log.e(TAG, String.format("timed out while trying to get data from url %s", url));
            result = getString(R.string.http_response_timeout);
        }

        return result;
    }

    /**
     * an AsyncTask that will fetch the content of a "whatismyip" service, giving us our external IP address
     */
    private class GetExternalIPTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... p) {
            return getDataFromUrl(getString(R.string.url_ip));
        }

        protected void onPreExecute() {
            // show progress bar
            mIndSlider = mSlider.startIndeterminate();
        }

        protected void onPostExecute(String ip) {
            // hide the progress bar
            if (mIndSlider != null) {
                mIndSlider.hide();
                mIndSlider = null;
            }

            // add external ip to the list
            mInfoTable.put("ext", ip);

            // notify that the card UI must be redrawn
            mCardAdapter.notifyDataSetChanged();
            // play a nice sound
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.playSoundEffect(Sounds.SUCCESS);

            if (!ip.equals(getString(R.string.http_response_na)) && !ip.equals(getString(R.string.http_response_timeout))) {
                // get more info on the external IP
                mExtInfoTask = new GetExternalIPInfoTask();
                mExtInfoTask.execute(ip);
            }

        }
    }

    /**
     * an AsyncTask that will fetch the content of an "ip info" service, giving us the provider of our external IP
     */
    private class GetExternalIPInfoTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... ip) {
            String networkProviderName = getDataFromUrl(getString(R.string.url_provider_name, ip[0]));
            int idx = networkProviderName.indexOf(" ");
            if (idx > 0) {
                networkProviderName = networkProviderName.substring(idx, networkProviderName.length()).trim();
            }
            return networkProviderName;
        }

        protected void onPreExecute() {
            // show that we are looking for the provider
            mInfoTable.put("provider", getString(R.string.retrieving));
            // notify that the card UI must be redrawn
            mCardAdapter.notifyDataSetChanged();
            // show progress bar
            mIndSlider = mSlider.startIndeterminate();
        }

        protected void onPostExecute(String networkProviderName) {
            // hide the progress bar
            if (mIndSlider != null) {
                mIndSlider.hide();
                mIndSlider = null;
            }
            // add external ip to the list
            mInfoTable.put("provider", networkProviderName);

            // notify that the card UI must be redrawn
            mCardAdapter.notifyDataSetChanged();
            // play a nice sound
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.playSoundEffect(Sounds.SUCCESS);

        }
    }
}
