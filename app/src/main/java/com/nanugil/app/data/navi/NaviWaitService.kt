package com.nanugil.app.data.navi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nanugil.app.R

/**
 * 내장 내비 검색 화면에서 운전자가 목적지를 고르는 동안 앱이 얼지 않게 붙잡아 두는 짧은 포그라운드 서비스.
 *
 * 안드로이드 14는 뒤로 간 앱을 몇 초 뒤 얼려서(cached app freezer) 내비의 "안내 시작" 이벤트를 받지 못한다.
 * 그러면 안내가 시작돼도 나누길 화면으로 돌아올 수 없다. 이 서비스가 도는 동안은 얼지 않는다.
 * shortService라서 최대 3분이면 시스템이 끝내고, 안내가 시작되거나 검색을 그만두면 바로 멈춘다.
 */
class NaviWaitService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_search)
            .setContentTitle("내비에서 목적지를 고르는 중")
            .setContentText("안내가 시작되면 나누길 화면으로 돌아와요")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "포그라운드 서비스 시작 실패", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** shortService 시간(3분)이 다 되면 시스템이 부른다. 바로 멈춰야 앱이 죽지 않는다. */
    override fun onTimeout(startId: Int) {
        Log.d(TAG, "대기 시간 초과 → 서비스 종료")
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "내비 연동", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        private const val TAG = "NaviWaitService"
        private const val CHANNEL_ID = "navi_wait"
        private const val NOTIFICATION_ID = 42

        /** 앱이 화면에 있을 때 부른다 (뒤에서는 포그라운드 서비스를 시작할 수 없다) */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, NaviWaitService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "대기 서비스 시작 실패", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NaviWaitService::class.java))
        }
    }
}
