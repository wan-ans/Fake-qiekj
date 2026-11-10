package com.fakepangguai.qiekj.data

import okhttp3.Interceptor
import okhttp3.Response

/** 记录接口请求/响应；开启日志时才写入，且对 token 做脱敏。 */
class ApiLogInterceptor(private val logStore: LogStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!logStore.recording) {
            return chain.proceed(chain.request())
        }
        val request = chain.request()
        val path = request.url.encodedPath
        val start = System.currentTimeMillis()
        return try {
            val response = chain.proceed(request)
            val cost = System.currentTimeMillis() - start
            val body = runCatching { response.peekBody(2048)?.string().orEmpty() }.getOrDefault("")
            logStore.append(
                "API " + path + " -> " + response.code + " (" + cost + "ms) " + scrub(body).take(600),
            )
            response
        } catch (error: Throwable) {
            val cost = System.currentTimeMillis() - start
            logStore.append("API " + path + " -> ERROR (" + cost + "ms) " + (error.message ?: error.javaClass.simpleName))
            throw error
        }
    }

    private fun scrub(text: String): String = text
        .replace(Regex("(token=)[^&\\s\"]+"), "${'$'}1***")
        .replace(Regex("(\"(?:token|Authorization)\"\\s*:\\s*\")[^\"]*"), "${'$'}1***")
}
