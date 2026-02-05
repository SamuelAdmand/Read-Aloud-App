package com.samuel.readaloud.googletts

object Constants {
    const val TRANSLATE_URL = "https://translate.google.com/_/TranslateWebserverUi/data/batchexecute"
    
    val HEADERS = mapOf(
        "Referer" to "http://translate.google.com/",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/47.0.2526.106 Safari/537.36",
        "Content-Type" to "application/x-www-form-urlencoded;charset=utf-8"
    )
    
    const val RPC_ID = "jQ1olc"
}
