package com.wegrzyn.marcin.newaudiocast

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener


class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object{
        val TAG = "TAGTEST"
    }

    private var mCastSession: CastSession? = null
    private var mSessionManager: SessionManager = CastContext.getSharedInstance(application).sessionManager
    private val mSessionManagerListener: SessionManagerListener<CastSession> =
        SessionManagerListenerImpl()

    inner class SessionManagerListenerImpl : SessionManagerListener<CastSession> {
        override fun onSessionEnded(p0: CastSession, p1: Int) {
            TODO("Not yet implemented")
        }

        override fun onSessionEnding(p0: CastSession) {
            TODO("Not yet implemented")
        }

        override fun onSessionResumeFailed(p0: CastSession, p1: Int) {
            TODO("Not yet implemented")
        }

        override fun onSessionResumed(p0: CastSession, p1: Boolean) {
            TODO("update menu")
        }

        override fun onSessionResuming(p0: CastSession, p1: String) {
            TODO("Not yet implemented")
        }

        override fun onSessionStartFailed(p0: CastSession, p1: Int) {
            TODO("Not yet implemented")
        }

        override fun onSessionStarted(p0: CastSession, p1: String) {
            TODO("update menu")
        }

        override fun onSessionStarting(p0: CastSession) {
            TODO("Not yet implemented")
        }

        override fun onSessionSuspended(p0: CastSession, p1: Int) {
            TODO("Not yet implemented")
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
}