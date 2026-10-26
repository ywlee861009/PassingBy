package com.kero.passingby.experience

import com.kero.passingby.experience.api.R

enum class JourneyMode(val id: String, val label: Int, val description: Int) {
    TRAIN("train", R.string.mode_train, R.string.mode_train_description),
    AIRPLANE("airplane", R.string.mode_airplane, R.string.mode_airplane_description),
    SHIP("ship", R.string.mode_ship, R.string.mode_ship_description),
    SPACESHIP("spaceship", R.string.mode_spaceship, R.string.mode_spaceship_description);

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id }
    }
}
