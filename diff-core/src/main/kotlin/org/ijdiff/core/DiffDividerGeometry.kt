// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.ijdiff.core

/**
 * Pure UI-toolkit-independent geometry calculations for the center Diff Splitter polygons
 * connecting left editor line blocks to right editor line blocks using cubic Bezier curves.
 * Ported from `com.intellij.diff.util.DiffDividerDrawUtil`.
 */
object DiffDividerGeometry {

    data class CubicCurveSegment(
        val startX: Float,
        val startY: Float,
        val ctrl1X: Float,
        val ctrl1Y: Float,
        val ctrl2X: Float,
        val ctrl2Y: Float,
        val endX: Float,
        val endY: Float
    )

    /**
     * Represents the closed polygon connecting `[startY1, endY1]` at `x = 0` to `[startY2, endY2]` at `x = width`.
     */
    data class DividerPolygon(
        val startY1: Float,
        val endY1: Float,
        val startY2: Float,
        val endY2: Float,
        val topCurve: CubicCurveSegment,
        val bottomCurve: CubicCurveSegment,
        val type: TextDiffType
    )

    /**
     * Computes the cubic bezier polygon for a single diff fragment across a divider of width [width].
     * Control points are placed at 35% and 65% of the divider width to match IntelliJ's smooth S-curve.
     */
    @JvmStatic
    fun createPolygon(
        width: Float,
        startY1: Float,
        endY1: Float,
        startY2: Float,
        endY2: Float,
        type: TextDiffType
    ): DividerPolygon {
        val cp1X = width * 0.35f
        val cp2X = width * 0.65f

        val topCurve = CubicCurveSegment(
            startX = 0f,
            startY = startY1,
            ctrl1X = cp1X,
            ctrl1Y = startY1,
            ctrl2X = cp2X,
            ctrl2Y = startY2,
            endX = width,
            endY = startY2
        )

        val bottomCurve = CubicCurveSegment(
            startX = width,
            startY = endY2,
            ctrl1X = cp2X,
            ctrl1Y = endY2,
            ctrl2X = cp1X,
            ctrl2Y = endY1,
            endX = 0f,
            endY = endY1
        )

        return DividerPolygon(
            startY1 = startY1,
            endY1 = endY1,
            startY2 = startY2,
            endY2 = endY2,
            topCurve = topCurve,
            bottomCurve = bottomCurve,
            type = type
        )
    }
}
