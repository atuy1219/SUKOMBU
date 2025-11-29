package com.atuy.scomb.util

import java.io.IOException

class SessionExpiredException : Exception("Session has expired. Please log in again.")

class NetworkException(message: String = "Network error", cause: Throwable? = null) : IOException(message, cause)

class ServerException(val code: Int, message: String = "Server error") : Exception(message)

class ClientException(val code: Int, message: String = "Client error") : Exception(message)