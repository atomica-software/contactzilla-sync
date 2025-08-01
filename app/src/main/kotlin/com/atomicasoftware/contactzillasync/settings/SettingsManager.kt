/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.atomicasoftware.contactzillasync.settings

import android.util.NoSuchPropertyException
import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import com.atomicasoftware.contactzillasync.settings.SettingsManager.OnChangeListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.Writer
import java.lang.ref.WeakReference
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings manager which coordinates [SettingsProvider]s to read/write
 * application settings.
 */
@Singleton
class SettingsManager @Inject constructor(
    private val logger: Logger,
    providerMap: Map<Int, @JvmSuppressWildcards SettingsProvider>
): SettingsProvider.OnChangeListener {

    private val providers = LinkedList<SettingsProvider>()
    private var writeProvider: SettingsProvider? = null

    private val observers = LinkedList<WeakReference<OnChangeListener>>()

    init {
        providerMap                 // get providers from Hilt
            .toSortedMap()          // sort by Int key
            .values.reversed()      // take reverse-sorted values (because high priority numbers shall be processed first)
            .forEach { provider ->
            logger.info("Loading settings provider: ${provider.javaClass.name}")

            // register for changes
            provider.setOnChangeListener(this)

            // add to list of available providers
            providers += provider
        }

        // settings will be written to the first writable provider
        writeProvider = providers.firstOrNull { it.canWrite() }
        logger.info("Changed settings are handled by $writeProvider")
    }

    /**
     * Requests all providers to reload their settings.
     */
    @AnyThread
    fun forceReload() {
        for (provider in providers)
            provider.forceReload()

        // notify possible listeners
        onSettingsChanged(null)
    }


    /*** OBSERVERS ***/

    fun addOnChangeListener(observer: OnChangeListener) {
        synchronized(observers) {
            observers += WeakReference(observer)
        }
    }

    fun removeOnChangeListener(observer: OnChangeListener) {
        synchronized(observers) {
            observers.removeAll { it.get() == null || it.get() == observer }
        }
    }

    /**
     * Notifies registered listeners about changes in the configuration.
     * Called by config providers when settings have changed.
     */
    @AnyThread
    override fun onSettingsChanged(key: String?) {
        synchronized(observers) {
            for (observer in observers.mapNotNull { it.get() })
                observer.onSettingsChanged()
        }
    }

    /**
     * Returns a Flow that
     *
     * - always emits the initial value of the setting, and then
     * - emits the new value whenever the setting changes.
     *
     * @param getValue   used to determine the current value of the setting
     */
    @VisibleForTesting
    internal fun<T> observerFlow(getValue: () -> T): Flow<T> = callbackFlow {
        // emit value on changes
        val listener = OnChangeListener {
            trySend(getValue())
        }
        addOnChangeListener(listener)

        // get current value and emit it as first state
        trySend(getValue())

        // wait and clean up
        awaitClose { removeOnChangeListener(listener) }
    }


    /*** SETTINGS ACCESS ***/

    fun containsKey(key: String) = providers.any { it.contains(key) }
    fun containsKeyFlow(key: String): Flow<Boolean> = observerFlow { containsKey(key) }

    private fun<T> getValue(key: String, reader: (SettingsProvider) -> T?): T? {
        logger.fine("Looking up setting $key")
        val result: T? = null
        for (provider in providers)
            try {
                val value = reader(provider)
                val maskedValue = SettingsUtils.filterPassword(key, value)
                logger.finer("${provider::class.java.simpleName}: $key = $maskedValue")
                if (value != null) {
                    logger.fine("Looked up setting $key -> $maskedValue")
                    return value
                }
            } catch(e: Exception) {
                logger.log(Level.SEVERE, "Couldn't read setting from $provider", e)
            }
        logger.fine("Looked up setting $key -> no result")
        return result
    }

    fun getBooleanOrNull(key: String): Boolean? = getValue(key) { provider -> provider.getBoolean(key) }
    fun getBoolean(key: String): Boolean = getBooleanOrNull(key) ?: throw NoSuchPropertyException(key)
    fun getBooleanFlow(key: String): Flow<Boolean?> = observerFlow { getBooleanOrNull(key) }
    fun getBooleanFlow(key: String, defaultValue: Boolean): Flow<Boolean> = observerFlow { getBooleanOrNull(key) ?: defaultValue }

    fun getIntOrNull(key: String): Int? = getValue(key) { provider -> provider.getInt(key) }
    fun getInt(key: String): Int = getIntOrNull(key) ?: throw NoSuchPropertyException(key)
    fun getIntFlow(key: String): Flow<Int?> = observerFlow { getIntOrNull(key) }

    fun getLongOrNull(key: String): Long? = getValue(key) { provider -> provider.getLong(key) }
    fun getLong(key: String) = getLongOrNull(key) ?: throw NoSuchPropertyException(key)

    fun getString(key: String) = getValue(key) { provider -> provider.getString(key) }
    fun getStringFlow(key: String): Flow<String?> = observerFlow { getString(key) }


    fun isWritable(key: String): Boolean {
        for (provider in providers) {
            if (provider.canWrite())
                return true
            else if (provider.contains(key))
                // non-writeable provider contains this key -> setting will always be provided by this read-only provider
                return false
        }
        return false
    }

    private fun<T> putValue(key: String, value: T?, writer: (SettingsProvider) -> Unit) {
        logger.fine("Trying to write setting $key = $value")
        val provider = writeProvider ?: return
        try {
            writer(provider)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Couldn't write setting to $writeProvider", e)
        }
    }

    fun putBoolean(key: String, value: Boolean?) =
        putValue(key, value) { provider -> provider.putBoolean(key, value) }

    fun putInt(key: String, value: Int?) =
        putValue(key, value) { provider -> provider.putInt(key, value) }

    fun putLong(key: String, value: Long?) =
        putValue(key, value) { provider -> provider.putLong(key, value) }

    fun putString(key: String, value: String?) =
        putValue(key, value) { provider -> provider.putString(key, value) }

    fun remove(key: String) = putString(key, null)


    /*** HELPERS ***/

    fun dump(writer: Writer) {
        for ((idx, provider) in providers.withIndex()) {
            writer.write("${idx + 1}. ${provider::class.java.simpleName} canWrite=${provider.canWrite()}\n")
            provider.dump(writer)
        }
    }


    fun interface OnChangeListener {
        /**
         * Will be called when something has changed in a [SettingsProvider].
         * May run in worker thread!
         */
        @AnyThread
        fun onSettingsChanged()
    }

}