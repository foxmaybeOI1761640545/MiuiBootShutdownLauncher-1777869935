package com.example.miuipower.heartrate

import android.content.Context
import com.getcapacitor.JSObject
import java.util.concurrent.CopyOnWriteArrayList

typealias HeartRateEventSink = (eventName: String, data: JSObject, retain: Boolean) -> Unit

object HeartRateEnvironment {
    private val eventSinks = CopyOnWriteArrayList<HeartRateEventSink>()

    @Volatile
    private var sharedManager: HeartRateBleManager? = null

    fun manager(context: Context): HeartRateBleManager {
        return sharedManager ?: synchronized(this) {
            sharedManager ?: HeartRateBleManager(
                context = context.applicationContext,
                storage = HeartRateStorage(context.applicationContext.filesDir),
            ) { eventName, data, retain ->
                for (sink in eventSinks) {
                    sink(eventName, data, retain)
                }
            }.also { sharedManager = it }
        }
    }

    fun addEventSink(sink: HeartRateEventSink): () -> Unit {
        eventSinks.add(sink)
        return { eventSinks.remove(sink) }
    }
}
