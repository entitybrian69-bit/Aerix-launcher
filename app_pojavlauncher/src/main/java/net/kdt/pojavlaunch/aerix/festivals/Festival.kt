/*
 * Zalith Launcher 2
 * Ported to Aerix Launcher (PojavLauncher) from mirai-launcher-:
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/utils/festival/Festival.kt
 * The com.xhinliang:LunarCalendar dependency and java.time (API 26+) are replaced with
 * java.util.Calendar and android.icu.util.ChineseCalendar (API 24+), so the class runs on minSdk 23.
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.festivals

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import net.kdt.pojavlaunch.R
import java.util.Calendar
import java.util.Date

/** Gregorian month (1..12) and day, plus the lunar month/day when the platform can compute it. */
class FestivalDate(val month: Int, val day: Int, val lunarMonth: Int?, val lunarDay: Int?) {
    fun checkDate(month: Int, day: Int) = this.month == month && this.day == day
    fun checkDateRange(month: Int, days: IntRange) = this.month == month && this.day in days
    fun checkLunar(month: Int, day: Int) = lunarMonth == month && lunarDay == day

    companion object {
        fun of(date: Date = Date()): FestivalDate {
            val calendar = Calendar.getInstance().apply { time = date }
            var lunarMonth: Int? = null
            var lunarDay: Int? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val chinese = android.icu.util.ChineseCalendar(date)
                if (chinese.get(android.icu.util.ChineseCalendar.IS_LEAP_MONTH) == 0) {
                    lunarMonth = chinese.get(android.icu.util.Calendar.MONTH) + 1
                    lunarDay = chinese.get(android.icu.util.Calendar.DAY_OF_MONTH)
                }
            }
            return FestivalDate(
                month = calendar.get(Calendar.MONTH) + 1,
                day = calendar.get(Calendar.DAY_OF_MONTH),
                lunarMonth = lunarMonth,
                lunarDay = lunarDay
            )
        }
    }
}

enum class Festival(
    val isChinese: Boolean,
    val isInternational: Boolean,
    @field:StringRes
    val textRes: Int
) {
    /** New Year */
    NEW_YEAR(isChinese = true, isInternational = true, textRes = R.string.aerix_festivals_new_year) {
        override fun isToday(date: FestivalDate) = date.checkDate(1, 1)
    },

    /** Spring Festival */
    SPRING_FESTIVAL(isChinese = true, isInternational = false, textRes = R.string.aerix_festivals_spring) {
        override fun isToday(date: FestivalDate) = date.checkLunar(1, 1)
    },

    /** Qingming */
    QING_MING(isChinese = true, isInternational = false, textRes = R.string.aerix_festivals_qing_ming) {
        override fun isToday(date: FestivalDate) = date.checkDateRange(4, 4..6)
    },

    /** Dragon Boat Festival */
    DRAGON_BOAT(isChinese = true, isInternational = false, textRes = R.string.aerix_festivals_dragon_boat) {
        override fun isToday(date: FestivalDate) = date.checkLunar(5, 5)
    },

    /** Mid-Autumn Festival */
    MID_AUTUMN(isChinese = true, isInternational = false, textRes = R.string.aerix_festivals_mid_autumn) {
        override fun isToday(date: FestivalDate) = date.checkLunar(8, 15)
    },

    /** National Day */
    NATIONAL_DAY(isChinese = true, isInternational = false, textRes = R.string.aerix_festivals_national_day) {
        override fun isToday(date: FestivalDate) = date.checkDateRange(10, 1..7)
    },

    /** April Fools' Day */
    APRIL_FOOLS(isChinese = false, isInternational = true, textRes = R.string.aerix_festivals_april_fools) {
        override fun isToday(date: FestivalDate) = date.checkDate(4, 1)
    },

    /** Christmas */
    CHRISTMAS(isChinese = false, isInternational = true, textRes = R.string.aerix_festivals_christmas) {
        override fun isToday(date: FestivalDate) = date.checkDateRange(12, 24..25)
    },

    /** Halloween */
    HALLOWEEN(isChinese = false, isInternational = true, textRes = R.string.aerix_festivals_halloween) {
        override fun isToday(date: FestivalDate) = date.checkDate(10, 31)
    },

    /** Valentine's Day */
    VALENTINES(isChinese = false, isInternational = true, textRes = R.string.aerix_festivals_valentines) {
        override fun isToday(date: FestivalDate) = date.checkDate(2, 14)
    },

    /** Qixi */
    QIXI(isChinese = true, isInternational = false, textRes = R.string.aerix_festivals_qixi) {
        override fun isToday(date: FestivalDate) = date.checkLunar(7, 7)
    };

    abstract fun isToday(date: FestivalDate): Boolean
}

/** Same rule as mirai utils/isChinese: the Chinese festival set is used for zh locales. */
fun isChineseLocale(context: Context): Boolean {
    val locale = androidx.core.os.ConfigurationCompat.getLocales(context.resources.configuration).get(0)
        ?: java.util.Locale.getDefault()
    return locale.language.equals("zh", ignoreCase = true)
}

/**
 * Festivals active today.
 * @param containsChinese whether Chinese festivals are included
 */
fun getTodayFestivals(date: FestivalDate = FestivalDate.of(), containsChinese: Boolean): List<Festival> {
    return Festival.entries.filter { festival ->
        val type = if (containsChinese) festival.isChinese || festival.isInternational else festival.isInternational
        type && festival.isToday(date)
    }
}
