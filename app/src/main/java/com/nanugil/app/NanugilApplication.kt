package com.nanugil.app

import android.app.Application
import android.util.Log
import com.naver.maps.map.NaverMapSdk

class NanugilApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 키는 local.properties의 NAVER_MAP_CLIENT_ID → BuildConfig로 들어온다 (README "시작하기" 참고).
        NaverMapSdk.getInstance(this).apply {
            client = NaverMapSdk.NcpKeyClient(BuildConfig.NAVER_MAP_CLIENT_ID)
            onAuthFailedListener = NaverMapSdk.OnAuthFailedListener { e ->
                // 키 누락, 패키지명 미등록(com.nanugil.app) 등이면 지도가 회색으로 뜨고 여기로 온다.
                Log.e("NaverMap", "네이버 지도 인증 실패", e)
            }
        }
    }
}
