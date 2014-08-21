package com.lechucksoftware.proxy.proxysettings;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.text.TextUtils;
import android.util.Log;

import com.lechucksoftware.proxy.proxysettings.db.WiFiAPEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.shouldit.proxy.lib.APL;
import be.shouldit.proxy.lib.APLNetworkId;
import be.shouldit.proxy.lib.WiFiAPConfig;
import be.shouldit.proxy.lib.enums.SecurityType;
import be.shouldit.proxy.lib.utils.ProxyUtils;

/**
 * Created by Marco on 15/09/13.
 */
public class WifiNetworksManager
{
    private static final String TAG = WifiNetworksManager.class.getSimpleName();
    private WifiNetworkStatus wifiNetworkStatus;

    public WifiNetworksManager(Context ctx)
    {
        wifiNetworkStatus = new WifiNetworkStatus();
    }

    public void updateWifiApConfigs()
    {
        synchronized (wifiNetworkStatus)
        {
            App.getLogger().startTrace(TAG,"updateWifiApConfigs", Log.DEBUG, true);

            Map<Long,WiFiAPEntity> persistedWifiAp = App.getDBManager().getAllWifiAp();
            App.getLogger().partialTrace(TAG, "updateWifiApConfigs", "getAllWifiAp", Log.DEBUG);

            wifiNetworkStatus.wifiApConfigsByAPLNetId = APL.getWifiAPConfigurations();
            App.getLogger().partialTrace(TAG,"updateWifiApConfigs", "getWifiAPConfigurations", Log.DEBUG);

            wifiNetworkStatus.wifiAPConfigList = new ArrayList<WiFiAPConfig>(wifiNetworkStatus.wifiApConfigsByAPLNetId.values());
            App.getLogger().partialTrace(TAG,"updateWifiApConfigs", "new ArrayList<WiFiAPConfig>", Log.DEBUG);

            updateWifiConfigWithScanResults(APL.getWifiManager().getScanResults());
            App.getLogger().partialTrace(TAG,"updateWifiApConfigs", "updateWifiConfigWithScanResults", Log.DEBUG);

            buildSortedConfigurationsList();
            App.getLogger().partialTrace(TAG,"updateWifiApConfigs", "buildSortedConfigurationsList", Log.DEBUG);

            App.getLogger().stopTrace(TAG, "updateWifiApConfigs", Log.DEBUG);
        }
    }

    public void updateWifiConfig(WiFiAPConfig wiFiAPConfig)
    {
        synchronized (wifiNetworkStatus)
        {
            if (wifiNetworkStatus.wifiApConfigsByAPLNetId != null)
            {
                APLNetworkId aplNetworkId = wiFiAPConfig.getAPLNetworkId();

                if (wifiNetworkStatus.wifiApConfigsByAPLNetId.containsKey(aplNetworkId))
                {
                    wifiNetworkStatus.wifiApConfigsByAPLNetId.get(aplNetworkId).updateProxyConfiguration(wiFiAPConfig);
                }
                else
                {
                    wifiNetworkStatus.wifiApConfigsByAPLNetId.put(aplNetworkId, wiFiAPConfig);
                    wifiNetworkStatus.wifiApConfigsByWifiNetworkId.put(wiFiAPConfig.getNetworkId(), wiFiAPConfig);
                    wifiNetworkStatus.wifiAPConfigList.add(wiFiAPConfig);
                }

                buildSortedConfigurationsList();
            }
        }
    }

    public void removeWifiConfig(APLNetworkId aplNetworkId)
    {
        synchronized (wifiNetworkStatus)
        {
            if (aplNetworkId != null)
            {
                WiFiAPConfig wiFiAPConfig = wifiNetworkStatus.wifiApConfigsByAPLNetId.remove(aplNetworkId);
//            wifiApEntitiesByAPLNetId.remove(aplNetworkId);
                wifiNetworkStatus.wifiApConfigsByWifiNetworkId.remove(wiFiAPConfig.getNetworkId());
                wifiNetworkStatus.wifiAPConfigList.remove(wiFiAPConfig);

                buildSortedConfigurationsList();
            }
        }
    }

    public void updateCurrentWifiInfo(WifiInfo currentWifiInfo)
    {
        App.getLogger().startTrace(TAG,"updateCurrentWifiInfo", Log.DEBUG);

        synchronized (wifiNetworkStatus)
        {
            if (!wifiNetworkStatus.wifiApConfigsByAPLNetId.isEmpty())
            {
                for (WiFiAPConfig conf : wifiNetworkStatus.wifiApConfigsByAPLNetId.values())
                {
                    conf.updateWifiInfo(currentWifiInfo, null);
                }

                buildSortedConfigurationsList();
            }
        }

        App.getLogger().stopTrace(TAG,"updateCurrentWifiInfo", Log.DEBUG);
    }

    public void updateWifiConfigWithScanResults(List<ScanResult> scanResults)
    {
        List<String> scanResultsStrings = new ArrayList<String>();

        synchronized (wifiNetworkStatus)
        {
            // clear all the savedConfigurations AP status
            if (!wifiNetworkStatus.wifiApConfigsByAPLNetId.isEmpty())
            {
                App.getLogger().startTrace(TAG, "Clear scan status from AP configs", Log.DEBUG);
                for (WiFiAPConfig conf : wifiNetworkStatus.wifiApConfigsByAPLNetId.values())
                {
                    conf.clearScanStatus();
                }
                App.getLogger().stopTrace(TAG, "Clear scan status from AP configs", Log.DEBUG);
            }

            for (ScanResult res : scanResults)
            {
                scanResultsStrings.add(res.SSID + " level: " + res.level);
                String currSSID = ProxyUtils.cleanUpSSID(res.SSID);
                SecurityType security = ProxyUtils.getSecurity(res);
                APLNetworkId aplNetworkId = new APLNetworkId(currSSID, security);

                if (wifiNetworkStatus.wifiApConfigsByAPLNetId.containsKey(aplNetworkId))
                {
                    WiFiAPConfig conf = wifiNetworkStatus.wifiApConfigsByAPLNetId.get(aplNetworkId);
                    if (conf != null)
                    {
                        conf.updateScanResults(res);
                    }
                }
                else
                {
                    if (wifiNetworkStatus.notConfiguredWifi.containsKey(aplNetworkId))
                    {
                        wifiNetworkStatus.notConfiguredWifi.remove(aplNetworkId);
                    }

                    wifiNetworkStatus.notConfiguredWifi.put(aplNetworkId, res);
                }
            }

            buildSortedConfigurationsList();
        }

        App.getLogger().d(TAG, "Updating from scanresult: " + TextUtils.join(", ", scanResultsStrings.toArray()));
    }

    private void buildSortedConfigurationsList()
    {
        App.getLogger().startTrace(TAG, "buildSortedConfigurationsList", Log.DEBUG);

        try
        {
            synchronized (wifiNetworkStatus)
            {
                Collections.sort(wifiNetworkStatus.wifiAPConfigList);
            }
        }
        catch (IllegalArgumentException e)
        {
            Map<String, String> map = new HashMap<String, String>();
            map.put("config_list", configListToDBG().toString());
            App.getEventsReporter().sendException(e, map);
        }

        App.getLogger().stopTrace(TAG, "buildSortedConfigurationsList", Log.DEBUG);
    }

    private Map<APLNetworkId, WiFiAPConfig> getWifiApConfigsByAPLNetId()
    {
        return wifiNetworkStatus.wifiApConfigsByAPLNetId;
    }

    public List<WiFiAPConfig> getSortedWifiApConfigsList()
    {
        if (wifiNetworkStatus.wifiAPConfigList == null || wifiNetworkStatus.wifiAPConfigList.isEmpty())
        {
            updateWifiApConfigs();
        }

        return wifiNetworkStatus.wifiAPConfigList;
    }

    private String getConfigurationsString()
    {
        if (!getWifiApConfigsByAPLNetId().isEmpty())
        {
            return TextUtils.join(", ", getWifiApConfigsByAPLNetId().keySet());
        }
        else
        {
            return "No configured Wi-Fi networks";
        }
    }

    public WiFiAPConfig updateCurrentConfiguration()
    {
        WiFiAPConfig updatedConf = null;

        App.getLogger().startTrace(TAG, "updateCurrentConfiguration", Log.INFO);

        if (APL.getWifiManager() != null && APL.getWifiManager().isWifiEnabled())
        {
            WifiInfo info = APL.getWifiManager().getConnectionInfo();
            if (info != null)
            {
                int networkId = info.getNetworkId();
                if (wifiNetworkStatus.wifiApConfigsByWifiNetworkId.containsKey(networkId))
                {
                    updatedConf = wifiNetworkStatus.wifiApConfigsByWifiNetworkId.get(networkId);
                }

                mergeWithCurrentConfiguration(updatedConf);
            }
        }

        App.getLogger().stopTrace(TAG, "updateCurrentConfiguration", Log.INFO);

        return updatedConf;
    }

    private void mergeWithCurrentConfiguration(WiFiAPConfig updated)
    {
        if (wifiNetworkStatus.currentConfiguration == null)
        {
            if (updated != null)
            {
                wifiNetworkStatus.currentConfiguration = updated;
                App.getLogger().d(TAG, "updateCurrentConfiguration - Set current configuration (was NULL before)");
            }
            else
            {
                App.getLogger().d(TAG, "updateCurrentConfiguration - Same configuration: no need to update it (both NULL)");
            }
        }
        else if ((wifiNetworkStatus.currentConfiguration == null) || (updated != null && wifiNetworkStatus.currentConfiguration != null && wifiNetworkStatus.currentConfiguration.compareTo(updated) != 0))
        {
            // Update currentConfiguration only if it's different from the previous
            wifiNetworkStatus.currentConfiguration = updated;
            App.getLogger().d(TAG, "updateCurrentConfiguration - Updated current configuration");
        }
        else
        {
            App.getLogger().d(TAG, "updateCurrentConfiguration - Same configuration: no need to update it");
        }
    }

    public WiFiAPConfig getCachedConfiguration()
    {
//        if (currentConfiguration == null)
//        {
            return updateCurrentConfiguration();
//        }
//
//        return currentConfiguration;
    }

//    /**
//     * If necessary updates the configuration list and sort it
//     *
//     * @return the sorted list of current proxy savedConfigurations
//     */
//    private List<WiFiAPConfig> getConfigurationsList()
//    {
//        return wifiAPConfigList;
//    }
    /**
     * Updates the proxy configuration list
     */

    public synchronized void updateProxyConfigurationList()
    {
        App.getLogger().startTrace(TAG, "updateProxyConfigurationList", Log.DEBUG);

        //Get information regarding current saved configuration
        List<APLNetworkId> internalSavedSSID = getInternalSavedWifiConfigurations();

        //Get latests information regarding configured AP
        List<APLNetworkId> notMoreConfiguredSSID = updateCachedWifiAP(internalSavedSSID);

        // Remove from current configuration the SSID that are not more configured into Android's Wi-Fi settings
        removeNotMoreConfiguredSSID(notMoreConfiguredSSID);

        // Update savedConfigurations with latest Wi-Fi scan results
//        updateWifiApConfigs();

        // If the configuration has been updated sort again the list!!
//        if (updatedConfiguration && !getWifiApConfigsByAPLNetId().isEmpty())
        {
            App.getLogger().d(TAG, "Configuration updated -> need to create again the sorted list");
            buildSortedConfigurationsList();
        }

        App.getLogger().d(TAG, "Final savedConfigurations list: " + getConfigurationsString());
        App.getLogger().stopTrace(TAG, "updateProxyConfigurationList", Log.DEBUG);
    }

    private void removeNotMoreConfiguredSSID(List<APLNetworkId> internalSavedSSID)
    {
//        LogWrapper.startTrace(TAG,"removeNoMoreConfiguredSSID", Log.DEBUG);
        if (!getWifiApConfigsByAPLNetId().isEmpty())
        {
            for (APLNetworkId netId : internalSavedSSID)
            {
                if (getWifiApConfigsByAPLNetId().containsKey(netId))
                {
                    WiFiAPConfig removed = getWifiApConfigsByAPLNetId().remove(netId);
                    App.getLogger().w(TAG, "Removing from Proxy Settings configuration a no more configured SSID: " + removed.toShortString());
                }
            }

//            LogWrapper.d(TAG, "Cleaned up savedConfigurations list: " + getConfigurationsString());

        }
//        LogWrapper.stopTrace(TAG,"removeNoMoreConfiguredSSID", Log.DEBUG);
    }

    private List<APLNetworkId> updateCachedWifiAP(List<APLNetworkId> internalSavedSSID)
    {
//        LogWrapper.startTrace(TAG,"getSavedConfigurations", Log.DEBUG);

        // Get updated list of Proxy savedConfigurations from APL
        List<WiFiAPConfig> updatedConfigurations = new ArrayList<WiFiAPConfig>(APL.getWifiAPConfigurations().values());
        if (updatedConfigurations != null)
        {
            for (WiFiAPConfig conf : updatedConfigurations)
            {
                if (conf != null)
                {
                    wifiNetworkStatus.wifiApConfigsByAPLNetId = getWifiApConfigsByAPLNetId();
                    if (wifiNetworkStatus.wifiApConfigsByAPLNetId != null
                            && conf.getAPLNetworkId() != null
                            && wifiNetworkStatus.wifiApConfigsByAPLNetId.containsKey(conf.getAPLNetworkId()))
                    {
                        // Updates already saved configuration
                        WiFiAPConfig originalConf = getWifiApConfigsByAPLNetId().get(conf.getAPLNetworkId());
//                        if (originalConf.updateProxyConfiguration(conf))
//                            updatedConfiguration = true;
                    }
                    else
                    {
                        // Add new found configuration
//                        App.getLogger().d(TAG, "Adding to list new Wi-Fi AP configuration: " + conf.toShortString());
                        getWifiApConfigsByAPLNetId().put(conf.getAPLNetworkId(), conf);
                    }

                    if (internalSavedSSID.contains(conf.getAPLNetworkId()))
                    {
                        internalSavedSSID.remove(conf.getAPLNetworkId());
                    }
                }
            }
        }

//        LogWrapper.d(TAG,"Updated savedConfigurations list: " + getConfigurationsString());
//        LogWrapper.d(TAG,"Configurations that need to be removed: " + TextUtils.join(", " , internalSavedSSID));

//        LogWrapper.stopTrace(TAG,"getSavedConfigurations", Log.DEBUG);

        return internalSavedSSID;
    }

    private List<APLNetworkId> getInternalSavedWifiConfigurations()
    {
//        LogWrapper.startTrace(TAG,"getSavedConfigurations", Log.DEBUG);

        Collection<APLNetworkId> savedNetworks = null;
        List<APLNetworkId> internalSavedSSID = new ArrayList<APLNetworkId>();

        if (!getWifiApConfigsByAPLNetId().isEmpty())
        {
            savedNetworks = getWifiApConfigsByAPLNetId().keySet();
            for (APLNetworkId wifiNet : savedNetworks)
            {
                internalSavedSSID.add(wifiNet);
            }
        }

//        LogWrapper.stopTrace(TAG,"getSavedConfigurations", Log.DEBUG);

        return internalSavedSSID;
    }

    public WiFiAPConfig getConfiguration(APLNetworkId aplNetworkId)
    {
        WiFiAPConfig selected = null;

        if (wifiNetworkStatus.wifiApConfigsByAPLNetId.containsKey(aplNetworkId))
        {
            selected = wifiNetworkStatus.wifiApConfigsByAPLNetId.get(aplNetworkId);
        }

        return selected;
    }

    public JSONObject configListToDBG()
    {
        JSONObject dbg = new JSONObject();

        try
        {
            JSONArray configurations = new JSONArray();

            synchronized (wifiNetworkStatus)
            {
                for (WiFiAPConfig conf : wifiNetworkStatus.wifiAPConfigList)
                {
                    configurations.put(conf.toJSON());
                }
            }

            dbg.put("configurations", configurations);
        }
        catch (JSONException e)
        {
            APL.getEventsReporter().sendException(e);
        }

        return dbg;
    }
}