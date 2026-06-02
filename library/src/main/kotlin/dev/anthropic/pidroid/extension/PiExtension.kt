package dev.anthropic.pidroid.extension

/**
 * Interface for compile-time Pi extensions.
 *
 * Extensions register tools and event handlers during [onRegister].
 * In MVP, extensions are compiled into the app — runtime loading is post-MVP.
 *
 * ## Lifecycle
 * 1. Host creates extension instances and passes them in [PiRuntimeConfig.extensions]
 * 2. During `PiRuntime.initialize()`, each extension's [onRegister] is called
 * 3. Extensions register tools and event handlers via [ExtensionApi]
 * 4. After init, the extension list is frozen — no new registrations
 *
 * ## Thread Safety
 * [onRegister] is called sequentially on a single coroutine during init.
 * After init, registered tools and handlers are accessed read-only.
 */
interface PiExtension {
    /** Unique extension name (used for logging and debugging) */
    val name: String

    /**
     * Called during runtime initialization.
     *
     * Register tools and event handlers here. This is the only
     * opportunity to register — the API is closed after init.
     */
    suspend fun onRegister(api: ExtensionApi)
}
