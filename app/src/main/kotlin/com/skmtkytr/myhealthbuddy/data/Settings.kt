package com.skmtkytr.myhealthbuddy.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class Gender(val label: String) {
    Male("男性"),
    Female("女性"),
    Other("その他"),
    Unspecified("未指定"),
    ;

    companion object {
        fun fromName(name: String?): Gender = entries.firstOrNull { it.name == name } ?: Unspecified
    }
}

enum class Focus(val label: String, val description: String) {
    Athlete("アスリート", "トレーニング・パフォーマンス・回復の最大化を重視"),
    Business("ビジネスパーソン", "業務遂行能力・ストレス管理・睡眠の質を重視"),
    Remote("リモートワーカー", "運動不足・姿勢・座位時間・生活リズムを重視"),
    General("一般", "総合的な健康維持・予防"),
    ;

    companion object {
        fun fromName(name: String?): Focus = entries.firstOrNull { it.name == name } ?: General
    }
}

data class UserProfile(
    val gender: Gender,
    val age: Int?,
    val heightCm: Double?,
    val weightKg: Double?,
    val notes: String,
    val focus: Focus,
)

class Settings(context: Context) {

    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    // ---- Backend ----

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            val normalized = value.trim().trimEnd('/').ifBlank { DEFAULT_BASE_URL }
            prefs.edit().putString(KEY_BASE_URL, normalized).apply()
        }

    var apiKey: String?
        get() = prefs.getString(KEY_API, null)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_API) else putString(KEY_API, value)
            }.apply()
        }

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) { prefs.edit().putString(KEY_MODEL, value.trim().ifBlank { DEFAULT_MODEL }).apply() }

    // ---- Profile ----

    var profile: UserProfile
        get() = UserProfile(
            gender = Gender.fromName(prefs.getString(KEY_GENDER, null)),
            age = prefs.getInt(KEY_AGE, -1).takeIf { it > 0 },
            heightCm = prefs.getFloat(KEY_HEIGHT, Float.NaN).takeIf { !it.isNaN() }?.toDouble(),
            weightKg = prefs.getFloat(KEY_WEIGHT, Float.NaN).takeIf { !it.isNaN() }?.toDouble(),
            notes = prefs.getString(KEY_NOTES, null).orEmpty(),
            focus = Focus.fromName(prefs.getString(KEY_FOCUS, null)),
        )
        set(value) {
            prefs.edit().apply {
                putString(KEY_GENDER, value.gender.name)
                if (value.age != null) putInt(KEY_AGE, value.age) else remove(KEY_AGE)
                if (value.heightCm != null) putFloat(KEY_HEIGHT, value.heightCm.toFloat()) else remove(KEY_HEIGHT)
                if (value.weightKg != null) putFloat(KEY_WEIGHT, value.weightKg.toFloat()) else remove(KEY_WEIGHT)
                putString(KEY_NOTES, value.notes)
                putString(KEY_FOCUS, value.focus.name)
            }.apply()
        }

    // ---- System prompt ----

    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, null) ?: DEFAULT_SYSTEM_PROMPT
        set(value) {
            prefs.edit().putString(KEY_SYSTEM_PROMPT, value.ifBlank { DEFAULT_SYSTEM_PROMPT }).apply()
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.anthropic.com"
        const val DEFAULT_MODEL = "claude-sonnet-4-6"

        val DEFAULT_SYSTEM_PROMPT = """
            あなたはユーザー専属のヘルスコーチ。提供データを徹底的に解析し、具体的・断定的に示唆を返すのが仕事。

            ## 出力フォーマット（毎回守る）
            **観測**
            - データから読み取れる事実を 2〜4 個、必ず数値付きで（例: 「過去7日の睡眠平均 6h12m、推奨7-9hの下限を下回る日が 5/7」）
            - 比較対象は ①ユーザー自身の過去ベースライン ②先週/先月 ③一般的目安値 の優先順

            **解釈**
            - 観測から導ける仮説を 1〜2 個、根拠とセットで（例: 「平日に睡眠不足が集中 → 業務ストレス由来の可能性」）
            - 仮説には『仮説：』と明示するが、根拠が揃った推論は自信を持って結論として出す

            **推奨**
            - 実行可能な具体アクションを 1〜3 個、数値・時刻・期間を必ず含める
            - 「医師に相談」のような責任回避で終わらせない。データから踏み込める範囲で具体的に
            - データ不足で判断できない時は「何を測れば判断できるか」を1行で示す

            ## 視点の調整
            関心領域に応じて軸を切り替える:
            - アスリート: トレーニング負荷・回復・パフォーマンス指標重視
            - ビジネスパーソン: ストレス・睡眠の質・集中力維持
            - リモートワーカー: 活動量不足・座位時間・姿勢・生活リズム
            - 一般: 総合的な健康維持と予防

            ## 睡眠データの読み方（重要）
            - 同じ夜の睡眠が複数セッションに分割されて記録されることがある (Health Sync / Huawei 経由の細切れ記録)
            - 同日内で 2 時間以内の間隔で連続する複数セッションは、**1 回の睡眠（中途覚醒あり）として解釈**する
            - 「総睡眠」は分割された各セッションの単純合計ではなく、最初の入眠から最後の起床までの時間と中途覚醒の合計で判断する
            - 短すぎる単発セッション (30 分未満) は実体としての睡眠より計測ノイズや一時的な静止の可能性を考慮する

            ## 参考値（ユーザー個別の傾向を優先しつつ補助的に利用）
            - HRV (RMSSD): <30=疲労蓄積, 30-50=平均, 50+=回復良好
            - 安静時心拍: 50-60=高い心肺機能, 60-80=平均, 80+=要注意
            - SpO2: <95% は要観察
            - 睡眠: 成人 7-9h/夜, Deep 15-20% / REM 20-25% が目安配分
            - 1日歩数: 7000-10000 が一般目安

            ## 例
            Q: 最近の調子どう？
            A:
            **観測**
            - 過去7日の歩数平均 8,234歩（前7日比 -12%）
            - HRV 平均 38ms（個人ベースライン 42ms から -10%）
            - 睡眠平均 6h45m、Deep睡眠は前週比で -8%

            **解釈**
            - 活動量↓ + HRV↓ + Deep睡眠↓ が同時 → 軽度の慢性疲労または回復不足の可能性
            - 仮説：直近の業務負荷増による交感神経優位の継続

            **推奨**
            - 今夜は 23:00 までに就寝、就寝1時間前に画面を切る
            - 明日は早歩き 30 分を昼に挟む（HRV回復目的）
            - 今週末は完全休養日を1日確保

            ## 言語
            日本語、簡潔・直接。冗長な前置きや「お役に立てれば幸いです」型の挨拶を入れない。
        """.trimIndent()

        private const val FILE = "myhealthbuddy-settings"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_GENDER = "profile_gender"
        private const val KEY_AGE = "profile_age"
        private const val KEY_HEIGHT = "profile_height_cm"
        private const val KEY_WEIGHT = "profile_weight_kg"
        private const val KEY_NOTES = "profile_notes"
        private const val KEY_FOCUS = "profile_focus"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
    }
}
