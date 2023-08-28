package com.lucario.antidhrishti;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ClassActivity extends AppCompatActivity implements ClassViewAdapter.startVerification {

    private static String batch_ID;
    private static String secret;

    private RecyclerView recyclerView;

    private ClassViewAdapter adapter;
    private ActivityResultLauncher<Intent> launcher;

    private ArrayList<ClassDataModel> data;

    private String class_name, time;
    private String BaseURL = "https://5a22-152-58-213-31.ngrok-free.app";
    private boolean attended = false;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_classes);
        SharedPreferences sharedPreferences = getSharedPreferences("cred", MODE_PRIVATE);
        batch_ID = sharedPreferences.getString("batch-id", null);
        secret = sharedPreferences.getString("secret-key", null);
        recyclerView = findViewById(R.id.classes_recycler_view);
        new Thread(() -> {
            data = getClassDataFromServer(secret, batch_ID);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    // Assuming you are using some Custom Adapter which takes 'data' as parameter
                    ClassViewAdapter adapter = new ClassViewAdapter(data, ClassActivity.this);
                    recyclerView.setAdapter(adapter);
                    recyclerView.setLayoutManager(new LinearLayoutManager(ClassActivity.this));

                    // To notify the adapter about data change
                    adapter.notifyDataSetChanged();
                }
            });
        }).start();

        launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            boolean successful = data.getBooleanExtra("result", false);
                            if(successful){
                                new Thread(() -> markAttended(class_name, secret, batch_ID, time, "")).start();
                            }
                        }
                    } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                        Toast.makeText(this, "Verification cancelled", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private String getStudentID(){
        SharedPreferences sharedPreferences = getSharedPreferences("cred", MODE_PRIVATE);
        String id = sharedPreferences.getString("student-id", null);
//        System.out.println(id);
        try{
            id = id.substring(id.length()-3);
//            System.out.println(id);
        } catch (Exception e){
            e.printStackTrace();
        }
        return id;
    }

    private void markAttended(String class_name, String secretKey, String batchId, String time, String student_id){
        OkHttpClient client = new OkHttpClient();
        // Create json body with secret-key & batch-id
        MediaType mediaType = MediaType.parse("application/json;charset=UTF-8");
        FormBody formBody = new FormBody.Builder()
                .add("secret-key", secretKey)
                .add("batch-id", batchId)
                .add("student-id", getStudentID())
                .add("class-name", class_name)
                .add("time", time)
                .build();

        // Assuming server URL as "your_server_url"
        Request request = new Request.Builder().url(BaseURL+"/mark_attendance.php").post(formBody).build();

        try {
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            } else {
                String responseBody = response.body().string();
                System.out.println(responseBody);
                JSONObject jsonObject = new JSONObject(responseBody);
                if(jsonObject.getBoolean("success")){
                    runOnUiThread(()->Toast.makeText(this, "Attendance marked successfully", Toast.LENGTH_SHORT).show());
                    attended = true;
                } else {
                    runOnUiThread(()->Toast.makeText(this, "Attendance marking failed", Toast.LENGTH_SHORT).show());
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public ArrayList<ClassDataModel> getClassDataFromServer(String secretKey, String batchId) {
        OkHttpClient client = new OkHttpClient();
        // Create json body with secret-key & batch-id
        MediaType mediaType = MediaType.parse("application/json;charset=UTF-8");
        FormBody formBody = new FormBody.Builder()
                .add("secret-key", secretKey)
                .add("batch-id", batchId)
                .build();

        // Assuming server URL as "your_server_url"
        Request request = new Request.Builder().url(BaseURL+"/getclass.php").post(formBody).build();

        try {
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            } else {
                String responseBody = response.body().string();
                System.out.println(responseBody);
                JSONArray jsonArray = new JSONArray(responseBody);

                // Create a list to hold all your ClassDataModel Objects
                ArrayList<ClassDataModel> classModelList = new ArrayList<>();

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject classDataObject = jsonArray.getJSONObject(i);

                    String className = classDataObject.getString("class_name");

                    SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Date startTime = dateTimeFormat.parse(classDataObject.getString("start_time"));
                    Date endTime = dateTimeFormat.parse(classDataObject.getString("end_time"));
//                    boolean isAttended = (classDataObject.getInt("attended") == 1);
                    boolean isAttended = false;
                    int timeCredits = classDataObject.getInt("time_credits");
                    boolean canAttend = (classDataObject.getInt("can_attend") == 1);

                    ClassDataModel classDataModel = new ClassDataModel(className, startTime, endTime, timeCredits, isAttended, canAttend);
                    classModelList.add(classDataModel);
                }
                return classModelList;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean startVerification(String class_name, String time) {
        this.class_name = class_name;
        this.time = time;
        launcher.launch(new Intent(this, FaceVerificationActivity.class));
        return attended;
    }


}
