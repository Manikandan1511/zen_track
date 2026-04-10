package com.example.zentrack;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.example.zentrack.R;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvToggle, tvError;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private boolean isLoginMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        // Already logged in? Go to Home
        if (mAuth.getCurrentUser() != null) {
            goHome(); return;
        }

        etEmail     = findViewById(R.id.etEmail);
        etPassword  = findViewById(R.id.etPassword);
        btnLogin    = findViewById(R.id.btnLogin);
        tvToggle    = findViewById(R.id.tvSignUp);
        progressBar = findViewById(R.id.progressBar);
        tvError     = findViewById(R.id.tvError);

        btnLogin.setOnClickListener(v -> handleAuth());

        tvToggle.setOnClickListener(v -> {
            isLoginMode = !isLoginMode;
            btnLogin.setText(isLoginMode ? "LOGIN" : "CREATE ACCOUNT");
            tvToggle.setText(isLoginMode
                    ? "Don't have an account?  Sign Up"
                    : "Already have an account?  Login");
            tvError.setVisibility(View.GONE);
        });
    }

    private void handleAuth() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) { etEmail.setError("Enter email"); return; }
        if (TextUtils.isEmpty(pass))  { etPassword.setError("Enter password"); return; }
        if (pass.length() < 6)        { etPassword.setError("Min 6 characters"); return; }

        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);
        tvError.setVisibility(View.GONE);

        if (isLoginMode) {
            mAuth.signInWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(task -> {
                        progressBar.setVisibility(View.GONE);
                        btnLogin.setEnabled(true);
                        if (task.isSuccessful()) goHome();
                        else showError(task.getException());
                    });
        } else {
            mAuth.createUserWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(task -> {
                        progressBar.setVisibility(View.GONE);
                        btnLogin.setEnabled(true);
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Account created! Welcome!", Toast.LENGTH_LONG).show();
                            goHome();
                        } else showError(task.getException());
                    });
        }
    }

    private void showError(Exception e) {
        String msg = "Something went wrong";
        if (e != null && e.getMessage() != null) {
            String m = e.getMessage();
            if (m.contains("email address is already")) msg = "Email already registered. Try logging in.";
            else if (m.contains("badly formatted"))      msg = "Invalid email format.";
            else if (m.contains("password is invalid") || m.contains("no user record")) msg = "Wrong email or password.";
            else if (m.contains("network"))              msg = "No internet connection.";
            else msg = m;
        }
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
