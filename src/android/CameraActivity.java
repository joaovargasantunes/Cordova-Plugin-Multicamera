/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cordova.plugin.multicamera;

import android.support.v4.app.Fragment;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.WindowManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class CameraActivity extends AppCompatActivity {
	public JSONArray images = new JSONArray();
	public JSONArray files = new JSONArray();
	private static final int ERROR_CODE = 0;
	private static final int SUCCESS_CODE = 1;
	public static final String TAG = "PluginMulticamera";
	public void adicionarArquivo(String absolutePath){
		// Log.d(TAG,"Novo arquivo: "+absolutePath);
		this.files.put(absolutePath);
	}
	public void adicionarImagem(String encodedImage){
		this.images.put(encodedImage);
	}
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		// Log.d(TAG,"CameraActivity onCreate");
        super.onCreate(savedInstanceState);
		// Log.d(TAG,"CameraActivity 2");
		// setContentView(R.layout.activity_camera);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			/*
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
							  WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
			*/
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		setContentView(this.getResources().getIdentifier("activity_camera", "layout", this.getPackageName()));
		// Log.d(TAG,"CameraActivity 3");
        if (null == savedInstanceState) {
			// Log.d(TAG,"CameraActivity 4");
            // getSupportFragmentManager().beginTransaction().replace(R.id.container, Camera2BasicFragment.newInstance()).commit();
			getSupportFragmentManager().beginTransaction().replace(this.getResources().getIdentifier("container", "id", this.getPackageName()), Camera2BasicFragment.newInstance()).commit();
        }else{
			// Log.d(TAG,"CameraActivity 5");
		}
    }
	@Override
	public void onStart(){
		// Log.d(TAG,"CameraActivity onStart");
		super.onStart();
		// Log.d(TAG,"CameraActivity onStart 2");
        Bundle extras = getIntent().getExtras();
		// Log.d(TAG,"CameraActivity onStart 3");
        if (extras == null) {
            // Log.d(TAG, "onStart without action");
            sendActivityResult(9, "called without action");
        } else {
			// Log.d(TAG,"CameraActivity onStart 4");
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                // Log.d(TAG, " onStartBundle extras -> "+String.format("%s %s (%s)", key,value.toString(), value.getClass().getName()));
            }
			// Log.d(TAG,"CameraActivity onStart 5");
        }
	}
	@Override
	public void onPause(){
		// Log.d(TAG,"CameraActivity onPause!");
		super.onPause();
	}
	@Override
	public void onStop(){
		// Log.d(TAG,"CameraActivity onStop!");
		super.onStop();
	}
	@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
		// Log.d(TAG,"CameraActivity onActivityResult");
		// Log.d(TAG,"CameraActivity requestCode: "+requestCode);
		// Log.d(TAG,"CameraActivity resultCode: "+resultCode);
		Bundle extras = data.getExtras();
		// Log.d(TAG,"CameraActivity onActivityResult 2");
		for (String key : extras.keySet()) {
			Object value = extras.get(key);
			// Log.d(TAG, "onActivityResult Bundle extras -> "+String.format("%s %s (%s)", key,value.toString(), value.getClass().getName()));
		}
		// Log.d(TAG,"CameraActivity onActivityResult 3");
		switch(requestCode){
			case ERROR_CODE:
			case SUCCESS_CODE:
				switch(resultCode){
					case AppCompatActivity.RESULT_OK:
						// Log.d(TAG,"CameraActivity onActivityResult 4");
						try {
							// Log.d(TAG,"CameraActivity onActivityResult 5");
							// Log.d(TAG,"CameraActivity tirei "+this.images.length()+" foto(s)");
							// Log.d(TAG,"CameraActivity tirei "+this.files.length()+" foto(s)");
							// Log.d(TAG,"CameraActivity onActivityResult 6");
							JSONObject obj = new JSONObject();
							// Log.d(TAG,"CameraActivity onActivityResult 7");
							// obj.put("fotos",this.images.toString());
							obj.put("fotos",this.files.toString());
							// Log.d(TAG,"CameraActivity onActivityResult 8");
							sendActivityResult(AppCompatActivity.RESULT_OK, obj.toString());
						} catch(Exception ex) {
							// Log.d(TAG,"CameraActivity onActivityResult exception");
							ex.printStackTrace();
						}
						break;
					case AppCompatActivity.RESULT_CANCELED:
						// Log.d(TAG,"CameraActivity onActivityResult 9");
						// Log.d(TAG, "Resultado cancelado!");
                        sendActivityResult(AppCompatActivity.RESULT_CANCELED, "user cancelled");
						break;
					default:
						// Log.d(TAG,"CameraActivity onActivityResult default");
                        sendActivityResult(resultCode, "Unknown");
						break;

				}
		}
	}

	public void removeAllFragments(){
		for (Fragment fragment:getSupportFragmentManager().getFragments()) {
			if(fragment != null){
				getSupportFragmentManager().beginTransaction().remove(fragment).commit();
			}
		}
	}

	public void sendActivityResult(int resultCode, String response) {
		// Log.d(TAG,"CameraActivity sendActivityResult... resultCode: "+resultCode);
		// Log.d(TAG,"CameraActivity sendActivityResult... response: "+response);
        Intent intent = new Intent();
		// Log.d(TAG,"CameraActivity sendActivityResult... 1");
        intent.putExtra("data", response);
		// Log.d(TAG,"CameraActivity sendActivityResult... 2");
        setResult(resultCode, intent);
		// Log.d(TAG,"CameraActivity sendActivityResult... 3");
		removeAllFragments();
        finish();// Exit of this activity !
		// Log.d(TAG,"CameraActivity sendActivityResult... 4");
    }

    public void sendActivityResultJSON(int resultCode,JSONArray response) {
		// Log.d(TAG,"CameraActivity sendActivityResultJSON... resultCode: "+resultCode);
		// Log.d(TAG,"CameraActivity sendActivityResultJSON... response: "+response.toString());
        Intent intent = new Intent();
		// Log.d(TAG,"CameraActivity sendActivityResultJSON 1");
        intent.putExtra("data", response.toString());
		// Log.d(TAG,"CameraActivity sendActivityResultJSON 2");
        setResult(resultCode, intent);
		// Log.d(TAG,"CameraActivity sendActivityResultJSON 3");
		removeAllFragments();
        finish();// Exit of this activity !
		// Log.d(TAG,"CameraActivity sendActivityResultJSON 4");
	}

}
