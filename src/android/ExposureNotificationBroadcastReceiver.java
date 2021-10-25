package com.outsystems.exposurenotifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

public class ExposureNotificationBroadcastReceiver extends BroadcastReceiver {
    public static CallbackContext callbackContext;
    @Override
    public void onReceive(Context context, Intent intent) {
        PluginResult result;
        switch (intent.getAction()){
            case ExposureNotificationClient.ACTION_EXPOSURE_NOT_FOUND:
                result = new PluginResult(PluginResult.Status.OK,"Not Found");
                break;
            case ExposureNotificationClient.ACTION_EXPOSURE_STATE_UPDATED:
                result = new PluginResult(PluginResult.Status.OK,"Possible Exposure");
                break;

            default:
                result = new PluginResult(PluginResult.Status.OK,intent.getAction());
                break;

        }
        result.setKeepCallback(true);
        if (callbackContext != null){
            callbackContext.sendPluginResult(result);
        }

    }
}
