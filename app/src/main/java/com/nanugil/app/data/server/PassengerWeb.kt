package com.nanugil.app.data.server

/**
 * 동승자 웹 참여 주소. 03 대기실 QR에 넣는다.
 * 예: passengerJoinUrl("https://host/", "4f7k2q") == "https://host/join?code=4F7K2Q"
 * baseUrl은 BuildConfig.PASSENGER_WEB_URL (local.properties).
 */
fun passengerJoinUrl(baseUrl: String, inviteCode: String): String =
    "${baseUrl.trimEnd('/')}/join?code=${inviteCode.trim().uppercase()}"
