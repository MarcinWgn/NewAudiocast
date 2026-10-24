package com.wegrzyn.marcin.newaudiocast

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest

@Composable
fun StationGrid(
    stations: List<RadioStation>,
    bottomSpace: Dp,
    onStationClick: (RadioStation) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // Always 2 columns in portrait; landscape fits as many as the width allows.
        columns = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) {
            GridCells.Fixed(2)
        } else {
            GridCells.Adaptive(minSize = 170.dp)
        },
        modifier = modifier
    ) {
        items(stations) { item ->
            RadioCard(station = item, onClick = { onStationClick(item) })
        }
        // Keeps the last cards scrollable above the control belt and system bar.
        item(span = {
            GridItemSpan(maxLineSpan)
        }) {
            Column {
                Spacer(Modifier.height(bottomSpace))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
            }
        }
    }
}

@Composable
private fun RadioCard(station: RadioStation, onClick: () -> Unit) {
    val ctx = LocalContext.current
    OutlinedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
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
    context.startActivity(Intent(Intent.ACTION_VIEW, uriString.toUri()), null)
}
