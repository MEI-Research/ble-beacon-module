package com.pilrhealth

import android.os.Build
import androidx.annotation.RequiresApi
import com.squareup.tape2.QueueFile
import org.appcelerator.kroll.KrollProxy
import org.appcelerator.kroll.common.Log
import org.appcelerator.titanium.TiApplication
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * Delivers message objects reliably to EMA
 *
 * The message is an JSON object with a "type" property.  Type 'message' is reserved for
 * app_log entries. (See EmaLog).
 *
 * Using Titanium properties to queue messages can exhaust memory if messages are generated in
 * background for a long time without the app fetching them.  This module will limit the memory used
 * to `max_fetch_bytes`.
 *
 * ```typescript
 * type Event = Record<string></string>,unknown> & { "type": string }
 * ```
 *
 * See https://github.com/square/tape
 */
@RequiresApi(api = Build.VERSION_CODES.O)
class EmaMessageQueue(eventName: String, owner: KrollProxy) {
    // Limit the size of the fetchMessages response
    var max_fetch_bytes = 2 * 1024 * 1024
    private val eventName: String
    private val owner: KrollProxy
    private var undeliveredMessages: QueueFile? = null

    /**
     * @param eventName name for the event triggered when a message is sent
     * @param owner - the KrollProxy (or KrollModule) that delivers Ti events. The owner should
     * implement a "@Kroll fetchMessages()" method that delegates to an EmaMessageQueue
     */
    init {
        Log.d(TAG, "created $eventName")
        this.eventName = eventName
        this.owner = owner
        val dir = TiApplication.getInstance().filesDir
        val file = File(dir, "queue-$eventName")
        try {
            undeliveredMessages = QueueFile.Builder(file).build()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Dequeue and return  queued messages.  To avoid exhausting memory, not all messages may be
     * return in one call. See `max_fetch_bytes`.
     *
     * A KrollProxy method should delegate to this.
     *
     * @return JSON-encoding of the array of outstanding events.
     *
     * TODO: If not all messages are returned, schedule an event to fire shortly.
     */
    fun fetchMessages(): String {
        Log.d(TAG, "fetchMessages: intitial count=" + undeliveredMessages!!.size())
        val result = StringBuffer("[")
        while (result.length < max_fetch_bytes) {
            try {
                val bytes = undeliveredMessages!!.peek()
                if (bytes == null) {
                    Log.d(TAG, "fetchMessages: no more queued")
                    break
                }
                if (result.length > 1) result.append(",")
                result.append(String(bytes, StandardCharsets.UTF_8))
                undeliveredMessages!!.remove()
            } catch (e: Exception) {
                Log.e(TAG, "cannot read undeliveredMessages", e)
                break
            }
        }
        result.append("]")
        Log.d(TAG, "fetchMessages: remaining count=" + undeliveredMessages!!.size())
        return result.toString()
    }

    /**
     * Queue an message to send to EMA
     * @param message - JSON-encodable message. EMA will expect a 'type' field
     */
    fun sendMessage(message: Map<String, Any?>) {
        val messageEncoded = JSONObject(message).toString()
        Log.d(TAG, "sendMessage: $message")
        try {
            sendEncodedMessage(messageEncoded)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        Log.d(TAG, "sendMessage, undelivered message count=" + undeliveredMessages!!.size())
    }

    @Synchronized
    @Throws(IOException::class)
    private fun sendEncodedMessage(messageEncoded: String) {
        undeliveredMessages!!.add(messageEncoded.toByteArray(StandardCharsets.UTF_8))
        // Eventually EMA will only need to call fetchMessages() on open, resume and when this event
        // is received. Or this module could detect open & resumes and fire the event..
        val hasListener = owner.fireEvent(eventName, null)
        if (!hasListener) {
            Log.d(TAG, "No listener for message: $messageEncoded")
        }
    }

    companion object {
        const val TAG = "EmaMessageQueue"

        fun encodeTimestamp(millis: Long): String {
            val formatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
                .withZone(ZoneId.systemDefault());
            val instant = Instant.ofEpochMilli(millis)
            return formatter.format(instant)
        }
    }
}