package com.pilrhealth

import android.util.Log
import org.appcelerator.titanium.TiApplication
import org.appcelerator.titanium.TiProperties
import java.lang.IllegalStateException
import kotlin.reflect.KProperty

private const val TAG = "PersistedProperty"

fun persistedBoolean(defaultValue: Boolean) = PersistedProperty(defaultValue, {it.toBoolean()})
fun persistedString(defaultValue: String) = PersistedProperty(defaultValue, {it})
fun persistedLong(defaultValue: Long) = PersistedProperty(defaultValue, {it.toLong()})

/**
 * A property delegate for properties backed by a persistent Titanium property
 */
class PersistedProperty<T>(
    defaultValue: T,
    val fromString: (String) -> T,
    val intoString: (T) -> String = { it.toString() },
    val willUpdate: (T?,T) -> T = { oldVal, newVal -> newVal },
) {
    var key: String? = null
    var value: T = defaultValue // willUpdate(null, defaultValue)

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        ensureInit(thisRef, property)
        return value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        ensureInit(thisRef, property)
        this.value = willUpdate(this.value, value)
        tiProperties().setString(key, intoString(value))
    }

    private fun ensureInit(thisRef: Any?, property: KProperty<*>) {
        if (key == null) {
            if (thisRef == null) {
                throw IllegalStateException("No owner for property $property")
            }
            key = "${thisRef.javaClass.name}.${property.name}"
            val stringVal = tiProperties().getString(key, null)
            if (stringVal != null) {
                value = willUpdate(this.value, fromString(stringVal))
            }
            Log.d(TAG, "restored $key -> $value")
        }
    }

    private inline fun tiProperties(): TiProperties =
        TiApplication.getInstance().getAppProperties()
}
