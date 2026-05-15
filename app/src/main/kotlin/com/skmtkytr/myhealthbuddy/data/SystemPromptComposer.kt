package com.skmtkytr.myhealthbuddy.data

import com.skmtkytr.myhealthbuddy.data.db.VitalType
import java.time.ZoneId

object SystemPromptComposer {

    fun compose(
        basePrompt: String,
        profile: UserProfile,
        summary: DbSummary,
        zone: ZoneId = ZoneId.systemDefault(),
        recentDays: Int = 14,
        maxSleepNights: Int = Int.MAX_VALUE,
        maxExercises: Int = Int.MAX_VALUE,
    ): String {
        val sb = StringBuilder()
        sb.appendLine(basePrompt.trim())
        sb.appendLine()
        sb.appendLine(profileSection(profile, summary))
        sb.appendLine()
        sb.append(
            HealthContextBuilder.build(
                summary = summary,
                zone = zone,
                recentDays = recentDays,
                maxSleepNights = maxSleepNights,
                maxExercises = maxExercises,
            ),
        )
        return sb.toString()
    }

    private fun profileSection(profile: UserProfile, summary: DbSummary): String {
        val sb = StringBuilder()
        sb.appendLine("# ユーザープロフィール")
        sb.appendLine("- 性別: ${profile.gender.label}")
        sb.appendLine("- 年齢: ${profile.age?.let { "$it 歳" } ?: "未設定"}")
        sb.appendLine("- 身長: ${profile.heightCm?.let { "%.1f cm".format(it) } ?: "未設定"}")

        val hcWeight = summary.latestVital(VitalType.Weight)?.value
        val weightLine = when {
            profile.weightKg != null && hcWeight != null ->
                "${"%.1f".format(profile.weightKg)} kg (申告)  / ${"%.1f".format(hcWeight)} kg (Health Connect 最新)"
            profile.weightKg != null -> "${"%.1f".format(profile.weightKg)} kg (申告)"
            hcWeight != null -> "${"%.1f".format(hcWeight)} kg (Health Connect 最新)"
            else -> "未設定"
        }
        sb.appendLine("- 体重: $weightLine")

        if (profile.heightCm != null) {
            val weight = profile.weightKg ?: hcWeight
            if (weight != null && profile.heightCm > 0) {
                val h = profile.heightCm / 100.0
                val bmi = weight / (h * h)
                sb.appendLine("- BMI: %.1f".format(bmi))
            }
        }

        sb.appendLine("- 関心領域: ${profile.focus.label} — ${profile.focus.description}")

        if (profile.notes.isNotBlank()) {
            sb.appendLine("- メモ: ${profile.notes}")
        }
        return sb.toString()
    }
}
