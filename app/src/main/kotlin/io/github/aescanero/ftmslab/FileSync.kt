package io.github.aescanero.ftmslab

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import io.github.aescanero.ftmslab.data.SyncEngine
import io.github.aescanero.ftmslab.protocol.DemoWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class SyncState(
    /** Nombre del fichero elegido; null si la sincronización no está configurada. */
    val fileName: String? = null,
    val running: Boolean = false,
    val lastSyncAtMs: Long? = null,
    val received: Int = 0,
    val sent: Int = 0,
    /** Mensaje técnico del último fallo (en inglés), o null. */
    val error: String? = null,
)

/**
 * Sincronización con un fichero SQLite elegido por el usuario con el selector de Android (Storage
 * Access Framework). Si el fichero está en Google Drive, la app de Drive es la que lo sube y lo baja:
 * esta app no se conecta a Internet ni necesita credenciales propias.
 */
class FileSync(private val context: Context, private val container: AppContainer) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(
        SyncState(
            fileName = prefs.getString(KEY_NAME, null)?.takeIf { prefs.getString(KEY_URI, null) != null },
            lastSyncAtMs = prefs.getLong(KEY_LAST, 0).takeIf { it > 0 },
        ),
    )
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val uri: Uri? get() = prefs.getString(KEY_URI, null)?.let(Uri::parse)

    fun setFile(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            .onFailure { container.eventLog.add("Sync", "Persistable permission not granted: ${it.message}") }
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: uri.toString()
        prefs.edit().putString(KEY_URI, uri.toString()).putString(KEY_NAME, name).remove(KEY_LAST).apply()
        _state.value = SyncState(fileName = name)
        request()
    }

    fun clear() {
        uri?.let { current ->
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.releasePersistableUriPermission(current, flags) }
        }
        prefs.edit().clear().apply()
        _state.value = SyncState()
    }

    /** Lanza una sincronización en segundo plano si hay fichero configurado. */
    fun request() {
        if (uri == null) return
        container.scope.launch(Dispatchers.IO) { sync() }
    }

    private suspend fun sync() = mutex.withLock {
        val target = uri ?: return@withLock
        _state.update { it.copy(running = true, error = null) }
        val resolver = context.contentResolver
        val temp = File(context.cacheDir, "sync.db")
        try {
            temp.delete()
            File(temp.path + "-journal").delete()
            val input = resolver.openInputStream(target) ?: error("Cannot read the sync file")
            input.use { source -> temp.outputStream().use { source.copyTo(it) } }
            // Un fichero vacío es uno recién creado: SQLite lo inicializa.
            if (temp.length() == 0L) {
                temp.delete()
            } else {
                check(SyncEngine.isSqlite(temp)) { "The chosen file is not an FTMS Lab database" }
            }
            val result = SyncEngine(container.dao).sync(temp, container.profiles.rows(), DemoWorkout.NAME)
            container.profiles.replaceAll(result.profiles)
            val output = runCatching { resolver.openOutputStream(target, "wt") }.getOrNull()
                ?: resolver.openOutputStream(target, "w")
                ?: error("Cannot write the sync file")
            output.use { sink -> temp.inputStream().use { it.copyTo(sink) } }

            // Las sesiones traídas de otro dispositivo también se guardan en el Health Connect de este.
            if (container.settings.value.healthConnect) {
                result.receivedSessionIds.forEach { id ->
                    val session = container.dao.session(id) ?: return@forEach
                    if (container.healthConnect.write(session, container.dao.samples(id))) {
                        container.dao.setHealthConnectAt(id, System.currentTimeMillis())
                    }
                }
            }
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST, now).apply()
            container.eventLog.add("Sync", "Synced: ${result.received} received, ${result.sent} sent")
            _state.update { it.copy(lastSyncAtMs = now, received = result.received, sent = result.sent) }
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            container.eventLog.add("Sync", "Sync failed: $message")
            _state.update { it.copy(error = message) }
        } finally {
            temp.delete()
            _state.update { it.copy(running = false) }
        }
    }

    private companion object {
        const val KEY_URI = "uri"
        const val KEY_NAME = "fileName"
        const val KEY_LAST = "lastSyncAtMs"
    }
}
