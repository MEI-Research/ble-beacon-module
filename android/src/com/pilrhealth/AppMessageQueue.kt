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
 * Delivers message objects reliably to the app.
 *
 * The message is an JSON object with a "type" property.  Type 'message' is reserved for
 * app_log entries. (See EmaLog).
 *
 * Long-running background operation using Titanium properties to queue messages will eventually
 * exhaust memory if the app is not started.
 *
 * This module appends to a queue backed by a file. Also, fetchMessages() returns batches
 * of no more than `max_fetch_bytes`.
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
object AppMessageQueue {
    const val TAG = "EncounterMessageQueue"

    var owner: KrollProxy? = null
    var eventName: String = "ble.event"

    // Limit the size of the fetchMessages response
    var maxFetchBytes = 2 * 1024 * 1024

    var undeliveredMessages: QueueFile
    init {
        val dir = TiApplication.getInstance().filesDir
        val file = File(dir, "queue-$eventName")
        undeliveredMessages = QueueFile.Builder(file).build()
        appLog("AppMessageQueue init",
            "undelivered_message_count" to undeliveredMessages.size())
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
            val nextMessage = nextMessageString()
            if (nextMessage == null) {
                break
            }
            if (result.length > 1) result.append(",")
            result.append(nextMessage)
        }
        result.append("]")
        Log.d(TAG, "fetchMessages: remaining count=" + undeliveredMessages.size())
        return result.toString()
    }

    fun fetchNextMessage(): JSONObject? = nextMessageString()?.let { JSONObject(it) }

    fun nextMessageString(): String? = synchronized(this) {
        try {
            val bytes = undeliveredMessages.peek()
            if (bytes == null) {
                Log.d(TAG, "fetchMessages: no more queued")
                return null
            }
            undeliveredMessages.remove()
            return String(bytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "cannot read undeliveredMessages", e)
            return null
        }
    }

    /**
     * Queue an message to send to EMA
     * 'event_type' and 'timestamp' should always be set
     * @param messageObj - JSON-encodable message. EMA will expect a 'type' field
     */
    fun sendMessage(messageObj: Map<String, Any?>) = synchronized(this) {
        try {
            val json = JSONObject()
            for ((key, value) in messageObj.entries) {
                when (value) {
                    is String, is Number, is Boolean -> json.put(key, value)
                    null -> json.put(key, "(null)")
                    else -> json.put(key, value.toString())
                }
            }
            val messageEncoded = json.toString()
            sendEncodedMessage(messageEncoded)
            Log.d(TAG, "sendMessage, undelivered message count=" + undeliveredMessages.size())
        //} catch (e: IOException) {
        } catch (e: Exception) {
            Log.e(TAG, "sendEncodedMessage failed, messageObj=$messageObj", e)
        }
    }

    fun appLog(message: String, vararg additionalData: Pair<String, Any?>) = synchronized(this) {
       sendMessage(
           mapOf(
               "event_type" to "message",
               "timestamp" to AppMessageQueue.encodeTimestamp(System.currentTimeMillis()),
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

    fun encodeTimestamp(millis: Long): String {
        val formatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
            .withZone(ZoneId.systemDefault())
        val instant = Instant.ofEpochMilli(millis)
        return formatter.format(instant)
    }
}