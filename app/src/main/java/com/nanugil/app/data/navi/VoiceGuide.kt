package com.nanugil.app.data.navi

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 길안내 음성 (안드로이드 기본 TTS, 한국어).
 * 차량에 한국어 음성이 없으면 조용히 넘어간다 (화면 안내는 그대로 동작).
 */
class VoiceGuide(context: Context) {

    private var ready = false
    private var pending: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.KOREAN)
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ready) Log.w(TAG, "한국어 음성이 없어 음성 안내를 끕니다")
            pending?.let { if (ready) speak(it) }
            pending = null
        } else {
            Log.w(TAG, "TTS 초기화 실패: $status")
        }
    }

    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        // 이전 안내가 남아 있으면 끊고 최신 안내를 말한다
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val TAG = "VoiceGuide"
    }
}
