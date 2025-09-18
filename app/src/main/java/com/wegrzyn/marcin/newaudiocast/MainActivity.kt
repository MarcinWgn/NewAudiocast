package com.wegrzyn.marcin.newaudiocast

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.mediarouter.app.MediaRouteButton
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.google.android.gms.cast.framework.CastButtonFactory

class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                PortraitView()
            }
        }
    }


    @Composable
    fun PortraitView() {
        Box(modifier = Modifier.fillMaxSize()) {
            CardsList(
                modifier = Modifier
                    .fillMaxHeight()
            )
            Column(
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxHeight()
            ) {
                ControlBelt(modifier = Modifier)
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
            }
        }
    }

    // TODO: cast button
    @Composable
    fun CastButtonXmlView() {
        AndroidView(factory = { context ->
            val castButton = MediaRouteButton(context)
            CastButtonFactory.setUpMediaRouteButton(context, castButton)
            return@AndroidView castButton
        }, update = {

        })
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
            item(span = {
                GridItemSpan(maxLineSpan)
            }) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            }
            items(Stations.stationsList) { item ->
                RadioCard(station = item)
            }
            item {
                Spacer(Modifier.height(84.dp))
            }
        }
    }

    @Composable
    fun RadioCard(station: RadioStation, model: MainViewModel = viewModel()) {
        val ctx = LocalContext.current
        OutlinedCard(
//            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
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
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(data = station.img)
                            .decoderFactory(SvgDecoder.Factory())
                            .crossfade(1000)
                            .build(),
                        placeholder = painterResource(R.drawable.baseline_radio_24),
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
                }
            }
        }
    }

    private fun goPage(uriString: String, context: Context) {
        val uri = uriString.toUri()
        context.startActivity(Intent(Intent.ACTION_VIEW, uri), null)
    }


    @Composable
    fun ControlBelt(model: MainViewModel = viewModel(), modifier: Modifier) {
        val value by model.stateLiveData.observeAsState()
        val name by model.stNameLiveData.observeAsState("")
        val imgUri by model.imgLiveData.observeAsState()
        val isShowing by model.beltIsShowing.observeAsState(false)
        val castDevName by model.castDevName.observeAsState("")

        OutlinedCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isShowing) Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            ImageRequest.Builder(LocalContext.current)
                                .data(data = imgUri)
                                .placeholder(R.drawable.baseline_radio_24)
                                .decoderFactory(SvgDecoder.Factory())
                                .crossfade(500)
                                .build()
                        ),
                        contentDescription = null,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .padding(start = 16.dp)
                    )
                    Column(modifier = Modifier.padding(start = 24.dp, end = 50.dp)) {
                        Text(style = AppTypography.titleLarge, modifier = Modifier, text = name)
                        Text(style = AppTypography.titleSmall, text = castDevName)
                    }
                    IconButton(
                        onClick = { model.playPause() }, modifier = Modifier.padding(start = 16.dp)
                    ) {
                        if (value == MainViewModel.PLAYING || value == MainViewModel.PAUSE) {
                            Icon(
                                imageVector =
                                    when (value) {
                                        MainViewModel.PLAYING -> ImageVector.vectorResource(id = R.drawable.pause_24)
                                        MainViewModel.PAUSE -> ImageVector.vectorResource(id = R.drawable.play_24)
                                        else -> ImageVector.vectorResource(id = R.drawable.play_24)
                                    }, contentDescription = null, modifier = Modifier
                                    .padding(8.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }

                    if (value == MainViewModel.BUFFERING) {
                        CircularProgressIndicator()
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CastButtonXmlView()
                }
            }
        }
    }
}

