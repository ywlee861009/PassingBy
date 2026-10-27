package com.kero.passingby.experience

import com.kero.passingby.session.JourneyStore
import org.junit.Assert.*
import org.junit.Test

class ExperienceRegistryTest {
    private fun provider(id: String, vararg routes: String, aliases: Map<String, String> = emptyMap()) = object : ExperienceProvider {
        override val id = id
        override val courseAliases = aliases
        override val courses = routes.map { route -> object : JourneyCourse {
            override val id = route
            override val number = 1
            override val region = 0
            override val vehicle = 0
            override val stations = emptyList<JourneyStation>()
            override val productId: String? = null
        } }
        override fun create(store: JourneyStore): ExperienceProgram = error("No renderer is needed for catalog selection")
    }

    @Test fun catalogAndLookupSelectTheOwningProviderWithoutDependingOnTrain() {
        val first = provider("first", "a", "b")
        val second = provider("second", "c")
        val registry = ExperienceRegistry(listOf(first, second))
        assertEquals(listOf("a", "b", "c"), registry.courses.map { it.id })
        assertSame(second, registry.providerFor("c"))
        assertSame(first, registry.providerFor("a"))
        assertSame(registry.courses[2], registry.courseById("c"))
    }

    @Test fun unknownAndMissingSavedRoutesFallBackToTheFirstRegisteredExperience() {
        val first = provider("first", "a")
        val registry = ExperienceRegistry(listOf(first, provider("second", "b")))
        assertSame(first, registry.providerFor(null))
        assertSame(first, registry.providerFor("removed"))
        assertEquals("a", registry.courseById("removed").id)
    }

    @Test fun removedCourseAliasesRestoreTheOwningModeAndResolvePendingCompletions() {
        val first = provider("first", "a")
        val space = provider("space", "escape", aliases = mapOf("old-moon" to "escape", "old-mars" to "escape"))
        val registry = ExperienceRegistry(listOf(first, space))
        assertEquals(listOf("a", "escape"), registry.courses.map { it.id })
        assertEquals("escape", registry.canonicalCourseId("old-moon"))
        assertSame(space, registry.providerFor("old-mars"))
        assertEquals("escape", registry.courseById("old-mars").id)
        assertThrows(IllegalArgumentException::class.java) {
            ExperienceRegistry(listOf(first, provider("invalid", "b", aliases = mapOf("old" to "a"))))
        }
    }

    @Test fun ambiguousOrEmptyRegistriesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ExperienceRegistry(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { ExperienceRegistry(listOf(provider("empty"))) }
        assertThrows(IllegalArgumentException::class.java) { ExperienceRegistry(listOf(provider("empty"), provider("valid", "a"))) }
        assertThrows(IllegalArgumentException::class.java) { ExperienceRegistry(listOf(provider("same", "a"), provider("same", "b"))) }
        assertThrows(IllegalArgumentException::class.java) { ExperienceRegistry(listOf(provider("first", "a"), provider("second", "a"))) }
    }
}
