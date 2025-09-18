package com.detchenkov.capacitorintents;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.MimeTypeMap;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(name = "CapacitorIntentsPlugin")
public class CapacitorIntents extends Plugin {

    private static final String LOG_TAG = "Capacitor Intents";
    private Map<String, PluginCall> watchingCalls = new HashMap<>();
    private Map<String, BroadcastReceiver> receiverMap = new HashMap<>();

    @PluginMethod(returnType = PluginMethod.RETURN_CALLBACK)
    public void registerBroadcastReceiver(PluginCall call) throws JSONException {
        call.setKeepAlive(true);
        requestBroadcastUpdates(call);
        watchingCalls.put(call.getCallbackId(), call);
    }

    @PluginMethod
    public void unregisterBroadcastReceiver(PluginCall call) {
        String callbackId = call.getString("id");
        if (callbackId != null) {
            PluginCall removed = watchingCalls.remove(callbackId);
            if (removed != null) {
                removeReceiver(callbackId);
                removed.release(bridge);
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void sendBroadcastIntent(PluginCall call) {
        String action = call.getString("action");
        JSObject extras = call.getObject("extras");
        Intent intended = new Intent(action);

        //from https://github.com/darryncampbell/darryncampbell-cordova-plugin-intent
        Map<String, Object> extrasMap = new HashMap<String, Object>();
        Bundle bundle = null;
        String bundleKey = "";
        if (extras != null) {
            try {
                JSONArray extraNames = extras.names();
                for (int i = 0; i < extraNames.length(); i++) {
                    String key = extraNames.getString(i);
                    Object extrasObj = extras.get(key);
                    if (extrasObj instanceof JSONObject) {
                        //  The extra is a bundle
                        bundleKey = key;
                        bundle = toBundle((JSONObject) extras.get(key));
                    } else {
                        extrasMap.put(key, extras.get(key));
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (bundle != null) intended.putExtra(bundleKey, bundle);

        for (String key : extrasMap.keySet()) {
            Object value = extrasMap.get(key);
            String valueStr = String.valueOf(value);
            // If type is text html, the extra text must sent as HTML
            if (key.equals(Intent.EXTRA_EMAIL)) {
                // allows to add the email address of the receiver
                intended.putExtra(Intent.EXTRA_EMAIL, new String[] { valueStr });
            } else if (key.equals(Intent.EXTRA_KEY_EVENT)) {
                // allows to add a key event object
                try {
                    JSONObject keyEventJson = new JSONObject(valueStr);
                    int keyAction = keyEventJson.getInt("action");
                    int keyCode = keyEventJson.getInt("code");
                    KeyEvent keyEvent = new KeyEvent(keyAction, keyCode);
                    intended.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                if (value instanceof Boolean) {
                    intended.putExtra(key, Boolean.valueOf(valueStr));
                } else if (value instanceof Integer) {
                    intended.putExtra(key, Integer.valueOf(valueStr));
                } else if (value instanceof Long) {
                    intended.putExtra(key, Long.valueOf(valueStr));
                } else if (value instanceof Double) {
                    intended.putExtra(key, Double.valueOf(valueStr));
                } else {
                    intended.putExtra(key, valueStr);
                }
            }
        }

        this.getContext().sendBroadcast(intended);
        call.resolve();
    }

    private Bundle toBundle(final JSONObject obj) {
        Bundle returnBundle = new Bundle();
        if (obj == null) {
            return null;
        }
        try {
            Iterator<?> keys = obj.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                Object compare = obj.get(key);
                if (obj.get(key) instanceof String) returnBundle.putString(key, obj.getString(key)); else if (
                    obj.get(key) instanceof Boolean
                ) returnBundle.putBoolean(key, obj.getBoolean(key)); else if (obj.get(key) instanceof Integer) returnBundle.putInt(
                    key,
                    obj.getInt(key)
                ); else if (obj.get(key) instanceof Long) returnBundle.putLong(key, obj.getLong(key)); else if (
                    obj.get(key) instanceof Double
                ) returnBundle.putDouble(key, obj.getDouble(key)); else if (
                    obj.get(key).getClass().isArray() || obj.get(key) instanceof JSONArray
                ) {
                    JSONArray jsonArray = obj.getJSONArray(key);
                    int length = jsonArray.length();
                    if (jsonArray.get(0) instanceof String) {
                        String[] stringArray = new String[length];
                        for (int j = 0; j < length; j++) stringArray[j] = jsonArray.getString(j);
                        returnBundle.putStringArray(key, stringArray);
                        //returnBundle.putParcelableArray(key, obj.get);
                    } else {
                        if (key.equals("PLUGIN_CONFIG")) {
                            ArrayList<Bundle> bundleArray = new ArrayList<Bundle>();
                            for (int k = 0; k < length; k++) {
                                bundleArray.add(toBundle(jsonArray.getJSONObject(k)));
                            }
                            returnBundle.putParcelableArrayList(key, bundleArray);
                        } else {
                            Bundle[] bundleArray = new Bundle[length];
                            for (int k = 0; k < length; k++) bundleArray[k] = toBundle(jsonArray.getJSONObject(k));
                            returnBundle.putParcelableArray(key, bundleArray);
                        }
                    }
                } else if (obj.get(key) instanceof JSONObject) returnBundle.putBundle(key, toBundle((JSONObject) obj.get(key)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return returnBundle;
    }

    private void requestBroadcastUpdates(final PluginCall call) throws JSONException {
        final String callBackID = call.getCallbackId();
        IntentFilter ifilt = new IntentFilter();
        JSArray filters = call.getArray("filters");
        JSArray categories = call.getArray("categories");
        // Support all Capacitor Versions
        if (filters == null || filters.length() == 0) {
            call.reject("Filters are required: at least 1 entry");
            return;
        }
        for (int i = 0; i < filters.length(); i++) {
            ifilt.addAction(filters.getString(i));
        }
        // Add Support for Categories
        if (categories != null && categories.length() > 0) {
            for (int i = 0; i < categories.length(); i++) {
                ifilt.addCategory(categories.getString(i));
            }
        }
        receiverMap.put(
            callBackID,
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    PluginCall refCall = watchingCalls.get(callBackID);
                    if (refCall != null) {
                        JSObject jsO = null;
                        try {
                            jsO = JSObject.fromJSONObject(getIntentJson(intent));
                            refCall.resolve(jsO);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            this.getContext().registerReceiver(receiverMap.get(callBackID), ifilt, Context.RECEIVER_EXPORTED);
        else
            this.getContext().registerReceiver(receiverMap.get(callBackID), ifilt);
    }

    private void removeReceiver(String callBackID) {
        this.getContext().unregisterReceiver(receiverMap.get(callBackID));
        this.receiverMap.remove(callBackID);
    }

    private static Object toJsonValue(final Object value) throws JSONException {
        //  Credit: https://github.com/napolitano/cordova-plugin-intent
        if (value == null) {
            return null;
        } else if (value instanceof Bundle) {
            final Bundle bundle = (Bundle) value;
            final JSONObject result = new JSONObject();
            for (final String key : bundle.keySet()) {
                result.put(key, toJsonValue(bundle.get(key)));
            }
            return result;
        } else if ((value.getClass().isArray())) {
            final JSONArray result = new JSONArray();
            int length = Array.getLength(value);
            for (int i = 0; i < length; ++i) {
                result.put(i, toJsonValue(Array.get(value, i)));
            }
            return result;
        } else if (value instanceof ArrayList<?>) {
            final ArrayList arrayList = (ArrayList<?>) value;
            final JSONArray result = new JSONArray();
            for (int i = 0; i < arrayList.size(); i++) result.put(toJsonValue(arrayList.get(i)));
            return result;
        } else if (
            value instanceof String ||
            value instanceof Boolean ||
            value instanceof Integer ||
            value instanceof Long ||
            value instanceof Double
        ) {
            return value;
        } else {
            return String.valueOf(value);
        }
    }

    private static JSONObject toJsonObject(Bundle bundle) {
        //  Credit: https://github.com/napolitano/cordova-plugin-intent
        try {
            return (JSONObject) toJsonValue(bundle);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Cannot convert bundle to JSON: " + e.getMessage(), e);
        }
    }

    private JSONObject getIntentJson(Intent intent) {
        //  Credit: https://github.com/darryncampbell/darryncampbell-cordova-plugin-intent
        JSONObject intentJSON = null;
        ClipData clipData = null;
        JSONObject[] items = null;
        ContentResolver cR = this.getContext().getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            clipData = intent.getClipData();
            if (clipData != null) {
                int clipItemCount = clipData.getItemCount();
                items = new JSONObject[clipItemCount];

                for (int i = 0; i < clipItemCount; i++) {
                    ClipData.Item item = clipData.getItemAt(i);

                    try {
                        items[i] = new JSONObject();
                        items[i].put("htmlText", item.getHtmlText());
                        items[i].put("intent", item.getIntent());
                        items[i].put("text", item.getText());
                        items[i].put("uri", item.getUri());

                        if (item.getUri() != null) {
                            String type = cR.getType(item.getUri());
                            String extension = mime.getExtensionFromMimeType(cR.getType(item.getUri()));

                            items[i].put("type", type);
                            items[i].put("extension", extension);
                        }
                    } catch (JSONException e) {
                        Log.d(LOG_TAG, " Error thrown during intent > JSON conversion");
                        Log.d(LOG_TAG, e.getMessage());
                        Log.d(LOG_TAG, Arrays.toString(e.getStackTrace()));
                    }
                }
            }
        }

        try {
            intentJSON = new JSONObject();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (items != null) {
                    intentJSON.put("clipItems", new JSONArray(items));
                }
            }

            intentJSON.put("type", intent.getType());
            intentJSON.put("extras", toJsonObject(intent.getExtras()));
            intentJSON.put("action", intent.getAction());
            intentJSON.put("categories", intent.getCategories());
            intentJSON.put("flags", intent.getFlags());
            intentJSON.put("component", intent.getComponent());
            intentJSON.put("data", intent.getData());
            intentJSON.put("package", intent.getPackage());

            return intentJSON;
        } catch (JSONException e) {
            Log.d(LOG_TAG, " Error thrown during intent > JSON conversion");
            Log.d(LOG_TAG, e.getMessage());
            Log.d(LOG_TAG, Arrays.toString(e.getStackTrace()));

            return null;
        }
    }
}
