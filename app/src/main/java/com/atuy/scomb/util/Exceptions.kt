package com.atuy.scomb.util

class SessionExpiredException : Exception("Session has expired. Please log in again.")

class ScrapingFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)