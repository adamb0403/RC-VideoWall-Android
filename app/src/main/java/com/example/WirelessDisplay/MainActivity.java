package com.example.WirelessDisplay;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.os.Bundle;
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    ActivityResultLauncher<String[]> mPermissionResultLauncher;
    private boolean isStoragePermissionGranted = false;
    private boolean isLocationPermissionGranted = false;
//    private boolean isConnectPermissionGranted = false;

    private static final int SPCODE = 100;
    static int IMAGE_COUNTER = 0;
    static int SLIDESHOW_TIME = 5;
    static String[] textImage = new String[100];
    Button getimagebtn, getgifbtn, storage, slideshowTime;
    ImageView imageV;
    TextView tv;

    @SuppressLint("MissingPermission") // Wants BLUETOOTH_CONNECT permission for Android 12
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



        storage = findViewById(R.id.storage);
        getimagebtn=findViewById(R.id.getimagebutton);
        getgifbtn=findViewById(R.id.getgifbutton);
        imageV=findViewById(R.id.imageView);
        tv=findViewById(R.id.textView);
        slideshowTime=findViewById(R.id.setSlideshowTime);


        updateImageSelectedText();

        ActivityResultLauncher<Intent> getImage = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        Uri uri = data.getData();
                        Bitmap image = obtainBitmap(uri);
                        obtainPixels(image);
                        IMAGE_COUNTER++;
                        updateImageSelectedText();
//                            BLUETOOTH COMMUNICATION!!!!!!
                    }
                });

        ActivityResultLauncher<Intent> getGif = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        Uri uri = data.getData();
                        editGif(uri);
                    }
                });

        slideshowTime.setOnClickListener(view -> setSlideshowTime());

//        storage.setOnClickListener(view -> checkStoragePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, SPCODE));

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

//    public void checkStoragePermission(String permission, int requestCode)
//    {
//        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
//
//            ActivityCompat.requestPermissions(MainActivity.this, new String[] { permission }, requestCode);
//        }
//        else {
//            Toast.makeText(MainActivity.this, "Permission already granted", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
//    {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//
//        if (requestCode == SPCODE) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                Toast.makeText(MainActivity.this, "Storage Permission Granted", Toast.LENGTH_SHORT).show();
//            } else {
//                Toast.makeText(MainActivity.this, "Storage Permission Denied", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }

    public void updateImageSelectedText() {
        tv.setText("Images Selected: " + IMAGE_COUNTER);
    }

    public void setClearSelection(View v) {
        IMAGE_COUNTER = 0;
        Arrays.fill(textImage, null);
        imageV.setImageDrawable(null);
        updateImageSelectedText();
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

    public Bitmap obtainBitmap (Uri uri) {
        ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
        Bitmap bitmap = null;
        try {
            bitmap = ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.RGBA_F16, true); //Mutable
        } catch (IOException e) {
            e.printStackTrace();
        }

        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 32,32, false);
        imageV.setImageBitmap(bitmap);

        return resized;
    }

    public void obtainPixels (Bitmap image) {
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

        textImage[IMAGE_COUNTER] = Builder.toString();
    }

    public void editGif(Uri uri) {
        String path = uri.getPath();
        TextView tv=findViewById(R.id.textView);
        tv.setText(path);

        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

//        Bitmap testBitmap = BitmapFactory.decodeFile(uri.getPath());
//        imageV.setImageBitmap(testBitmap);

    }
}