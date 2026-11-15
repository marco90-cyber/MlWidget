package com.marco.mlwidget

// Generato insieme a res/layout/widget.xml
object SlotIds {
    val FONTS = listOf("light", "regular", "medium", "bold", "condensed", "mono", "dot")

    /** [posto][ombra * 7 + carattere] */
    val IDS = arrayOf(
        intArrayOf(R.id.s0_light, R.id.s0_regular, R.id.s0_medium, R.id.s0_bold, R.id.s0_condensed, R.id.s0_mono, R.id.s0_dot, R.id.s0_light_sh, R.id.s0_regular_sh, R.id.s0_medium_sh, R.id.s0_bold_sh, R.id.s0_condensed_sh, R.id.s0_mono_sh, R.id.s0_dot_sh),
        intArrayOf(R.id.s1_light, R.id.s1_regular, R.id.s1_medium, R.id.s1_bold, R.id.s1_condensed, R.id.s1_mono, R.id.s1_dot, R.id.s1_light_sh, R.id.s1_regular_sh, R.id.s1_medium_sh, R.id.s1_bold_sh, R.id.s1_condensed_sh, R.id.s1_mono_sh, R.id.s1_dot_sh),
        intArrayOf(R.id.s2_light, R.id.s2_regular, R.id.s2_medium, R.id.s2_bold, R.id.s2_condensed, R.id.s2_mono, R.id.s2_dot, R.id.s2_light_sh, R.id.s2_regular_sh, R.id.s2_medium_sh, R.id.s2_bold_sh, R.id.s2_condensed_sh, R.id.s2_mono_sh, R.id.s2_dot_sh),
        intArrayOf(R.id.s3_light, R.id.s3_regular, R.id.s3_medium, R.id.s3_bold, R.id.s3_condensed, R.id.s3_mono, R.id.s3_dot, R.id.s3_light_sh, R.id.s3_regular_sh, R.id.s3_medium_sh, R.id.s3_bold_sh, R.id.s3_condensed_sh, R.id.s3_mono_sh, R.id.s3_dot_sh)
    )

    val ALL: List<Int> = IDS.flatMap { it.toList() }

    val ZONES = intArrayOf(R.id.z0, R.id.z1, R.id.z2)

    fun id(slot: Int, font: String, shadow: Boolean): Int {
        val f = FONTS.indexOf(font).let { if (it < 0) 3 else it }
        return IDS[slot][(if (shadow) FONTS.size else 0) + f]
    }
}
