package com.tubetv.app.ui.common

import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

fun Throwable.userMessage(): String {
    if (generateSequence(this) { it.cause }.any { it is SignInConfirmNotBotException }) {
        return "YouTube 暂时要求这个网络登录验证（“确认你不是机器人”）。请稍后再试，或用 YouTube 应用打开。"
    }
    return listOfNotNull(javaClass.simpleName, message, cause?.let { "(${it.javaClass.simpleName}: ${it.message})" })
        .joinToString(" ")
}
