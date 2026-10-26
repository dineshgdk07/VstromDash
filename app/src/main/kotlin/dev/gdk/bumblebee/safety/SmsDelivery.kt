package dev.gdk.bumblebee.safety

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sends the crash texts and follows each one until the phone network takes it. Handing a text
 * to Android isn't the same as it going out: with no signal it fails a moment later. Each
 * contact's text reports back; a failed one is sent again with growing gaps (15 s up to 5 min,
 * about 25 minutes in all), and [status] says how many got through so the alarm can be honest.
 */
object SmsDelivery {
    /** How one send-out stands: [sent] of [total] contacts confirmed, [retrying] still trying. */
    data class Status(
        val total: Int,
        val sent: Int = 0,
        val retrying: Int = 0,
        val failed: Int = 0,
        /** Why the last failure happened, in plain words. */
        val reason: String? = null,
    ) {
        val done: Boolean get() = sent + failed == total
    }

    private const val ACTION_SENT = "dev.gdk.bumblebee.safety.SMS_SENT"
    private const val EXTRA_BATCH = "batch"
    private const val EXTRA_CONTACT = "contact"
    private val BACKOFF_S = longArrayOf(15, 30, 60, 120, 180, 300, 300, 300, 300)

    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status.asStateFlow()

    /** Called on the main thread whenever [status] changes, to refresh the alarm notification. */
    var onChange: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val requestCodes = AtomicInteger((System.currentTimeMillis() % 100_000).toInt() * 100)
    private var batch = 0
    private var appContext: Context? = null

    private class Contact(val number: String, val parts: ArrayList<String>) {
        var attempt = 0
        var pending = 0
        var partFailed = false
        var state = State.SENDING
    }

    private enum class State { SENDING, WAITING, SENT, FAILED }

    private var contacts: List<Contact> = emptyList()

    /**
     * Sends [body] to every number, replacing any earlier send-out's tracking. Returns how many
     * were handed to the phone; [status] then follows what the network did with them.
     */
    fun send(context: Context, numbers: List<String>, body: String): Int {
        val ctx = context.applicationContext
        appContext = ctx
        val sms = smsManager(ctx) ?: return 0
        main.removeCallbacksAndMessages(null)
        batch++
        val parts = sms.divideMessage(body)
        contacts = numbers.map { Contact(it, parts) }
        _status.value = Status(total = contacts.size)
        var queued = 0
        contacts.forEachIndexed { i, c -> if (dispatch(ctx, sms, i, c)) queued++ }
        publish()
        return queued
    }

    /** Stops retrying; the status stays for the screen. */
    fun stop() {
        main.removeCallbacksAndMessages(null)
        batch++
    }

    private fun dispatch(ctx: Context, sms: SmsManager, index: Int, c: Contact): Boolean {
        c.attempt++
        c.pending = c.parts.size
        c.partFailed = false
        c.state = State.SENDING
        val sent = ArrayList<PendingIntent>(c.parts.size)
        repeat(c.parts.size) {
            val intent = Intent(ctx, SmsSentReceiver::class.java)
                .setAction(ACTION_SENT)
                .putExtra(EXTRA_BATCH, batch)
                .putExtra(EXTRA_CONTACT, index)
            sent += PendingIntent.getBroadcast(ctx, requestCodes.incrementAndGet(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
        }
        return runCatching { sms.sendMultipartTextMessage(c.number, null, c.parts, sent, null) }
            .onFailure {
                // Refused outright: no receipts will come, so count it as one failed part.
                c.pending = 1
                onResult(batch, index, ok = false, code = -1)
            }
            .isSuccess
    }

    internal fun onResult(batchId: Int, index: Int, ok: Boolean, code: Int) {
        if (batchId != batch) return
        val c = contacts.getOrNull(index) ?: return
        if (c.state != State.SENDING) return
        if (!ok) {
            c.partFailed = true
            _status.value = _status.value?.copy(reason = reason(code))
        }
        c.pending--
        if (c.pending > 0) return
        if (!c.partFailed) {
            c.state = State.SENT
        } else if (c.attempt <= BACKOFF_S.size) {
            val delay = BACKOFF_S[c.attempt - 1] * 1000
            val current = batch
            c.state = State.WAITING
            main.postDelayed({
                val ctx = appContext ?: return@postDelayed
                val sms = smsManager(ctx) ?: return@postDelayed
                if (current == batch) dispatch(ctx, sms, index, c)
                publish()
            }, delay)
        } else {
            c.state = State.FAILED
        }
        publish()
    }

    private fun publish() {
        val s = _status.value ?: return
        _status.value = s.copy(
            sent = contacts.count { it.state == State.SENT },
            failed = contacts.count { it.state == State.FAILED },
            retrying = contacts.count { it.state == State.WAITING || (it.state == State.SENDING && it.attempt > 1) },
        )
        main.post { onChange?.invoke() }
    }

    private fun reason(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_NO_SERVICE, SmsManager.RESULT_ERROR_RADIO_OFF -> "no mobile signal"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "the phone's SMS limit was reached"
        else -> "the phone couldn't send it"
    }

    private fun smsManager(ctx: Context): SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ctx.getSystemService(SmsManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        SmsManager.getDefault()
    }

    /** How the network answered one part of one text. */
    class SmsSentReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_SENT) return
            onResult(
                intent.getIntExtra(EXTRA_BATCH, -1),
                intent.getIntExtra(EXTRA_CONTACT, -1),
                ok = resultCode == Activity.RESULT_OK,
                code = resultCode,
            )
        }
    }
}
