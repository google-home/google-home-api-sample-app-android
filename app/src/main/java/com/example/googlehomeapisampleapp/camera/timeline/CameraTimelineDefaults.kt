/* Copyright 2025 Google LLC */
package com.example.googlehomeapisampleapp.camera.timeline

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object CameraTimelineDefaults {

    /** Total width reserved for the playhead time label (e.g. "12:00:00 PM"). */
    val maxPlayheadWidth: Dp = 80.dp

    /**
     * Vertical offset from the top of the timeline canvas to the playhead line.
     * Kept small so it sits near the top of the timeline section (not full screen).
     */
    val playheadOffset: Dp = 80.dp

    /** Width of the video recording segment bar. */
    val videoSegmentWidth: Dp = 12.dp

    /** Width of the camera state (no-video reason) segment bar. */
    val cameraStateSegmentWidth: Dp = 12.dp

    /** Width of the events segment bar. */
    val eventSegmentWidth: Dp = 12.dp

    /** Horizontal gap between adjacent segment bars. */
    val segmentSpacing: Dp = 6.dp

    /**
     * Height of the vertical gradient overlay drawn around the playhead line.
     */
    val gradientHeight: Dp = 80.dp

    /** Length of the horizontal tick dash drawn at each hour marker. */
    val markerDashWidth: Dp = 8.dp
}