package com.kero.passingby.scene

import org.junit.Assert.*
import org.junit.Test

class SeasonTest {
    @Test fun stableIdsAndUnknownSettingsFallBackToOriginal() {
        assertEquals(Season.DEFAULT, Season.fromId(null))
        assertEquals(Season.DEFAULT, Season.fromId("winter"))
        assertEquals(Season.AUTUMN, Season.fromId("autumn"))
        assertEquals("default", Season.DEFAULT.id)
    }

    @Test fun togglesAlwaysStartFromOriginalAndLeaveSkyBuildingsAndEvergreensAlone() {
        val src = Palette.of(TimeOfDay.DAY)
        val original = src.c.copyOf()
        val out = Palette()
        seasonInto(out, src, Season.AUTUMN)
        val autumn = out.c.copyOf()
        repeat(100) {
            seasonInto(out, src, Season.DEFAULT)
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
        assertTrue(brightness(night.c[Palette.AUTUMN_RED]) < brightness(day.c[Palette.AUTUMN_RED]) / 2)
        val tones = FoliageTones()
        tones.update(day.c[Palette.TREE], 0f, 1f, day, Season.AUTUMN)
        val bare = tones.forObject(SpriteKind.ROUND, 0).copyOf()
        snowInto(day, 0f, 1f)
        tones.update(day.c[Palette.TREE], 0f, 1f, day, Season.AUTUMN)
        val covered = tones.forObject(SpriteKind.ROUND, 0)
        assertTrue(brightness(covered[TONE_LIGHT]) > brightness(bare[TONE_LIGHT]))
        assertEquals(mix(bare[TONE_LIGHT], day.c[Palette.SNOW_WHITE], .75f), covered[TONE_LIGHT])
    }


}
