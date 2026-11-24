package com.nesperoudelmonico.projectvideo.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelsTest {

    @Test
    fun `tiempo de exposicion corto se expresa como fraccion`() {
        assertEquals("1/8000 s", Labels.exposureTime(125_000L))
        assertEquals("1/30 s", Labels.exposureTime(33_333_333L))
        assertEquals("1/2 s", Labels.exposureTime(500_000_000L))
    }

    @Test
    fun `tiempo de exposicion largo se expresa en segundos`() {
        assertEquals("2.00 s", Labels.exposureTime(2_000_000_000L))
        assertEquals("—", Labels.exposureTime(0L))
    }

    @Test
    fun `fps a partir de la duracion de frame`() {
        assertEquals(30.0, Labels.fpsFromFrameDuration(33_333_333L), 0.01)
        assertEquals(60.0, Labels.fpsFromFrameDuration(16_666_667L), 0.01)
        assertEquals(0.0, Labels.fpsFromFrameDuration(0L), 0.0)
        assertEquals("60 fps", Labels.fps(59.9999))
    }

    @Test
    fun `nombres de nivel de hardware y su orden`() {
        assertEquals("LIMITED", Labels.hardwareLevel(0))
        assertEquals("LEVEL_3", Labels.hardwareLevel(3))
        assertEquals("desconocido", Labels.hardwareLevel(null))
        assertTrue(Labels.hardwareLevelRank(2) < Labels.hardwareLevelRank(0)) // LEGACY < LIMITED
        assertTrue(Labels.hardwareLevelRank(0) < Labels.hardwareLevelRank(1)) // LIMITED < FULL
        assertTrue(Labels.hardwareLevelRank(1) < Labels.hardwareLevelRank(3)) // FULL < LEVEL_3
    }

    @Test
    fun `nombres de capacidades`() {
        assertEquals("MANUAL_SENSOR", Labels.capability(1))
        assertEquals("DYNAMIC_RANGE_TEN_BIT", Labels.capability(18))
        assertEquals("CAPABILITY_99", Labels.capability(99))
    }

    @Test
    fun `gigabytes decimales`() {
        assertEquals("1.5 GB", Labels.gigabytes(1_500_000_000L))
    }
}
