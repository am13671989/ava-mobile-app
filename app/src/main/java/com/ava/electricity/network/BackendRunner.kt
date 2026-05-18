package com.ava.electricity.network

import android.os.Handler
import android.os.Looper

object BackendRunner {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun <T> run(
        task: () -> T,
        onSuccess: (T) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        Thread {
            try {
                val result = task()
                mainHandler.post { onSuccess(result) }
            } catch (error: Exception) {
                mainHandler.post { onError(error) }
            }
        }.start()
    }
}
