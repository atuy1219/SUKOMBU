package com.atuy.scomb.util

/**
 * ScombZのセッションが無効になった、または期限切れになったことを示す例外
 */
class SessionExpiredException : Exception("Session has expired. Please log in again.")

/**
 * スクレイピング処理中に予期せぬエラーが発生したことを示す例外
 */
class ScrapingFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)