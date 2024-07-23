package com.example.otgcam;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.example.otgcam.USB_PERMISSION";
    private static final String TAG = "OTGCam";

    private UsbManager usbManager;
    private UsbDevice usbDevice;
    private UsbDeviceConnection usbConnection;
    private UsbInterface usbInterface;

    private RelativeLayout relativeLayout;
    private ScrollView scrollView;
    private TextView messageTextView;
    private ImageView imageView;

    private CameraConfig cameraConfig;

    private Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            setupDevice(device);
                        }
                    } else {
                        showMessage("USB permission denied");
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = (ImageView) findViewById(R.id.imageView);
        messageTextView = (TextView) findViewById(R.id.messageTextView);
        messageTextView.setVisibility(View.GONE);
        scrollView = (ScrollView) findViewById(R.id.scrollView);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);


        new Thread(new Runnable() {
            @Override
            public void run() {
                findCamera();
            }
        }).start();
    }

    private void findCamera() {
        showMessage("Searching for camera...");

        HashMap<String, UsbDevice> deviceList = (HashMap<String, UsbDevice>) usbManager.getDeviceList();
        for (Map.Entry<String, UsbDevice> entry : deviceList.entrySet()) {
            UsbDevice device = entry.getValue();
            showMessage("Device found: " + device.getDeviceName());

            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface usbInterface = device.getInterface(i);
                showMessage("Interface " + i + ": Class = " + usbInterface.getInterfaceClass() +
                        ", SubClass = " + usbInterface.getInterfaceSubclass());

                for (int j = 0; j < usbInterface.getEndpointCount(); j++) {
                    UsbEndpoint endpoint = usbInterface.getEndpoint(j);
                    showMessage("Endpoint " + j + ": Address = " + endpoint.getAddress() +
                            ", Attributes = " + endpoint.getAttributes() +
                            ", Direction = " + endpoint.getDirection() +
                            ", Type = " + endpoint.getType());

                    if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                        showMessage("Trying to open device with interface " + i + " and endpoint " + j);
                        usbDevice = device;
                        this.usbInterface = usbInterface;
                        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                        usbManager.requestPermission(usbDevice, permissionIntent);
                        return; // Exit after finding the device
                    }
                }
            }
        }
        showMessage("No suitable USB camera found");
    }

    private void setupDevice(UsbDevice device) {
        showMessage("Setting up device: " + device.getDeviceName());

        usbConnection = usbManager.openDevice(device);
        if (usbConnection != null && usbConnection.claimInterface(usbInterface, true)) {
            showMessage("Device connected and interface claimed");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    findSuitableEndpoint();
                }
            }).start();
        } else {
            showMessage("Failed to claim interface or open device");
        }
    }

    private int currentInterfaceIndex = 0;
    private int currentEndpointIndex = 0;

    private void findSuitableEndpoint() {
        if (usbInterface == null || usbConnection == null) {
            showMessage("UsbInterface or UsbConnection is null");
            return;
        }

        interval(new Task() {
            @Override
            public boolean execute() {
                if (currentInterfaceIndex >= usbDevice.getInterfaceCount()) {
                    showMessage("All interfaces and endpoints checked");
                    return true;
                }

                UsbInterface usbInterface = usbDevice.getInterface(currentInterfaceIndex);
                if (currentEndpointIndex >= usbInterface.getEndpointCount()) {
                    currentInterfaceIndex++;
                    currentEndpointIndex = 0;
                    return false;
                }

                UsbEndpoint endpoint = usbInterface.getEndpoint(currentEndpointIndex);
                showMessage("Testing interface " + currentInterfaceIndex + ", endpoint " + currentEndpointIndex +
                        ": Address = " + endpoint.getAddress() +
                        ", Type = " + endpoint.getType());

                switch (endpoint.getType()) {
                    case UsbConstants.USB_ENDPOINT_XFER_BULK:
                        testBulkTransfer(endpoint);
                        break;
                    case UsbConstants.USB_ENDPOINT_XFER_ISOC:
                        testIsochronousTransfer(endpoint);
                        break;
                    case UsbConstants.USB_ENDPOINT_XFER_CONTROL:
                        showMessage("Control transfer not supported");
                        break;
                    case UsbConstants.USB_ENDPOINT_XFER_INT:
                        testInterruptTransfer(endpoint);
                        break;
                    default:
                        showMessage("Unknown endpoint type: " + endpoint.getType());
                        break;
                }

                currentEndpointIndex++;
                return false;
            }
        });
    }

    private void testBulkTransfer(UsbEndpoint endpoint) {
        showMessage("Testing BULK transfer");
        byte[] buffer = new byte[512];
        int received = usbConnection.bulkTransfer(endpoint, buffer, buffer.length, 1000);
        if (received > 0) {
            processFrame(buffer, received);
        } else {
            showMessage("No data received in BULK transfer");
        }
    }

    private void testIsochronousTransfer(UsbEndpoint endpoint) {
        showMessage("Testing ISOCHRONOUS transfer");
        UsbRequest request = new UsbRequest();
        request.initialize(usbConnection, endpoint);
        ByteBuffer buffer = ByteBuffer.allocate(512);
        request.queue(buffer, buffer.capacity());
        if (usbConnection.requestWait() != null) {
            processFrame(buffer.array(), buffer.position());
        } else {
            showMessage("No data received in ISOCHRONOUS transfer");
        }
    }

    private void testInterruptTransfer(UsbEndpoint endpoint) {
        showMessage("Testing INTERRUPT transfer");
        byte[] buffer = new byte[512]; // Reduced buffer size
        int received = usbConnection.bulkTransfer(endpoint, buffer, buffer.length, 1000);
        if (received > 0) {
            processFrame(buffer, received);
        } else {
            showMessage("No data received in INTERRUPT transfer");
        }
    }

    private static void interval(Task task) {
        boolean shouldContinue = task.execute();

        if (!shouldContinue) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    interval(task);
                }
            }, 100);
        }
    }

    interface Task {
        boolean execute();
    }



    private void processFrame(byte[] buffer, int length) {
        showMessage("Processing frame, length: " + length);

        YuvImage yuvImage = new YuvImage(buffer, ImageFormat.YUY2, cameraConfig.getWidth(), cameraConfig.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, cameraConfig.getWidth(), cameraConfig.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        final Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                imageView.setImageBitmap(bitmap);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
        if (usbConnection != null) {
            usbConnection.releaseInterface(usbInterface);
            usbConnection.close();
        }
    }

    private ArrayList<String> messageQueue = new ArrayList<>();
    private boolean isDisplaying = false;
    private StringBuilder displayedMessages = new StringBuilder();

    private void showMessage(final String message) {
        messageQueue.add(message);
        if (!isDisplaying) {
            displayNextMessage();
        }
    }

    private void displayNextMessage() {
        if (!messageQueue.isEmpty()) {
            isDisplaying = true;
            final String currentMessage = messageQueue.remove(0);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (displayedMessages.length() > 0) {
                        displayedMessages.append("\n");
                    }
                    displayedMessages.append(currentMessage);
                    messageTextView.setText(displayedMessages.toString());
                    messageTextView.setVisibility(View.VISIBLE);
                    scrollView.scrollTo(0, scrollView.getBottom());
                }
            });

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!messageQueue.isEmpty()) {
                        displayNextMessage();
                    } else {
                        isDisplaying = false;
                    }
                }
            }, 100); // 100ms delay
        } else {
            isDisplaying = false;
        }
    }
}
