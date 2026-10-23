package com.kero.passingby.journey

import com.kero.passingby.session.JourneyStore
import com.kero.passingby.session.SessionPersistence
import java.util.UUID

/** Owns deadlines, experience transitions and atomic checkpoints independently of drawing. */
class SessionRuntime(
    private val store: SessionPersistence,
    program: JourneyProgram,
    now: () -> Long,
    private val scheduler: SessionScheduler,
) : AutoCloseable {
    var program: JourneyProgram = program
        private set
    private val coordinator = SessionCoordinator(store.minutes, now)
    private var sessionId = UUID.randomUUID().toString()
    private val stamped = mutableSetOf<Int>()
    private val listeners = linkedSetOf<(SessionEvent?) -> Unit>()
    private var cancel: (() -> Unit)? = null
    private var started = false
    private var closed = false
    var checkpointVersion = 0L
        private set

    val state get() = coordinator.state
    val elapsedSeconds get() = coordinator.elapsedSeconds
    val deadlineElapsedMillis get() = coordinator.deadlineElapsedMillis
    val durationSeconds get() = coordinator.durationSeconds
    val rate get() = coordinator.rate
    val scale get() = coordinator.scale
    val moving get() = coordinator.moving
    val restRemainingSeconds get() = coordinator.restRemainingSeconds

    init {
        val initial = coordinator.snapshot()
        store.loadActive()?.let { active ->
            runCatching {
                coordinator.restore(SessionSnapshot(SessionState(SessionPhase.valueOf(active.phase), active.tripMinutes,
                    active.restEnd, active.restDone), active.clock))
                program.restore(active)
                sessionId = active.sessionId.takeIf(String::isNotBlank) ?: "legacy:${program.courseId}:${active.clock.first()}"
            }.onFailure { coordinator.restore(initial) }
        }
        program.configure(state.tripMinutes)
    }

    fun observe(listener: (SessionEvent?) -> Unit): () -> Unit {
        check(!closed)
        listeners += listener
        return { listeners -= listener }
    }

    fun start() {
        check(!closed)
        if (started) return
        started = true
        refresh()
    }

    /** Also used by the platform alarm after the process was recreated. */
    fun refresh() {
        check(!closed)
        apply(coordinator.advance())
        checkpointStations()
        notifyChanged(null)
        schedule()
    }

    fun depart() = command { depart() }
    fun pause() = command { pause() }
    fun resume() = command { resume() }
    fun end() = command { end() }
    fun rest() = command { rest() }
    fun getOff() = command { getOff() }

    private fun command(action: SessionCoordinator.() -> SessionEvent?) {
        check(!closed)
        apply(coordinator.advance())
        apply(coordinator.action())
        notifyChanged(null)
        schedule()
    }

    private fun apply(event: SessionEvent?) {
        if (event == null) return
        if (event is SessionEvent.Departed) {
            sessionId = UUID.randomUUID().toString()
            stamped.clear()
        }
        program.onSessionEvent(event, state)
        checkpoint(event is SessionEvent.Arrived)
        notifyChanged(event)
    }

    fun selectMinutes(minutes: Int) {
        coordinator.selectMinutes(minutes)
        store.minutes = minutes
        if (state.phase == SessionPhase.DOCKED) program.configure(state.tripMinutes)
        checkpoint(arrived = false)
        notifyChanged(null)
    }

    fun selectCourse(id: String, force: Boolean = false, replacement: JourneyProgram? = null): Boolean {
        if (!force && state.phase != SessionPhase.DOCKED) return false
        coordinator.reset()
        if (replacement != null) program = replacement
        program.selectCourse(id, state.tripMinutes)
        stamped.clear()
        checkpoint(arrived = false)
        notifyChanged(null)
        schedule()
        return true
    }

    fun seek(seconds: Double) {
        coordinator.seek(seconds)
        refresh()
        checkpoint()
    }

    fun skipRest() { coordinator.skipRest(); refresh() }
    fun setScale(scale: Double) { coordinator.setScale(scale); checkpoint(arrived = false); notifyChanged(null); schedule() }

    /** Persists program settings changed by its owner without advancing any rendering state. */
    fun checkpoint() { checkpoint(arrived = false); notifyChanged(null) }

    private fun checkpoint(arrived: Boolean) {
        val stations = if (moving || state.phase == SessionPhase.ARRIVED) program.reachedStations(elapsedSeconds) else emptySet()
        store.saveSession(program.checkpoint(coordinator.snapshot(), sessionId), program.courseId, program.direction,
            stations, arrived)
        stamped += stations
        checkpointVersion++
    }

    private fun checkpointStations() {
        if (moving && !stamped.containsAll(program.reachedStations(elapsedSeconds))) checkpoint()
    }

    private fun notifyChanged(event: SessionEvent?) { listeners.toList().forEach { it(event) } }

    private fun schedule() {
        cancel?.invoke()
        cancel = null
        if (started && !closed && (state.phase == SessionPhase.RUNNING || state.phase == SessionPhase.RESTING ||
                state.phase == SessionPhase.PAUSED && rate > 0)) {
            cancel = scheduler.schedule(250L, ::refresh)
        }
    }

    override fun close() {
        closed = true
        cancel?.invoke()
        cancel = null
        listeners.clear()
    }
}
