package io.github.aescanero.fs2ftms.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
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

    @Query("SELECT * FROM sessions WHERE finished = 0")
    suspend fun unfinished(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: Long): SessionEntity?

    @Query("SELECT * FROM samples WHERE sessionId = :id ORDER BY timestampMs")
    suspend fun samples(id: Long): List<SampleEntity>

    @Query("UPDATE sessions SET manualKcal = :kcal WHERE id = :id")
    suspend fun setManualKcal(id: Long, kcal: Int?)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)
}

@Database(entities = [SessionEntity::class, SampleEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN estimatedKcal INTEGER")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "fs2ftms.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
