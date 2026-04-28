package com.example.safefnow2.ui.common

class Event<out T>(private val value: T) {
    private var handled = false

    fun getIfNotHandled(): T? {
        if (handled) return null
        handled = true
        return value
    }

    fun peek(): T = value
}

