package com.example.miuipower.heartrate

import android.content.Context
import com.getcapacitor.JSObject
import java.util.concurrent.CopyOnWriteArrayList

typealias HeartRateEventSink = (eventName: String, data: JSObject, retain: Boolean) -> Unit

object HeartRateEnvironment {
    private val eventSinks = CopyOnWriteArrayList<HeartRateEventSink>()

    @Volatile
    private var sharedManager: HeartRateBleManager? = null
    @Volatile
    private var sharedChunkWriter: HeartRateChunkWriter? = null
    @Volatile
    private var sharedGithubUploader: HeartRateGithubUploader? = null
    @Volatile
    private var sharedUploadQueue: HeartRateUploadQueue? = null
    @Volatile
    private var sharedAutoController: AutoHeartRateController? = null

    fun manager(context: Context): HeartRateBleManager {
        return sharedManager ?: synchronized(this) {
            sharedManager ?: HeartRateBleManager(
                context = context.applicationContext,
                storage = HeartRateStorage(context.applicationContext.filesDir),
            ) { eventName, data, retain ->
                dispatch(eventName, data, retain)
                if (eventName == "heartRateStateChanged") {
                    sharedAutoController?.handleHeartRateStateChanged(data)
                }
            }.also { sharedManager = it }
        }
    }

    fun autoController(context: Context): AutoHeartRateController {
        val appContext = context.applicationContext
        return sharedAutoController ?: synchronized(this) {
            sharedAutoController ?: run {
                val manager = manager(appContext)
                val chunkWriter = sharedChunkWriter ?: HeartRateChunkWriter(appContext.filesDir)
                    .also { sharedChunkWriter = it }
                val githubUploader = sharedGithubUploader ?: HeartRateGithubUploader(appContext)
                    .also { sharedGithubUploader = it }
                lateinit var controller: AutoHeartRateController
                val uploadQueue = sharedUploadQueue ?: HeartRateUploadQueue(
                    context = appContext,
                    chunkWriter = chunkWriter,
                    githubUploader = githubUploader,
                    settingsProvider = { controller.getSettings() },
                ) { eventName, data, retain ->
                    dispatch(eventName, data, retain)
                }.also { sharedUploadQueue = it }
                controller = AutoHeartRateController(
                    context = appContext,
                    manager = manager,
                    chunkWriter = chunkWriter,
                    uploadQueue = uploadQueue,
                ) { eventName, data, retain ->
                    dispatch(eventName, data, retain)
                }
                controller.also { sharedAutoController = it }
            }
        }
    }

    fun addEventSink(sink: HeartRateEventSink): () -> Unit {
        eventSinks.add(sink)
        return { eventSinks.remove(sink) }
    }

    private fun dispatch(eventName: String, data: JSObject, retain: Boolean) {
        for (sink in eventSinks) {
            sink(eventName, data, retain)
        }
    }
}
