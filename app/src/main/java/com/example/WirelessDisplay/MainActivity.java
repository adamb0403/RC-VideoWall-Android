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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
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

import com.bumptech.glide.Glide; // Include 3rd pty class Glide
import com.waynejo.androidndkgif.GifDecoder; // Include 3rd pty class GifDecoder

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

    ActivityResultLauncher<String[]> mPermissionResultLauncher; // Declare permission launcher
    private boolean isConnectPermissionGranted = false; // Declare permisson check for BT connect

    private static final int SELECT_DEVICE_REQUEST_CODE = 0; // Used to identify which intent is used

    private final static int CONNECTING_STATUS = 1; // used in bluetooth handler to identify message status
    private final static int BT_WRITE = 2;
    private final static int BT_CANCEL = 3;
    private final static int PROGRESS_BAR = 4;

    private static BluetoothSocket HC05socket; // Represents the Bluetooth connection
    public static Handler handler; // Initialise return Handler

    static int IMAGE_COUNTER = 0; // Global record of no. images selected
    static int SLIDESHOW_TIME = 5; // Timing used for images
    static int FPS = 24; // Timing used for animated gifs
    static int DECIDER; //
    static byte[][] byteImage = new byte[254][3072]; // All decoded images/animations are stored in an array of byte arrays
    Button getimagebtn, getgifbtn, clearSel, slideshowTime, setFPSbtn, btbtn, disconnectbtn, sendBluetooth; // Declare buttons, image views etc
    ImageView imageV;
    TextView tv, btText;
    ProgressBar pBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) { // Equivalent to "main" method. On application start this method is run
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= 31) { // If user is using Android 12, check BLUETOOTH_CONNECT permission

            mPermissionResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {

                if (result.get(Manifest.permission.BLUETOOTH_CONNECT) != null){

                    isConnectPermissionGranted = result.get(Manifest.permission.BLUETOOTH_CONNECT);

                }
            });

            requestPermission(); // Call method to request permission
        }

        // Assign the device's Bluetooth module as a manipulatable object
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(MainActivity.this, "Bluetooth is not supported on this device.", Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(MainActivity.this, "Bluetooth is compatible on this device.", Toast.LENGTH_SHORT).show();
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE); // Call intent to enable the Bluetooth adapter
                startActivity(enableBtIntent); // Does not support API > 30. Add Connect permission to support android11
            }
        }

        clearSel=findViewById(R.id.clearSelection); // Initialise all buttons/textviews/imageviews in activity_main.xml
        getimagebtn=findViewById(R.id.getimagebutton);
        getgifbtn=findViewById(R.id.getgifbutton);
        imageV=findViewById(R.id.imageView);
        tv=findViewById(R.id.textView);
        btText=findViewById(R.id.BTtext);
        slideshowTime=findViewById(R.id.setSlideshowTime);
        setFPSbtn=findViewById(R.id.setFPS);
        btbtn=findViewById(R.id.btbutton);
        disconnectbtn=findViewById(R.id.disconnectBt);
        sendBluetooth=findViewById(R.id.sendBT);
        pBar=findViewById(R.id.progressBar); // Initialise progress bar

        sendBluetooth.setEnabled(false); // Set Send bluetooth button to disabled on app start
        updateImageSelectedText(0); // Call method to initialise image selected text

        // Result launcher for the intent created when "upload image" is pressed
        ActivityResultLauncher<Intent> getImage = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData(); // When image is selected from explorer, store it in "data"
                        Uri uri = data.getData(); // Obtain the universal resource identifier of the "data" intent
                        Bitmap image = obtainBitmap(uri); // Call method to obtain a bitmap image from the uri
                        image = resizeBitmap(image); // Call method to compress the bitmap to 32x32
                        obtainPixels(image); // Call method to obtain the colours for each pixel of the compressed bitmap
                        IMAGE_COUNTER++;
                        updateImageSelectedText(0);
                        getgifbtn.setEnabled(false); // Disable "upload GIF" button
                        setFPSbtn.setEnabled(false);
                        DECIDER = 2; // Associate slideshow uploads with no. 2 for the decider
                        bluetoothButtonCheck(); // Call method to check whether the Send Bt button should be enabled
                    }
                });

        // Result launcher for the intent created when "upload image" is pressed
        ActivityResultLauncher<Intent> getGif = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        Uri uri = data.getData();
                        editGif(uri); // Call method to decode animated GIF
                        updateImageSelectedText(1);
                        getimagebtn.setEnabled(false); // Disable "upload GIF" button
                        slideshowTime.setEnabled(false);
                        DECIDER = 1; // Associate animated GIF uploads with no. 1 for the decider
                        bluetoothButtonCheck();
                    }
                });

        getimagebtn.setOnClickListener(view -> { // Listen for when "Upload image" button is clicked
            Intent intent=new Intent(Intent.ACTION_GET_CONTENT); // Call an intent to open the Android content explorer
            intent.setType("image/*"); // Limit clickable objects to only image types
            getImage.launch(intent); // Call the Result launcher above for getImage
        });

        getgifbtn.setOnClickListener(view -> { // Listen for when "Upload gif" button is clicked
            Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/gif"); // Limit clickable objects to only GIF image types
            getGif.launch(intent); // Call the Result launcher above for getGif
        });

        sendBluetooth.setOnClickListener(view -> { // Listen for when "Send Bluetooth" button is clicked
            pBar.setProgress(0);
            clearSel.setEnabled(false);
            disconnectbtn.setEnabled(false);
            sendBluetooth.setEnabled(false);
            // Create new thread to call write() method to not block the main UI
            new Thread(() -> new ConnectedThread(HC05socket).write()).start();
        });

        btbtn.setOnClickListener(view -> { // Listen for when "Connect Bluetooth" button is clicked
            btbtn.setEnabled(false);
            btText.setText("Searching...");
            companionDeviceManager(); // Call method to open the companion device manager to scan for Bluetooth devices
        });

        disconnectbtn.setOnClickListener(view -> { // Listen for when "Disconnect Bluetooth" button is clicked
            // Call method to close the BT connection within ConnectedThread class and pass bluetooth socket
            new ConnectedThread(HC05socket).cancel();
        });

        slideshowTime.setOnClickListener(view -> setSlideshowTime()); // Call method to change slideshow time when button is clicked
        setFPSbtn.setOnClickListener(view -> setFPS()); // Call method to change fps when button is clicked

        clearSel.setOnClickListener(view -> { // Listen for when "Clear Selection" button is clicked
            setClearSelection(); // Call method to initialise bytes / selection
            getimagebtn.setEnabled(true); // Revert select buttons to their original state
            getgifbtn.setEnabled(true);
            slideshowTime.setEnabled(true);
            setFPSbtn.setEnabled(true);
            bluetoothButtonCheck();
        });

        handler = new Handler(Looper.getMainLooper()) { // Initialise the handler on the main looper
            // Run different commands depending on what message is sent to the handler (More detail in background threads)
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

                    case BT_WRITE:
                        switch (msg.arg1) {
                            case 1:
                                clearSel.setEnabled(true);
                                disconnectbtn.setEnabled(true);
                                sendBluetooth.setEnabled(true);
                                Toast.makeText(MainActivity.this, "Successfully sent data.", Toast.LENGTH_LONG).show();
                                break;
                            case -1:
                                clearSel.setEnabled(true);
                                disconnectbtn.setEnabled(true);
                                sendBluetooth.setEnabled(true);
                                Toast.makeText(MainActivity.this, "Error occurred when sending data... Closing bluetooth connection", Toast.LENGTH_LONG).show();
                                new ConnectedThread(HC05socket).cancel(); // Close the BT connection after a write error
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

                    case PROGRESS_BAR:
                        pBar.setProgress(msg.arg2, true); // Update the progress bar between sending bytes
                        break;
                }
            }
        };
    }

    private void requestPermission(){
        isConnectPermissionGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED;

        List<String> permissionRequest = new ArrayList<String>();



        if (!isConnectPermissionGranted){

            permissionRequest.add(Manifest.permission.BLUETOOTH_CONNECT);

        }

        if (!permissionRequest.isEmpty()){

            mPermissionResultLauncher.launch(permissionRequest.toArray(new String[0]));

        }
    }

    public void bluetoothButtonCheck() {
        // If Bluetooth socket and the first array of the byte array are not empty, enable the send BT button
        sendBluetooth.setEnabled(HC05socket != null && byteImage[0] != null);
    }

    public void updateImageSelectedText(int x) {
        switch (x) {
            case 0: // Set Image selected text if user is uploading images
                tv.setText("Images Selected: " + IMAGE_COUNTER);
                break;
            case 1: // Set Image selected text if user is uploading gifs
                tv.setText("Frames: " + IMAGE_COUNTER);
                break;
        }
    }

    public void setClearSelection() {
        IMAGE_COUNTER = 0; // Initilaise image counter
        //byteImage;
        imageV.setImageDrawable(null); // Remove the image from the UI display
        updateImageSelectedText(0); // Call method to update the selection text
    }

    public void setFPS() {
        // Declare and initialise a builder for a "dialog", a small pop up window
        final AlertDialog.Builder popup = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater(); // Declare and initialise Inflater to link the fps.xml layout
        View dialogView = inflater.inflate(R.layout.fps, null); // Create new view containing fps.xml as the popup screen
        popup.setTitle("FPS:");
        popup.setView(dialogView);
        final NumberPicker fpsTime = dialogView.findViewById(R.id.fpsTime); // Initialise the number scroller

        fpsTime.setMaxValue(60); //  Set max/min values
        fpsTime.setMinValue(1);
        fpsTime.setValue(FPS); // Set default value (24)
        fpsTime.setWrapSelectorWheel(true);

        popup.setPositiveButton("Set", (dialog, id) -> FPS = fpsTime.getValue()); // If button is pressed update FPS to value selected
        popup.setNegativeButton("Cancel", (dialog, id) -> { }); // User stopped dialog. Return to main xml

        AlertDialog alertDialog = popup.create(); // Build the dialog from the builder created at start of method
        alertDialog.show(); // Display the popup screen
    }

    public void setSlideshowTime() {
        // Almost identical to setFPS(), but for slideshow time
        final AlertDialog.Builder popup = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.slideshow, null);
        popup.setTitle("Seconds Per Image:");
        popup.setView(dialogView);
        final NumberPicker slideshowTime = dialogView.findViewById(R.id.slideshowTime);

        slideshowTime.setMaxValue(100);
        slideshowTime.setMinValue(1);
        slideshowTime.setValue(SLIDESHOW_TIME);
        slideshowTime.setWrapSelectorWheel(true);

        popup.setPositiveButton("Set", (dialog, id) -> SLIDESHOW_TIME = slideshowTime.getValue());
        popup.setNegativeButton("Cancel", (dialog, id) -> { });

        AlertDialog alertDialog = popup.create();
        alertDialog.show();
    }

    public void companionDeviceManager() {
        // Create a filter to control what devices are displayed in the popup
        BluetoothDeviceFilter dFilter = new BluetoothDeviceFilter.Builder()
                        .setNamePattern(Pattern.compile("WIRELESSDISPLAY")) // Only display devices with this name
                        .build();

        AssociationRequest pRequest = new AssociationRequest.Builder() // Initialise an Association request and include the filter
                .addDeviceFilter(dFilter)
                .build();

        // Initialise the device manager and run associate() to take the request and callback
        CompanionDeviceManager deviceManager = (CompanionDeviceManager) getSystemService(Context.COMPANION_DEVICE_SERVICE);
        deviceManager.associate(pRequest, new CompanionDeviceManager.Callback() {
                    // This callback indicates when a device that matches the device filter has been found
                    @Override
                    // Dialog box is opened once device is found showing available devices
                    public void onDeviceFound(IntentSender chooserLauncher) {
                        try {
                            // When user selects a device, result is sent to onActivityResult to be handled
                            startIntentSenderForResult(chooserLauncher, SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            // failed to send the intent
                        }
                    }

                    @Override
                    public void onFailure(CharSequence error) {
                        // No devices could be found matching the filter
                        Toast.makeText(MainActivity.this, "No bluetooth devices found.", Toast.LENGTH_SHORT).show();
                        btText.setText("Bluetooth: Disconnected");
                        btbtn.setEnabled(true);
                    }
                }, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == SELECT_DEVICE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // BT device HC05device will now represent the remote device. ie. the device chosen by the user
                BluetoothDevice HC05device = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);

                if (HC05device != null) {
                    HC05device.createBond(); // Start the pairing process with HC05device
                    Toast.makeText(MainActivity.this, "Initiating connection...", Toast.LENGTH_SHORT).show();
                    // Start thread to initiate connection with BT device
                    new CreateConnectThread(HC05device).start();
                }
            }
        } else {
            // if request code is not SELECT_DEVICE_REQUEST_CODE, retry
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public Bitmap obtainBitmap (Uri uri) {
        ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri); // Create a image source from the uri
        Bitmap bitmap = null; // Initialise bitmap as null so as to not confuse .setImageBitmap
        try {
            // Create bitmap from the source, where each pixel channel is 16 bits. This is a blocking call
            bitmap = ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.RGBA_F16, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        imageV.setImageBitmap(bitmap); // Display the bitmap via the image view
        return bitmap;
    }

    public Bitmap resizeBitmap (Bitmap bitmap) {
        // Compress the bitmap to 32x32
        return Bitmap.createScaledBitmap(bitmap, 32,32, false);
    }

    public void obtainPixels (Bitmap image) {
        int counter = 0;

        // For all pixels, obtain red, green, blue values and store them as bytes in the byteImage array
        for (int x=0; x<32; x++) {
            for (int y=0; y<32; y++) {
                byteImage[IMAGE_COUNTER][counter] = (byte) Color.red(image.getPixel(x, y));
                byteImage[IMAGE_COUNTER][counter+1] = (byte) Color.green(image.getPixel(x, y));
                byteImage[IMAGE_COUNTER][counter+2] = (byte) Color.blue(image.getPixel(x, y));
                counter+=3;
            }
        }
    }

    public void editGif(Uri uri) {
        GifDecoder gifDecoder = new GifDecoder(); // Create an object of 3rd pty class GifDecoder
        // Call method to obtain bitmaps of all frames of animated gif
        // Call returnFilepath as filename
        boolean isSucceeded = gifDecoder.load(returnFilepath(this, uri));
        if (isSucceeded) {
            // For every frame, store in a bitmap, resize, then obtain pixels
            for (IMAGE_COUNTER = 0; IMAGE_COUNTER < gifDecoder.frameNum(); ++IMAGE_COUNTER) {
                Bitmap bitmap = gifDecoder.frame(IMAGE_COUNTER);
                bitmap = resizeBitmap(bitmap);
                obtainPixels(bitmap);
            }
            IMAGE_COUNTER = gifDecoder.frameNum(); // Set image counter to frame count
        }

        Glide.with(this) // Using 3rd pty class Glide, set the image view to display the animated GIF
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
        File file = new File(filePath); // Create a file at the above path
        try {
            InputStream inputStream = contentResolver.openInputStream(uri); // Load the data from uri into an input stream

            if (inputStream == null)
                return null;

            OutputStream outputStream = new FileOutputStream(file); // Link the output stream to the newly created file
            byte[] buffer = new byte[10240000]; // Buffer of max size 10MB for uri data
            int length;

            while ((length = inputStream.read(buffer)) > 0)
                outputStream.write(buffer, 0, length); // Write the input stream data byte by byte to the output stream/file

            outputStream.close();
            inputStream.close();
        } catch (IOException ignore) {
            return null;
        }
        return file.getAbsolutePath(); // Return the path for the new file
    }


    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    public static class CreateConnectThread extends Thread { // Class running on background thread to perform Bluetooth connections

        public CreateConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null; // Use temp socket that is later assigned to HC05socket as that is final
            UUID uuid = device.getUuids()[0].getUuid(); // Generate the UUID of the remote device
            try {
                // Create a socket which will be used to connect to the remote device via the uuid
                tmp = device.createRfcommSocketToServiceRecord(uuid);

            } catch (IOException e) {
                Log.e("CreateConnectThread", "Socket's create() method failed", e);
            }
            HC05socket = tmp; // Assign the newly created socket to the Global final socket HC05socket
        }

        public void run() {
            try {
                HC05socket.connect(); // Connect to the remote device through the socket. This call blocks
                Log.e("Status", "Device connected");

                // Send appropriate message to the UI handler in main thread reporting connecting status
                handler.obtainMessage(CONNECTING_STATUS, 1, 1).sendToTarget();
            } catch (IOException connectException) { // Unable to connect; close the socket and return.
                try {
                    HC05socket.close(); // Close the bluetooth socket
                    Log.e("Status", "Cannot connect to device");
                    handler.obtainMessage(CONNECTING_STATUS, -1, 1).sendToTarget();
                } catch (IOException closeException) {
                    Log.e("run socket", "Could not close the client socket", closeException);
                }
                return;
            }

            // If connection was successful, start the ConnectedThread class on a separate thread ready for data transfer
            new ConnectedThread(HC05socket).start();
        }
    }

    public static class ConnectedThread extends Thread { // Class running on background thread to perform Bluetooth data transfer

        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        static volatile boolean RECEIVE_CONFIRM = false; // Set the initial value of RECEIVE_CONFIRM

        public ConnectedThread(BluetoothSocket socket) {
            // Initialise the final socket, temp input/output streams
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream(); // Assign the input stream to hold incoming data from the remote device
            } catch (IOException e) {
                Log.e("connectedthreadstuff", "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream(); // Assign the output stream to hold outgoing data to the remote device
            } catch (IOException e) {
                Log.e("connectedthreadstuff", "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn; // Assign the temp streams to the final ones
            mmOutStream = tmpOut;
        }

        public void run() {
            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    if ((byte) mmInStream.read() > 0) {
                        RECEIVE_CONFIRM = true; // If the input stream holds data, set RECEIVE_CONFIRM to true
                    }

                } catch (IOException e) {
                    Log.d("connectedthreadstuff", "Input stream was disconnected", e);
                    break;
                }
            }
        }

        public void write() { // Method to send data to the remote device
            try {
                mmOutStream.write((byte) IMAGE_COUNTER); // Write the value for image counter to the output stream
                mmOutStream.write((byte) DECIDER); // Write the value for image counter to the output stream

                switch (DECIDER) {
                    case 1:
                        mmOutStream.write((byte) FPS); // If a gif is to be sent, write FPS to the output stream
                        break;
                    case 2:
                        mmOutStream.write((byte) SLIDESHOW_TIME); // If image(s) is to be sent, write slideshow time to the output stream
                }

                while (!RECEIVE_CONFIRM) {} // Hold the method is no receive byte has been received in the input stream

                for (int x=0; x<IMAGE_COUNTER; x++) { // Iterate for all images/frames loaded
                    // Call method to divide the byte array into chunks = 3072/chunksize
                    byte[][] chunked_image = divideArray(byteImage[x], 128); //

                    for (byte[] value : chunked_image) { // Iterate for all chunks of the byteImage
                        while (!RECEIVE_CONFIRM) {} // Hold if RECEIVE_CONFIRM is false
                        mmOutStream.write(value); // Write a chunk of the byteImage to the output stream
                        RECEIVE_CONFIRM = false; // Switch RECEIVE_CONFIRM to false to hold the next chunk from being sent
                    }

                    // Send message to the UI handler updating the progress bar with how many images have been sent
                    handler.obtainMessage(PROGRESS_BAR, 1, (x*100)/IMAGE_COUNTER).sendToTarget();
                }

                // Send message to UI handler to set progress bar to 100
                handler.obtainMessage(PROGRESS_BAR, 1, 100).sendToTarget();
                // Send message to UI handler informing that all images have been sent to the remote device
                handler.obtainMessage(BT_WRITE, 1, 1).sendToTarget();
            } catch (IOException e) {
                Log.e("connectedthreadstuff", "Error occurred when sending data", e);
                // Send error message to the UI handler
                handler.obtainMessage(BT_WRITE, -1, 1).sendToTarget();
            }
        }

        public static byte[][] divideArray(byte[] source, int chunksize) { // Method to split byte array into chunks
            int chunks = source.length/chunksize;
            byte[][] return_chunks = new byte[chunks][chunksize]; // Declare new array of byte arrays with chunks and chunksize
            int cursor = 0;

            for(int i = 0; i < return_chunks.length; i++) { // Iterate for all chunks
                // Copy bytes from source to selected chunk
                return_chunks[i] = Arrays.copyOfRange(source, cursor, cursor + chunksize);
                cursor += chunksize; // Increase the cursor to the number of the next chunk of bytes to be stored
            }
            return return_chunks; // Return the chunked byte arrays
        }

        public void cancel() { // Method to close the Bluetooth socket
            try {
                mmSocket.close(); // Close the connection
                HC05socket = null; // Set the global socket to null
                // Send message to UI handler informing of closure
                handler.obtainMessage(BT_CANCEL, 1, 1).sendToTarget();
            } catch (IOException e) {
                Log.e("connectedthreadstuff", "Could not close the connect socket", e);
                handler.obtainMessage(BT_CANCEL, -1, 1).sendToTarget();
            }
        }
    }
}