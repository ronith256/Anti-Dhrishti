package com.lucario.antidhrishti;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {
    private EditText username, password;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private Button loginButton;
    private boolean buttonState;

    private String baseURL = "https://antidhrishti.lucario.site";
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences("cred", MODE_PRIVATE);
        if(isLoggedIn()){
            if(sharedPreferences.getBoolean("face-enrolled", false)){
                Intent intent = new Intent(LoginActivity.this, ClassActivity.class);
                startActivity(intent);
                finish();
            } else {
                Intent intent = new Intent(LoginActivity.this, FaceEnroll.class);
                startActivity(intent);
                finish();
            }
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }

        setContentView(R.layout.activity_login);
        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        loginButton = findViewById(R.id.loginButton);

        loginButton.setEnabled(false);

        username.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                buttonState = charSequence.length() > 1;
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        password.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                loginButton.setEnabled(charSequence.length() > 1 && buttonState);
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        loginButton.setOnClickListener(e->{
            new LoginTask().execute(username.getText().toString().trim(), password.getText().toString());
        });
    }


    public String hashWithSHA256(String textToHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(
                    textToHash.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if(hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private boolean isLoggedIn(){
        return sharedPreferences.getBoolean("isLoggedIn", false);
    }

    private class LoginTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            String username = params[0];
            String password = params[1];

            try {
                return login(username, password);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                sharedPreferences.edit().putBoolean("isLoggedIn", true).apply();
                Intent intent = new Intent(LoginActivity.this, FaceEnroll.class);
                startActivity(intent);
            } else {
                showErrorDialog();
            }
        }

        private boolean login(String username, String password) {
            OkHttpClient client = new OkHttpClient();

            FormBody formBody = new FormBody.Builder()
                    .add("username", username)
                    .add("password", hashWithSHA256(password))
                    .build();

            Request request = new Request.Builder()
                    .url(baseURL + "/login")
                    .post(formBody)
                    .addHeader("Accept", "application/json")
                    .build();

            try {
                Response response = client.newCall(request).execute();
                JSONObject jsonObject = new JSONObject(response.body().string());
                String secretKey = jsonObject.getString("Secret-Key");
                String classID = jsonObject.getString("Batch-ID");
                String studentID = jsonObject.getString("Student-ID");
//                System.out.println(studentID);
//                System.out.println(secretKey);
//                System.out.println(classID);
                if (secretKey != null) {
                    sharedPreferences.edit().putString("secret-key", secretKey).apply();
                    sharedPreferences.edit().putString("batch-id", classID).apply();
                    sharedPreferences.edit().putString("student-id", studentID).apply();
                    System.out.println(classID);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(LoginActivity.this, "Logged IN", Toast.LENGTH_SHORT).show();
                    }
                });
                return response.code() == 200;
            } catch (IOException | JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void showErrorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Invalid Credentials")
                .setMessage("The username or password you entered doesn't appear to belong to an account. Please check your input and try again.")
                .setPositiveButton("Try Again", (dialog, which) -> {
                    // Handle "Try Again" button click
                    dialog.dismiss();
                });

        AlertDialog dialog = builder.create();
        dialog.show();

        // Set a custom width for the dialog buttons area
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setMinimumWidth(0);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setMinimumHeight(0);
    }

}
