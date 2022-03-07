package com.wegrzyn.marcin.newaudiocast

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage


class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object{
        val TAG = "TAGTEST"

        const val PLAYING = 1
        const val PAUSE = 2
        const val BUFFERING = 3
    }

    lateinit var remoteMediaClient : RemoteMediaClient

    private val _stateLiveData = MutableLiveData<Int>()
    val stateLiveData : LiveData<Int> = _stateLiveData

    private val _stNameLiveData = MutableLiveData(" ")
    val stNameLiveData : LiveData<String> = _stNameLiveData

    private val _imgUrlLivedata = MutableLiveData<Uri>()
    val imgLiveData : LiveData<Uri> = _imgUrlLivedata

    private var mCastSession: CastSession? = null
    private var mSessionManager: SessionManager = CastContext.getSharedInstance(application).sessionManager

    private val mSessionManagerListener: SessionManagerListener<CastSession> =
        SessionManagerListenerImpl()

    inner class SessionManagerListenerImpl : SessionManagerListener<CastSession> {
        override fun onSessionEnded(p0: CastSession, p1: Int) {
        }

        override fun onSessionEnding(p0: CastSession) {

        }

        override fun onSessionResumeFailed(p0: CastSession, p1: Int) {
        }

        override fun onSessionResumed(p0: CastSession, p1: Boolean) {
            mCastSession = p0
        }

        override fun onSessionResuming(p0: CastSession, p1: String) {
            mCastSession = p0
        }

        override fun onSessionStartFailed(p0: CastSession, p1: Int) {
        }

        override fun onSessionStarted(p0: CastSession, p1: String) {
            mCastSession = p0
        }

        override fun onSessionStarting(p0: CastSession) {
            mCastSession = p0
        }

        override fun onSessionSuspended(p0: CastSession, p1: Int) {
        }

    }
    init {
        mSessionManager.addSessionManagerListener(mSessionManagerListener, CastSession::class.java)
        Log.d(TAG, "ViewModel Start")
    }

    override fun onCleared() {
        super.onCleared()
        mSessionManager.removeSessionManagerListener(mSessionManagerListener, CastSession::class.java)
        mCastSession = null

    }

    fun radioCast(radioStation: RadioStation,toast: ()->Unit ){

        val mediaMetaData = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC)
        mediaMetaData.putString(MediaMetadata.KEY_TITLE,radioStation.name)
        mediaMetaData.addImage(WebImage(Uri.parse(radioStation.img)))

        val mediaInfo = MediaInfo.Builder(radioStation.uri)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType("audio/mpeg3")
            .setMetadata(mediaMetaData)
            .build()

        val mediaLoadRequestData = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(false)
            .build()

        if (mCastSession!=null&& mCastSession!!.remoteMediaClient != null){
            Log.d(TAG,"remote media client is not null")

            remoteMediaClient = mCastSession?.remoteMediaClient!!
            remoteMediaClient.load(mediaLoadRequestData)
            val waitToResult = remoteMediaClient.load(mediaLoadRequestData)
            waitToResult.addStatusListener {
                remoteMediaClient.play()
            }
            remoteMediaClient.registerCallback( object : RemoteMediaClient.Callback(){
                override fun onStatusUpdated() {
                    super.onStatusUpdated()
                    checkState(remoteMediaClient)
                }
            })
        }else{
            Log.d(TAG,"remote media client is null")
            toast()
        }
    }

    fun checkState(remoteMediaClient: RemoteMediaClient){
        Log.d(TAG, "update")

        when{
            remoteMediaClient.isPlaying -> _stateLiveData.postValue(PLAYING)
            remoteMediaClient.isPaused -> _stateLiveData.postValue(PAUSE)
            remoteMediaClient.isBuffering -> _stateLiveData.postValue(BUFFERING)
        }
        val stName = remoteMediaClient.mediaInfo?.metadata?.getString(MediaMetadata.KEY_TITLE)
        _stNameLiveData.postValue(stName!!)
        if(remoteMediaClient.mediaInfo?.metadata!!.hasImages()){
            val imgUri = remoteMediaClient.mediaInfo?.metadata?.images?.get(0)?.url
            _imgUrlLivedata.postValue(imgUri!!)
        }

    }
    fun playPause(){
        when{
            remoteMediaClient.isPaused -> remoteMediaClient.play()
            remoteMediaClient.isPlaying -> remoteMediaClient.pause()

        }
    }
}