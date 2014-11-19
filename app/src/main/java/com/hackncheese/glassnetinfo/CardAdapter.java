package com.hackncheese.glassnetinfo;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;

import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;

import java.util.Hashtable;

/**
 * Created by se on 19/11/14.
 */
public class CardAdapter extends CardScrollAdapter {

    private final Context mContext;
    private final Hashtable<String, String> mIPs;

    @Override
    public int getItemViewType(int position){
        return 0;
    }

    @Override
    public int getCount() {
        return 1;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public int getPosition(Object item) {
        return AdapterView.INVALID_POSITION;
    }

    /** Initializes a new adapter with the specified context and list of items. */
    public CardAdapter(Context context, Hashtable<String, String> ips) {
        mContext = context;
        mIPs = ips;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        CardBuilder card = new CardBuilder(mContext, CardBuilder.Layout.EMBED_INSIDE)
                .setEmbeddedLayout(R.layout.main);
        View view = card.getView(convertView, parent);

        String wifiIP = mIPs.get("wlan0");
        if (wifiIP != null) {
            TextView textViewWifiIP = (TextView) view.findViewById(R.id.textViewWifiIP);
            textViewWifiIP.setText(wifiIP);
        }

        String wifiSSID = mIPs.get("ssid");
        if (wifiSSID != null) {
            TextView textViewWifiSSID = (TextView) view.findViewById(R.id.textViewWifiSSID);
            textViewWifiSSID.setText(wifiSSID);
        }

        String extIP = mIPs.get("ext");
        if (extIP != null) {
            TextView textViewExtIP = (TextView) view.findViewById(R.id.textViewExtIP);
            textViewExtIP.setText(extIP);
        }

        String extProvider = mIPs.get("provider");
        if (extProvider != null) {
            TextView textViewExtProvider = (TextView) view.findViewById(R.id.textViewExtProvider);
            textViewExtProvider.setText(extProvider);
        }

        return view;
    }

}
