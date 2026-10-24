package com.wegrzyn.marcin.newaudiocast

class RadioStation(
    val name: String,
    val uri: String,
    val img: String,
    val page: String,
    val contentType: String = MP3
) {
    companion object {
        const val MP3 = "audio/mpeg"
        const val AAC = "audio/aac"
        const val HLS = "application/x-mpegURL"
    }

    override fun toString(): String {
        return " name: $name"
    }
}
