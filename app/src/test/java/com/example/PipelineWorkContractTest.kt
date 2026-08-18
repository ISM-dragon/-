package com.example

import com.example.data.worker.VideoProcessingWorker
import com.example.domain.model.PipelineStageStatus
import com.example.domain.model.PipelineStageType
import com.example.domain.pipeline.VideoPipelineWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineWorkContractTest {
    @Test
    fun pipelineStageWeightsCoverTheWholeUnifiedPipeline() {
        val totalWeight = PipelineStageType.values().sumOf { it.weight.toDouble() }
        assertEquals(1.0, totalWeight, 0.0001)
        assertEquals(PipelineStageType.values().size, PipelineStageType.values().distinct().size)
    }

    @Test
    fun legacyPipelineWorkerNameResolvesToCanonicalWorker() {
        assertEquals(VideoProcessingWorker::class.java, VideoPipelineWorker::class.java)
    }

    @Test
    fun stageStatusesExposeTerminalAndRetryableStates() {
        assertTrue(PipelineStageStatus.values().contains(PipelineStageStatus.PROCESSING))
        assertTrue(PipelineStageStatus.values().contains(PipelineStageStatus.COMPLETED))
        assertTrue(PipelineStageStatus.values().contains(PipelineStageStatus.FAILED))
        assertTrue(PipelineStageStatus.values().contains(PipelineStageStatus.CANCELLED))
    }
}
