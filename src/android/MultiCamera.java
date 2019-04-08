/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package cordova.plugin.multicamera;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

public class MultiCamera extends CordovaPlugin {

    public static final String TAG = "PluginMulticamera";

    public static final int open = 1;

    private static final String ActivityName = "cordova.plugin.multicamera.CameraActivity";

    private CallbackContext callback;

    /**
     * Constructor.
     */
    public MultiCamera() {
		// Log.d(TAG,"MultiCamera constructor!");
    }

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		// Log.d(TAG,"MultiCamera initialize!");
        super.initialize(cordova, webView);

        // Log.d(TAG, "Initializing MultiCamera");
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArry of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  True if the action was valid, false if not.
     */
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		// Log.d(TAG,"MultiCamera exec!");
		// Log.d(TAG,"action: "+action);
        callback = callbackContext;
        if(action.equals("open")) {
			// Log.d(TAG,"Sim, action = "+action);
            try {
				// Log.d(TAG,"Antes de criar o Intent.");
				// Log.d(TAG,"Atividade: "+ActivityName);
				Context context = cordova.getActivity().getApplicationContext();
                // Intent intent = new Intent(ActivityName);
				Intent intent = new Intent(context, CameraActivity.class);
				// Log.d(TAG,"Antes de criar o Intent");
                // Send some info to the activity to retrieve it later
				// Log.d(TAG,"intent.putExtra('action'): "+MultiCamera.open);
                intent.putExtra("action", MultiCamera.open);
				// Log.d(TAG,"Antes do cordova.startActivityForResult.");
                cordova.startActivityForResult((CordovaPlugin) this, intent, MultiCamera.open);
				// Log.d(TAG,"Antes do result.");
                PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
				// Log.d(TAG,"Depois do result.");
                result.setKeepCallback(true);
				// Log.d(TAG,"Depois do result.setKeepCallback.");
                callback.sendPluginResult(result);
				// Log.d(TAG,"Depois do callback.sendPluginResult.");
                // Log.d(TAG, "Open started");
            } catch(Exception ex) {
				// Log.d(TAG, "execute Exception");
				ex.printStackTrace();
			}
        } else {
            return false;   // Returning false results in a "MethodNotFound" error
        }

        return true;
    }

    /**
     * CameraActivity result
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
		// Log.d(TAG,"MultiCamera onActivityResult... requestCode: "+requestCode+" | resultCode: "+resultCode);
		if(data != null){
			// Log.d(TAG,"MultiCamera onActivityResult... 1");
			Bundle extras = data.getExtras();
			// Log.d(TAG,"MultiCamera onActivityResult... 2");
			for (String key : extras.keySet()) {
				// Log.d(TAG,"MultiCamera onActivityResult... xyz");
				Object value = extras.get(key);
				// Log.d(TAG, "MultiCamera onActivityResult Bundle extras -> "+String.format("%s %s (%s)", key,value.toString(), value.getClass().getName()));
			}
			// Log.d(TAG,"MultiCamera onActivityResult 3");
		}
		// Log.d(TAG,"MultiCamera onActivityResult... 4");
        switch(requestCode) {
            case MultiCamera.open:
				Bundle extras = new Bundle();
				if(data != null){
					extras = data.getExtras();
				}
				// Log.d(TAG,"MultiCamera onActivityResult 3");
                if(resultCode == cordova.getActivity().RESULT_OK) {
					// Log.d(TAG,"MultiCamera onActivityResult 4");
                    // boolean result = extras.getString("data").equals("true");
                    // Log.d(TAG,"MultiCamera onActivityResult extras: "+extras.toString());
                    callback.sendPluginResult(new PluginResult(PluginResult.Status.OK, extras.getString("data")));
					// Log.d(TAG,"MultiCamera onActivityResult 5");
                } else if(resultCode == 0) {
					// Log.d(TAG,"MultiCamera onActivityResult 6");
                    // callback.sendPluginResult(new PluginResult(PluginResult.Status.OK, null));
					callback.sendPluginResult(new PluginResult(PluginResult.Status.NO_RESULT,""));
					String error = "Erro desconhecido";
					if(extras.containsKey("data") && !extras.getString("data").equals(null)){
						error = extras.getString("data");
					}
					sendError(error);
				}else{
					// Log.d(TAG,"MultiCamera onActivityResult 7");
					String error = "Erro desconhecido";
					if(extras.containsKey("data") && !extras.getString("data").equals(null)){
						error = extras.getString("data");
					}
					sendError(extras.getString("data"));
				}

                return;
            default:
                // Handle other results if exists.
                super.onActivityResult(requestCode, resultCode, data);
        }
	}
	
	@Override
	public void onDestroy(){
		// Log.d(TAG, "CordovaActivity.onDestroy()");
		super.onDestroy();
	}

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    private void sendError(String error) {
		// Log.d(TAG,"MultiCamera sendError: "+error);
        callback.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, error));
    }
}
