package com.example.timebreaker.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.timebreaker.R
import com.example.timebreaker.ui.home.HomeFragment   // <-- must exist!

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment())
                .commit()
        }
    }
}
