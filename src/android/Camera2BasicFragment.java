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

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.SensorManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.util.TimingLogger;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;

public class Camera2BasicFragment extends Fragment implements View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";
	private JSONArray images = new JSONArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Tag for the {@link Log}.
     */
	private static final String TAG = "PluginMulticamera";

	/**
     * Maximum number of images for the ImageReader.
     */
	private static final int MAX_CAPTURE_IMAGES = 12;

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
	 * 
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

	};

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link android.util.Size} of camera preview.
     */
	private Size mPreviewSize;
	
	/**
     * The {@link android.util.Size} of camera preview.
     */
    private int countFotos = 0;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

		@Override
		public void onClosed(@NonNull CameraDevice cameraDevice) {
			// Log.d(TAG,"CameraDevice.StateCallback... onClosed");
			mCameraOpenCloseLock.release();
			mCameraDevice = null;
		}

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
			// Log.d(TAG,"CameraDevice.StateCallback... onOpened");
            // This method is called when the camera is opened.  We start camera preview here.
            // mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
			// Log.d(TAG,"CameraDevice.StateCallback... onDisconnected");
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
			// Log.d(TAG,"CameraDevice.StateCallback... onError: "+error);
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * This is the output file for our picture.
     */
    private File mFile;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
		// final Activity activity = getActivity();
        @Override
        public void onImageAvailable(ImageReader reader) {
			// mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
			// mBackgroundHandler.post(new ImageSaver(reader.acquireLatestImage(), mFile));
            try{
                mBackgroundHandler.post(new Runnable() {
                    private final Image rImage = reader.acquireLatestImage();
                    /**
                    * The file we save the image into.
                    */
                    private final File rFile = mFile;
                    @Override
                    public void run () {
                        // rImage = reader.acquireLatestImage();
                        // rFile = mFile;
                        if(rImage && rImage.getPlanes()){
                            Image.Plane[] planes = rImage.getPlanes();
                            if(planes.length < 1 || planes[0].getBuffer() == null){
                                return;
                            }
                        }else{
                            return;
                        }
                        ByteBuffer buffer = planes[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        // rImage.close();
                        FileOutputStream output = null;
                        try {
                            output = new FileOutputStream(rFile);
                            output.write(bytes);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            rImage.close();
                            if (null != output) {
                                try {
                                    output.close();
                                    String encodedImage = Base64.encodeToString(bytes, Base64.DEFAULT);
                                    addFile(rFile.getAbsolutePath());
                                    showToast("Foto tirada com sucesso.");
                                    // showImageView(rImage);
                                    // Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                    // mImageView.setImageBitmap(bitmap);
                                    // addBase64(encodedImage);
                                    // this.images.put(encodedImage);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        // make operation on UI - on example
                        // on progress bar.
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;


	/**
	 * Orientation event listener
	 */

	private OrientationEventListener orientationEventListener;

	/**
	 *  Holds current bitmap rotate angle
	*/

	private int currentRotation;

	/**
	 *  Holds current orientation value
	*/

	public static final String CAMERA_FRONT = "1";
	public static final String CAMERA_BACK = "0";
    private boolean isFlashSupported;
	private boolean isTorchOn = true;

	private int currentOrientation;
	/**
	 * Holds device default orientation
	*/
	private int defaultOrientation;
    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    }else if(afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
                            || afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED){
                        unlockFocus();
                    }else if(afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                            || afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED){
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }else{
                        //0,1,2 nao da pau
                        // Log.d("CAMERA_LOG","afState desconhecido (deu pau): "+afState);
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // Log.d("CAMERA_LOG","STATE_WAITING_PRECAPTURE");
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // Log.d("CAMERA_LOG","STATE_WAITING_NON_PRECAPTURE");
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            // Log.d(TAG,"onCaptureFailed: "+failure.getReason());
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
	}
	
	public int getDeviceDefaultOrientation() {
		Activity activity = getActivity();
		WindowManager windowManager =  (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
		Configuration config = getResources().getConfiguration();
		int rotation = windowManager.getDefaultDisplay().getRotation();
		if ( ((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) &&
				config.orientation == Configuration.ORIENTATION_LANDSCAPE)
			|| ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) &&    
				config.orientation == Configuration.ORIENTATION_PORTRAIT)) {
		  return Configuration.ORIENTATION_LANDSCAPE;
		} else { 
		  return Configuration.ORIENTATION_PORTRAIT;
		}
	}

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static int dpToPx(int dp, Context ctx) {
        float density = ctx.getResources()
                .getDisplayMetrics()
                .density;
        return Math.round((float) dp * density);
    }

    private void showImageView(final File f, int bitmapRotation){
        final Activity activity = getActivity();
        if(activity != null){
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
					// Camera2BasicFragment.this.countFotos++;
					// TextView text1 = (TextView) activity.findViewById(activity.getResources().getIdentifier("text1", "id", activity.getPackageName()));
					// text1.setText(Camera2BasicFragment.this.countFotos == 1 ? "1 foto" : Camera2BasicFragment.this.countFotos+" fotos");
					Context ctx = getContext();
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(f.getAbsolutePath(),options);
                    int imageHeight = options.outHeight;
                    int imageWidth = options.outWidth;
                    String imageType = options.outMimeType;
                    BitmapFactory.Options opts = new BitmapFactory.Options();
					int squareDim = dpToPx(48,ctx);
					// Log.d(TAG,"dpToPx(48,ctx): "+squareDim);
					Integer reqWidth = new Integer(squareDim);
					Integer reqHeight = new Integer(squareDim);
					// Log.d(TAG,"reqWidth: "+reqWidth);
					// Log.d(TAG,"reqHeight: "+reqHeight);
                    opts.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
					// Log.d(TAG,"opts.inSampleSize: "+opts.inSampleSize);
					opts.inJustDecodeBounds = false;
					/*Matrix matrix = new Matrix();
					matrix.postRotate(bitmapRotation);*/
					Bitmap myBitmap = BitmapFactory.decodeFile(f.getAbsolutePath(),opts);
					// myBitmap = Bitmap.createBitmap(myBitmap,0,0,myBitmap.getWidth(),myBitmap.getHeight(),matrix,true);
                    ImageView imgView = new ImageView(ctx);
                    imgView.setImageBitmap(myBitmap);
                    imgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
					// ScrollView hsv = (ScrollView) activity.findViewById(activity.getResources().getIdentifier("hsv", "id", activity.getPackageName()));
					LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(squareDim,squareDim);
                    layout.setMargins(dpToPx(16,ctx),dpToPx(16,ctx),dpToPx(16,ctx),dpToPx(16,ctx));
                    imgView.setLayoutParams(layout);
					LinearLayout ln = (LinearLayout) activity.findViewById(activity.getResources().getIdentifier("gallery", "id", activity.getPackageName()));
					ln.addView(imgView,0);
                }
            });
        }
	}

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
            int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                    option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            // Log.d("TEXTURE_VIEW","Retornou aqui: "+Collections.min(bigEnough, new CompareSizesByArea()));
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            // Log.d("TEXTURE_VIEW","Retornou embaixo: "+Collections.max(notBigEnough, new CompareSizesByArea()));
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static Camera2BasicFragment newInstance() {
		Camera2BasicFragment fragment = new Camera2BasicFragment();
		fragment.setRetainInstance(true);
		return fragment;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		// Log.d(TAG, "Camera2BasicFragment onCreate()");
	}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
		return inflater.inflate(getActivity().getResources().getIdentifier("fragment_camera2_basic", "layout", getActivity().getPackageName()),container,false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
		final CameraActivity activity = ((CameraActivity) getActivity());
		view.findViewById(activity.getResources().getIdentifier("picture", "id", activity.getPackageName())).setOnClickListener(this);
		mTextureView = (AutoFitTextureView) view.findViewById(activity.getResources().getIdentifier("texture", "id", activity.getPackageName()));
		Button back = activity.findViewById(activity.getResources().getIdentifier("back", "id", activity.getPackageName()));
		back.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				activity.sendActivityResult(Activity.RESULT_CANCELED, "[]");
				closeCamera();
			}
		});
		Button confirm = activity.findViewById(activity.getResources().getIdentifier("confirm", "id", activity.getPackageName()));
		confirm.setClickable(false);
		confirm.setEnabled(false);
		confirm.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				activity.sendActivityResult(Activity.RESULT_OK, activity.files.toString());
				closeCamera();
			}
		});
		// int idButtonFlash = activity.getResources().getIdentifier("button_flash", "id", activity.getPackageName());
		// flashButton = (Button) view.findViewById(idButtonFlash);
		Button flashButton = activity.findViewById(activity.getResources().getIdentifier("button_flash", "id", activity.getPackageName()));
		flashButton.setRotation(270);
		flashButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setupFlashButton();
			}
		});
		// Log.d(TAG, "Camera2BasicFragment onViewCreated 3");
		orientationEventListener = new OrientationEventListener(activity, SensorManager.SENSOR_DELAY_NORMAL) {
			@Override
			public void onOrientationChanged(int angle) {
				if(angle >= 315 || angle <= 44){
					currentRotation = 90;
				}else if(angle >= 45 && angle <= 134){
					currentRotation = 180;
				}else if(angle >= 135 && angle <= 224){
					currentRotation = 270;
				}else if(angle >= 225 && angle <= 314){
					currentRotation = 0;
				}
			}
		};
		orientationEventListener.enable();
		this.defaultOrientation = this.getDeviceDefaultOrientation();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
		// Log.d(TAG, "Camera2BasicFragment onActivityCreated");
		super.onActivityCreated(savedInstanceState);
		// Log.d(TAG, "Camera2BasicFragment onActivityCreated 2");
        mFile = new File(getActivity().getExternalFilesDir(null), System.currentTimeMillis()+".jpg");
		// Log.d(TAG, "Camera2BasicFragment onActivityCreated 3");
        // Log.d(TAG,"ANDRE - mFile: "+mFile.toString());
    }

	@Override
	public void onStart(){
		super.onStart();
	}

    @Override
    public void onResume() {
		super.onResume();
		startBackgroundThread();
		if(orientationEventListener != null){
			orientationEventListener.enable();
		}

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
	}

    @Override
    public void onPause() {
		// Log.d(TAG,"Camera2BasicFragment onPause!");
		if(orientationEventListener != null){
			orientationEventListener.disable();
		}
        closeCamera();
		stopBackgroundThread();
		super.onPause();
    }

    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
				Activity activity = getActivity();
				Resources resources = activity.getResources();
				int idRequestPermission = resources.getIdentifier("request_permission", "string", activity.getPackageName());
				ErrorDialog.newInstance(getString(idRequestPermission)).show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
			// Log.d(TAG,"setUpCameraOutputs("+width+","+height+")");
            for (String cameraId : manager.getCameraIdList()) {
				// Log.d(TAG,"cameraId: "+cameraId);
				CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
				// Log.d(TAG,"setUpCameraOutputs 1");
                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
				}
				// Log.d(TAG,"setUpCameraOutputs 2");
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
				}
				// Log.d(TAG,"setUpCameraOutputs 3");
				// For still image captures, we use the largest available size.
				/*List sizeList = Arrays.asList(map.getOutputSizes(ImageFormat.JPEG));
				for(int i=0;i<sizeList.size();i++){
					Size size = sizeList.get(i);
					// Log.d(TAG,"sizeList["+i+"]: "+size.getWidth()+"x"+size.getHeight());
				}*/
				Point displaySize = new Point();
				activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
				// Log.d(TAG,"setUpCameraOutputs 4");
				int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;
				Size[] sizeArray = map.getOutputSizes(ImageFormat.JPEG);
				// Log.d(TAG,"setUpCameraOutputs 5");
				for (Size size : sizeArray){
					// Log.d(TAG,"size: "+size.getWidth()+"x"+size.getHeight());
				}
				// Log.d(TAG,"setUpCameraOutputs 6");
				Size largest = Collections.max(Arrays.asList(sizeArray),new CompareSizesByArea());
				Size FULL_HD = new Size(MAX_PREVIEW_WIDTH,MAX_PREVIEW_HEIGHT);
				// Log.d(TAG,"optimal params... ");
				// Log.d(TAG,"width: "+width);
				// Log.d(TAG,"height: "+height);
				// Log.d(TAG,"maxPreviewWidth: "+MAX_PREVIEW_WIDTH);
				// Log.d(TAG,"maxPreviewHeight: "+MAX_PREVIEW_HEIGHT);
				// Log.d(TAG,"aspectRatio: "+FULL_HD.getWidth()+"x"+FULL_HD.getHeight());
				Size optimal  = chooseOptimalSize(sizeArray,width,height,MAX_PREVIEW_WIDTH,MAX_PREVIEW_HEIGHT,FULL_HD);
				// Log.d(TAG,"largest: "+largest.getWidth()+"x"+largest.getHeight());
				// Log.d(TAG,"optimal: "+optimal.getWidth()+"x"+optimal.getHeight());
                mImageReader = ImageReader.newInstance(optimal.getWidth(), optimal.getHeight(),ImageFormat.JPEG, MAX_CAPTURE_IMAGES);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
				int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
				// Log.d("ORIENTATION","displayRotation: "+displayRotation);
                //noinspection ConstantConditions
				mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
				// Log.d("ORIENTATION","mSensorOrientation: "+mSensorOrientation);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0: //0
                    case Surface.ROTATION_180: //2
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90: //1
                    case Surface.ROTATION_270: //3
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
					maxPreviewHeight = displaySize.x;
					// Log.d("ORIENTATION", "rotatedPreviewWidth: "+rotatedPreviewWidth);
					// Log.d("ORIENTATION", "rotatedPreviewHeight: "+rotatedPreviewHeight);
					// Log.d("ORIENTATION", "maxPreviewWidth: "+maxPreviewWidth);
					// Log.d("ORIENTATION", "maxPreviewHeight: "+maxPreviewHeight);
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);
				// Log.d(TAG,"setUpCameraOutputs... mPreviewSize: "+mPreviewSize.getWidth()+"x"+mPreviewSize.getHeight());
                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int correctedHeight = (int) (largest.getWidth()/(maxPreviewWidth/(double)maxPreviewHeight));
				int orientation = getResources().getConfiguration().orientation;
				// Log.d(TAG,"setUpCameraOutputs orientation... getResources().getConfiguration().orientation: "+orientation);
				/*
				if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    //mTextureView.setLayoutParams(new FrameLayout.LayoutParams(largest.getWidth(), correctedHeight));
                } else {
                    mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
				}
				*/

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
				mFlashSupported = available == null ? false : available;
				mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
			// Log.d(TAG,"Deu merda aqui!");
            e.printStackTrace();
        } catch (NullPointerException e) {
			// Log.d(TAG,"Ou deu merda aqui!");
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
			Resources resources = activity.getResources();
			int idCameraerror = resources.getIdentifier("camera_error", "string", activity.getPackageName());
			ErrorDialog.newInstance(getString(idCameraerror)).show(getChildFragmentManager(), FRAGMENT_DIALOG);
			// ErrorDialog.newInstance(getActivity().getResources().getIdentifier("camera_error", "string", getActivity().getPackageName())).show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    /**
     * Opens the camera specified by {@link Camera2BasicFragment#mCameraId}.
     */
    private void openCamera(int width, int height) {
		// Log.d("OPENCAMERA","openCamera("+width+","+height+")");
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
		}
        setUpCameraOutputs(width, height);
		configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
		}
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);
            // We set up a CaptureRequest.Builder with the output Surface.
			// mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
			mPreviewRequestBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 70);
            mPreviewRequestBuilder.addTarget(surface);
            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
				new CameraCaptureSession.StateCallback() {
					@Override
					public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
						// The camera is already closed
						if (null == mCameraDevice) {
							return;
						}
						// When the session is ready, we start displaying the preview.
						mCaptureSession = cameraCaptureSession;
						try {
							// Auto focus should be continuous for camera preview.
							mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
							// Flash is automatically enabled when necessary.
							setFlash(mPreviewRequestBuilder);
							// setAutoFlash(mPreviewRequestBuilder);

							// Finally, we start displaying the camera preview.
							mPreviewRequest = mPreviewRequestBuilder.build();
							mCaptureSession.setRepeatingRequest(mPreviewRequest,mCaptureCallback,mBackgroundHandler);
							mCameraOpenCloseLock.release();										
						} catch (CameraAccessException e) {
							e.printStackTrace();
						}
					}
					@Override
					public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
					}
				}, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
		}
        mTextureView.setTransform(matrix);
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        lockFocus();
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_START);
			// Tell #mCaptureCallback to wait for the lock.
			setFlash(mPreviewRequestBuilder);
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            // Use the same AE and AF modes as the preview.
			captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
			setFlash(captureBuilder);
            // setAutoFlash(captureBuilder);
            // Orientation
			int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
			// Log.d("ORIENTATION","getResources().getConfiguration().orientation: "+getResources().getConfiguration().orientation);
			// Log.d("ORIENTATION","captureStillPicture rotation: "+rotation);
			// int jpegOrientation = getOrientation(rotation);
			int jpegOrientation = getOrientation(currentRotation);
			// Log.d("ORIENTATION","captureStillPicture jpegOrientation: "+jpegOrientation);
			// int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
			// String message = "\njpegOrientation: "+jpegOrientation;
			// InfoDialog.newInstance(message).show(getChildFragmentManager(), FRAGMENT_DIALOG);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, currentRotation);
            // Log.d(TAG,"ANTES DA CAPTURE SESSION: "+System.currentTimeMillis());
            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
					int bitmapRotation = currentRotation;
					unlockFocus();
					// Log.d(TAG,"DEPOIS DO UNLOCK FOCUS: "+System.currentTimeMillis());
                    showImageView(mFile,bitmapRotation);
					mFile = new File(getActivity().getExternalFilesDir(null), System.currentTimeMillis()+".jpg");
                }
            };
            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
	}

	public void setFlash(CaptureRequest.Builder requestBuilder){
		if(isTorchOn){
			// requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
			requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
			requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
		}else{
			// requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
			requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
			requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
		}
	}

	public void setupFlashButton() {
		Activity activity = getActivity();
		Button flashButton = activity.findViewById(activity.getResources().getIdentifier("button_flash", "id", activity.getPackageName()));
		if (mCameraId.equals(CAMERA_BACK) && mFlashSupported) {
			flashButton.setVisibility(View.VISIBLE);
			if (isTorchOn) {
				int drawableFlashOff = activity.getResources().getIdentifier("ic_flash_off", "drawable", activity.getPackageName());
				// flashButton.setImageResource(drawableFlashOff);
				flashButton.setBackgroundResource(drawableFlashOff);
				isTorchOn = false;
			} else {
				int drawableFlashOn = activity.getResources().getIdentifier("ic_flash_on", "drawable", activity.getPackageName());
				// flashButton.setImageResource(drawableFlashOn);
				flashButton.setBackgroundResource(drawableFlashOn);
				isTorchOn = true;
			}
		} else {
			flashButton.setVisibility(View.GONE);
		}
	}
	

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
			setFlash(mPreviewRequestBuilder);
            // setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View view) {
		Activity activity = getActivity();
		int idPicture = activity.getResources().getIdentifier("picture", "id", activity.getPackageName());
		int idInfo = activity.getResources().getIdentifier("info", "id", activity.getPackageName());
		if(view.getId() == idPicture){
			takePicture();
		}else if(view.getId() == idInfo && null != activity){
			new AlertDialog.Builder(activity)
				.setMessage(activity.getResources().getIdentifier("intro_message", "string", activity.getPackageName()))
				.setPositiveButton(android.R.string.ok, null)
				.show();
		}
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

	public void addBase64(String encodedImage){
		CameraActivity ca = ((CameraActivity) getActivity());
		ca.adicionarImagem(encodedImage);
		Button confirm = ca.findViewById(ca.getResources().getIdentifier("confirm", "id", ca.getPackageName()));
		confirm.setClickable(true);
		confirm.setEnabled(true);
	}

	public void addFile(String absolutePath){
		CameraActivity ca = ((CameraActivity) getActivity());
		ca.adicionarArquivo(absolutePath);
		this.countFotos++;
		ca.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// Stuff that updates the UI
				TextView text1 = (TextView) ca.findViewById(ca.getResources().getIdentifier("text1", "id", ca.getPackageName()));
				text1.setText(Camera2BasicFragment.this.countFotos == 1 ? "1 foto" : Camera2BasicFragment.this.countFotos+" fotos");
				Button confirm = ca.findViewById(ca.getResources().getIdentifier("confirm", "id", ca.getPackageName()));
				confirm.setClickable(true);
				confirm.setEnabled(true);
			}
		});
	}

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        ImageSaver(Image image, File file) {
            mImage = image;
            // Log.d(TAG,"ANDRE - mImage: "+mImage.getWidth()+"x"+mImage.getHeight());
            // Log.d(TAG,"ANDRE - File: "+file.toString());
            // Log.d(TAG,"ANDRE - File absolutePath: "+file.getAbsolutePath());
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
						// String encodedImage = Base64.encodeToString(bytes, Base64.DEFAULT);
						// this.images.put(encodedImage);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

	}
	
	public static class InfoDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static InfoDialog newInstance(String message) {
            InfoDialog dialog = new InfoDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
			final Activity activity = getActivity();
			AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
			dialog.setMessage(getArguments().getString(ARG_MESSAGE));
			dialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialogInterface, int i) {
					dismiss();
				}
			});
			return dialog.create();
        }

    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
					.setMessage(getActivity().getResources().getIdentifier("request_permission", "string", getActivity().getPackageName()))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            parent.requestPermissions(new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Activity activity = parent.getActivity();
							if (activity != null) {
								activity.finish();
							}
						}
					})
                    .create();
        }
    }

}
