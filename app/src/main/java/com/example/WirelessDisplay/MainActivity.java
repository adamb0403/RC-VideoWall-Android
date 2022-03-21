package com.example.WirelessDisplay;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import android.content.Intent;
import android.app.Activity;
import android.widget.ImageView;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Color;
import android.widget.Toast;
import android.widget.NumberPicker;

import com.bumptech.glide.Glide;
import com.waynejo.androidndkgif.GifDecoder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    ActivityResultLauncher<String[]> mPermissionResultLauncher;
    private boolean isStoragePermissionGranted = false;
    private boolean isLocationPermissionGranted = false;
//    private boolean isConnectPermissionGranted = false;

    private static final int SELECT_DEVICE_REQUEST_CODE = 0;
    private final static int CONNECTING_STATUS = 1; // used in bluetooth handler to identify message status
    private final static int MESSAGE_READ = 2;
    private final static int BT_WRITE = 3;
    private final static int BT_CANCEL = 4;

    private static BluetoothSocket HC05socket;
    public static Handler handler;

    static int IMAGE_COUNTER = 0;
    static int SLIDESHOW_TIME = 5;
    static String[] textImage = new String[200];
    Button getimagebtn, getgifbtn, clearSel, slideshowTime, btbtn, disconnectbtn, sendBluetooth;
    ImageView imageV;
    TextView tv, btText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPermissionResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {

            if (result.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) != null){

                isStoragePermissionGranted = result.get(Manifest.permission.WRITE_EXTERNAL_STORAGE);

            }

            if (result.get(Manifest.permission.ACCESS_FINE_LOCATION) != null){

                isLocationPermissionGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);

            }

//            if (result.get(Manifest.permission.BLUETOOTH_CONNECT) != null){
//
//                isConnectPermissionGranted = result.get(Manifest.permission.BLUETOOTH_CONNECT);
//
//            }

        });

        requestPermission();

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(MainActivity.this, "Bluetooth is not supported on this device.", Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(MainActivity.this, "Bluetooth is compatible on this device.", Toast.LENGTH_SHORT).show();
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivity(enableBtIntent); // Does not support API > 30. Add Connect permission to support android11
            }
        }

        clearSel=findViewById(R.id.clearSelection);
        getimagebtn=findViewById(R.id.getimagebutton);
        getgifbtn=findViewById(R.id.getgifbutton);
        imageV=findViewById(R.id.imageView);
        tv=findViewById(R.id.textView);
        btText=findViewById(R.id.BTtext);
        slideshowTime=findViewById(R.id.setSlideshowTime);
        btbtn=findViewById(R.id.btbutton);
        disconnectbtn=findViewById(R.id.disconnectBt);
        sendBluetooth=findViewById(R.id.sendBT);

        sendBluetooth.setEnabled(false);
        updateImageSelectedText(0);

        ActivityResultLauncher<Intent> getImage = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        Uri uri = data.getData();
                        Bitmap image = obtainBitmap(uri);
                        image = resizeBitmap(image);
                        obtainPixels(image, 0, 0);
                        IMAGE_COUNTER++;
                        updateImageSelectedText(0);
                        getgifbtn.setEnabled(false);
//                        sendBluetooth.setEnabled(true);
                        bluetoothButtonCheck();
                    }
                });

        ActivityResultLauncher<Intent> getGif = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        Uri uri = data.getData();
                        setClearSelection();
                        editGif(uri);
                        updateImageSelectedText(1);
                        SLIDESHOW_TIME = 0;
                        getimagebtn.setEnabled(false);
//                        sendBluetooth.setEnabled(true);
                        bluetoothButtonCheck();
                    }
                });

        getimagebtn.setOnClickListener(view -> {
            Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            getImage.launch(intent);
        });

        getgifbtn.setOnClickListener(view -> {
            Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/gif");
            getGif.launch(intent);
        });

        sendBluetooth.setOnClickListener(view -> {
            new ConnectedThread(HC05socket).write();
        });

        btbtn.setOnClickListener(view -> {
            companionDeviceManager();
        });

        disconnectbtn.setOnClickListener(view -> {
            new ConnectedThread(HC05socket).cancel();
        });

        slideshowTime.setOnClickListener(view -> setSlideshowTime());

        clearSel.setOnClickListener(view -> {
            setClearSelection();
            getimagebtn.setEnabled(true);
            getgifbtn.setEnabled(true);
            bluetoothButtonCheck();
        });

        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case CONNECTING_STATUS:
                        switch (msg.arg1) {
                            case 1:
                                btText.setText("Bluetooth: Connected");
                                Toast.makeText(MainActivity.this, "Successfully connected bluetooth.", Toast.LENGTH_SHORT).show();
                                btbtn.setEnabled(false);
                                disconnectbtn.setEnabled(true);
                                bluetoothButtonCheck();
                                break;
                            case -1:
                                btText.setText("Bluetooth: Connection failed");
                                Toast.makeText(MainActivity.this, "Could not initiate bluetooth connection.", Toast.LENGTH_SHORT).show();
                                btbtn.setEnabled(true);
                                disconnectbtn.setEnabled(false);
                                break;
                        }
                        break;

                    case MESSAGE_READ:
                        switch (msg.arg1) {
                            case 1:
                                String arduinomsg = msg.obj.toString();
                                tv.setText(arduinomsg);
                                break;
                            case -1:

                                break;
                        }
                        break;

                    case BT_WRITE:
                        switch (msg.arg1) {
                            case 1:

                                break;
                            case -1:
                                Toast.makeText(MainActivity.this, "Error occurred when sending data... Closing bluetooth connection", Toast.LENGTH_LONG).show();
                                new ConnectedThread(HC05socket).cancel();
                                break;
                        }
                        break;

                    case BT_CANCEL:
                        switch (msg.arg1) {
                            case 1:
                                btText.setText("Bluetooth: Disconnected");
                                Toast.makeText(MainActivity.this, "Successfully disconnected bluetooth.", Toast.LENGTH_SHORT).show();
                                btbtn.setEnabled(true);
                                disconnectbtn.setEnabled(false);
                                bluetoothButtonCheck();
                                break;
                            case -1:
                                Toast.makeText(MainActivity.this, "Could not disconnect bluetooth.", Toast.LENGTH_SHORT).show();
                                break;
                        }
                        break;
                }
            }
        };
    }

    private void requestPermission(){

        isStoragePermissionGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;

        isLocationPermissionGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

//        isConnectPermissionGranted = ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.BLUETOOTH_CONNECT
//        ) == PackageManager.PERMISSION_GRANTED;

        List<String> permissionRequest = new ArrayList<String>();

        if (!isStoragePermissionGranted){

            permissionRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        }

        if (!isLocationPermissionGranted){

            permissionRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);

        }

//        if (!isConnectPermissionGranted){
//
//            permissionRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
//
//        }

        if (!permissionRequest.isEmpty()){

            mPermissionResultLauncher.launch(permissionRequest.toArray(new String[0]));

        }
    }

    public void bluetoothButtonCheck() {
        sendBluetooth.setEnabled(HC05socket != null && textImage[0] != null);
    }

    public void updateImageSelectedText(int x) {
        switch (x) {
            case 0:
                tv.setText("Images Selected: " + IMAGE_COUNTER);
                break;
            case 1:
                tv.setText("Frames: " + IMAGE_COUNTER);
                break;
        }
    }

    public void setClearSelection() {
        IMAGE_COUNTER = 0;
        Arrays.fill(textImage, null);
        imageV.setImageDrawable(null);
        updateImageSelectedText(0);
    }

    public void setSlideshowTime() {
        final AlertDialog.Builder popup = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.slideshow, null);
        popup.setTitle("Seconds Per Image:");
        popup.setView(dialogView);
        final NumberPicker slideshowTime = dialogView.findViewById(R.id.slideshowTime);

        slideshowTime.setMaxValue(100);
        slideshowTime.setMinValue(0);
        slideshowTime.setValue(SLIDESHOW_TIME);
        slideshowTime.setWrapSelectorWheel(true);

        popup.setPositiveButton("Set", (dialog, id) -> SLIDESHOW_TIME = slideshowTime.getValue());
        popup.setNegativeButton("Cancel", (dialog, id) -> {
            // User cancelled the dialog
        });

        AlertDialog alertDialog = popup.create();
        alertDialog.show();
    }

    public void companionDeviceManager() {
        CompanionDeviceManager deviceManager =
                (CompanionDeviceManager) getSystemService(
                        Context.COMPANION_DEVICE_SERVICE
                );

        // To skip filtering based on name and supported feature flags,
        // don't include calls to setNamePattern() and addServiceUuid(),
        // respectively. This example uses Bluetooth.
        BluetoothDeviceFilter deviceFilter =
                new BluetoothDeviceFilter.Builder()
                        .setNamePattern(Pattern.compile("WIRELESSDISPLAY"))
//                        .addServiceUuid(
//                                new ParcelUuid(new UUID(0x123abcL, -1L)), null
//                        )
                        .build();

        // The argument provided in setSingleDevice() determines whether a single
        // device name or a list of device names is presented to the user as
        // pairing options.
        AssociationRequest pairingRequest = new AssociationRequest.Builder()
                .addDeviceFilter(deviceFilter)
//                .setSingleDevice(false)
                .build();

        // When the app tries to pair with the Bluetooth device, show the
        // appropriate pairing request dialog to the user.
        deviceManager.associate(pairingRequest,
                new CompanionDeviceManager.Callback() {
                    @Override
                    public void onDeviceFound(IntentSender chooserLauncher) {
                        try {
                            startIntentSenderForResult(chooserLauncher, SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            // failed to send the intent
                        }
                    }

                    @Override
                    public void onFailure(CharSequence error) {
                        // handle failure to find the companion device
                    }
                }, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == SELECT_DEVICE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                BluetoothDevice HC05device = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);

                if (HC05device != null) {
                    HC05device.createBond();
                    Toast.makeText(MainActivity.this, "Initiating connection...", Toast.LENGTH_SHORT).show();
                    // ... Continue interacting with the paired device.
                    new CreateConnectThread(HC05device).start();
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public Bitmap obtainBitmap (Uri uri) {
        ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
        Bitmap bitmap = null;
        try {
            bitmap = ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.RGBA_F16, true); //Mutable
        } catch (IOException e) {
            e.printStackTrace();
        }
        imageV.setImageBitmap(bitmap);
        return bitmap;
    }

    public Bitmap resizeBitmap (Bitmap bitmap) {
        return Bitmap.createScaledBitmap(bitmap, 32,32, false);
    }

    public void obtainPixels (Bitmap image, int z, int gifcounter) {
        StringBuilder Builder = new StringBuilder();

        for (int x=0; x<32; x++) {
            for (int y = 0; y < 32; y++) {
                int pixel = image.getPixel(x, y);
                int red = Color.red(pixel) / 16;
                int green = Color.green(pixel) / 16;
                int blue = Color.blue(pixel) / 16;

                String redh = Integer.toHexString(red).toUpperCase();
                String greenh = Integer.toHexString(green).toUpperCase();
                String blueh = Integer.toHexString(blue).toUpperCase();

                Builder.append(redh).append(greenh).append(blueh);
            }
        }

        switch (z) {
            case 0:
                textImage[IMAGE_COUNTER] = Builder.toString();
                break;
            case 1:
                textImage[gifcounter] = Builder.toString();
        }
    }

    public void editGif(Uri uri) {
        GifDecoder gifDecoder = new GifDecoder();
        boolean isSucceeded = gifDecoder.load(returnFilepath(this, uri));
        if (isSucceeded) {
            for (int i = 0; i < gifDecoder.frameNum(); ++i) {
                Bitmap bitmap = gifDecoder.frame(i);
                bitmap = resizeBitmap(bitmap);
                obtainPixels(bitmap, 1, i);
            }
            IMAGE_COUNTER = gifDecoder.frameNum();
        }

        Glide.with(this)
                .asGif()
                .load(uri)
                .into(imageV);
    }

    // Method to obtain the file path from a uri, taken from "Attaullah" via stackOverflow.
    // Copies the file from uri into the apps data directory, then obtains the file path from it.
    public static String returnFilepath(Context context, Uri uri) {
        final ContentResolver contentResolver = context.getContentResolver();
        if (contentResolver == null)
            return null;

        // Create file path inside app's data dir
        String filePath = context.getApplicationInfo().dataDir + File.separator + "temp_file";
        File file = new File(filePath);
        try {
            InputStream inputStream = contentResolver.openInputStream(uri);
            if (inputStream == null)
                return null;
            OutputStream outputStream = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0)
                outputStream.write(buf, 0, len);
            outputStream.close();
            inputStream.close();
        } catch (IOException ignore) {
            return null;
        }
        return file.getAbsolutePath();
    }


    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    public static class CreateConnectThread extends Thread {

        public CreateConnectThread(BluetoothDevice device) {
            /*
            Use a temporary object that is later assigned to mmSocket
            because mmSocket is final.
             */
            BluetoothSocket tmp = null;
            UUID uuid = device.getUuids()[0].getUuid();

            try {
                /*
                Get a BluetoothSocket to connect with the given BluetoothDevice.
                Due to Android device varieties,the method below may not work fo different devices.
                You should try using other methods i.e. :
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                 */
                tmp = device.createRfcommSocketToServiceRecord(uuid);

            } catch (IOException e) {
                Log.e("CreateConnectThread", "Socket's create() method failed", e);
            }
            HC05socket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
//            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//            bluetoothAdapter.cancelDiscovery();
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                HC05socket.connect();
                Log.e("Status", "Device connected");
                handler.obtainMessage(CONNECTING_STATUS, 1, 1).sendToTarget();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    HC05socket.close();
                    Log.e("Status", "Cannot connect to device");
                    handler.obtainMessage(CONNECTING_STATUS, -1, 1).sendToTarget();
                } catch (IOException closeException) {
                    Log.e("run socket", "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            new ConnectedThread(HC05socket).run();
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                HC05socket.close();
            } catch (IOException e) {
                Log.e("cancel socket", "Could not close the client socket", e);
            }
        }
    }

    public static class ConnectedThread extends Thread {

        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        static volatile boolean RECIEVE_CONFIRM = false;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e("connectedthreadstuff", "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e("connectedthreadstuff", "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    if ((byte) mmInStream.read() > 0) {
                        RECIEVE_CONFIRM = true;
                    }

                } catch (IOException e) {
                    Log.d("connectedthreadstuff", "Input stream was disconnected", e);
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void write() {
            try {
                mmOutStream.write((byte) IMAGE_COUNTER);
                mmOutStream.write((byte) SLIDESHOW_TIME);

                while (!RECIEVE_CONFIRM) {}

                for (int x=0; x<IMAGE_COUNTER; x++) {
                    byte[] bytes = textImage[x].getBytes();
                    byte[][] chunked_image = divideArray(bytes, 64); // 48 chunks

                    for (byte[] value : chunked_image) {
                        while (!RECIEVE_CONFIRM) {}
                        mmOutStream.write(value);
                        RECIEVE_CONFIRM = false;
                    }
                }

                // Share the sent message with the UI activity.
//                Message writtenMsg = handler.obtainMessage(
//                        MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
//                writtenMsg.sendToTarget();
            } catch (IOException e) {
                Log.e("connectedthreadstuff", "Error occurred when sending data", e);
                handler.obtainMessage(BT_WRITE, -1, 1).sendToTarget();
            }
        }

        public static byte[][] divideArray(byte[] source, int chunksize) {
            byte[][] return_chunks = new byte[(int)Math.ceil(source.length / (double)chunksize)][chunksize];
            int start = 0;

            for(int i = 0; i < return_chunks.length; i++) {
                return_chunks[i] = Arrays.copyOfRange(source, start, start + chunksize);
                start += chunksize ;
            }
            return return_chunks;
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
                HC05socket = null;
                handler.obtainMessage(BT_CANCEL, 1, 1).sendToTarget();
            } catch (IOException e) {
                Log.e("connectedthreadstuff", "Could not close the connect socket", e);
                handler.obtainMessage(BT_CANCEL, -1, 1).sendToTarget();
            }
        }
    }
}