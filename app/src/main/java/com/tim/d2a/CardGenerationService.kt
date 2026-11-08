package com.tim.d2a

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tim.d2a.data.CardDraft
import com.tim.d2a.data.LookupResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

internal data class GenerationResult(
    val draft: CardDraft, val lookup: LookupResult, val message: String?, val previousDraft: CardDraft?,
)
internal data class GenerationState(
    val id: Long = 0, val running: Boolean = false,
    val result: GenerationResult? = null, val error: String? = null,
)

/** Owns immediate user-requested API work independently of Activity/Compose lifetime.
 * Never replay a paid request after process death; completed drafts are committed by the task.
 */
class CardGenerationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            job?.cancel()
            job = null
            pendingTask = null
            if (state.value.running) {
                state.value = state.value.copy(running = false, error = "Generation cancelled. Any completed text draft is preserved.")
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val taskId = intent?.getLongExtra(EXTRA_ID, 0L) ?: 0L
        val currentState = state.value
        val task = pendingTask
        if (!currentState.running || (taskId != 0L && taskId != currentState.id) || task == null) {
            if (!currentState.running) stopSelf(startId)
            return START_NOT_STICKY
        }
        pendingTask = null
        val activeId = currentState.id
        job?.cancel()
        try {
            channels()
            getSystemService(NotificationManager::class.java).cancel(FINISHED)
            ServiceCompat.startForeground(this, PROGRESS, notification("Generating card", "You can leave the app while this finishes.", true),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        } catch (error: Exception) {
            if (state.value.id == activeId) {
                state.value = GenerationState(id = activeId, error = "Could not start generation: ${error.message}")
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val launchedJob = scope.launch {
            try {
                val result = withTimeout(10 * 60 * 1000L) { task(activeId) }
                if (state.value.id == activeId && state.value.running) {
                    state.value = GenerationState(activeId, result = result)
                    notifyFinished("Card ready for review", "Tap to review your saved draft.")
                }
            } catch (cancelled: CancellationException) {
                if (state.value.id == activeId && state.value.running) {
                    if (cancelled is TimeoutCancellationException) {
                        state.value = state.value.copy(running = false, error = "Generation timed out. Check drafts before retrying.")
                        notifyFinished("Generation timed out", "Open Dict·Anki to check drafts and retry.")
                    } else {
                        state.value = state.value.copy(running = false, error = "Generation cancelled. Any completed text draft is preserved.")
                    }
                }
                throw cancelled
            } catch (error: Exception) {
                if (state.value.id == activeId && state.value.running) {
                    state.value = state.value.copy(running = false, error = error.message ?: "Could not generate a card. Try again.")
                    notifyFinished("Card generation failed", "Open Dict·Anki to check drafts and retry.")
                }
            } finally {
                if (job === coroutineContext[Job]) job = null
                if (state.value.id == activeId && !state.value.running) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
        }
        job = launchedJob
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        if (state.value.running) {
            state.value = state.value.copy(running = false, error = "Android stopped generation. Check drafts before retrying.")
        }
        job?.cancel()
        job = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        scope.cancel()
        job = null
        if (state.value.running && pendingTask == null) {
            state.value = state.value.copy(running = false, error = "Generation interrupted. Check drafts before retrying.")
        }
        super.onDestroy()
    }

    private fun channels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("generation", "Card generation", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel("review", "Cards ready for review", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun notification(title: String, text: String, running: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            action = REVIEW
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, if (running) "generation" else "review")
            .setSmallIcon(R.drawable.ic_logo_monochrome)
            .setContentTitle(title).setContentText(text).setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(running).setAutoCancel(!running).setOnlyAlertOnce(running)
            .apply {
                if (running) {
                    setProgress(0, 0, true)
                    setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                    addAction(0, "Cancel", PendingIntent.getService(this@CardGenerationService, 1,
                        Intent(this@CardGenerationService, CardGenerationService::class.java).setAction(CANCEL),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
            }.build()
    }

    private fun notifyFinished(title: String, message: String) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            try { NotificationManagerCompat.from(this).notify(FINISHED, notification(title, message, false)) }
            catch (_: SecurityException) { /* Permission may be revoked during generation. */ }
        }
    }

    companion object {
        const val REVIEW = "com.tim.d2a.REVIEW_GENERATED_CARD"
        private const val CANCEL = "com.tim.d2a.CANCEL_GENERATION"
        private const val EXTRA_ID = "com.tim.d2a.GENERATION_ID"
        private const val PROGRESS = 1001
        private const val FINISHED = 1002
        internal val state = MutableStateFlow(GenerationState())
        private var pendingTask: (suspend (Long) -> GenerationResult)? = null

        internal fun start(context: Context, task: suspend () -> GenerationResult) {
            if (state.value.running) return
            val nextId = state.value.id + 1
            state.value = GenerationState(id = nextId, running = true)
            pendingTask = { task() }
            try {
                ContextCompat.startForegroundService(context, Intent(context, CardGenerationService::class.java).putExtra(EXTRA_ID, nextId))
            } catch (error: Exception) {
                pendingTask = null
                state.value = GenerationState(nextId, error = error.message)
                throw error
            }
        }
        internal fun cancel(context: Context) {
            if (state.value.running) context.startService(Intent(context, CardGenerationService::class.java).setAction(CANCEL))
        }
    }
}
