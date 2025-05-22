package com.example.kaisoloapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.widget.Toolbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class RatingActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rating);

        //Toolbar initialisieren
        Toolbar toolbar = findViewById(R.id.navigationToolbar);
        setSupportActionBar(toolbar);

        setupSystemBarInsets(findViewById(R.id.ratingLayout));

    }


}
