package id.ac.tazkia.nightguard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Warna & komponen UI bersama (dibuat lewat kode, tanpa library tambahan). */
object Ui {
    // Warna diambil dari logo STMIK Tazkia: biru #194189 dan oranye #EE7B1D
    const val BG = 0xFF0A1A3A.toInt()
    const val BG2 = 0xFF194189.toInt()
    const val BG3 = 0xFF050C1F.toInt()
    const val CARD = 0xFF13285A.toInt()
    const val ACCENT = 0xFFEE7B1D.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val MUTED = 0xFF9DB8AD.toInt()
    const val RED = 0xFFFF6B6B.toInt()
    const val GREEN = 0xFF4ADE80.toInt()

    fun px(c: Context, dp: Int) = (dp * c.resources.displayMetrics.density).toInt()

    fun rounded(c: Context, fill: Int, stroke: Int? = null, radiusDp: Int = 14) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = px(c, radiusDp).toFloat()
        if (stroke != null) setStroke(px(c, 1), stroke)
    }

    fun text(c: Context, s: String, sp: Float, color: Int = TEXT, bold: Boolean = false, center: Boolean = false) =
        TextView(c).apply {
            text = s
            textSize = sp
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            if (center) gravity = Gravity.CENTER
        }

    fun pinField(c: Context, hint: String) = EditText(c).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        gravity = Gravity.CENTER
        textSize = 20f
        setTextColor(TEXT)
        setHintTextColor(MUTED)
        background = rounded(c, 0x22FFFFFF, MUTED)
        setPadding(px(c, 14), px(c, 12), px(c, 14), px(c, 12))
    }

    fun field(c: Context, hint: String, value: String, numeric: Boolean = false) = EditText(c).apply {
        this.hint = hint
        setText(value)
        inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        textSize = 16f
        setTextColor(TEXT)
        setHintTextColor(MUTED)
        background = rounded(c, 0x22FFFFFF, MUTED)
        setPadding(px(c, 14), px(c, 10), px(c, 14), px(c, 10))
    }

    fun button(c: Context, label: String, primary: Boolean = true, danger: Boolean = false, onClick: () -> Unit) =
        Button(c).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            when {
                danger -> { setTextColor(RED); background = rounded(c, 0x00000000, RED) }
                primary -> { setTextColor(TEXT); background = rounded(c, ACCENT) }
                else -> { setTextColor(TEXT); background = rounded(c, 0x22FFFFFF, MUTED) }
            }
            setPadding(px(c, 16), px(c, 12), px(c, 16), px(c, 12))
            setOnClickListener { onClick() }
        }

    /**
     * Logo STMIK Tazkia: lingkaran putih (agar bagian biru tetap kontras di latar gelap)
     * dengan bayangan lembut dan cincin halo tipis bernuansa oranye di sekelilingnya.
     */
    fun logo(c: Context, sizeDp: Int): android.view.View {
        val outer = px(c, sizeDp)
        val inner = outer * 76 / 100
        val halo = android.widget.FrameLayout(c).apply {
            layoutParams = LinearLayout.LayoutParams(outer, outer).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x14FFFFFF)
                setStroke(px(c, 2), 0x55EE7B1D)
            }
            clipChildren = false
            clipToPadding = false
        }
        val img = android.widget.ImageView(c).apply {
            setImageResource(R.drawable.logo_tazkia)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFFFFFF.toInt())
            }
            val pad = inner * 20 / 100
            setPadding(pad, pad, pad, pad)
            elevation = px(c, 10).toFloat()
        }
        halo.addView(img, android.widget.FrameLayout.LayoutParams(inner, inner, android.view.Gravity.CENTER))
        return halo
    }

    fun topMargin(c: Context, dp: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = px(c, dp) }
}
