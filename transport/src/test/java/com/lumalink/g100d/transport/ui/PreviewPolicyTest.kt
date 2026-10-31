package com.lumalink.g100d.transport.ui

import com.google.common.truth.Truth.assertThat
import com.lumalink.g100d.protocol.CameraFile
import com.lumalink.g100d.protocol.MediaKind
import org.junit.Test

class PreviewPolicyTest {
    @Test fun jpegEnhancesAutomaticallyWhileRawKeepsQuickPreview() {
        val jpg = CameraFile("/original.JPG", "a.JPG", 11000000, kind = MediaKind.JPEG)
        assertThat(enhancesPreview(jpg, false)).isTrue()
        assertThat(enhancesPreview(jpg.copy(kind = MediaKind.RAW), false)).isFalse()
        assertThat(qualityPreviewFile(jpg, false)).isEqualTo(jpg)
    }
    @Test fun advertisedLargePreviewIsPreferredUnlessOriginalExplicitlyRequested() {
        val jpg = CameraFile("/original.JPG", "a.JPG", 11000000, kind = MediaKind.JPEG, largePreviewPath = "/large.JPG")
        assertThat(qualityPreviewFile(jpg, false).path).isEqualTo("/large.JPG")
        assertThat(qualityPreviewFile(jpg, false).sizeBytes).isEqualTo(0)
        assertThat(qualityPreviewFile(jpg, true).path).isEqualTo("/original.JPG")
        assertThat(qualityPreviewFile(jpg.copy(kind = MediaKind.RAW), false).path).isEqualTo("/original.JPG")
    }
    @Test fun openingCachedQuickDoesNotFetchOriginal() {
        assertThat(previewPlan(false, true, false)).isEqualTo(PreviewPlan.CACHED_QUICK)
    }
    @Test fun openingUncachedPhotoFetchesOnlyQuickPreview() {
        assertThat(previewPlan(false, false, false)).isEqualTo(PreviewPlan.FETCH_QUICK)
    }
    @Test fun explicitOriginalRequestIgnoresQuickCache() {
        assertThat(previewPlan(true, true, false)).isEqualTo(PreviewPlan.FETCH_ORIGINAL)
    }
    @Test fun originalAlreadyInMemoryIsReused() {
        assertThat(previewPlan(true, true, true)).isEqualTo(PreviewPlan.CACHED_ORIGINAL)
    }
    @Test fun rawAndJpegReuseAdvertisedThumbnail() {
        val jpg = CameraFile("/DO001.JPG", "001.JPG", 11, thumbnailPath = "/DT001.JPG")
        val raw = jpg.copy(path = "/DO001.RW2", name = "001.RW2")
        assertThat(quickPreviewKey(raw)).isEqualTo(quickPreviewKey(jpg))
        assertThat(quickPreviewKey(raw.copy(thumbnailPath = null))).isEqualTo("/DT001.JPG")
    }
    @Test fun cacheEvictsLeastRecentlyUsedResource() {
        val cache = SharedPreviewCache<String>(2)
        cache.put("a", "first"); cache.put("b", "second")
        assertThat(cache.get("a")).isEqualTo("first")
        cache.put("c", "third")
        assertThat(cache.get("b")).isNull()
        assertThat(cache.get("a")).isEqualTo("first")
        cache.clear()
        assertThat(cache.get("a")).isNull()
    }
}
