package com.kero.passingby.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 객차 안 환경음을 코드로 합성한다(음원 파일 없음).
 *
 * - 실내 울림: 낮게 거른 갈색 잡음 + 공조기 같은 낮은 웅웅거림. 서 있어도 조금 들린다.
 * - 바람: 높게 거른 흰 잡음. 속도의 제곱에 비례.
 * - 레일 이음매: 바퀴 두 쌍이 지나가는 둔한 "덜컹-덜컹". 간격은 속도에 반비례.
 *   폰 스피커는 500Hz 아래를 거의 못 내므로 150~800Hz 대역의 몸통 소리를 함께 낸다.
 * - 빗방울: 비 세기만큼 유리에 톡톡 떨어지는 짧은 소리.
 * - 터널: 울림이 커지고 바람이 먹먹해진다.
 * - 차임: 중간 역에 설 때 두 음 "띵-동"(실제 역 안내음이 아닌 일반 음).
 *
 * 폰 스피커로 들을 때 바람 1 : 빗방울 3 : 덜컹 6 정도가 되게 맞췄다(고속 열차 순항, 비 최대 기준).
 * 완행은 덜컹이 두 배쯤 크다([clack]).
 *
 * 다른 앱 음악을 멈추지 않도록 오디오 포커스를 요청하지 않는다(명세 5.6).
 * 소리 매개변수는 화면 프레임에서 정하고, 소리 스레드는 그것을 부드럽게 따라간다.
 */
class AmbientSound {
    /** 0~1: 순항 대비 속도. */
    @Volatile var speed = 0f
    @Volatile var rain = 0f
    /** 0~1: 터널 안. */
    @Volatile var tunnel = 0f
    /** 0~1: 전체 음량. */
    @Volatile var volume = .6f
    /** 레일 소리 세기(완행일수록 크다). */
    @Volatile var clack = .5f

    @Volatile private var playing = false
    @Volatile private var chimeRequest = false
    private var thread: Thread? = null

    /** 재생을 시작한다. 막 꺼지며 소리가 줄어드는 중이면 그 스레드가 다시 커진다. */
    fun start() {
        playing = true
        if (thread?.isAlive == true) return
        thread = Thread(::run, "ambient-sound").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    /** 소리를 천천히 줄이고 끝나면 스레드가 스스로 멈춘다. */
    fun stop() {
        playing = false
    }

    /** 중간 역 정차 차임을 한 번 울린다. */
    fun chime() {
        chimeRequest = true
    }

    private fun run() {
        val bufSize = max(AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), BLOCK * 4)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return
        val out = ShortArray(BLOCK)
        val rnd = Random(11)
        // 매개변수를 따라가는 값(지퍼 잡음을 막는다).
        var sp = 0f
        var rn = 0f
        var tn = 0f
        var vol = 0f
        // 필터 상태
        var brown = 0f
        var lowWind = 0f
        var lowRumble = 0f
        // 레일: 이동 거리 누적과 소리 꼬리
        var travel = 0.0
        var thump = 0f
        var secondIn = -1
        var lowThump = 0f
        var midThump = 0f
        // 빗방울
        var drop = 0f
        var dropTone = 0f
        // 웅웅거림·차임
        var humPhase = 0.0
        var chimeT = -1.0
        var fade = 0f
        track.play()
        while (playing || fade > .001f) {
            val target = if (playing) 1f else 0f
            for (i in 0 until BLOCK) {
                val k = 1f / (RATE * .35f)
                sp += (speed - sp) * k
                rn += (rain - rn) * k
                tn += (tunnel - tn) * (1f / (RATE * .25f))
                vol += (volume - vol) * k
                fade += (target - fade) * (1f / (RATE * .8f))

                val white = rnd.nextFloat() * 2 - 1
                // 실내 울림: 갈색 잡음을 한 번 더 낮게 거른다.
                brown = (brown + .02f * white) / 1.02f
                lowRumble += (brown - lowRumble) * .05f
                val rumble = lowRumble * (.9f + 2.6f * sp) * (1 + 1.8f * tn)
                // 바람: 흰 잡음에서 낮은 성분을 빼 높은 소리만 남긴다. 터널에서는 먹먹하게.
                lowWind += (white - lowWind) * (.08f + .25f * (1 - tn))
                val wind = (white - lowWind) * .023f * sp * sp * (1 - .6f * tn) + lowWind * .016f * sp * sp * tn
                // 레일 이음매: 일정 거리마다 둔한 소리 두 번(앞뒤 바퀴).
                travel += sp / RATE
                if (travel >= JOINT) {
                    travel -= JOINT
                    thump = 1f
                    secondIn = (RATE * (.11f / max(.25f, sp))).toInt()
                }
                if (secondIn > 0 && --secondIn == 0) thump = .8f
                thump *= .9992f
                val hit = white * thump
                // 150Hz 아래는 이어폰에서 들리는 둔한 울림, 150~800Hz는 폰 스피커에서도 들리는 몸통 소리.
                lowThump += (hit - lowThump) * .04f
                midThump += (hit - midThump) * .2f
                val rail = (lowThump * 1.6f + (midThump - lowThump) * 1.8f) * clack * min(1f, sp * 2.5f)
                // 빗방울: 드물게 짧고 높은 톡.
                if (rn > .01f && rnd.nextFloat() < rn * 45f / RATE) {
                    drop = .4f + rnd.nextFloat() * .6f
                    dropTone = rnd.nextFloat()
                }
                drop *= .985f - .01f * dropTone
                val rainTick = drop * white * .225f * rn + white * .0112f * rn
                // 공조기 같은 낮은 웅웅거림.
                humPhase += 2 * PI * 58 / RATE
                if (humPhase > 2 * PI) humPhase -= 2 * PI
                val hum = (sin(humPhase) * .012 + sin(humPhase * 2) * .006).toFloat()
                // 차임: 두 음, 짧게 사라짐.
                var chime = 0f
                if (chimeRequest) {
                    chimeRequest = false
                    chimeT = 0.0
                }
                if (chimeT >= 0) {
                    val t = chimeT
                    val f = if (t < .45) 784.0 else 622.0
                    val local = if (t < .45) t else t - .45
                    chime = (sin(2 * PI * f * t) * exp(-local * 3.2) * .08).toFloat()
                    chimeT += 1.0 / RATE
                    if (chimeT > 2.2) chimeT = -1.0
                }

                val s = (rumble * .9f + wind + rail + rainTick + hum + chime) * vol * fade
                out[i] = (s.coerceIn(-1f, 1f) * 32000).toInt().toShort()
            }
            if (track.write(out, 0, BLOCK) < 0) break
        }
        runCatching {
            track.stop()
            track.release()
        }
    }

    private companion object {
        const val RATE = 22050
        const val BLOCK = 1024
        /** 레일 이음매 간격(순항 1초 = 1). 순항에서 약 1.1초마다 덜컹. */
        const val JOINT = 1.1
    }
}
