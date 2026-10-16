package com.passingby.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Passing By 디자인 토큰(명세 4.4).
 * 카드 바탕은 고정색이 아니라 하늘 팔레트의 실내 벽색에서 매번 뽑는다([com.passingby.app.ui.journey.JourneyViewModel.paper]).
 * 서체 파일(Pretendard·마루 부리)은 아직 넣지 않아 시스템 산세리프·세리프를 쓴다.
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

object PbType {
    val kicker = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val title = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val label = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val button = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    /** 역 이름·도착 문구. 부리 계열 서체가 들어오면 여기만 바꾼다. */
    val voice = TextStyle(fontFamily = FontFamily.Serif, fontSize = 24.sp, lineHeight = 32.sp)
    val timerLarge = TextStyle(fontSize = 56.sp, lineHeight = 62.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    val timerMid = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
}
