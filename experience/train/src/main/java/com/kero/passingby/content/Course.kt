package com.kero.passingby.content

import com.kero.passingby.experience.train.R
import android.content.res.Resources
import androidx.annotation.StringRes
import com.kero.passingby.scene.CityLook
import com.kero.passingby.scene.FieldStyle
import com.kero.passingby.scene.LandmarkKind
import com.kero.passingby.scene.Theme

/**
 * 역. [name]은 화면 문구에 쓰는 이름(앱 언어를 따르는 문자열 리소스), [local]은 역 이름판에 크게 쓰는 현지 표기, [roman]은 그 아래 작은 글자.
 * 이름판은 실제 역처럼 현지 표기를 쓰므로 앱 언어와 상관없이 같다.
 * [look]은 그 도시의 모습(건물 높이·양식, 언덕·강, 뒷산). 실제 사진을 보고 알아볼 수 있는 특징을 맞춘다.
 */
class Station(@StringRes val name: Int, val local: String, val roman: String, val look: CityLook = CityLook.DEFAULT)

/**
 * 코스. 실제 노선 하나를 따라가고, 세션 하나가 코스 전체이며 중간 역마다 잠깐 정차한다(명세 7.2).
 * 실제 상표명은 쓰지 않는다("고속열차" 같은 일반 명칭).
 *
 * 코스 정의는 나라별 파일(KoreaCourses 등)에 두고 [ALL]이 모은다. 새 코스는 맨 뒤 번호로 붙여
 * 이미 판 코스의 번호가 밀리지 않게 한다.
 *
 * @param id 저장·결제 기록에 쓰는 값이라 출시 뒤에는 바꾸지 않는다.
 * @param number 1부터 빈틈없이. 화면에 "1코스"처럼 쓴다.
 * @param region 코스 이름(나라와 노선). 문자열 리소스.
 * @param vehicle 문자열 리소스.
 * @param cruise 시골 구간 순항 속도(장면 단위/초).
 * @param productId Play 결제 상품 ID. 무료 코스는 null. 한 번 등록한 ID는 다시 쓸 수 없다.
 * @param theme 지형·식생·마을·이름판. 같은 나라의 노선끼리 나눠 쓰거나 지형만 바꿔 쓴다.
 */
class Course(
    val id: String,
    val number: Int,
    @StringRes val region: Int,
    @StringRes val vehicle: Int,
    val stations: List<Station>,
    val cruise: Double,
    val productId: String?,
    /** 이 코스 날씨에 눈이 올 수 있는지. 남쪽 코스는 눈이 오지 않는다. */
    val snowy: Boolean,
    val theme: Theme,
    val features: List<Feature> = emptyList(),
    val landmarks: List<LandmarkSpot> = emptyList(),
) {
    val free: Boolean get() = productId == null

    /** [direction]이 −1이면 역방향(부산 → 서울). */
    fun ordered(direction: Int): List<Station> = if (direction < 0) stations.asReversed() else stations

    fun title(res: Resources, direction: Int): String {
        val s = ordered(direction)
        return "${res.getString(s.first().name)} → ${res.getString(s.last().name)}"
    }

    /** 중간 정차역 문구. "대전 · 대구 정차" */
    fun stopsLine(res: Resources, direction: Int): String {
        val mid = ordered(direction).drop(1).dropLast(1)
        return if (mid.isEmpty()) {
            res.getString(R.string.nonstop)
        } else {
            res.getString(R.string.stops_line, mid.joinToString(" · ") { res.getString(it.name) })
        }
    }

    companion object {
        const val FAST = 300.0
        const val REGIONAL = 170.0

        /**
         * 모든 코스, 번호 순서. 나라별 목록을 모은다.
         * 나라별 파일이 이 클래스를 만들므로 처음 쓸 때 모아야 초기화 순서가 꼬이지 않는다.
         */
        val ALL: List<Course> by lazy {
            listOf(KoreaCourses.ALL, JapanCourses.ALL, ChinaCourses.ALL, PortugalCourses.ALL, FranceCourses.ALL)
                .flatten()
                .sortedBy { it.number }
                .also(::validate)
        }

        fun byId(id: String?): Course = ALL.firstOrNull { it.id == id } ?: ALL.first()

        /** 코스를 더하다 생기기 쉬운 실수를 앱이 뜨자마자 드러낸다. */
        private fun validate(list: List<Course>) {
            check(list.map { it.number } == (1..list.size).toList()) { "코스 번호는 1부터 빈틈없이: ${list.map { it.number }}" }
            check(list.distinctBy { it.id }.size == list.size) { "코스 id가 겹친다" }
            val products = list.mapNotNull { it.productId }
            check(products.distinct().size == products.size) { "결제 상품 id가 겹친다" }
            for (c in list) {
                val segs = c.stations.size - 1
                check(c.landmarks.all { it.pos in 0.0..segs.toDouble() }) { "${c.id}: 랜드마크 위치가 노선 밖" }
                check(c.features.all { it.seg in 0 until segs }) { "${c.id}: 구역 구간 번호가 범위 밖" }
            }
        }
    }
}

/**
 * 코스 안 위치마다 달라지는 구역. 정방향 기준으로 [seg]번 구간의 거리 비율 [from]~[to]에 둔다.
 * 역방향으로 달릴 때는 여정이 위치를 뒤집어 쓴다.
 */
class Feature(
    val kind: Kind,
    val seg: Int,
    val from: Float,
    val to: Float,
    val style: FieldStyle = FieldStyle.NONE,
) {
    enum class Kind { SEA, RIVER, FIELD, TUNNEL, BRIDGE }
}

/**
 * 랜드마크 위치. 정방향 기준 노선 위치 [pos](구간 단위, 2.97이면 2번 구간의 97%)에서 화면으로 [dx]단위 옆.
 * 구간 길이는 세션 길이를 따라 늘어나므로, 역에서 보여야 하는 것은 [atStation]으로 화면 거리만큼 띄워 둔다.
 * 그래야 25분이든 90분이든 역에 서면 같은 자리에 보인다. 역방향으로 달리면 좌우가 뒤집힌다.
 */
class LandmarkSpot private constructor(val kind: LandmarkKind, val pos: Double, val dx: Float) {
    companion object {
        /** [station]번 역(정방향 번호)에 서 있을 때 화면 가운데에서 [dx]단위 옆(음수면 왼쪽). */
        fun atStation(kind: LandmarkKind, station: Int, dx: Float) = LandmarkSpot(kind, station.toDouble(), dx)

        /** [seg]번 구간의 거리 비율 [at]. 역 사이 풍경(후지산, 풍력발전기)에 쓴다. */
        fun along(kind: LandmarkKind, seg: Int, at: Float) = LandmarkSpot(kind, seg + at.toDouble(), 0f)
    }
}
