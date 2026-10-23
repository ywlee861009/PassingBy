package com.kero.passingby.experience

import com.kero.passingby.session.JourneyStore
import org.junit.Assert.*
import org.junit.Test

class ExperienceRegistryTest {
    private fun provider(id: String, vararg routes: String) = object : ExperienceProvider {
        override val id = id
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

    @Test fun ambiguousOrEmptyRegistriesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ExperienceRegistry(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { ExperienceRegistry(listOf(provider("empty"))) }
        assertThrows(IllegalArgumentException::class.java) { ExperienceRegistry(listOf(provider("empty"), provider("valid", "a"))) }
        assertThrows(IllegalArgumentException::class.java) { ExperienceRegistry(listOf(provider("same", "a"), provider("same", "b"))) }
        assertThrows(IllegalArgumentException::class.java) { ExperienceRegistry(listOf(provider("first", "a"), provider("second", "a"))) }
    }
}
