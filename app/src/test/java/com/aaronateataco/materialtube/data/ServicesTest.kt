package com.aaronateataco.materialtube.data

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class ServicesTest {
    @Test fun sponsorBlockSegmentsDecode() {
        val body = """[{"category":"sponsor","actionType":"skip","segment":[12.5,40.0],"UUID":"abc","videoDuration":600,"votes":3}]"""
        val seg = json.decodeFromString<List<SbSegment>>(body).single()
        assertEquals("sponsor", seg.category)
        assertEquals(12.5, seg.startSec, 0.0)
        assertEquals(40.0, seg.endSec, 0.0)
        assertEquals("abc", seg.uuid)
    }

    @Test fun malformedSegmentsDoNotCrash() {
        val seg = json.decodeFromString<SbSegment>("""{"category":"intro","segment":[]}""")
        assertEquals(0.0, seg.startSec, 0.0)
        assertEquals(0.0, seg.endSec, 0.0)
    }

    @Test fun everySkipCategoryHasALabel() {
        Settings().sbSkip.forEach { assertEquals(it, SbCategories.of(it)?.key) }
    }

    @Test fun oldSettingsStillLoadAfterNewFieldsAreAdded() {
        val s = json.decodeFromString<Settings>("""{"theme":2,"someRemovedSetting":true}""")
        assertEquals(2, s.theme)
        assertEquals(Settings().qualityWifi, s.qualityWifi)
    }

    @Test fun learningTopicsHaveSearches() {
        LearningTopics.all.forEach {
            assert(it.queries.isNotEmpty()) { it.key }
            assertEquals(it, LearningTopics.of(it.key))
        }
        Settings().learningTopics.forEach { assert(LearningTopics.of(it) != null) { it } }
    }
}
