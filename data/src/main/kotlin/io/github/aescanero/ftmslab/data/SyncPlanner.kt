package io.github.aescanero.ftmslab.data

/**
 * Decide qué hay que copiar o borrar en cada lado de una sincronización, a partir de la marca de
 * última modificación de cada elemento y de las bajas registradas (tombstones). Kotlin puro.
 *
 * Reglas: un elemento que solo está en un lado se copia al otro; si está en los dos, gana el más
 * reciente. Una baja gana si es posterior o igual a la última modificación del elemento; si no, el
 * elemento se volvió a crear después (p. ej. una máquina capturada de nuevo) y la baja se descarta.
 */
object SyncPlanner {
    data class Plan(
        /** Claves que hay que traer del fichero (nuevas o más recientes allí). */
        val toLocal: Set<String>,
        /** Claves que hay que llevar al fichero (nuevas o más recientes aquí). */
        val toRemote: Set<String>,
        val deleteLocal: Set<String>,
        val deleteRemote: Set<String>,
        /** Bajas que siguen vigentes, con su instante. */
        val tombstones: Map<String, Long>,
    )

    fun plan(local: Map<String, Long>, remote: Map<String, Long>, tombstones: Map<String, Long>): Plan {
        val toLocal = HashSet<String>()
        val toRemote = HashSet<String>()
        val deleteLocal = HashSet<String>()
        val deleteRemote = HashSet<String>()
        val kept = HashMap(tombstones)
        for (key in local.keys + remote.keys) {
            val localAt = local[key]
            val remoteAt = remote[key]
            val newest = maxOf(localAt ?: Long.MIN_VALUE, remoteAt ?: Long.MIN_VALUE)
            val deletedAt = tombstones[key]
            if (deletedAt != null && deletedAt >= newest) {
                if (localAt != null) deleteLocal += key
                if (remoteAt != null) deleteRemote += key
                continue
            }
            if (deletedAt != null) kept.remove(key)
            when {
                localAt == null -> toLocal += key
                remoteAt == null -> toRemote += key
                localAt > remoteAt -> toRemote += key
                remoteAt > localAt -> toLocal += key
            }
        }
        return Plan(toLocal, toRemote, deleteLocal, deleteRemote, kept)
    }
}
