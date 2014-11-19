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
 * An {@link Activity} showing a tuggable "Hello World!" card.
 * <p/>
 * The main content view is composed of a one-card {@link CardScrollView} that provides tugging
 * feedback to the user when swipe gestures are detected.
 * If your Glassware intends to intercept swipe gestures, you should set the content view directly
 * and use a {@link com.google.android.glass.touchpad.GestureDetector}.
 *
 * @see <a href="https://developers.google.com/glass/develop/gdk/touch">GDK Developer Guide</a>
 */
public class MainActivity extends Activity {

    // for logs
    private static final String TAG = MainActivity.class.getSimpleName();

    /**
     * {@link CardScrollView} to use as the main content view.
     */
    private CardScrollView mCardScroller;


    private Hashtable<String, String> ips;

    private CardAdapter mCardAdapter;
    private Slider mSlider;
    private Slider.Indeterminate mIndSlider;

    private GetExternalIPTask mExtTask;
    private GetExternalIPInfoTask mExtInfoTask;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        ips = getLocalIpAddresses();

        ips.put("ssid", getConnectedSSID());

        mCardAdapter = new CardAdapter(this, ips);
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

    public Hashtable<String, String> getLocalIpAddresses() {
        NetworkInterface intf;
        String address;
        Hashtable<String, String> h = new Hashtable<String, String>();

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                intf = en.nextElement();

                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    address = inetAddress.getHostAddress();

                    if (!inetAddress.isLoopbackAddress() && InetAddressUtils.isIPv4Address(address)) {
                        h.put(intf.getName(), address);
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(TAG, ex.getMessage());
        }

        return h;
    }

    public String getConnectedSSID() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String ssid = wifiInfo.getSSID();
        // TODO: add " (connecting)" if the wifi is in a connecting state

        if (ssid == null || ssid.equals("0x")) {
            ssid = "n/a";
        }
        else if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length()-1);
        }

        return ssid;
    }

    public String getExternalIpAddress() {
        OkHttpClient client = new OkHttpClient();
        String ip = "n/a";

        // don't wait more than 3 seconds total
        client.setConnectTimeout(1000, TimeUnit.MILLISECONDS);
        client.setWriteTimeout(1000, TimeUnit.MILLISECONDS);
        client.setReadTimeout(1000, TimeUnit.MILLISECONDS);

        Request request = new Request.Builder()
                .url(getString(R.string.url))
                .build();

        try {
            Response response = client.newCall(request).execute();
            ip = response.body().string();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        return ip;

    }

    public String getExternalIpInfoAddress(String ip) {
        OkHttpClient client = new OkHttpClient();
        String NetworkProviderName = ip;

        // don't wait more than 3 seconds total
        client.setConnectTimeout(1000, TimeUnit.MILLISECONDS);
        client.setWriteTimeout(1000, TimeUnit.MILLISECONDS);
        client.setReadTimeout(1000, TimeUnit.MILLISECONDS);

        Request request = new Request.Builder()
                .url(String.format("http://ipinfo.io/%s/org", ip))
                .build();

        try {
            Response response = client.newCall(request).execute();
            String resp = response.body().string();
            int idx = resp.indexOf(" ");
            if (idx > 0) {
                NetworkProviderName = resp.substring(idx, resp.length()).trim();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return NetworkProviderName;

    }

    private class GetExternalIPTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... p) {
            return getExternalIpAddress();
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
            ips.put("ext", ip);
            // notify that the card UI must be redrawn
            mCardAdapter.notifyDataSetChanged();
            // play a nice sound
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.playSoundEffect(Sounds.SUCCESS);

            // get more info on the external IP
            mExtInfoTask = new GetExternalIPInfoTask();
            mExtInfoTask.execute(ip);

        }
    }
    private class GetExternalIPInfoTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... ip) {
            return getExternalIpInfoAddress(ip[0]);
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
            ips.put("provider", networkProviderName);
            // notify that the card UI must be redrawn
            mCardAdapter.notifyDataSetChanged();
            // play a nice sound
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            am.playSoundEffect(Sounds.SUCCESS);

        }
    }
}
