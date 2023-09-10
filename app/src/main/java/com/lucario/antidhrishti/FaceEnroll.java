package com.lucario.antidhrishti;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;

public class FaceEnroll extends AppCompatActivity {

    private static final int TF_OD_API_INPUT_SIZE = 112;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "mobile_face_net.tflite";

    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private CameraDevice mCameraDevice;
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CameraCaptureSession mSession;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;

    private Button enrollButton;

    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;


    private SimilarityClassifier detector;

    private String faceFileName;
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_enroll);
        enrollButton = findViewById(R.id.button_enroll_face);
        mSurfaceView = findViewById(R.id.camera_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (mCameraDevice == null) {
                    openCamera();
                } else {
                    startPreview();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
            }
        });

        faceFileName = getIntent().getStringExtra("faceFileName");
        try{
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
        } catch (final IOException e) {
            e.printStackTrace();
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

//        mSurfaceView.setClipBounds(new Rect(0,0,640,480));
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setContourMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .build();

        FaceDetector faceDetector = FaceDetection.getClient(options);

        enrollButton.setOnClickListener(view -> {
            // Get the image from surface view
            try{
                Bitmap image = takePhoto();
                InputImage inputImage = InputImage.fromBitmap(image, 0);
                faceDetector.process(inputImage)
                        .addOnSuccessListener(faces -> {
                            if(faces.size() > 0){
                                SimilarityClassifier.Recognition rec = processSingleFace(faces.get(0), image, true);
                                saveFace(new SerializableRecognition(rec));
                                Intent intent = new Intent(FaceEnroll.this, ClassActivity.class);
                                startActivity(intent);
                                finish();
                                Toast.makeText(this, "Face enrolled", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "No face detected", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "No face detected", Toast.LENGTH_SHORT).show();
                        });
            } catch (NoImageException e) {
                Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private SimilarityClassifier.Recognition processSingleFace(Face face, Bitmap rgbFrameBitmap, boolean addFace) {
        int sourceW = 350;   // source image width
        int sourceH = 350;   // source image height
        int targetW = 240;   // target image width
        int targetH = 320;   // target image height
        int sensorOrientation = 0;  // assuming you always have front camera

        Matrix transform = createTransform(sourceW, sourceH, targetW, targetH, sensorOrientation);

        Bitmap faceBmp = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Bitmap.Config.ARGB_8888);
        final Canvas cvFace = new Canvas(faceBmp);

        final RectF boundingBox = new RectF(face.getBoundingBox());
        final boolean goodConfidence = true;

        if (boundingBox != null && goodConfidence) {
            transform.mapRect(boundingBox);
            float sx = ((float) TF_OD_API_INPUT_SIZE) / boundingBox.width();
            float sy = ((float) TF_OD_API_INPUT_SIZE) / boundingBox.height();
            Matrix matrix = new Matrix();
            matrix.postTranslate(-boundingBox.left, -boundingBox.top);
            matrix.postScale(sx, sy);

            // assume rgbFrameBitmap is your source 350x350 image
            cvFace.drawBitmap(rgbFrameBitmap, matrix, null);

            String label = "";
            float confidence = 0.5f;
            Integer color = Color.BLUE;
            Object extra = null;
            Bitmap crop = null;

            if (addFace) {
                crop = Bitmap.createBitmap(rgbFrameBitmap,
                        (int) boundingBox.left,
                        (int) boundingBox.top,
                        (int) boundingBox.width(),
                        (int) boundingBox.height());
            }

            final long startTime = SystemClock.uptimeMillis();
            final List<SimilarityClassifier.Recognition> resultsAux = detector.recognizeImage(faceBmp, addFace);

            if (resultsAux.size() > 0) {
                SimilarityClassifier.Recognition result = resultsAux.get(0);
                extra = result.getExtra();

                float conf = result.getDistance();
                if (conf < 1.0f) {
                    confidence = conf;
                    label = result.getTitle();
                    if (result.getId().equals("0")) {
                        color = Color.GREEN;
                    } else {
                        color = Color.RED;
                    }
                }
            }

            final SimilarityClassifier.Recognition recognizedFace = new SimilarityClassifier.Recognition("0", label, confidence, boundingBox);
            recognizedFace.setColor(color);
            recognizedFace.setLocation(boundingBox);
            recognizedFace.setExtra(extra);
            recognizedFace.setCrop(crop);

            return recognizedFace;
        }
        return null;
    }

    private Matrix createTransform(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight,
            final int applyRotation) {

        Matrix matrix = new Matrix();
        if (applyRotation != 0) {
            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);

            // Rotate around origin.
            matrix.postRotate(applyRotation);
        }
        return matrix;
    }
    private void saveFace(SerializableRecognition rec) {
        // Write the chat list to a file
        try {
            FileOutputStream fos = openFileOutput("face.info", MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(rec);
            oos.close();
            fos.close();
            getSharedPreferences("cred", MODE_PRIVATE).edit().putBoolean("face-enrolled", true).apply();
        } catch (IOException e) {
            e.printStackTrace();
        }
//        try {
//            File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), faceFileName);
//            FileOutputStream fos = new FileOutputStream(file);
//            ObjectOutputStream oos = new ObjectOutputStream(fos);
//            oos.writeObject(rec);
//            oos.close();
//            fos.close();
//            getSharedPreferences("cred", MODE_PRIVATE).edit().putBoolean("face-enrolled", true).apply();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }


    private Bitmap takePhoto() throws NoImageException {

        // Create a bitmap the size of the scene view.
        final Bitmap bitmap = Bitmap.createBitmap(mSurfaceView.getWidth(), mSurfaceView.getHeight(),
                Bitmap.Config.ARGB_8888);
        AtomicBoolean imageCreated = new AtomicBoolean(true);

        // Create a handler thread to offload the processing of the image.
        final HandlerThread handlerThread = new HandlerThread("PixelCopier");
        handlerThread.start();

        // Make the request to copy.
        PixelCopy.request(this.mSurfaceView, bitmap, (copyResult) -> {
            if (!(copyResult == PixelCopy.SUCCESS)) {
                Toast toast = Toast.makeText(this,
                        "Failed to copyPixels: " + copyResult, Toast.LENGTH_LONG);
                toast.show();
                imageCreated.set(false);
            }
            handlerThread.quitSafely();
        }, new Handler(handlerThread.getLooper()));
        if(!imageCreated.get())
            throw new NoImageException();
        return bitmap;
    }

    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = getFrontCameraID();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            cameraManager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private String getFrontCameraID(){
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraId = cameraManager.getCameraIdList();
            for (String id : cameraId) {
                if (cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    return id;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void uploadFaceInfo(){
        OkHttpClient client = new OkHttpClient();
    }

    private void startPreview() {
        if (mCameraDevice == null || !mSurfaceView.getHolder().getSurface().isValid()) {
            return;
        }
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(mSurfaceHolder.getSurface());

            Executor executor = ContextCompat.getMainExecutor(this);
            SessionConfiguration sessionConfiguration = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                    Collections.singletonList(new OutputConfiguration(mSurfaceHolder.getSurface())),
                    executor, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mSession = session;
                    try {
                        mSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, new Handler(Looper.getMainLooper()));
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            }
            );
            mCameraDevice.createCaptureSession(sessionConfiguration);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public static class NoImageException extends Exception {
        public NoImageException() {
            super("Could not capture image");
        }
    }
}