package com.atuy.scomb.util

interface LoginListener {    
    fun onSuccess(sessionId: String)
    fun onLoginError(message: String)
    fun onTwoFactorCodeExtracted(code: String)
}
