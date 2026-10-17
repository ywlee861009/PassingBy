package com.passingby.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.passingby.app.R

/**
 * Passing By 디자인 토큰(명세 4.4).
 * 카드 바탕은 고정색이 아니라 하늘 팔레트의 실내 벽색에서 매번 뽑는다([com.passingby.app.ui.journey.JourneyViewModel.paper]).
 * 본문은 Pretendard, 도착 문구 같은 목소리 글자는 고운바탕. 둘 다 SIL OFL 1.1이고 원문은 assets/licenses에 있다.
 * 고운바탕은 상용 한글 2,350자와 영문·기호만 남긴 서브셋이라 그 밖의 글자는 시스템 글꼴로 넘어간다.
 * 한자·일본어는 두 서체 모두에 없어 시스템 글꼴로 그린다.
 */
object Pb {
    /** 승차권 종이색. 밤에는 실내 벽색 쪽으로 어두워진다. */
    val Paper = Color(0xFFF3ECE2)
    val Ink = Color(0xFF2B211C)
    /** 종이 위 보조 글자. 가장 어두운 종이(밤, 약 #C0B8AF)에서도 대비 약 5:1. */
    val InkSoft = Color(0xFF4A3C33)
    val Line = Color(0xFFB9AC9E)
    /** 짙은 노을 주황. 흰 글자 대비 약 5:1. 버튼 바탕은 이 색만 쓴다. */
    val Accent = Color(0xFFB4532A)
    val OnAccent = Color.White
    /** 풍경 위 글자 바탕(명세 6장). */
    val Scrim = Color(0x80000000)
    val OnScrim = Color.White
    val OnScrimSoft = Color(0xFFE6DED3)
    /** 진행 바의 열차 표시. 흰 테두리와 함께 쓰며 글자 바탕으로는 쓰지 않는다. */
    val Marker = Color(0xFFE8936A)
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
)

val GowunBatang = FontFamily(Font(R.font.gowun_batang_regular, FontWeight.Normal))

object PbType {
    val kicker = TextStyle(fontFamily = Pretendard, fontSize = 13.sp, lineHeight = 18.sp)
    val title = TextStyle(fontFamily = Pretendard, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontFamily = Pretendard, fontSize = 14.sp, lineHeight = 20.sp)
    val label = TextStyle(fontFamily = Pretendard, fontSize = 15.sp, lineHeight = 20.sp)
    val button = TextStyle(fontFamily = Pretendard, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    /** 역 이름·도착 문구(부리 계열). */
    val voice = TextStyle(fontFamily = GowunBatang, fontSize = 24.sp, lineHeight = 32.sp)
    val timerLarge = TextStyle(fontFamily = Pretendard, fontSize = 56.sp, lineHeight = 62.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    val timerMid = TextStyle(fontFamily = Pretendard, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
}
