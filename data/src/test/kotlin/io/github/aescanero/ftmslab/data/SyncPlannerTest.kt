package io.github.aescanero.ftmslab.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPlannerTest {
    @Test
    fun copiaLoQueFaltaEnCadaLado() {
        val plan = SyncPlanner.plan(local = mapOf("a" to 1L), remote = mapOf("b" to 1L), tombstones = emptyMap())
        assertEquals(setOf("b"), plan.toLocal)
        assertEquals(setOf("a"), plan.toRemote)
    }

    @Test
    fun ganaLaVersionMasReciente() {
        val plan = SyncPlanner.plan(local = mapOf("a" to 5L, "b" to 1L), remote = mapOf("a" to 3L, "b" to 2L), tombstones = emptyMap())
        assertEquals(setOf("a"), plan.toRemote)
        assertEquals(setOf("b"), plan.toLocal)
    }

    @Test
    fun iguales_noSeTocan() {
        val plan = SyncPlanner.plan(local = mapOf("a" to 4L), remote = mapOf("a" to 4L), tombstones = emptyMap())
        assertEquals(emptySet<String>(), plan.toLocal + plan.toRemote)
    }

    @Test
    fun laBajaBorraEnAmbosLados() {
        val plan = SyncPlanner.plan(local = mapOf("a" to 4L), remote = mapOf("a" to 4L), tombstones = mapOf("a" to 9L))
        assertEquals(setOf("a"), plan.deleteLocal)
        assertEquals(setOf("a"), plan.deleteRemote)
        assertEquals(mapOf("a" to 9L), plan.tombstones)
    }

    @Test
    fun unaBajaAnteriorNoBorraUnElementoRecreado() {
        val plan = SyncPlanner.plan(local = mapOf("m" to 20L), remote = emptyMap(), tombstones = mapOf("m" to 10L))
        assertEquals(setOf("m"), plan.toRemote)
        assertEquals(emptySet<String>(), plan.deleteLocal)
        assertEquals(emptyMap<String, Long>(), plan.tombstones)
    }

    @Test
    fun bajaSinElementoSeConserva() {
        val plan = SyncPlanner.plan(local = emptyMap(), remote = emptyMap(), tombstones = mapOf("x" to 1L))
        assertEquals(mapOf("x" to 1L), plan.tombstones)
    }
}
