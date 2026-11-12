package com.nanugil.app.data.server

import com.nanugil.app.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/**
 * 앱 전체가 함께 쓰는 Supabase 연결 (ADR-001).
 * URL과 anon key는 local.properties의 SUPABASE_URL, SUPABASE_ANON_KEY → BuildConfig로 들어온다.
 */
object Supabase {

    val client by lazy {
        check(BuildConfig.SUPABASE_URL.isNotBlank()) { "local.properties에 SUPABASE_URL, SUPABASE_ANON_KEY를 넣어 주세요" }
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }

    /** 로그인 안 돼 있으면 익명 로그인. 사용자 ID를 돌려준다. 세션은 기기에 저장돼 앱을 다시 켜도 같은 ID다. */
    suspend fun ensureSignedIn(): String {
        val auth = client.auth
        auth.awaitInitialization()
        if (auth.currentSessionOrNull() == null) auth.signInAnonymously()
        return checkNotNull(auth.currentUserOrNull()).id
    }
}

/**
 * 서버가 돌려준 오류. [code]는 docs/group-trip-api.md "오류 코드" 표의 값
 * (NOT_MEMBER, INVALID_CODE, TOO_MANY_ATTEMPTS …). 화면은 코드에 맞는 안내 문구를 보여 준다.
 */
class ServerException(val code: String, cause: Throwable? = null) : Exception(code, cause)

/** RPC가 `raise exception '코드'`로 실패하면 [ServerException]으로 바꾼다. */
internal suspend fun <T> serverCall(block: suspend () -> T): T {
    Supabase.ensureSignedIn()
    return try {
        block()
    } catch (e: RestException) {
        throw ServerException(e.error, e)
    }
}
