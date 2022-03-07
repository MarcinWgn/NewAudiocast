package com.wegrzyn.marcin.newaudiocast

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
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
        ConstraintLayout {
            val (list, belt) = createRefs()
            Mylist(modifier = Modifier
                .padding(top = 8.dp, bottom = 8.dp)
                .constrainAs(list){
                start.linkTo(parent.start)
                top.linkTo(parent.top)
                end.linkTo(parent.end)
            })
            ControlBelt(modifier = Modifier.constrainAs(belt){
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                bottom.linkTo(parent.bottom)
            })
        }
    }

    @Composable
    fun Mylist(modifier: Modifier){
        LazyColumn(modifier = modifier){
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
    fun RadioCard(station: RadioStation, modifier: Modifier, model: MainViewModel = viewModel() ){
            val ctx = LocalContext.current
        Card(elevation = 0.dp
            ,shape = RoundedCornerShape(20.dp)
            ,border = BorderStroke(1.dp, Color.Black)
            ,modifier = modifier
            .padding(8.dp)) {
            Column(modifier = Modifier.clickable {
                model.radioCast(radioStation = station, toast = {
                   val tst = Toast.makeText(ctx,"Moja Kochana Jadziu CASTUJ :)", Toast.LENGTH_SHORT)
                    tst.setGravity(Gravity.CENTER,0,0)
                    tst.show()
                })
            }) {
                Text(style = AppTypography.titleLarge , text = station.name, modifier = Modifier
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 16.dp ))
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
                    Text(style = AppTypography.bodyMedium
                        , text = "Ramówka"
                        , modifier = Modifier
                            .padding(16.dp)
                            .clickable {
                                goPage(station.page)
                            })
                }
            }
        }
    }
    private fun goPage(uriString: String){
        val uri = Uri.parse(uriString)
        startActivity(Intent(Intent.ACTION_VIEW,uri))
    }

    @Composable
    fun ControlBelt(model: MainViewModel = viewModel(), modifier: Modifier){

        val value by model.stateLiveData.observeAsState()
        val name by model.stNameLiveData.observeAsState()
        val imgUri by model.imgLiveData.observeAsState()
        
        Card (modifier = modifier
            .fillMaxWidth(1f)
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
            ,shape = RoundedCornerShape(20.dp)
            ,border = BorderStroke(1.dp, Color.Black)){

            ConstraintLayout {

                val (image, text, button, progress) = createRefs()

                Image(painter = rememberImagePainter(data = imgUri
                    ,builder = {
                    crossfade(500)
                } ),contentDescription = null, modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .constrainAs(image) {
                        start.linkTo(parent.start, margin = 16.dp)
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    })
                Text(style = AppTypography.titleLarge, modifier = Modifier
                    .padding(16.dp)
                    .constrainAs(button) {
                        start.linkTo(image.end, margin = 24.dp)
                        bottom.linkTo(parent.bottom)
                        top.linkTo(parent.top)
                    }, text = name!!)
                IconButton(onClick = { /*TODO*/ }, modifier = Modifier
                    .constrainAs(text) {
                        top.linkTo(parent.top, margin = 16.dp)
                        end.linkTo(parent.end, margin = 16.dp)
                        bottom.linkTo(parent.bottom, margin = 16.dp)
                    }) {
                    if(value == MainViewModel.PLAYING || value == MainViewModel.PAUSE){
                        Icon(
                            imageVector =
                            when (value) {
                                MainViewModel.PLAYING -> ImageVector.vectorResource(id = R.drawable.pause_24)
                                MainViewModel.PAUSE -> ImageVector.vectorResource(id = R.drawable.play_24)
                                else -> ImageVector.vectorResource(id = R.drawable.play_24)
                            }, contentDescription = null
                            , modifier = Modifier
                                .padding(8.dp)
                                .size(35.dp)
                                .clip(CircleShape)
                                .clickable {
                                model.playPause()
                            }
                        )
                    }
                }
                if (value == MainViewModel.BUFFERING){
                CircularProgressIndicator(modifier = Modifier
                    .constrainAs(progress) {
                        top.linkTo(parent.top, margin = 16.dp)
                        end.linkTo(parent.end, margin = 20.dp)
                        bottom.linkTo(parent.bottom, margin = 16.dp)
                    }) 
                }
            }
        }
    }
}