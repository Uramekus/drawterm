package org.echoline.drawterm;

import android.util.Log;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.security.spec.ECField;

public class MySurfaceView extends SurfaceView implements SurfaceHolder.Callback {
	private int screenWidth, screenHeight;
	private MainActivity mainActivity;

	public MySurfaceView(Context context, int w, int h) {
		super(context);
		this.screenWidth = w;
		this.screenHeight = h;
		mainActivity = (MainActivity)context;
		mainActivity.setWidth(screenWidth);
		mainActivity.setHeight(screenHeight);
		setWillNotDraw(true);

		getHolder().addCallback(this);

		setOnTouchListener(new View.OnTouchListener() {
			private int[] mouse = new int[3];

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				int buttons = 0;
				if (event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
					int state = event.getButtonState();
					if ((state & MotionEvent.BUTTON_PRIMARY) != 0) buttons |= 1;
					if ((state & MotionEvent.BUTTON_TERTIARY) != 0) buttons |= 2;
					if ((state & MotionEvent.BUTTON_SECONDARY) != 0) buttons |= 4;
				} else {
					CheckBox left = (CheckBox)mainActivity.findViewById(R.id.mouseLeft);
					CheckBox middle = (CheckBox)mainActivity.findViewById(R.id.mouseMiddle);
					CheckBox right = (CheckBox)mainActivity.findViewById(R.id.mouseRight);
					CheckBox up = (CheckBox)mainActivity.findViewById(R.id.mouseUp);
					CheckBox down = (CheckBox)mainActivity.findViewById(R.id.mouseDown);
					
					if (left != null && left.isChecked()) buttons |= 1;
					if (middle != null && middle.isChecked()) buttons |= 2;
					if (right != null && right.isChecked()) buttons |= 4;
					if (up != null && up.isChecked()) buttons |= 8;
					if (down != null && down.isChecked()) buttons |= 16;

					if (buttons == 0) {
						int count = event.getPointerCount();
						if (count == 1) buttons = 1;
						else if (count == 2) buttons = 4;
						else if (count >= 3) buttons = 2;
					}
				}

				int action = event.getActionMasked();
				if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_MOVE) {
					mouse[0] = Math.round(event.getX());
					mouse[1] = Math.round(event.getY());
					mouse[2] = buttons;
					mainActivity.setMouse(mouse);
				} else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
					mouse[0] = Math.round(event.getX());
					mouse[1] = Math.round(event.getY());
					mouse[2] = 0;
					mainActivity.setMouse(mouse);
				} else {
					return false;
				}
				return true;
			}
		});

		setOnGenericMotionListener(new View.OnGenericMotionListener() {
			private int[] mouse = new int[3];

			@Override
			public boolean onGenericMotion(View v, MotionEvent event) {
				if (event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
					int action = event.getActionMasked();
					if (action == MotionEvent.ACTION_HOVER_MOVE) {
						mouse[0] = Math.round(event.getX());
						mouse[1] = Math.round(event.getY());
						mouse[2] = 0;
						mainActivity.setMouse(mouse);
						return true;
					} else if (action == MotionEvent.ACTION_SCROLL) {
						float scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
						mouse[0] = Math.round(event.getX());
						mouse[1] = Math.round(event.getY());
						mouse[2] = scrollY > 0 ? 8 : 16;
						mainActivity.setMouse(mouse);
						mouse[2] = 0;
						mainActivity.setMouse(mouse);
						return true;
					}
				}
				return false;
			}
		});
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		mainActivity.setDTSurface(holder.getSurface());
	}

	private android.os.Handler resizeHandler = new android.os.Handler(android.os.Looper.getMainLooper());
	private Runnable resizeRunnable = null;

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, final int width, final int height) {
		if (resizeRunnable != null) {
			resizeHandler.removeCallbacks(resizeRunnable);
		}
		resizeRunnable = new Runnable() {
			@Override
			public void run() {
				mainActivity.setWidth(width);
				mainActivity.setHeight(height);
				mainActivity.resizeDT();
			}
		};
		resizeHandler.postDelayed(resizeRunnable, 100);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		mainActivity.setDTSurface(null);
	}

	@Override
	public boolean onCheckIsTextEditor() {
		return true;
	}

	@Override
	public android.view.inputmethod.InputConnection onCreateInputConnection(android.view.inputmethod.EditorInfo outAttrs) {
		outAttrs.inputType = android.text.InputType.TYPE_NULL;
		outAttrs.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NONE;
		return new android.view.inputmethod.BaseInputConnection(this, false);
	}
}
