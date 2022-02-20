package com.wegrzyn.marcin.newaudiocast

import android.os.Bundle
import android.util.Log
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import coil.compose.rememberImagePainter
import com.google.android.gms.cast.framework.CastButtonFactory

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val composeView = findViewById<ComposeView>(R.id.compose_layout_id)
        composeView.setContent {
            AppTheme{
                MyView()
            }
        }
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
    @Composable
    fun MyView(){
        Mylist()
    }

    @Composable
    fun Mylist(){
        LazyColumn(){
            var length = Stations.stationsList.size
            val twoStations = ArrayList<RadioStation>(2)

            Stations.stationsList.forEach {
                length--
                twoStations.add(it)
                Log.d(MainViewModel.TAG, "stations lenght: $length twoArr: $twoStations size: ${twoStations.size}")
                if (twoStations.size == 2){
                    val first = twoStations[0]
                    val sec = twoStations[1]
                    item {
                        CardsRow(first = first, second = sec )
                    }
                    twoStations.clear()
                }
            }
        }
    }

    @Composable
    fun CardsRow(first: RadioStation, second: RadioStation){
        Row {
            RadioCard(station = first, modifier = Modifier.fillMaxWidth(0.5f))
            if (second!=null)
                RadioCard(station = second, modifier = Modifier.fillMaxWidth(1f))
        }
        }
    @Composable
    fun RadioCard(station: RadioStation, modifier: Modifier){
        Card(elevation = 0.dp
            ,shape = RoundedCornerShape(10.dp)
            ,border = BorderStroke(1.dp, Color.Black)
            ,modifier = modifier
            .padding(8.dp)) {
            Column() {
                Text(style = AppTypography.titleLarge , text = station.name, modifier = Modifier.padding(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = rememberImagePainter(
                            data =station.img,
                            builder = {
                                crossfade(500)
                            }),
                        contentDescription =null,
                        modifier = Modifier
                            .size(80.dp)
                            .padding(8.dp)
                            .clip(CircleShape))
                    Text(style = AppTypography.labelLarge, text = "Ramówka", modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}