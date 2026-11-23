package io.github.aescanero.ftmslab.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Perfil de máquina tal como viaja en el fichero de sincronización (JSON del ProfileStore). */
data class ProfileRow(val address: String, val json: String, val updatedAtMs: Long)

/**
 * Sincroniza la base de datos local (Room) con un fichero SQLite compartido, por ejemplo en Google
 * Drive. La base local sigue siendo la de trabajo; el fichero solo es el punto de encuentro entre
 * dispositivos. Las sesiones se identifican por su inicio (startedAtMs), igual que en Health Connect.
 *
 * Esquema del fichero (pensado también para consultarlo con SQL fuera de la app):
 * sessions (una fila por sesión), samples (muestras por sessionStartedAtMs), profiles (máquinas),
 * tombstones (bajas) y meta.
 */
class SyncEngine(private val dao: SessionDao) {
    data class Result(
        val received: Int,
        val sent: Int,
        /** Ids locales de las sesiones nuevas traídas del fichero. */
        val receivedSessionIds: List<Long>,
        val profiles: List<ProfileRow>,
    )

    suspend fun sync(file: File, localProfiles: List<ProfileRow>, excludedMachine: String): Result {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            createSchema(db)
            val tombstones = (dao.tombstones().map { Triple(it.kind, it.key, it.atMs) } + readTombstones(db))
                .groupBy { it.first to it.second }
                .mapValues { (_, list) -> list.maxOf { it.third } }
            fun tombstonesOf(kind: String) = tombstones.filterKeys { it.first == kind }.mapKeys { it.key.second }

            val local = dao.finishedSessions().filter { it.machineName != excludedMachine }.associateBy { it.startedAtMs.toString() }
            val remote = readStamps(db, "SELECT startedAtMs, updatedAtMs FROM sessions")
            val sessions = SyncPlanner.plan(local.mapValues { it.value.updatedAtMs }, remote, tombstonesOf(Tombstones.SESSION))

            val localProfileMap = localProfiles.associateBy { it.address }
            val remoteProfiles = readProfiles(db)
            val profiles = SyncPlanner.plan(
                localProfileMap.mapValues { it.value.updatedAtMs },
                remoteProfiles.mapValues { it.value.updatedAtMs },
                tombstonesOf(Tombstones.PROFILE),
            )

            db.beginTransaction()
            try {
                sessions.deleteRemote.forEach { key ->
                    db.delete("samples", "sessionStartedAtMs = ?", arrayOf(key))
                    db.delete("sessions", "startedAtMs = ?", arrayOf(key))
                }
                for (key in sessions.toRemote) {
                    val session = local.getValue(key)
                    db.insertWithOnConflict("sessions", null, session.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
                    if (key !in remote) dao.samples(session.id).forEach { db.insert("samples", null, it.toValues(session.startedAtMs)) }
                }
                profiles.deleteRemote.forEach { db.delete("profiles", "address = ?", arrayOf(it)) }
                profiles.toRemote.forEach { address ->
                    val row = localProfileMap.getValue(address)
                    db.insertWithOnConflict(
                        "profiles",
                        null,
                        ContentValues().apply {
                            put("address", row.address)
                            put("json", row.json)
                            put("updatedAtMs", row.updatedAtMs)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
                db.delete("tombstones", null, null)
                val kept = sessions.tombstones.map { TombstoneEntity(Tombstones.SESSION, it.key, it.value) } +
                    profiles.tombstones.map { TombstoneEntity(Tombstones.PROFILE, it.key, it.value) }
                kept.forEach {
                    db.insert(
                        "tombstones",
                        null,
                        ContentValues().apply {
                            put("kind", it.kind)
                            put("key", it.key)
                            put("atMs", it.atMs)
                        },
                    )
                }
                db.insertWithOnConflict(
                    "meta",
                    null,
                    ContentValues().apply {
                        put("key", "format")
                        put("value", FORMAT)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                db.setTransactionSuccessful()

                // Cambios en local, una vez preparado el fichero.
                sessions.deleteLocal.forEach { key -> local[key]?.let { dao.deleteSession(it.id) } }
                val received = ArrayList<Long>()
                for (key in sessions.toLocal) {
                    val row = readSession(db, key) ?: continue
                    val existing = local[key]
                    if (existing != null) {
                        dao.updateSession(row.copy(id = existing.id, healthConnectAtMs = existing.healthConnectAtMs))
                    } else {
                        val id = dao.insertSession(row)
                        dao.insertSamples(readSamples(db, key, id))
                        received += id
                    }
                }
                dao.replaceTombstones(kept)

                val merged = (localProfileMap.keys + remoteProfiles.keys)
                    .filter { it !in profiles.deleteLocal && it !in profiles.deleteRemote }
                    .mapNotNull { address -> if (address in profiles.toLocal) remoteProfiles[address] else localProfileMap[address] }
                return Result(received.size, sessions.toRemote.size, received, merged)
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sessions (startedAtMs INTEGER PRIMARY KEY, endedAtMs INTEGER, " +
                "durationSeconds INTEGER, distanceMeters INTEGER, machineKcal INTEGER, manualKcal INTEGER, " +
                "estimatedKcal INTEGER, averageCadence INTEGER, averagePowerWatts INTEGER, averageSpeedKmh REAL, " +
                "averageResistance REAL, strideCount REAL, averageHeartRate INTEGER, maxHeartRate INTEGER, " +
                "machineName TEXT, machineType TEXT, updatedAtMs INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS samples (sessionStartedAtMs INTEGER, timestampMs INTEGER, " +
                "elapsedSeconds INTEGER, speedKmh REAL, cadence INTEGER, distanceMeters INTEGER, strideCount REAL, " +
                "powerWatts INTEGER, energyKcal INTEGER, resistance REAL, heartRate INTEGER)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS samples_session ON samples (sessionStartedAtMs)")
        db.execSQL("CREATE TABLE IF NOT EXISTS profiles (address TEXT PRIMARY KEY, json TEXT, updatedAtMs INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS tombstones (kind TEXT, key TEXT, atMs INTEGER, PRIMARY KEY (kind, key))")
    }

    private fun readStamps(db: SQLiteDatabase, sql: String): Map<String, Long> = db.rawQuery(sql, null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getLong(0).toString(), c.getLong(1)) }
    }

    private fun readProfiles(db: SQLiteDatabase): Map<String, ProfileRow> =
        db.rawQuery("SELECT address, json, updatedAtMs FROM profiles", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), ProfileRow(c.getString(0), c.getString(1), c.getLong(2))) }
        }

    private fun readTombstones(db: SQLiteDatabase): List<Triple<String, String, Long>> =
        db.rawQuery("SELECT kind, key, atMs FROM tombstones", null).use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1), c.getLong(2))) }
        }

    private fun readSession(db: SQLiteDatabase, key: String): SessionEntity? =
        db.rawQuery("SELECT * FROM sessions WHERE startedAtMs = ?", arrayOf(key)).use { c ->
            if (!c.moveToFirst()) return null
            SessionEntity(
                startedAtMs = c.long("startedAtMs")!!,
                endedAtMs = c.long("endedAtMs") ?: 0,
                durationSeconds = c.int("durationSeconds") ?: 0,
                distanceMeters = c.int("distanceMeters") ?: 0,
                machineKcal = c.int("machineKcal") ?: 0,
                manualKcal = c.int("manualKcal"),
                estimatedKcal = c.int("estimatedKcal"),
                averageCadence = c.int("averageCadence") ?: 0,
                averagePowerWatts = c.int("averagePowerWatts") ?: 0,
                averageSpeedKmh = c.double("averageSpeedKmh") ?: 0.0,
                averageResistance = c.double("averageResistance"),
                strideCount = c.double("strideCount") ?: 0.0,
                averageHeartRate = c.int("averageHeartRate"),
                maxHeartRate = c.int("maxHeartRate"),
                machineName = c.string("machineName"),
                machineType = c.string("machineType"),
                finished = true,
                updatedAtMs = c.long("updatedAtMs") ?: 0,
            )
        }

    private fun readSamples(db: SQLiteDatabase, key: String, sessionId: Long): List<SampleEntity> =
        db.rawQuery("SELECT * FROM samples WHERE sessionStartedAtMs = ? ORDER BY timestampMs", arrayOf(key)).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        SampleEntity(
                            sessionId = sessionId,
                            timestampMs = c.long("timestampMs") ?: 0,
                            elapsedSeconds = c.int("elapsedSeconds") ?: 0,
                            speedKmh = c.double("speedKmh") ?: 0.0,
                            cadence = c.int("cadence") ?: 0,
                            distanceMeters = c.int("distanceMeters") ?: 0,
                            strideCount = c.double("strideCount") ?: 0.0,
                            powerWatts = c.int("powerWatts") ?: 0,
                            energyKcal = c.int("energyKcal") ?: 0,
                            resistance = c.double("resistance"),
                            heartRate = c.int("heartRate"),
                        ),
                    )
                }
            }
        }

    private fun SessionEntity.toValues() = ContentValues().apply {
        put("startedAtMs", startedAtMs)
        put("endedAtMs", endedAtMs)
        put("durationSeconds", durationSeconds)
        put("distanceMeters", distanceMeters)
        put("machineKcal", machineKcal)
        put("manualKcal", manualKcal)
        put("estimatedKcal", estimatedKcal)
        put("averageCadence", averageCadence)
        put("averagePowerWatts", averagePowerWatts)
        put("averageSpeedKmh", averageSpeedKmh)
        put("averageResistance", averageResistance)
        put("strideCount", strideCount)
        put("averageHeartRate", averageHeartRate)
        put("maxHeartRate", maxHeartRate)
        put("machineName", machineName)
        put("machineType", machineType)
        put("updatedAtMs", updatedAtMs)
    }

    private fun SampleEntity.toValues(sessionStartedAtMs: Long) = ContentValues().apply {
        put("sessionStartedAtMs", sessionStartedAtMs)
        put("timestampMs", timestampMs)
        put("elapsedSeconds", elapsedSeconds)
        put("speedKmh", speedKmh)
        put("cadence", cadence)
        put("distanceMeters", distanceMeters)
        put("strideCount", strideCount)
        put("powerWatts", powerWatts)
        put("energyKcal", energyKcal)
        put("resistance", resistance)
        put("heartRate", heartRate)
    }

    private fun Cursor.index(name: String) = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }

    private fun Cursor.long(name: String): Long? = index(name)?.let { getLong(it) }

    private fun Cursor.int(name: String): Int? = index(name)?.let { getInt(it) }

    private fun Cursor.double(name: String): Double? = index(name)?.let { getDouble(it) }

    private fun Cursor.string(name: String): String? = index(name)?.let { getString(it) }

    companion object {
        const val FORMAT = "ftmslab-sync-v1"

        /** Cabecera de un fichero SQLite, para no sobrescribir por error un fichero de otro tipo. */
        fun isSqlite(file: File): Boolean = file.inputStream().use { input ->
            val header = ByteArray(16)
            input.read(header) == 16 && String(header, Charsets.US_ASCII) == "SQLite format 3\u0000"
        }
    }
}
