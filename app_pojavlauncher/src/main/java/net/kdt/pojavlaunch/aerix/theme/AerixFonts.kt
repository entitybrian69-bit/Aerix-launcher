package net.kdt.pojavlaunch.aerix.theme

import android.app.Activity
import android.content.Context
import androidx.annotation.FontRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import net.kdt.pojavlaunch.R

/** Bundled, offline font choices. IDs are stable because they are saved in preferences. */
data class AerixFontChoice(val id: String, val label: String, @FontRes val resource: Int = 0) {
    fun family(): FontFamily = if (resource == 0) FontFamily.SansSerif else FontFamily(Font(resource))
}

object AerixFonts {
    private const val PREFS = "aerix_font_style"
    private const val KEY = "selected"
    val choices = listOf(
        AerixFontChoice("default", "Default"),
        AerixFontChoice("minecraft", "Minecraft", R.font.minecraft_ten),
        AerixFontChoice("pressstart2p", "Pixel · Press Start 2P", R.font.font_pressstart2p),
        AerixFontChoice("silkscreen", "Pixel · Silkscreen", R.font.font_silkscreen),
        AerixFontChoice("vt323", "Pixel · VT323", R.font.font_vt323),
        AerixFontChoice("lato", "Lato", R.font.font_lato),
        AerixFontChoice("poppins", "Poppins", R.font.font_poppins),
        AerixFontChoice("firasans", "Fira Sans", R.font.font_firasans),
        AerixFontChoice("kanit", "Kanit", R.font.font_kanit),
        AerixFontChoice("oxygen", "Oxygen", R.font.font_oxygen),
        AerixFontChoice("ibmplexmono", "IBM Plex Mono", R.font.font_ibmplexmono),
        AerixFontChoice("varelaround", "Varela Round", R.font.font_varelaround),
        AerixFontChoice("titilliumweb", "Titillium Web", R.font.font_titilliumweb),
        AerixFontChoice("bebasneue", "Bebas Neue", R.font.font_bebasneue),
        AerixFontChoice("anton", "Anton", R.font.font_anton),
        AerixFontChoice("arvo", "Arvo", R.font.font_arvo),
        AerixFontChoice("comforter", "Comforter", R.font.font_comforter),
        AerixFontChoice("amaticsc", "Amatic SC", R.font.font_amaticsc),
        AerixFontChoice("abrilfatface", "Abril Fatface", R.font.font_abrilfatface),
        AerixFontChoice("lobster", "Lobster", R.font.font_lobster),
        AerixFontChoice("righteous", "Righteous", R.font.font_righteous),
        AerixFontChoice("alegreyasans", "Alegreya Sans", R.font.font_alegreyasans),
        AerixFontChoice("hind", "Hind", R.font.font_hind),
        AerixFontChoice("alfaslabone", "Alfa Slab One", R.font.font_alfaslabone),
        AerixFontChoice("acme", "Acme", R.font.font_acme),
        AerixFontChoice("architectsdaughter", "Architects Daughter", R.font.font_architectsdaughter),
        AerixFontChoice("barlow", "Barlow", R.font.font_barlow),
        AerixFontChoice("breeserif", "Bree Serif", R.font.font_breeserif),
        AerixFontChoice("creepster", "Creepster", R.font.font_creepster),
        AerixFontChoice("mukta", "Mukta", R.font.font_mukta),
        AerixFontChoice("patrickhand", "Patrick Hand", R.font.font_patrickhand),
        AerixFontChoice("chakrapetch", "Chakra Petch", R.font.font_chakrapetch),
        AerixFontChoice("asapcondensed", "Asap Condensed", R.font.font_asapcondensed),
        AerixFontChoice("inconsolata", "Inconsolata", R.font.font_inconsolata),
        AerixFontChoice("cabincondensed", "Cabin Condensed", R.font.font_cabincondensed)
    )
    private var initialized = false
    var selectedId by mutableStateOf("default")
        private set

    fun initialize(context: Context) {
        if (initialized) return
        val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "default")
        selectedId = choices.firstOrNull { it.id == saved }?.id ?: "default"
        initialized = true
    }

    fun select(context: Context, choice: AerixFontChoice) {
        require(choice in choices)
        initialize(context)
        selectedId = choice.id
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, choice.id).apply()
        (context as? Activity)?.let { applyNativeTheme(it) }
    }

    /** Apply the selected family when a legacy Android activity or dialog is inflated. */
    @JvmStatic
    fun applyNativeTheme(activity: Activity) {
        initialize(activity)
        val style = when (selectedId) {
            "default" -> R.style.AerixFont_default
            "minecraft" -> R.style.AerixFont_minecraft
            "pressstart2p" -> R.style.AerixFont_pressstart2p
            "silkscreen" -> R.style.AerixFont_silkscreen
            "vt323" -> R.style.AerixFont_vt323
            "lato" -> R.style.AerixFont_lato
            "poppins" -> R.style.AerixFont_poppins
            "firasans" -> R.style.AerixFont_firasans
            "kanit" -> R.style.AerixFont_kanit
            "oxygen" -> R.style.AerixFont_oxygen
            "ibmplexmono" -> R.style.AerixFont_ibmplexmono
            "varelaround" -> R.style.AerixFont_varelaround
            "titilliumweb" -> R.style.AerixFont_titilliumweb
            "bebasneue" -> R.style.AerixFont_bebasneue
            "anton" -> R.style.AerixFont_anton
            "arvo" -> R.style.AerixFont_arvo
            "comforter" -> R.style.AerixFont_comforter
            "amaticsc" -> R.style.AerixFont_amaticsc
            "abrilfatface" -> R.style.AerixFont_abrilfatface
            "lobster" -> R.style.AerixFont_lobster
            "righteous" -> R.style.AerixFont_righteous
            "alegreyasans" -> R.style.AerixFont_alegreyasans
            "hind" -> R.style.AerixFont_hind
            "alfaslabone" -> R.style.AerixFont_alfaslabone
            "acme" -> R.style.AerixFont_acme
            "architectsdaughter" -> R.style.AerixFont_architectsdaughter
            "barlow" -> R.style.AerixFont_barlow
            "breeserif" -> R.style.AerixFont_breeserif
            "creepster" -> R.style.AerixFont_creepster
            "mukta" -> R.style.AerixFont_mukta
            "patrickhand" -> R.style.AerixFont_patrickhand
            "chakrapetch" -> R.style.AerixFont_chakrapetch
            "asapcondensed" -> R.style.AerixFont_asapcondensed
            "inconsolata" -> R.style.AerixFont_inconsolata
            "cabincondensed" -> R.style.AerixFont_cabincondensed
            else -> R.style.AerixFont_default
        }
        activity.theme.applyStyle(style, true)
    }

    fun selected(): AerixFontChoice = choices.firstOrNull { it.id == selectedId } ?: choices.first()
}
