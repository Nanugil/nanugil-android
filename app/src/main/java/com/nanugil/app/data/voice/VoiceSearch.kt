package com.nanugil.app.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 음성 검색 상태. */
data class VoiceState(
    val listening: Boolean = false,
    /** 말하는 중에 실시간으로 들어오는 글자 */
    val partial: String = "",
    val error: String? = null,
)

/**
 * 목적지 음성 검색 (안드로이드 SpeechRecognizer, 한국어).
 * 차량에 등록된 음성 인식 엔진(Pleos는 ai.umos.ambientai)을 그대로 쓴다.
 *
 * - [start]는 메인 스레드에서 부른다 (SpeechRecognizer 제약)
 * - 권한: RECORD_AUDIO (화면에서 먼저 받는다)
 */
class VoiceSearch(context: Context) {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    /** 듣기 시작. 말이 끝나면 [onResult]로 가장 그럴듯한 문장을 준다. */
    fun start(onResult: (String) -> Unit) {
        if (!isAvailable) {
            _state.value = VoiceState(error = "이 차량에서는 음성 인식을 쓸 수 없어요")
            return
        }
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceState(listening = true)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults.firstText() ?: return
                _state.value = _state.value.copy(partial = text)
            }

            override fun onResults(results: Bundle?) {
                val text = results.firstText()
                _state.value = VoiceState()
                if (!text.isNullOrBlank()) onResult(text) else _state.value = VoiceState(error = "말씀을 알아듣지 못했어요")
                releaseRecognizer()
            }

            override fun onError(error: Int) {
                Log.w(TAG, "음성 인식 오류 $error")
                _state.value = VoiceState(error = error.toMessage())
                releaseRecognizer()
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        _state.value = VoiceState(listening = true)
        r.startListening(intent)
    }

    fun stop() {
        recognizer?.cancel()
        releaseRecognizer()
        if (_state.value.listening) _state.value = VoiceState()
    }

    fun clearError() {
        if (_state.value.error != null) _state.value = VoiceState()
    }

    fun release() = stop()

    private fun releaseRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun Bundle?.firstText(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun Int.toMessage(): String = when (this) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말씀을 알아듣지 못했어요. 다시 눌러 말해 주세요"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요해요"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "음성 인식 서버에 연결하지 못했어요"
        SpeechRecognizer.ERROR_AUDIO -> "마이크 입력을 받을 수 없어요"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식이 이미 사용 중이에요"
        else -> "음성 인식 오류 ($this)"
    }

    private companion object {
        const val TAG = "VoiceSearch"
    }
}
