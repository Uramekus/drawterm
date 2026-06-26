package org.echoline.drawterm;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Environment;

import android.app.Activity;

import android.app.Notification;
import android.app.NotificationChannel; 
import android.app.NotificationManager;

import android.content.Intent;
import android.app.PendingIntent;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.Surface;
import android.view.inputmethod.InputMethodManager;
import android.view.KeyEvent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.graphics.ImageFormat;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.File;
import java.util.Map;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.ArrayList;

public class MainActivity extends Activity {
	private Map<String, ?> map;
	private MainActivity mainActivity;
	String applicationId,sessionId;
	private boolean dtrunning = false;
	private DrawTermThread dthread;
	private int notificationId;
	private CameraDevice cameraDevice = null;
	private byte []jpegBytes;

	static {
		System.loadLibrary("drawterm");
	}

	public void showNotification(String text) {
		Intent i = new Intent(this, MainActivity.class);
		i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		int flags = PendingIntent.FLAG_ONE_SHOT;
		if (android.os.Build.VERSION.SDK_INT >= 23) { // M
			flags |= PendingIntent.FLAG_IMMUTABLE;
		}
		PendingIntent pi = PendingIntent.getActivity(this,
        0 /* Request code */,
        i,
        flags);

		NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

		if (android.os.Build.VERSION.SDK_INT >= 26) { // O
			NotificationChannel channel = new NotificationChannel("drawterm", "Drawterm Errors", NotificationManager.IMPORTANCE_HIGH);
			notificationManager.createNotificationChannel(channel);
		}

		Notification.Builder builder;
		if (android.os.Build.VERSION.SDK_INT >= 26) {
			builder = new Notification.Builder(MainActivity.this, "drawterm");
		} else {
			builder = new Notification.Builder(MainActivity.this);
		}

		builder.setSmallIcon(R.drawable.ic_small)
			.setContentText(text)
			.setContentIntent(pi)
			.setStyle(new Notification.BigTextStyle().bigText(text));

		notificationManager.notify(notificationId, builder.build());
		notificationId++;
	}

	public void showConnectionNotification() {
		Intent serviceIntent = new Intent(this, DrawtermService.class);
		if (android.os.Build.VERSION.SDK_INT >= 26) {
			startForegroundService(serviceIntent);
		} else {
			startService(serviceIntent);
		}
	}

	public void hideConnectionNotification() {
		Intent serviceIntent = new Intent(this, DrawtermService.class);
		serviceIntent.putExtra("stop", true);
		startService(serviceIntent);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		if (intent.getBooleanExtra("disconnect", false)) {
			hideConnectionNotification();
			if (dtrunning) {
				Intent restartIntent = new Intent(this, MainActivity.class);
				restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
				startActivity(restartIntent);
				System.exit(0);
			}
		}
	}

	@Override
	public void onBackPressed() {
		if (!dtrunning) {
			if (findViewById(R.id.servers) == null) {
				setContentView(R.layout.activity_main);
				populateServers(this);
				return;
			}
		}
		super.onBackPressed();
	}

	public int numCameras() {
		try {
			return ((CameraManager)getSystemService(Context.CAMERA_SERVICE)).getCameraIdList().length;
		} catch (CameraAccessException e) {
			Log.w("drawterm", e.toString());
			return 0;
		}
	}

	public void takePicture(int id) {
		try {
			if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
				requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 1);
				return;
			}
			final HandlerThread mBackgroundThread = new HandlerThread("Camera Background");
			mBackgroundThread.start();
			final Handler mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
			CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
			String []cameraIdList = manager.getCameraIdList();
			manager.openCamera(cameraIdList[id], new CameraDevice.StateCallback() {
				public void onOpened(CameraDevice device) {
					cameraDevice = device;
					try {
						ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
						final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
						captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
						captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);
						captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
						captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getWindowManager().getDefaultDisplay().getRotation());
						captureBuilder.addTarget(reader.getSurface());
						reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
							public void onImageAvailable(ImageReader reader) {
								Image image = null;
								try {
									image = reader.acquireLatestImage();
									ByteBuffer buffer = image.getPlanes()[0].getBuffer();
									jpegBytes = new byte[buffer.capacity()];
									buffer.get(jpegBytes);
								} catch (Exception e) {
									Log.w("drawterm", e.toString());
								} finally {
									if (image != null) {
										image.close();
									}
								}
							}
						}, mBackgroundHandler);
						List<Surface> outputSurfaces = new ArrayList<Surface>(1);
						outputSurfaces.add(reader.getSurface());
						cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
							public void onConfigured(CameraCaptureSession session) {
								try {
									List<CaptureRequest> captureRequests = new ArrayList<CaptureRequest>(10);
									for (int i = 0; i < 10; i++)
										captureRequests.add(captureBuilder.build());
									session.captureBurst(captureRequests, new CameraCaptureSession.CaptureCallback() {
										public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber) {
											try {
												sendPicture(jpegBytes);
												mBackgroundThread.quitSafely();
												mBackgroundThread.join();
												cameraDevice.close();
											} catch (Exception e) {
												Log.w("drawterm", e.toString());
											}
										}
									}, mBackgroundHandler);
								} catch (CameraAccessException e) {
									e.printStackTrace();
								}
							}
							public void onConfigureFailed(CameraCaptureSession session) {
							}
						}, mBackgroundHandler);
					} catch (Exception e) {
						Log.w("drawterm", e.toString());
					}
				}
				public void onDisconnected(CameraDevice device) {
					if (cameraDevice != null)
						cameraDevice.close();
					cameraDevice = null;
				}
				public void onError(CameraDevice device, int error) {
					if (cameraDevice != null)
						cameraDevice.close();
					cameraDevice = null;
				}
			}, mBackgroundHandler);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void serverView(View v) {
		setContentView(R.layout.server_main);
		serverButtons();

		String s = (String)map.get(((TextView)v).getText().toString());
		String []a = s.split("\007");

		((EditText)findViewById(R.id.cpuServer)).setText((String)a[0]);
		((EditText)findViewById(R.id.authServer)).setText((String)a[1]);
		((EditText)findViewById(R.id.userName)).setText((String)a[2]);
		if (a.length > 3)
			((EditText)findViewById(R.id.passWord)).setText((String)a[3]);

		((EditText)findViewById(R.id.aliasName)).setText(((TextView)v).getText().toString());
	}

	public void populateServers(Context context) {
		ListView ll = (ListView)findViewById(R.id.servers);
		ArrayAdapter<String> la = new ArrayAdapter<String>(this, R.layout.item_main);
		SharedPreferences settings = getSharedPreferences("DrawtermPrefs", 0);
		map = (Map<String, ?>)settings.getAll();
		String key;
		Object []keys = map.keySet().toArray();
		for (int i = 0; i < keys.length; i++) {
			key = (String)keys[i];
			la.add(key);
		}
		ll.setAdapter(la);

		ll.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
				serverView(view);
			}
		});

		ll.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
				String key = (String) parent.getItemAtPosition(position);
				SharedPreferences settings = getSharedPreferences("DrawtermPrefs", 0);
				SharedPreferences.Editor editor = settings.edit();
				editor.remove(key);
				editor.commit();
				populateServers(MainActivity.this);
				return true;
			}
		});

		setDTSurface(null);
		dtrunning = false;
	}

	public void runDrawterm(String []args, String pass) {
		//window dimensions, Activity.getResources().getConfiguration().screenWidth
		//https://developer.android.com/topic/arc/window-management
		// this is fucked up ^

		WindowMetrics windowMetrics = getWindowManager().getCurrentWindowMetrics();
		int ww = windowMetrics.getBounds().width();
		int wh = windowMetrics.getBounds().height();

		setContentView(R.layout.drawterm_main);

		Button kbutton = findViewById(R.id.keyboardToggle);
		updatePeripheralUI();

		final MySurfaceView mView = new MySurfaceView(mainActivity, ww, wh);
		mView.setFocusableInTouchMode(true);
		mView.setFocusable(true);
		mView.requestFocus();

		android.widget.FrameLayout l = (android.widget.FrameLayout)findViewById(R.id.dlayout);
		l.addView(mView, 0, new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

		updatePeripheralUI();

		kbutton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View view) {
				InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
				mView.requestFocus();
				imm.showSoftInput(mView, InputMethodManager.SHOW_IMPLICIT);
			}
		});

		dthread = new DrawTermThread(args, pass, mainActivity);
		dthread.start();

		dtrunning = true;
		showConnectionNotification();
	}

	public void serverButtons() {
		Button button = (Button)findViewById(R.id.save);
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String cpu = ((EditText)findViewById(R.id.cpuServer)).getText().toString();
				String auth = ((EditText)findViewById(R.id.authServer)).getText().toString();
				String user = ((EditText)findViewById(R.id.userName)).getText().toString();
				String pass = ((EditText)findViewById(R.id.passWord)).getText().toString();
				String alias = ((EditText)findViewById(R.id.aliasName)).getText().toString().trim();

				if (alias.isEmpty()) {
					alias = user + "@" + cpu + " (auth="  + auth + ")";
					((EditText)findViewById(R.id.aliasName)).setText(alias);
				}

				SharedPreferences settings = getSharedPreferences("DrawtermPrefs", 0);
				SharedPreferences.Editor editor = settings.edit();
				editor.putString(alias, cpu + "\007" + auth + "\007" + user + "\007" + pass);
				editor.commit();

				setContentView(R.layout.activity_main);
				populateServers(MainActivity.this);
			}
		});

		android.widget.CheckBox passToggle = findViewById(R.id.passToggle);
		if (passToggle != null) {
			passToggle.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
					EditText passWord = findViewById(R.id.passWord);
					if (isChecked) {
						passWord.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
					} else {
						passWord.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
					}
					passWord.setSelection(passWord.getText().length());
				}
			});
		}

		button = (Button) findViewById(R.id.connect);
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View view) {
				String cpu = ((EditText)findViewById(R.id.cpuServer)).getText().toString();
				String auth = ((EditText)findViewById(R.id.authServer)).getText().toString();
				String user = ((EditText)findViewById(R.id.userName)).getText().toString();
				String pass = ((EditText)findViewById(R.id.passWord)).getText().toString();

				String args[] = {"drawterm", "-p", "-h", cpu, "-a", auth, "-u", user};
				runDrawterm(args, pass);
			}
		});
	}

	private void checkAllPermissions() {
		android.content.SharedPreferences prefs = getSharedPreferences("DrawtermPrefs", 0);
		if (prefs.getBoolean("askedPermissions", false)) return;
		prefs.edit().putBoolean("askedPermissions", true).commit();
		
		if (android.os.Build.VERSION.SDK_INT >= 23) {
			java.util.List<String> perms = new java.util.ArrayList<String>();
			if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED)
				perms.add(android.Manifest.permission.CAMERA);
			if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)
				perms.add(android.Manifest.permission.RECORD_AUDIO);
			if (android.os.Build.VERSION.SDK_INT < 30 && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED)
				perms.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
				
			if (!perms.isEmpty()) {
				requestPermissions(perms.toArray(new String[0]), 1);
			}
		}
		
		if (android.os.Build.VERSION.SDK_INT >= 30) {
			if (!android.os.Environment.isExternalStorageManager()) {
				try {
					android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
					intent.addCategory("android.intent.category.DEFAULT");
					intent.setData(android.net.Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
					startActivity(intent);
				} catch (Exception e) {
					android.content.Intent intent = new android.content.Intent();
					intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
					startActivity(intent);
				}
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		if (android.os.Build.VERSION.SDK_INT >= 30) {
			getWindow().setDecorFitsSystemWindows(false);
			getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
			getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
		} else {
			View decorView = getWindow().getDecorView();
			decorView.setSystemUiVisibility(
				View.SYSTEM_UI_FLAG_LAYOUT_STABLE
				| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
			getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
			getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
		}
		
		mainActivity = this;
		setObject();
		setContentView(R.layout.activity_main);
		populateServers(this);

		View fab = findViewById(R.id.fab);
		fab.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setContentView(R.layout.server_main);
				serverButtons();
			}
		});

		android.hardware.input.InputManager inputManager = (android.hardware.input.InputManager) getSystemService(Context.INPUT_SERVICE);
		inputManager.registerInputDeviceListener(inputDeviceListener, null);

		new android.os.Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				checkAllPermissions();
			}
		}, 500);
	}

	private android.hardware.input.InputManager.InputDeviceListener inputDeviceListener = new android.hardware.input.InputManager.InputDeviceListener() {
		@Override
		public void onInputDeviceAdded(int deviceId) { updatePeripheralUI(); }
		@Override
		public void onInputDeviceRemoved(int deviceId) { updatePeripheralUI(); }
		@Override
		public void onInputDeviceChanged(int deviceId) { updatePeripheralUI(); }
	};

	public void setCursor(final int[] pixels, final int hotX, final int hotY) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (android.os.Build.VERSION.SDK_INT >= 24) {
					android.widget.FrameLayout l = (android.widget.FrameLayout)findViewById(R.id.dlayout);
					if (l != null && l.getChildCount() > 0) {
						View mView = l.getChildAt(0);
						if (mView instanceof MySurfaceView) {
							android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(pixels, 16, 16, android.graphics.Bitmap.Config.ARGB_8888);
							android.view.PointerIcon icon = android.view.PointerIcon.create(bitmap, hotX, hotY);
							mView.setPointerIcon(icon);
						}
					}
				}
			}
		});
	}

	public void updatePeripheralUI() {
		if (!dtrunning) return;

		boolean hasKeyboard = getResources().getConfiguration().keyboard != Configuration.KEYBOARD_NOKEYS;
		boolean hasMouse = false;
		int[] deviceIds = android.view.InputDevice.getDeviceIds();
		for (int id : deviceIds) {
			android.view.InputDevice device = android.view.InputDevice.getDevice(id);
			if (device != null && (device.getSources() & android.view.InputDevice.SOURCE_MOUSE) == android.view.InputDevice.SOURCE_MOUSE) {
				hasMouse = true;
				break;
			}
		}

		Button kbutton = findViewById(R.id.keyboardToggle);
		if (kbutton != null) {
			kbutton.setVisibility(hasKeyboard ? View.GONE : View.VISIBLE);
		}

		View[] mouseBtns = new View[] {
			findViewById(R.id.mouseLeft),
			findViewById(R.id.mouseMiddle),
			findViewById(R.id.mouseRight),
			findViewById(R.id.mouseUp),
			findViewById(R.id.mouseDown)
		};
		for (View v : mouseBtns) {
			if (v != null) {
				v.setVisibility(hasMouse ? View.GONE : View.VISIBLE);
			}
		}

		View dtButtons = findViewById(R.id.dtButtons);
		if (dtButtons != null) {
			if (hasKeyboard && hasMouse) {
				dtButtons.setVisibility(View.GONE);
			} else {
				dtButtons.setVisibility(View.VISIBLE);
			}
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		updatePeripheralUI();
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event)
	{
		if (!dtrunning) {
			return super.dispatchKeyEvent(event);
		}

		int k = event.getUnicodeChar();
		if (k == 0) {
			k = event.getDisplayLabel();
			if (k >= 'A' && k <= 'Z')
				k |= 0x20;
		}
		String chars = event.getCharacters();
		if (k == 0 && chars != null) {
			for (int i = 0; i < chars.length(); i++) {
				k = chars.codePointAt(i);
				keyDown(k);
				keyUp(k);
			}
			return true;
		}

		if (k == 0) switch (event.getKeyCode()) {
		case KeyEvent.KEYCODE_DEL:
			k = 0x0008;
			break;
		case KeyEvent.KEYCODE_FORWARD_DEL:
			k = 0x007F;
			break;
		case KeyEvent.KEYCODE_ESCAPE:
			k = 0x001B;
			break;
		case KeyEvent.KEYCODE_MOVE_HOME:
			k = 0xF00D;
			break;
		case KeyEvent.KEYCODE_MOVE_END:
			k = 0xF018;
			break;
		case KeyEvent.KEYCODE_PAGE_UP:
			k = 0xF00F;
			break;
		case KeyEvent.KEYCODE_PAGE_DOWN:
			k = 0xF013;
			break;
		case KeyEvent.KEYCODE_INSERT:
			k = 0xF014;
			break;
		case KeyEvent.KEYCODE_SYSRQ:
			k = 0xF010;
			break;
		case KeyEvent.KEYCODE_DPAD_UP:
			k = 0xF00E;
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			k = 0xF011;
			break;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			k = 0xF012;
			break;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			k = 0xF800;
			break;
		}

		if (k == 0)
			return true;

		if (event.isCtrlPressed()) {
			keyDown(0xF017);
		}
		if (event.isAltPressed() && k < 128) {
			keyDown(0xF015);
		}

		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			keyDown(k);
		}
		else if (event.getAction() == KeyEvent.ACTION_UP) {
			keyUp(k);
		}

		if (event.isCtrlPressed()) {
			keyUp(0xF017);
		}
		if (event.isAltPressed() && k < 128) {
			keyUp(0xF015);
		}

		return true;
	}

	@Override
	public void onDestroy()
	{
		android.hardware.input.InputManager inputManager = (android.hardware.input.InputManager) getSystemService(Context.INPUT_SERVICE);
		inputManager.unregisterInputDeviceListener(inputDeviceListener);

		setDTSurface(null);
		dtrunning = false;
		hideConnectionNotification();
		exitDT();
		super.onDestroy();
	}

	public void setClipBoard(final String str) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ClipboardManager cm = (ClipboardManager)getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
				if (cm != null) {
					ClipData cd = ClipData.newPlainText(null, str);
					cm.setPrimaryClip(cd);
				}
			}
		});
	}

	public String getClipBoard() {
		final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
		final String[] result = new String[]{""};
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ClipboardManager cm = (ClipboardManager)getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
				if (cm != null) {
					ClipData cd = cm.getPrimaryClip();
					if (cd != null && cd.getItemCount() > 0) {
						CharSequence text = cd.getItemAt(0).coerceToText(mainActivity.getApplicationContext());
						if (text != null) {
							result[0] = text.toString();
						}
					}
				}
				latch.countDown();
			}
		});
		try {
			latch.await(1, java.util.concurrent.TimeUnit.SECONDS);
		} catch (InterruptedException e) {}
		return result[0];
	}

	public native void dtmain(Object[] args);
	public native void setPass(String arg);
	public native void setWidth(int arg);
	public native void setHeight(int arg);
	public native void setDTSurface(Surface surface);
	public native void resizeDT();
	public native void setMouse(int[] args);
	public native void setObject();
	public native void keyDown(int c);
	public native void keyUp(int c);
	public native void exitDT();
	public native void sendPicture(byte[] array);
}
