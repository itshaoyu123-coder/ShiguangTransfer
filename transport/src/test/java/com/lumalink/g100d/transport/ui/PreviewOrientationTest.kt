package com.lumalink.g100d.transport.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreviewOrientationTest {
    /** Label the corners of a stored 3x2 image A,B,C,D clockwise from top-left.
     * Verify the upright top-left and top-right labels after bounds normalization. */
    private fun topEdge(orientation: Int): List<Char> {
        val matrix = previewOrientationMatrix(orientation)!!
        val corners = listOf(Triple(0f, 0f, 'A'), Triple(3f, 0f, 'B'), Triple(3f, 2f, 'C'), Triple(0f, 2f, 'D'))
            .map { (x,y,label) -> Triple(matrix[0]*x + matrix[1]*y, matrix[3]*x + matrix[4]*y, label) }
        val minY = corners.minOf { it.second }
        return corners.filter { it.second == minY }.sortedBy { it.first }.map { it.third }
    }
    @Test fun rotatesPortraitAndUpsideDownImagesUpright() {
        assertThat(topEdge(3)).containsExactly('C','D').inOrder()
        assertThat(topEdge(6)).containsExactly('D','A').inOrder()
        assertThat(topEdge(8)).containsExactly('B','C').inOrder()
    }
    @Test fun handlesAllMirroredOrientations() {
        assertThat(topEdge(2)).containsExactly('B','A').inOrder()
        assertThat(topEdge(4)).containsExactly('D','C').inOrder()
        assertThat(topEdge(5)).containsExactly('A','D').inOrder()
        assertThat(topEdge(7)).containsExactly('C','B').inOrder()
    }
    @Test fun absentNormalOrInvalidOrientationDoesNotTransform() {
        for (orientation in listOf(0,1,-1,9)) assertThat(previewOrientationMatrix(orientation)).isNull()
    }
}
