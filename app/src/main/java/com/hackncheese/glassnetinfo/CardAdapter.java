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
 * Populates views in a {@code CardScrollView} with a card built from a custom embedded layout to
 * show info on the current network state.
 */
public class CardAdapter extends CardScrollAdapter {

    private final Context mContext;
    private final Hashtable<String, String> mInfoTable;

    /**
     * Initializes a new adapter with the specified context and list of items.
     */
    public CardAdapter(Context context, Hashtable<String, String> infoTable) {
        mContext = context;
        mInfoTable = infoTable;
    }

    @Override
    public int getItemViewType(int position) {
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

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        CardBuilder card = new CardBuilder(mContext, CardBuilder.Layout.EMBED_INSIDE)
                .setEmbeddedLayout(R.layout.main);
        View view = card.getView(convertView, parent);

        String wifiIP = mInfoTable.get("wlan0");
        if (wifiIP != null) {
            TextView textViewWifiIP = (TextView) view.findViewById(R.id.textViewWifiIP);
            textViewWifiIP.setText(wifiIP);
        }

        String wifiSSID = mInfoTable.get("ssid");
        if (wifiSSID != null) {
            view.findViewById(R.id.rowWifiSSID).setVisibility(View.VISIBLE);
            TextView textViewWifiSSID = (TextView) view.findViewById(R.id.textViewWifiSSID);
            textViewWifiSSID.setText(wifiSSID);
        }

        String extIP = mInfoTable.get("ext");
        if (extIP != null) {
            TextView textViewExtIP = (TextView) view.findViewById(R.id.textViewExtIP);
            textViewExtIP.setText(extIP);
        }

        String extProvider = mInfoTable.get("provider");
        if (extProvider != null) {
            view.findViewById(R.id.rowExtProvider).setVisibility(View.VISIBLE);
            TextView textViewExtProvider = (TextView) view.findViewById(R.id.textViewExtProvider);
            textViewExtProvider.setText(extProvider);
        }

        return view;
    }

}
