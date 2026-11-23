package io.github.aescanero.ftmslab.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Insert
    suspend fun insertSample(sample: SampleEntity)

    @Insert
    suspend fun insertSamples(samples: List<SampleEntity>)

    @Query("SELECT * FROM sessions WHERE finished = 1 ORDER BY startedAtMs DESC")
    fun sessions(): Flow<List<SessionEntity>>

    @Query("UPDATE sessions SET healthConnectAtMs = :atMs WHERE id = :id")
    suspend fun setHealthConnectAt(id: Long, atMs: Long?)

    @Query(
        "SELECT * FROM sessions WHERE finished = 1 AND healthConnectAtMs IS NULL " +
            "AND (machineName IS NULL OR machineName != :demoName) ORDER BY startedAtMs",
    )
    suspend fun pendingHealthConnect(demoName: String): List<SessionEntity>

    @Query("UPDATE sessions SET machineType = :type WHERE machineType IS NULL")
    suspend fun fillMissingMachineType(type: String)

    @Query("SELECT * FROM sessions WHERE finished = 1")
    suspend fun finishedSessions(): List<SessionEntity>

    @Update
    suspend fun updateSamples(samples: List<SampleEntity>)

    @Query("SELECT * FROM sessions WHERE finished = 0")
    suspend fun unfinished(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: Long): SessionEntity?

    @Query("SELECT * FROM samples WHERE sessionId = :id ORDER BY timestampMs")
    suspend fun samples(id: Long): List<SampleEntity>

    @Query("UPDATE sessions SET manualKcal = :kcal, updatedAtMs = :atMs WHERE id = :id")
    suspend fun setManualKcal(id: Long, kcal: Int?, atMs: Long)

    @Query("SELECT * FROM tombstones")
    suspend fun tombstones(): List<TombstoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: TombstoneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstones(tombstones: List<TombstoneEntity>)

    @Query("DELETE FROM tombstones")
    suspend fun deleteTombstones()

    @Transaction
    suspend fun replaceTombstones(tombstones: List<TombstoneEntity>) {
        deleteTombstones()
        insertTombstones(tombstones)
    }

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)
}

@Database(entities = [SessionEntity::class, SampleEntity::class, TombstoneEntity::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN estimatedKcal INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN machineType TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN healthConnectAtMs INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN updatedAtMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE sessions SET updatedAtMs = endedAtMs")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS tombstones (kind TEXT NOT NULL, key TEXT NOT NULL, " +
                        "atMs INTEGER NOT NULL, PRIMARY KEY(kind, key))",
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "ftmslab.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
