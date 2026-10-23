package com.kero.passingby.experience

import androidx.annotation.StringRes

/** A localized location cue supplied by an experience, without its route or rendering internals. */
data class RouteCaption(@param:StringRes val name: Int, val kind: Kind) {
    enum class Kind { DEPARTING, PASSING, APPROACHING, STOPPED }
}
