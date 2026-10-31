package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.ZoneId

class CaptureDateHeaderTest {
    private val zone = ZoneId.of("UTC")
    private val expected = LocalDateTime.of(2026, 10, 1, 12, 34, 56).atZone(zone).toEpochSecond()
    private fun tiff(order: ByteOrder = ByteOrder.LITTLE_ENDIAN, magic: Int = 42): ByteArray {
        val b = ByteBuffer.allocate(78).order(order)
        b.put(if (order == ByteOrder.LITTLE_ENDIAN) byteArrayOf(73,73) else byteArrayOf(77,77))
        b.putShort(magic.toShort()).putInt(8)
        b.putShort(1).putShort(0x8769.toShort()).putShort(4).putInt(1).putInt(26).putInt(0)
        b.putShort(2)
        b.putShort(0x9003.toShort()).putShort(2).putInt(20).putInt(56)
        b.putShort(0x9004.toShort()).putShort(2).putInt(20).putInt(56)
        b.putInt(0)
        b.put("2026:10:01 12:34:56\u0000".toByteArray())
        return b.array()
    }
    private fun jpeg(tiff: ByteArray): ByteArray {
        val segment = "Exif\u0000\u0000".toByteArray() + tiff
        val length = segment.size + 2
        return byteArrayOf(-1,-40,-1,-31,(length shr 8).toByte(),length.toByte()) + segment + byteArrayOf(-1,-38)
    }
    @Test fun readsJpegOriginalDateWithoutImagePixels() {
        assertThat(captureDateFromHeader(jpeg(tiff()), zone)).isEqualTo(expected)
    }
    @Test fun readsBigEndianExifAndRw2Header() {
        assertThat(captureDateFromHeader(jpeg(tiff(ByteOrder.BIG_ENDIAN)), zone)).isEqualTo(expected)
        assertThat(captureDateFromHeader(tiff(magic = 85), zone)).isEqualTo(expected)
    }
    @Test fun skipsOtherJpegSegments() {
        val bytes = jpeg(tiff())
        assertThat(captureDateFromHeader(bytes.take(2).toByteArray() + byteArrayOf(-1,-32,0,4,0,0) + bytes.drop(2).toByteArray(), zone)).isEqualTo(expected)
    }
    @Test fun rejectsTruncatedAndMaliciousOffsetsWithoutThrowing() {
        val bytes = jpeg(tiff())
        for (size in 0 until bytes.size - 4) assertThat(captureDateFromHeader(bytes.copyOf(size), zone)).isNull()
        val bad = tiff()
        ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(4, Int.MAX_VALUE)
        assertThat(captureDateFromHeader(bad, zone)).isNull()
        assertThat(captureDateFromHeader("2026:10:01 12:34:56".toByteArray(), zone)).isNull()
    }
    @Test fun originalDateWinsOverModifiedDate() {
        val bytes = tiff().copyOf(96)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(40, 0x132.toShort()).putInt(48, 76)
        "2026:10:30 12:34:56\u0000".toByteArray().copyInto(bytes, 76)
        assertThat(captureDateFromHeader(bytes, zone)).isEqualTo(expected)
    }
    @Test fun headerReadStopsEarlyAndNeverConsumesWholeOriginal() {
        val bytes = (jpeg(tiff()) + ByteArray(2 * 1024 * 1024)).inputStream()
        val read = readCaptureDatePrefix(bytes)
        assertThat(read.date).isNotNull()
        assertThat(read.bytesRead).isEqualTo(2048)
        val unknown = ByteArray(2 * 1024 * 1024).inputStream()
        assertThat(readCaptureDatePrefix(unknown).bytesRead).isEqualTo(128 * 1024)
        assertThat(unknown.available()).isEqualTo(2 * 1024 * 1024 - 128 * 1024)
    }
    @Test fun largeExifDoesNotRequireMakerNotesAndThumbnailToArrive() {
        for (order in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            val photo = jpeg(tiff(order).copyOf(52000))
            assertThat(captureDateFromHeader(photo.copyOf(2048), zone)).isEqualTo(expected)
            assertThat(readCaptureDatePrefix(photo.inputStream()).bytesRead).isEqualTo(2048)
        }
    }
    @Test fun partialExifWaitsForOriginalInsteadOfReturningEarlierModifiedDate() {
        val bytes = tiff().copyOf(8192)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(36, 5000).putShort(40, 0x132.toShort()).putInt(48, 76)
        "2026:10:01 12:34:56\u0000".toByteArray().copyInto(bytes, 5000)
        "2026:10:30 12:34:56\u0000".toByteArray().copyInto(bytes, 76)
        val photo = jpeg(bytes)
        assertThat(captureDateFromHeader(photo.copyOf(2048), zone)).isNull()
        assertThat(captureDateFromHeader(photo, zone)).isEqualTo(expected)
    }
}
