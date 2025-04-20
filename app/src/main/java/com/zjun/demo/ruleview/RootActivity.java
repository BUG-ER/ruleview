package com.zjun.demo.ruleview;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

public class RootActivity extends AppCompatActivity {

    private Button btnMainActivity;
    private Button btnMainActivity1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_root);

        btnMainActivity = findViewById(R.id.btn_main_activity);
        btnMainActivity1 = findViewById(R.id.btn_main_activity1);

        btnMainActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RootActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });

        btnMainActivity1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RootActivity.this, MainActivity1.class);
                startActivity(intent);
            }
        });
    }
} 