package com.example.customcameraapp;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private ImageButton captureButton, flashButton, flipButton;
    private int cameraFacing = CameraSelector.LENS_FACING_BACK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        checkPermissions();
        setupCameraButtons();
    }

    private void initializeViews() {
        previewView = findViewById(R.id.cameraPreview);
        captureButton = findViewById(R.id.capture);
        flashButton = findViewById(R.id.toggleFlash);
        flipButton = findViewById(R.id.flipCamera);
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissions(permissions, 100);
        }
    }

    private void setupCameraButtons() {
        flipButton.setOnClickListener(v -> {
            cameraFacing = (cameraFacing == CameraSelector.LENS_FACING_BACK) ?
                    CameraSelector.LENS_FACING_FRONT :
                    CameraSelector.LENS_FACING_BACK;
            startCamera();
        });
    }

    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();

                Preview preview = new Preview.Builder().build();
                ImageCapture imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(cameraFacing)
                        .build();

                cameraProvider.unbindAll();
                Camera camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture);

                setupCameraControls(camera, imageCapture);
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

            } catch (Exception e) {
                Log.e("CameraX", "Failed to start camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void setupCameraControls(Camera camera, ImageCapture imageCapture) {
        captureButton.setOnClickListener(v -> takePicture(imageCapture));
        flashButton.setOnClickListener(v -> toggleFlash(camera));
    }

    private void takePicture(ImageCapture imageCapture) {
        // Create time-stamped name
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String name = "IMG_" + timestamp + ".jpg";

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera");
        }

        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
        ).build();

        imageCapture.takePicture(outputFileOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        Uri savedUri = output.getSavedUri();
                        String msg = "Photo saved successfully";
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();

                        // For older Android versions, trigger media scanner
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            mediaScanIntent.setData(savedUri);
                            sendBroadcast(mediaScanIntent);
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exc) {
                        Log.e("CameraX", "Photo capture failed: " + exc.getMessage(), exc);
                        Toast.makeText(MainActivity.this,
                                "Failed to save photo",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void toggleFlash(Camera camera) {
        if (camera.getCameraInfo().hasFlashUnit()) {
            boolean isFlashOn = camera.getCameraInfo().getTorchState().getValue() == 1;
            camera.getCameraControl().enableTorch(!isFlashOn);
            flashButton.setImageResource(isFlashOn ?
                    R.drawable.baseline_flash_on_24 :
                    R.drawable.baseline_flash_off_24);
        } else {
            Toast.makeText(this, "Flash not available", Toast.LENGTH_SHORT).show();
        }
    }
}
