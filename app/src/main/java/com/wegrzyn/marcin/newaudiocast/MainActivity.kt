package com.wegrzyn.marcin.newaudiocast

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.google.android.gms.cast.framework.CastButtonFactory
import com.wegrzyn.marcin.newaudiocast.MainViewModel.Companion.TAG

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        val composeView = findViewById<ComposeView>(R.id.compose_layout_id)
        composeView.setContent {
            AppTheme {
                when (LocalConfiguration.current.orientation) {
                    Configuration.ORIENTATION_PORTRAIT -> PortraitView()
                    Configuration.ORIENTATION_LANDSCAPE -> LandscapeView()
                    else -> PortraitView()
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: ")

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
    fun PortraitView() {
        Scaffold(bottomBar = {
            ControlBelt(modifier = Modifier)
        }) {
            CardsList(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp, bottom = it.calculateBottomPadding())
            )
        }
    }

    @Composable
    fun LandscapeView() {
        ConstraintLayout {
            val (list, belt) = createRefs()
            CardsList(modifier = Modifier
                .fillMaxWidth(0.6f)
                .constrainAs(list) {
                    end.linkTo(parent.end)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                })
            ControlBelt(modifier = Modifier
                .fillMaxWidth(0.4f)
                .constrainAs(belt) {
                    start.linkTo(parent.start)
                    end.linkTo(list.start)
                    bottom.linkTo(parent.bottom)
                })
        }
    }

    @Composable
    fun CardsList(modifier: Modifier) {
        LazyVerticalGrid(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            columns = GridCells.Fixed(2),
            modifier = modifier
        ) {
            items(Stations.stationsList) { item ->
                RadioCard(station = item)
            }
        }
    }

    @Composable
    fun RadioCard(station: RadioStation, model: MainViewModel = viewModel()) {
        val ctx = LocalContext.current
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(Color.Transparent),
            border = CardDefaults.outlinedCardBorder(true)
        ) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    model.radioCast(radioStation = station, toast = {
                        val tst = Toast.makeText(
                            ctx,
                            "Moja Kochana Jadziu CASTUJ :)",
                            Toast.LENGTH_SHORT
                        )
                        tst.setGravity(Gravity.CENTER, 0, 0)
                        tst.show()
                    })
                }) {
                Text(
                    style = AppTypography.titleLarge, text = station.name, modifier = Modifier
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 16.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            ImageRequest.Builder(LocalContext.current).data(data = station.img)
                                .placeholder(R.drawable.baseline_radio_24)
                                .decoderFactory(SvgDecoder.Factory())
                                .crossfade(1000)
                                .build()
                        ),
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .padding(8.dp)
                            .clip(CircleShape)
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.round_playlist_play_24),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(16.dp)
                            .clickable {
                                goPage(station.page, ctx)
                            }
                    )
//                    Text(style = AppTypography.bodySmall, text = "Ramówka", modifier = Modifier
//                        .padding(start = 8.dp, top = 16.dp, bottom = 16.dp, end = 8.dp)
//                        .clickable {
//                            goPage(station.page, ctx)
//                        })
                }
            }
        }
    }

    private fun goPage(uriString: String, context: Context) {
        val uri = Uri.parse(uriString)
        startActivity(context, Intent(Intent.ACTION_VIEW, uri), null)
    }


    @Composable
    fun ControlBelt(model: MainViewModel = viewModel(), modifier: Modifier) {
        val value by model.stateLiveData.observeAsState()
        val name by model.stNameLiveData.observeAsState("")
        val imgUri by model.imgLiveData.observeAsState()
        val isShowing by model.beltIsShowing.observeAsState(false)
        val castDevName by model.castDevName.observeAsState("")

        if (isShowing) {
            Card(
                modifier = modifier
                    .fillMaxWidth(1f)
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(Color.Transparent),
                border = CardDefaults.outlinedCardBorder(true)
            ) {

                ConstraintLayout(modifier = Modifier.fillMaxWidth(1f)) {
                    val (image, text, button, progress, device) = createRefs()
                    Image(painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current)
                            .data(data = imgUri)
                            .placeholder(R.drawable.baseline_radio_24)
                            .decoderFactory(SvgDecoder.Factory())
                            .crossfade(500)
                            .build()
                    ),
                        contentDescription = null,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .constrainAs(image) {
                                start.linkTo(parent.start, margin = 16.dp)
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom)
                            })
                    Text(
                        style = AppTypography.titleLarge, modifier = Modifier
                            .padding(16.dp)
                            .constrainAs(text) {
                                start.linkTo(image.end)
                                bottom.linkTo(device.top)
                                top.linkTo(parent.top)
                            }, text = name
                    )
                    Text(
                        style = AppTypography.titleSmall, modifier = Modifier
                            .constrainAs(device) {
                                start.linkTo(image.end, margin = 16.dp)
                                bottom.linkTo(parent.bottom, margin = 16.dp)
                                top.linkTo(text.bottom)
                            }, text = castDevName
                    )
                    IconButton(onClick = { model.playPause() }, modifier = Modifier
                        .constrainAs(button) {
                            top.linkTo(parent.top, margin = 16.dp)
                            end.linkTo(parent.end, margin = 16.dp)
                            bottom.linkTo(parent.bottom, margin = 16.dp)
                        }) {
                        if (value == MainViewModel.PLAYING || value == MainViewModel.PAUSE) {
                            Icon(
                                imageVector =
                                when (value) {
                                    MainViewModel.PLAYING -> ImageVector.vectorResource(id = R.drawable.pause_24)
                                    MainViewModel.PAUSE -> ImageVector.vectorResource(id = R.drawable.play_24)
                                    else -> ImageVector.vectorResource(id = R.drawable.play_24)
                                }, contentDescription = null, modifier = Modifier
                                    .padding(8.dp)
                                    .size(35.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                    if (value == MainViewModel.BUFFERING) {
                        CircularProgressIndicator(modifier = Modifier
                            .constrainAs(progress) {
                                top.linkTo(parent.top, margin = 16.dp)
                                end.linkTo(parent.end, margin = 16.dp)
                                bottom.linkTo(parent.bottom, margin = 16.dp)
                            })
                    }
                }
            }
        }
    }
}
