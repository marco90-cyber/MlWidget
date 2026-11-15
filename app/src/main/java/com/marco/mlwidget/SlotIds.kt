package com.marco.mlwidget

// Generato insieme a res/layout/widget.xml
object SlotIds {
    val FONTS = listOf("light", "regular", "medium", "bold", "condensed")

    /** [posto][ombra * 5 + carattere] */
    val IDS = arrayOf(
        intArrayOf(R.id.s0_light, R.id.s0_regular, R.id.s0_medium, R.id.s0_bold, R.id.s0_condensed, R.id.s0_light_sh, R.id.s0_regular_sh, R.id.s0_medium_sh, R.id.s0_bold_sh, R.id.s0_condensed_sh),
        intArrayOf(R.id.s1_light, R.id.s1_regular, R.id.s1_medium, R.id.s1_bold, R.id.s1_condensed, R.id.s1_light_sh, R.id.s1_regular_sh, R.id.s1_medium_sh, R.id.s1_bold_sh, R.id.s1_condensed_sh),
        intArrayOf(R.id.s2_light, R.id.s2_regular, R.id.s2_medium, R.id.s2_bold, R.id.s2_condensed, R.id.s2_light_sh, R.id.s2_regular_sh, R.id.s2_medium_sh, R.id.s2_bold_sh, R.id.s2_condensed_sh),
        intArrayOf(R.id.s3_light, R.id.s3_regular, R.id.s3_medium, R.id.s3_bold, R.id.s3_condensed, R.id.s3_light_sh, R.id.s3_regular_sh, R.id.s3_medium_sh, R.id.s3_bold_sh, R.id.s3_condensed_sh)
    )

    val ALL: List<Int> = IDS.flatMap { it.toList() }

    fun id(slot: Int, font: String, shadow: Boolean): Int {
        val f = FONTS.indexOf(font).let { if (it < 0) 3 else it }
        return IDS[slot][(if (shadow) 5 else 0) + f]
    }
}
