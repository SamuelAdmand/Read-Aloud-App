package com.samuel.readaloud.edgetts

object Constants {
    const val BASE_URL = "speech.platform.bing.com/consumer/speech/synthesize/readaloud"
    const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"

    const val WSS_URL = "wss://$BASE_URL/edge/v1?TrustedClientToken=$TRUSTED_CLIENT_TOKEN"
    const val VOICE_LIST_URL = "https://$BASE_URL/voices/list?trustedclienttoken=$TRUSTED_CLIENT_TOKEN"

    const val DEFAULT_VOICE = "en-US-EmmaMultilingualNeural"

    const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
    const val CHROMIUM_MAJOR_VERSION = "143"
    const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_FULL_VERSION"

    val BASE_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROMIUM_MAJOR_VERSION.0.0.0 Safari/537.36 Edg/$CHROMIUM_MAJOR_VERSION.0.0.0",
        "Accept-Encoding" to "gzip, deflate, br, zstd",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    val WSS_HEADERS = BASE_HEADERS + mapOf(
        "Pragma" to "no-cache",
        "Cache-Control" to "no-cache",
        "Origin" to "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold",
        "Sec-WebSocket-Version" to "13"
    )

    val VOICE_HEADERS = BASE_HEADERS + mapOf(
        "Authority" to "speech.platform.bing.com",
        "Sec-CH-UA" to "\" Not;A Brand\";v=\"99\", \"Microsoft Edge\";v=\"$CHROMIUM_MAJOR_VERSION\", \"Chromium\";v=\"$CHROMIUM_MAJOR_VERSION\"",
        "Sec-CH-UA-Mobile" to "?0",
        "Accept" to "*/*",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Dest" to "empty"
    )
}
