package com.wegrzyn.marcin.newaudiocast

import android.app.Application
import android.net.Uri
import android.util.Log
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

    private val _beltIsShowing = MutableLiveData<Boolean>(false)
    val beltIsShowing = _beltIsShowing

    private val _castDevName = MutableLiveData<String>("")
    val castDevName : LiveData<String> = _castDevName

    private var mCastSession: CastSession? = null
    private val mSessionManager: SessionManager = CastContext.getSharedInstance(application).sessionManager



    private val mSessionManagerListener: SessionManagerListener<CastSession> =
        SessionManagerListenerImpl()

    inner class SessionManagerListenerImpl : SessionManagerListener<CastSession> {
        override fun onSessionEnded(p0: CastSession, p1: Int) {
            Log.d(TAG,"session ended")
            mCastSession = p0
            _beltIsShowing.postValue(false)

        }

        override fun onSessionEnding(p0: CastSession) {
            Log.d(TAG,"session ending ${p0.castDevice?.friendlyName}")
        }

        override fun onSessionResumeFailed(p0: CastSession, p1: Int) {
            Log.d(TAG,"session resume ${p0.castDevice?.friendlyName}")

            _beltIsShowing.postValue(false)
        }

        override fun onSessionResumed(p0: CastSession, p1: Boolean) {
            Log.d(TAG,"session resumed ${p0.castDevice?.friendlyName} ${p0.applicationMetadata.toString()}")
            mCastSession = p0


        }

        override fun onSessionResuming(p0: CastSession, p1: String) {
            Log.d(TAG,"session Resuming ${p0.castDevice?.friendlyName}")
        }

        override fun onSessionStartFailed(p0: CastSession, p1: Int) {
            Log.d(TAG,"session start failed ${p0.castDevice?.friendlyName}")

            _beltIsShowing.postValue(false)
        }


        override fun onSessionStarted(p0: CastSession, p1: String) {

            Log.d(TAG,"session starded ${p0.castDevice?.friendlyName}")
            mCastSession = p0



        }

        override fun onSessionStarting(p0: CastSession) {
            Log.d(TAG,"session starting ${p0.castDevice?.friendlyName}")
        }

        override fun onSessionSuspended(p0: CastSession, p1: Int) {
            Log.d(TAG,"session suspended ${p0.castDevice?.friendlyName}")
            _beltIsShowing.postValue(false)
        }

    }
    init {
        mCastSession = mSessionManager.currentCastSession

        if (mCastSession!= null){

            remoteMediaClient = mCastSession?.remoteMediaClient!!

            checkState(remoteMediaClient)
        }

        mSessionManager.addSessionManagerListener(mSessionManagerListener, CastSession::class.java)
        Log.d(TAG, "ViewModel Start --> init")
    }

    override fun onCleared() {
        super.onCleared()
        mSessionManager.removeSessionManagerListener(mSessionManagerListener, CastSession::class.java)
        mCastSession = null

        Log.d(TAG,"onCleared")
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

        when{
            remoteMediaClient.isPlaying -> {
                _stateLiveData.postValue(PLAYING)
                _beltIsShowing.postValue(true)
            }

            remoteMediaClient.isPaused -> {
                _stateLiveData.postValue(PAUSE)
                _beltIsShowing.postValue(true)
            }

            remoteMediaClient.isBuffering -> {
                _stateLiveData.postValue(BUFFERING)
                _beltIsShowing.postValue(true)
            }
        }
        val stName = remoteMediaClient.mediaInfo?.metadata?.getString(MediaMetadata.KEY_TITLE)
        _stNameLiveData.postValue(stName!!)
        if(remoteMediaClient.mediaInfo?.metadata!!.hasImages()){
            val imgUri = remoteMediaClient.mediaInfo?.metadata?.images?.get(0)?.url
            _imgUrlLivedata.postValue(imgUri!!)
        }
        _castDevName.postValue(mCastSession?.castDevice?.friendlyName ?: "")
    }
    fun playPause(){
        when{
            remoteMediaClient.isPaused -> remoteMediaClient.play()
            remoteMediaClient.isPlaying -> remoteMediaClient.pause()

        }
    }
}