package org.lindroid.ui;

import static org.lindroid.ui.KeyCodeConverter.convertKeyCode;
import static org.lindroid.ui.NativeLib.nativeDisplayDestroyed;
import static org.lindroid.ui.NativeLib.nativeKeyEvent;
import static org.lindroid.ui.NativeLib.nativeTouchEvent;
import static org.lindroid.ui.NativeLib.nativePointerButtonEvent;
import static org.lindroid.ui.NativeLib.nativePointerMotionEvent;
import static org.lindroid.ui.NativeLib.nativePointerScrollEvent;
import static org.lindroid.ui.NativeLib.nativeReconfigureInputDevice;
import static org.lindroid.ui.NativeLib.nativeStopInputDevice;
import static org.lindroid.ui.NativeLib.nativeSurfaceChanged;
import static org.lindroid.ui.NativeLib.nativeSurfaceCreated;
import static org.lindroid.ui.NativeLib.nativeSurfaceDestroyed;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.Intent;
import android.view.InputDevice;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.os.IBinder;
import android.os.ServiceManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import vendor.lindroid.perspective.IPerspective;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnTouchListener, View.OnHoverListener, View.OnGenericMotionListener {
    private static final String TAG = "Lindroid";
    private static final String mContainerName = "default";
    private static final long DISPLAY_ID = 0;
    private IPerspective mPerspective;

    private static final String AIDL_SERVICE_NAME = "perspective";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        if (!HardwareService.isInstanceCreated()) {
            startService(new Intent(this, HardwareService.class));
        }
        SurfaceView sv = findViewById(R.id.surfaceView);
        SurfaceHolder sh = sv.getHolder();
        sv.setOnTouchListener(this);
        sv.setOnHoverListener(this);
        sv.setOnGenericMotionListener(this);
        sh.addCallback(this);

        // Hide pointer icon
        sv.setPointerIcon(PointerIcon.getSystemIcon(this, PointerIcon.TYPE_NULL));

        // Get perspective service
        final IBinder binder = ServiceManager.getService(AIDL_SERVICE_NAME);
        if (binder == null) {
            Log.e(TAG, "Failed to get binder from ServiceManager");
            new MaterialAlertDialogBuilder(this)
                .setTitle("Unsupported System")
                .setMessage("This system does not support Lindroid.")
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .setIcon(R.drawable.ic_warning)
                .show();
            return;
        } else {
            mPerspective = IPerspective.Stub.asInterface(binder);
        }

        // Check if container is running
        try {
            if(!mPerspective.isRunning(mContainerName)) {
                new MaterialAlertDialogBuilder(this)
                    .setTitle("Container isn't running")
                    .setMessage("Lindroid container currently isnt running, do you want to start it?.")
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        try {
                            mPerspective.start(mContainerName);
                        } catch (RemoteException e) {
                            Log.e(TAG, "RemoteException in start", e);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                    })
                    .setIcon(R.drawable.ic_help)
                    .show();
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in isRunning", e);
        }
    }

    @Override
    public void onBackPressed() {
        try {
            if(mPerspective.isRunning(mContainerName)) {
                new MaterialAlertDialogBuilder(this)
                    .setTitle("Stop Linux subsystem")
                    .setMessage("Do you want to stop Linux subsystem?")
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        try {
                            mPerspective.stop(mContainerName);
                        } catch (RemoteException e) {
                            Log.e(TAG, "RemoteException in start", e);
                        }
                        dialog.dismiss();
                        finish();
                    })
                    .setNeutralButton(android.R.string.cancel, (dialog, which) -> {
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.no, (dialog, which) -> {
                        super.onBackPressed();
                    })
                    .show();
            } else {
                finish();
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in isRunning", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Lets never destroy primary display
        if (DISPLAY_ID != 0) {
            nativeDisplayDestroyed(DISPLAY_ID);
        }
    }

    @Override
    public boolean onGenericMotion(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_SCROLL) {
            int vScroll = (int) motionEvent.getAxisValue(MotionEvent.AXIS_VSCROLL);
            int hScroll = (int) motionEvent.getAxisValue(MotionEvent.AXIS_HSCROLL);

            if (vScroll != 0)
                nativePointerScrollEvent(DISPLAY_ID, vScroll, true);
            else if (hScroll != 0)
                nativePointerScrollEvent(DISPLAY_ID, hScroll, false);
        }

        if (motionEvent.getAction() == MotionEvent.ACTION_BUTTON_PRESS ||
                motionEvent.getAction() == MotionEvent.ACTION_BUTTON_RELEASE) {
            int x = (int) motionEvent.getX();
            int y = (int) motionEvent.getY();
            boolean isDown = motionEvent.getAction() == MotionEvent.ACTION_BUTTON_PRESS;
            switch (motionEvent.getActionButton()) {
                case MotionEvent.BUTTON_PRIMARY:
                    nativePointerButtonEvent(DISPLAY_ID, 0x110, x, y, isDown);
                    break;
                case MotionEvent.BUTTON_SECONDARY:
                    nativePointerButtonEvent(DISPLAY_ID, 0x111, x, y, isDown);
                    break;
                case MotionEvent.BUTTON_TERTIARY:
                    nativePointerButtonEvent(DISPLAY_ID, 0x112, x, y, isDown);
                    break;
                case MotionEvent.BUTTON_BACK:
                    nativePointerButtonEvent(DISPLAY_ID, 0x116, x, y, isDown);
                    break;
                case MotionEvent.BUTTON_FORWARD:
                    nativePointerButtonEvent(DISPLAY_ID, 0x115, x, y, isDown);
                    break;
            }
        }
        return true;
    }

    @Override
    public boolean onHover(View view, MotionEvent motionEvent) {
        int x = (int) motionEvent.getX(0);
        int y = (int) motionEvent.getY(0);
        nativePointerMotionEvent(DISPLAY_ID, x, y);
        return true;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if(motionEvent.getSource() == InputDevice.SOURCE_MOUSE) {
            onGenericMotion(view, motionEvent);
            return onHover(view, motionEvent);
        }

        int pointerCount = motionEvent.getPointerCount();
        for (int i = 0; i < pointerCount; i++) {
            int pointerId = motionEvent.getPointerId(i);
            int action = motionEvent.getActionMasked();
            int x = (int) motionEvent.getX(i);
            int y = (int) motionEvent.getY(i);
            int pressure = (int) motionEvent.getPressure(i);
            if (action == MotionEvent.ACTION_MOVE ||
                    action == MotionEvent.ACTION_DOWN ||
                    action == MotionEvent.ACTION_UP)
                nativeTouchEvent(DISPLAY_ID, pointerId, action, pressure, x, y);
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        nativeKeyEvent(DISPLAY_ID, convertKeyCode(keyCode), true);
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        nativeKeyEvent(DISPLAY_ID, convertKeyCode(keyCode), false);
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        Surface surface = holder.getSurface();
        if (surface != null) {
            nativeSurfaceCreated(DISPLAY_ID, surface);
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int w, int h) {
        Surface surface = holder.getSurface();
        if (surface != null) {
            nativeStopInputDevice(DISPLAY_ID);
            nativeSurfaceChanged(DISPLAY_ID, surface);
            nativeReconfigureInputDevice(DISPLAY_ID, w, h);
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        Surface surface = holder.getSurface();
        if (surface != null) {
            nativeSurfaceDestroyed(DISPLAY_ID, surface);
            nativeStopInputDevice(DISPLAY_ID);
        }
    }
}
