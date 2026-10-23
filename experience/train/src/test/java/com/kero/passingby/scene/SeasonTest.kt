package com.kero.passingby.scene

import com.kero.passingby.experience.Season
import com.kero.passingby.experience.SeasonChoice
import com.kero.passingby.experience.TimeOfDay

import org.junit.Assert.*
import org.junit.Test

class SeasonTest {
    @Test fun stableIdsAndOldDefaultFallBackToSummer() {
        assertEquals(Season.SUMMER, Season.fromId(null))
        assertEquals(Season.WINTER, Season.fromId("winter"))
        assertEquals(Season.AUTUMN, Season.fromId("autumn"))
        assertEquals(Season.SUMMER, Season.fromId("default"))
        assertEquals(Season.SUMMER, Season.fromId("unknown"))
        assertEquals(4, Season.entries.size)
        assertEquals("summer", Season.SUMMER.id)
    }

    @Test fun togglesAlwaysStartFromOriginalAndLeaveSkyBuildingsAndEvergreensAlone() {
        val src = Palette.of(TimeOfDay.DAY)
        val original = src.c.copyOf()
        val out = Palette()
        seasonInto(out, src, Season.AUTUMN)
        val autumn = out.c.copyOf()
        repeat(100) {
            seasonInto(out, src, Season.SUMMER)
            assertArrayEquals(original, out.c)
            seasonInto(out, src, Season.AUTUMN)
            assertArrayEquals(autumn, out.c)
        }
        assertArrayEquals(original, src.c)
        for (key in intArrayOf(Palette.TOP, Palette.BOT, Palette.HAZE, Palette.SUN, Palette.TREE, Palette.POLE, Palette.PANEL)) {
            assertEquals(original[key], out.c[key])
        }
        val tones = FoliageTones()
        tones.update(out.c[Palette.TREE], 0f, 1f, out, Season.AUTUMN)
        val pine = tones.forObject(SpriteKind.PINE, 0).copyOf()
        assertArrayEquals(pine, tones.forObject(SpriteKind.BAMBOO, 2))
        assertArrayEquals(pine, tones.forObject(SpriteKind.CYPRESS, 1))
        assertArrayEquals(pine, tones.forObject(null, 0))
        assertFalse(pine.contentEquals(tones.forObject(SpriteKind.ROUND, 0)))
    }

    @Test fun snowCoversAutumnHighlightsAndNightFoliageStaysDark() {
        fun palette(time: TimeOfDay) = Palette().also { seasonInto(it, Palette.of(time), Season.AUTUMN) }
        val day = palette(TimeOfDay.DAY)
        val night = palette(TimeOfDay.NIGHT)
        fun brightness(color: Int) = ((color shr 16) and 255) + ((color shr 8) and 255) + (color and 255)
        assertTrue(brightness(night.c[Palette.FOLIAGE_A]) < brightness(day.c[Palette.FOLIAGE_A]) / 2)
        val tones = FoliageTones()
        tones.update(day.c[Palette.TREE], 0f, 1f, day, Season.AUTUMN)
        val bare = tones.forObject(SpriteKind.ROUND, 0).copyOf()
        snowInto(day, 0f, 1f)
        tones.update(day.c[Palette.TREE], 0f, 1f, day, Season.AUTUMN)
        val covered = tones.forObject(SpriteKind.ROUND, 0)
        assertTrue(brightness(covered[TONE_LIGHT]) > brightness(bare[TONE_LIGHT]))
        assertEquals(mix(bare[TONE_LIGHT], day.c[Palette.SNOW_WHITE], .75f), covered[TONE_LIGHT])
    }


    @Test fun allSeasonsKeepEvergreensAndSkyButHaveDifferentDeciduousTones() {
        val src = Palette.of(TimeOfDay.DAY)
        val colors = mutableSetOf<Int>()
        for (season in Season.entries) {
            val pal = Palette().also { seasonInto(it, src, season) }
            assertEquals(src.c[Palette.TOP], pal.c[Palette.TOP])
            val tones = FoliageTones()
            tones.update(pal.c[Palette.TREE], 0f, 1f, pal, season)
            assertEquals(src.c[Palette.TREE], tones.forObject(SpriteKind.PINE, 0)[TONE_BASE])
            colors += tones.forObject(SpriteKind.ROUND, 0)[TONE_BASE]
        }
        assertEquals(4, colors.size)
    }

    @Test fun onlyColdWinterHasSeasonalSnowAndOldTimesMigrateToDayOrNight() {
        for (season in Season.entries) {
            assertEquals(0f, season.baseSnow(false), 0f)
            if (season != Season.WINTER) assertEquals(0f, season.baseSnow(true), 0f)
        }
        assertTrue(Season.WINTER.baseSnow(true) > 0f)
        assertEquals(TimeOfDay.DAY, TimeOfDay.fromSetting("DAWN"))
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.fromSetting("DUSK"))
        assertNull(TimeOfDay.fromSetting(null))
        assertNull(TimeOfDay.fromSetting("unknown"))
    }
    @Test fun randomChoiceIsStableForAJourneyAndCanPickEverySeason() {
        val choices = (-100..100).map { SeasonChoice.RANDOM.resolve(it) }.toSet()
        assertEquals(Season.entries.toSet(), choices)
        for (seed in intArrayOf(Int.MIN_VALUE, -77, 0, 1234, Int.MAX_VALUE)) {
            assertEquals(SeasonChoice.RANDOM.resolve(seed), SeasonChoice.fromId("random").resolve(seed))
            for (season in Season.entries) {
                assertEquals(season, SeasonChoice.fromSeason(season).resolve(seed))
            }
        }
    }

    @Test fun randomPreferenceKeepsLegacySeasonIdsCompatible() {
        assertEquals(SeasonChoice.RANDOM, SeasonChoice.fromId("random"))
        assertEquals(SeasonChoice.SUMMER, SeasonChoice.fromId("default"))
        assertEquals(SeasonChoice.SUMMER, SeasonChoice.fromId(null))
        assertEquals(SeasonChoice.SUMMER, SeasonChoice.fromId("unknown"))
        assertEquals(5, SeasonChoice.entries.size)
        for (season in Season.entries) assertEquals(season, SeasonChoice.fromId(season.id).season)
    }

}
