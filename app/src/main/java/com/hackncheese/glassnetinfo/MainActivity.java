package com.hackncheese.glassnetinfo;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;

import com.google.android.glass.media.Sounds;
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

        mCardAdapter = new CardAdapter(this, mInfoTable);
        mCardScroller = new CardScrollView(this);
        mCardScroller.setAdapter(mCardAdapter);
        // Handle the TAP event.
        mCardScroller.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Plays disallowed sound to indicate that TAP actions are not supported.
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.playSoundEffect(Sounds.DISALLOWED);
            }
        });
        setContentView(mCardScroller);

        mSlider = Slider.from(mCardScroller);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCardScroller.activate();

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
            mInfoTable.put("ssid", getString(R.string.ssid_na));
        }

        // notify that the card UI must be redrawn
        mCardAdapter.notifyDataSetChanged();

        // get the external IP
        mExtTask = new GetExternalIPTask();
        mExtTask.execute();
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
            ssid = "n/a";
        } else if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            // ssid is often returned with surrounding double quotation marks. We take them off.
            ssid = ssid.substring(1, ssid.length() - 1);
        }

        return ssid;
    }

    /**
     * Retrieves the content of a URL
     * @param url : the url of the web page
     * @return the content as a {@link String}
     */
    private String getDataFromUrl(String url) {
        OkHttpClient client = new OkHttpClient();
        String result = getString(R.string.http_response_na);

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
            String extIp = getDataFromUrl(getString(R.string.url_ip));
            return extIp;
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

            if (!ip.equals(getString(R.string.http_response_na)) && !ip.equals(getString(R.string.http_response_timeout))) {
                // get more info on the external IP
                mExtInfoTask = new GetExternalIPInfoTask();
                mExtInfoTask.execute(ip);
            } else {
                mInfoTable.put("provider", getString(R.string.http_response_na));
            }

            // notify that the card UI must be redrawn
            mCardAdapter.notifyDataSetChanged();
            // play a nice sound
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.playSoundEffect(Sounds.SUCCESS);
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
