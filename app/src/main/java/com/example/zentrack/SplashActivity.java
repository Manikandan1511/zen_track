package com.example.zentrack;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.example.zentrack.R;

public class SplashActivity extends AppCompatActivity {

    private AnimatorSet floatSet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Check Firebase login — if logged in skip straight to Home
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            goTo(HomeActivity.class);
            return;
        }

        View logo = findViewById(R.id.splashLogo);
        logo.setAlpha(0f);
        logo.setScaleX(0.6f);
        logo.setScaleY(0.6f);

        ObjectAnimator a = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f);
        ObjectAnimator x = ObjectAnimator.ofFloat(logo, "scaleX", 0.6f, 1f);
        ObjectAnimator y = ObjectAnimator.ofFloat(logo, "scaleY", 0.6f, 1f);
        a.setDuration(900); x.setDuration(900); y.setDuration(900);

        AnimatorSet entry = new AnimatorSet();
        entry.playTogether(a, x, y);
        entry.setInterpolator(new AccelerateDecelerateInterpolator());
        entry.start();

        new Handler().postDelayed(() -> {
            if (!isDestroyed()) startFloating(logo);
        }, 950);

        Button btnGetStarted = findViewById(R.id.btnGetStarted);
        Button btnLogin      = findViewById(R.id.btnLoginSplash);
        btnGetStarted.setOnClickListener(v -> goTo(HomeActivity.class));
        btnLogin.setOnClickListener(v      -> goTo(LoginActivity.class));
    }

    private void startFloating(View v) {
        ObjectAnimator up   = ObjectAnimator.ofFloat(v, "translationY", 0f, -14f);
        ObjectAnimator down = ObjectAnimator.ofFloat(v, "translationY", -14f, 0f);
        up.setDuration(1400); down.setDuration(1400);
        floatSet = new AnimatorSet();
        floatSet.playSequentially(up, down);
        floatSet.setInterpolator(new AccelerateDecelerateInterpolator());
        floatSet.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator anim) {
                if (!isDestroyed() && floatSet != null) floatSet.start();
            }
        });
        floatSet.start();
    }

    private void goTo(Class<?> c) {
        startActivity(new Intent(this, c));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (floatSet != null) { floatSet.cancel(); floatSet = null; }
    }
}