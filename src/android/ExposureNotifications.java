package com.outsystems.exposurenotifications;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.util.Base64Utils;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.exposurenotification.ExposureNotificationClient;
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ExposureNotifications extends CordovaPlugin {

    private static String TAG = "ExposureNotificationsLog :";

    private static final String ACTION_START = "start";
    private static final String ACTION_RETRIEVE = "retrieve";
    private static final String ACTION_PROVIDE = "provide";
    private static final String ACTION_LISTENERS = "setListener";
    private static final String ACTION_STOP = "stop";

    private static final int REQUEST_START_CODE = 1;
    private static final int REQUEST_HISTORY_CODE = 2;

    private CallbackContext callbackContext;
    private ExposureNotificationClient client;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        client = Nearby.getExposureNotificationClient(cordova.getActivity());
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        PluginResult result = null;
        Boolean actionStatus;
        this.callbackContext = callbackContext;
        switch (action){
            case ACTION_PROVIDE:
                provide(args.getJSONArray(0));
                return true;
            case ACTION_RETRIEVE:
                retrieve();
                return true;
            case ACTION_START:
                start();
                return true;
            case ACTION_STOP:
                client.stop();
                result = new PluginResult(Status.NO_RESULT);
                result.setKeepCallback(false);
                callbackContext.sendPluginResult(result);
                return true;
            case ACTION_LISTENERS:
                ExposureNotificationBroadcastReceiver.callbackContext = callbackContext;
                sendSuccess(true);
                return true;
            default:
                result = new PluginResult(Status.INVALID_ACTION,"Invalid Action");
                result.setKeepCallback(false);
                callbackContext.sendPluginResult(result);
                return false;
        }
    }


    private void retrieve(){
        client.getTemporaryExposureKeyHistory().addOnSuccessListener(temporaryExposureKeys -> {
            JSONArray jsonResult = new JSONArray();
            for (TemporaryExposureKey key: temporaryExposureKeys) {
                JSONObject keyObject = new JSONObject();
                try {
                    keyObject.put("keyData",new String(key.getKeyData()));
                    keyObject.put("rollingStartIntervalNumber",key.getRollingStartIntervalNumber());
                    keyObject.put("transmissionRiskLevel",key.getTransmissionRiskLevel());
                    keyObject.put("rollingPeriod",key.getRollingPeriod());
                    keyObject.put("reportType",key.getReportType());
                    keyObject.put("daysSinceOnsetOfSymptoms",key.getDaysSinceOnsetOfSymptoms());
                    jsonResult.put(keyObject);
                } catch (JSONException e) {
                    sendError(e.getLocalizedMessage());
                    e.printStackTrace();
                }
            }
            sendSuccess(jsonResult);
        })
                .addOnFailureListener( exception -> {
                    if (exception instanceof ApiException) {
                        com.google.android.gms.common.api.Status status = ((ApiException) exception).getStatus();
                        if (status.hasResolution()) {
                            try {
                                status.startResolutionForResult(cordova.getActivity(), REQUEST_HISTORY_CODE);
                            } catch (IntentSender.SendIntentException e) {
                                e.printStackTrace();
                                sendError(e.getLocalizedMessage());
                            }
                        } else {
                            sendError(status.getStatusMessage());
                            // Handle other status.getStatusCode().
                        }
                    }
                });
    }

    private File decodeBase64Zip(String filename,String base64){
        byte[] fileData = Base64Utils.decode(base64);
        if (base64.equals("")){
            return null;
        }
        try {
            File newFile = new File(cordova.getActivity().getCacheDir()+filename+".zip");
            if (!newFile.exists()){
                newFile.createNewFile();
            }

            FileOutputStream fos = new FileOutputStream(newFile);
            fos.write(fileData);
            fos.close();
            return newFile;
        } catch (IOException e) {
            e.printStackTrace();
            sendError(e.getLocalizedMessage());
            return null;
        }
    }

    private File getFile(Uri uri) throws IOException {
        File destinationFilename = new File(cordova.getActivity().getApplicationContext().getFilesDir().getPath() + File.separatorChar + queryName(cordova.getActivity().getApplicationContext(), uri));
        try (InputStream ins = cordova.getActivity().getApplicationContext().getContentResolver().openInputStream(uri)) {
            createFileFromStream(ins, destinationFilename);
        } catch (Exception ex) {
            Log.e("Save File", ex.getMessage());
            ex.printStackTrace();
        }
        return destinationFilename;
    }

    private void createFileFromStream(InputStream ins, File destination) {
        try (OutputStream os = new FileOutputStream(destination)) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = ins.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            os.flush();
        } catch (Exception ex) {
            Log.e("Save File", ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static String queryName(Context context, Uri uri) {
        Cursor returnCursor =
                context.getContentResolver().query(uri, null, null, null, null);
        assert returnCursor != null;
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        returnCursor.moveToFirst();
        String name = returnCursor.getString(nameIndex);
        returnCursor.close();
        return name;
    }

    private void provide(JSONArray args) throws JSONException {
        List<File> exposureFiles = new ArrayList<>();

        for (int i = 0; i < args.length(); i++) {
            String name = "ExposureNotificationsDiagnosisKey"+i;
            Uri fileuri = Uri.parse(args.getString(0));
            //File newFile = decodeBase64Zip(name,args.getString(0));
            try {
                File newFile = getFile(fileuri);
                exposureFiles.add(newFile);
            }catch (IOException e){
                sendError(e.getLocalizedMessage());
            }
        }

        client.provideDiagnosisKeys(exposureFiles).addOnSuccessListener(unused -> {
            for (File file : exposureFiles) {
                file.delete();
            }
            getExposureWindow();
        }).addOnFailureListener(exception ->{
            sendError(exception.getLocalizedMessage());
        });
    }

    private void getExposureWindow() {
        client.getExposureWindows().addOnSuccessListener( exposureWindows -> {
            if (exposureWindows.isEmpty()){
                sendSuccess("No Exposures Found!");
            }else{
                sendSuccess("One or more exposures are found!");
            }
        }).addOnFailureListener(e -> {
            sendError(e.getLocalizedMessage());
        });
    }

    private void start() {
        client.isEnabled().addOnSuccessListener(isEnabled ->{
            if (isEnabled){
                sendSuccess("API Already started!");
            }else{
                client.start().addOnSuccessListener(unused -> {
                    // The app is authorized to use the Exposure Notifications API.
                    // The Exposure Notifications API started to scan (if not already doing so).
                    sendSuccess();
                }).addOnFailureListener( exception ->{
                    if (exception instanceof ApiException) {
                        com.google.android.gms.common.api.Status status = ((ApiException) exception).getStatus();
                        if (status.hasResolution()) {
                            try {
                                status.startResolutionForResult(cordova.getActivity(), REQUEST_START_CODE);
                            } catch (IntentSender.SendIntentException e) {
                                e.printStackTrace();
                                sendError(e.getLocalizedMessage());
                            }
                        } else {
                            sendError(status.getStatusMessage());
                            // Handle other status.getStatusCode().
                        }
                    }
                });
            }
        }).addOnFailureListener( exception-> sendError(exception.getLocalizedMessage()));

    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {

        switch (requestCode){
            case REQUEST_START_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    start();
                }else {
                    sendError("Resolution was rejected or cancelled!");
                }
                break;
            case REQUEST_HISTORY_CODE:
                if (resultCode == Activity.RESULT_OK){
                    retrieve();
                }else{
                    sendError("Resolution was rejected or cancelled!");
                }
            default:
                break;
        }

    }

    // Helper methods

    private void sendSuccess(boolean keepcallback) {

        PluginResult result = new PluginResult(Status.OK);
        result.setKeepCallback(keepcallback);
        callbackContext.sendPluginResult(result);
    }

    private void sendSuccess(JSONArray jsonResult) {
        PluginResult result = new PluginResult(Status.OK,jsonResult);
        result.setKeepCallback(false);
        callbackContext.sendPluginResult(result);
    }
    private void sendSuccess() {
        PluginResult result = new PluginResult(Status.OK);
        result.setKeepCallback(false);
        callbackContext.sendPluginResult(result);
    }

    private void sendSuccess(String msg) {
        Log.e(TAG, msg);
        PluginResult result = new PluginResult(Status.OK,msg);
        result.setKeepCallback(false);
        callbackContext.sendPluginResult(result);
    }

    private void sendError(String msg) {
        Log.e(TAG, msg);
        try{
            JSONObject errorResult = new JSONObject();
            errorResult.put("errorMessage", msg != null ? msg : "");
            PluginResult result = new PluginResult(Status.ERROR,errorResult);
            result.setKeepCallback(false);
            callbackContext.sendPluginResult(result);
        }catch (JSONException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }
}
