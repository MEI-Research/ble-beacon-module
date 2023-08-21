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
 * @param eventName name for the event triggered when a message is sent
 * implement a "@Kroll fetchMessages()" method that delegates to an EmaMessageQueue
 *
 * See https://github.com/square/tape
 */
@RequiresApi(api = Build.VERSION_CODES.O)
class EncounterMessageQueue(val eventName: String) {
    var owner: KrollProxy? = null

    // Limit the size of the fetchMessages response
    var maxFetchBytes = 2 * 1024 * 1024

    var undeliveredMessages: QueueFile
    init {
        val dir = TiApplication.getInstance().filesDir
        val file = File(dir, "queue-$eventName")
        undeliveredMessages = QueueFile.Builder(file).build()
        Log.i(TAG, "$eventName, init, undeliveredMessages.count=${undeliveredMessages.size()}")
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
    fun fetchMessages(): String = synchronized(this) {
        Log.d(TAG, "fetchMessages $eventName: initial count=" + undeliveredMessages.size())
        val result = StringBuffer("[")
        while (result.length < maxFetchBytes) {
            try {
                val bytes = undeliveredMessages.peek()
                if (bytes == null) {
                    Log.d(TAG, "fetchMessages: no more queued")
                    break
                }
                if (result.length > 1) result.append(",")
                result.append(String(bytes, StandardCharsets.UTF_8))
                undeliveredMessages.remove()
            } catch (e: Exception) {
                Log.e(TAG, "cannot read undeliveredMessages", e)
                break
            }
        }
        result.append("]")
        Log.d(TAG, "fetchMessages: remaining count=" + undeliveredMessages.size())
        return result.toString()
    }

    /**
     * Queue an message to send to EMA
     * 'event_type' and 'timestamp' should always be set
     * @param messageObj - JSON-encodable message. EMA will expect a 'type' field
     */
    fun sendMessage(messageObj: Map<String, Any?>) = synchronized(this) {
        try {
            val messageEncoded = JSONObject(messageObj).toString()
            sendEncodedMessage(messageEncoded)
            Log.d(TAG, "sendMessage, undelivered message count=" + undeliveredMessages.size())
        //} catch (e: IOException) {
        } catch (e: Exception) {
            Log.e(TAG, "sendEncodedMessage failed", e)
        }
    }

    fun appLog(message: String, vararg additionalData: Pair<String, Any?>) = synchronized(this) {
       sendMessage(
           mapOf(
               "event_type" to "message",
               "timestamp" to EncounterMessageQueue.encodeTimestamp(System.currentTimeMillis()),
               "message" to message,
               "more_data" to mapOf(*additionalData),
           ))
    }

    @Synchronized
    @Throws(IOException::class)
    private fun sendEncodedMessage(messageEncoded: String) {
        Log.d(TAG, "sendEncodedMessage($messageEncoded)")
        undeliveredMessages.add(messageEncoded.toByteArray(StandardCharsets.UTF_8))
        // Eventually EMA will only need to call fetchMessages() on open, resume and when this event
        // is received. Or this module could detect open & resumes and fire the event..
        val hasListener = owner?.fireEvent(eventName, null) ?: false
        if (!hasListener) {
            Log.d(TAG, "No listener for message: $messageEncoded")
        }
    }

    companion object {
        const val TAG = "EncounterMessageQueue"

        fun encodeTimestamp(millis: Long): String {
            val formatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
                .withZone(ZoneId.systemDefault())
            val instant = Instant.ofEpochMilli(millis)
            return formatter.format(instant)
        }
    }
}