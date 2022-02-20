package com.wegrzyn.marcin.newaudiocast

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.cast.framework.CastButtonFactory

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        menuInflater.inflate(R.menu.main_menu, menu)
        CastButtonFactory.setUpMediaRouteButton(
            applicationContext,
            menu,
            R.id.media_route_menu_item
        )
        return true
    }
}