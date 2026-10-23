package com.kero.passingby.journey

import kotlin.math.ceil

enum class SessionPhase { DOCKED, RUNNING, PAUSED, ARRIVED, RESTING }

/** 화면과 경험 구현에서 함께 읽는 세션 상태. 위치·방향·날씨는 경험 구현이 소유한다. */
data class SessionState(
    val phase: SessionPhase = SessionPhase.DOCKED,
    val tripMinutes: Int,
    val restEndElapsedMillis: Long = 0L,
    val restDone: Boolean = false,
) {
    init { require(tripMinutes > 0) }
}

/** JourneyClock의 숫자 배열은 저장 어댑터에서 직렬화한다. 생성 시 현재 시계의 복사본을 받는다. */
class SessionSnapshot(val state: SessionState, val clock: DoubleArray)

/** 전이에 따른 일회성 결과. 저장·알림·기록과 경험별 위치 변경은 호출자가 처리한다. */
sealed interface SessionEvent {
    data class Departed(val from: SessionPhase) : SessionEvent
    data class Paused(val progressPercent: Int) : SessionEvent
    data object Resumed : SessionEvent
    data class Ended(val tripMinutes: Int, val progressPercent: Int) : SessionEvent
    data object RestStarted : SessionEvent
    data class GotOff(val from: SessionPhase) : SessionEvent
    data class Arrived(val late: Boolean) : SessionEvent
    data class RestFinished(val late: Boolean) : SessionEvent
}

/**
 * 단조 증가 시각만 사용하는 세션 상태 머신. Android·Compose·코스·저장소에 의존하지 않는다.
 *
 * 호출자는 같은 실행 흐름에서 명령과 [advance]를 직렬로 호출하고, 반환된 이벤트를 처리한다.
 * 시간이 흐르는 데 프레임 누적은 필요 없으며, [advance]가 기한을 확인해 전이를 한 번 발생시킨다.
 * 새 인스턴스의 선택 시간은 최신 설정에서, 진행 중인 세션 시간은 [restore]에서 받는다.
 */
class SessionCoordinator(initialMinutes: Int, private val now: () -> Long) {
    private val clock = JourneyClock(now)
    private var selectedMinutes = initialMinutes

    var state = SessionState(tripMinutes = initialMinutes)
        private set

    val moving: Boolean
        get() = state.phase == SessionPhase.RUNNING || state.phase == SessionPhase.PAUSED
    val durationSeconds: Double get() = state.tripMinutes * 60.0
    val elapsedSeconds: Double get() = clock.seconds()
    val rate: Double get() = clock.rate()
    val scale: Double get() = clock.scale
    val restRemainingSeconds: Int
        get() = if (state.phase == SessionPhase.RESTING) {
            ceil((state.restEndElapsedMillis - now()) / 1000.0).toInt().coerceAtLeast(0)
        } else 0

    fun selectMinutes(minutes: Int) {
        require(minutes > 0)
        selectedMinutes = minutes
        if (state.phase == SessionPhase.DOCKED) state = state.copy(tripMinutes = minutes)
    }

    fun depart(): SessionEvent.Departed? {
        if (moving) return null
        val from = state.phase
        clock.seek(0.0, 1.0)
        state = SessionState(SessionPhase.RUNNING, selectedMinutes)
        return SessionEvent.Departed(from)
    }

    fun pause(): SessionEvent.Paused? {
        if (state.phase != SessionPhase.RUNNING) return null
        clock.setRate(0.0, STOP_SECONDS)
        state = state.copy(phase = SessionPhase.PAUSED)
        return SessionEvent.Paused(progressPercent())
    }

    fun resume(): SessionEvent? {
        if (state.phase != SessionPhase.PAUSED) return null
        clock.setRate(1.0, STOP_SECONDS)
        state = state.copy(phase = SessionPhase.RUNNING)
        return SessionEvent.Resumed
    }

    fun end(): SessionEvent.Ended? {
        if (!moving) return null
        val event = SessionEvent.Ended(state.tripMinutes, progressPercent())
        reset()
        return event
    }

    fun rest(): SessionEvent? {
        if (state.phase != SessionPhase.ARRIVED) return null
        state = state.copy(
            phase = SessionPhase.RESTING,
            restEndElapsedMillis = now() + REST_SECONDS * 1000L,
        )
        return SessionEvent.RestStarted
    }

    fun getOff(): SessionEvent.GotOff? {
        if (state.phase != SessionPhase.ARRIVED && state.phase != SessionPhase.RESTING) return null
        val from = state.phase
        reset()
        return SessionEvent.GotOff(from)
    }

    /** 화면을 그리지 않아도 기한을 확인할 수 있다. 동일한 완료 이벤트를 다시 반환하지 않는다. */
    fun advance(): SessionEvent? {
        if (moving && elapsedSeconds >= durationSeconds) {
            val late = elapsedSeconds - durationSeconds > LATE_SECONDS
            clock.seek(durationSeconds, 0.0)
            state = state.copy(phase = SessionPhase.ARRIVED)
            return SessionEvent.Arrived(late)
        }
        if (state.phase == SessionPhase.RESTING && now() >= state.restEndElapsedMillis) {
            val late = now() - state.restEndElapsedMillis > LATE_SECONDS * 1000
            reset()
            state = state.copy(restDone = true)
            return SessionEvent.RestFinished(late)
        }
        return null
    }

    /** 새 경험·코스로 바꿀 때의 초기화. 방향과 풍경 이동은 경험 어댑터가 수행한다. */
    fun reset() {
        clock.seek(0.0, 0.0)
        state = SessionState(tripMinutes = selectedMinutes)
    }

    /** 개발용 탐색. 일시정지 상태라면 멈춘 채로 이동한다. */
    fun seek(seconds: Double) {
        require(seconds.isFinite())
        if (moving) clock.seek(seconds.coerceIn(0.0, durationSeconds))
    }

    fun skipRest() {
        if (state.phase == SessionPhase.RESTING) state = state.copy(restEndElapsedMillis = now())
    }

    fun setScale(scale: Double) {
        require(scale.isFinite() && scale > 0)
        clock.setScale(scale)
    }

    fun snapshot(): SessionSnapshot = SessionSnapshot(state, clock.save())

    fun restore(snapshot: SessionSnapshot) {
        val saved = snapshot.clock
        require(saved.size == 6 && saved.all { it.isFinite() })
        require(saved[2] in 0.0..1.0 && saved[3] in 0.0..1.0 && saved[4] >= 0 && saved[5] > 0)
        clock.restore(saved)
        state = snapshot.state
    }

    private fun progressPercent(): Int = (elapsedSeconds / durationSeconds * 100).toInt().coerceIn(0, 100)

    private companion object {
        const val STOP_SECONDS = 3.5
        const val LATE_SECONDS = 3.0
        const val REST_SECONDS = 5 * 60
    }
}
