package com.example.label

import android.content.Context
import com.countryatv.core.LabelSpec

// The label stock this phone prints on. Kept per phone, since each phone pairs with its own printer.
class LabelSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("label_settings", Context.MODE_PRIVATE)

    fun load(): LabelSpec = LabelSpec(
        widthMm = prefs.getFloat(KEY_WIDTH, LabelSpec.DEFAULT.widthMm.toFloat()).toDouble(),
        heightMm = prefs.getFloat(KEY_HEIGHT, LabelSpec.DEFAULT.heightMm.toFloat()).toDouble(),
        dpi = prefs.getInt(KEY_DPI, LabelSpec.DEFAULT.dpi)
    )

    fun save(spec: LabelSpec) {
        prefs.edit()
            .putFloat(KEY_WIDTH, spec.widthMm.toFloat())
            .putFloat(KEY_HEIGHT, spec.heightMm.toFloat())
            .putInt(KEY_DPI, spec.dpi)
            .apply()
    }

    private companion object {
        const val KEY_WIDTH = "width_mm"
        const val KEY_HEIGHT = "height_mm"
        const val KEY_DPI = "dpi"
    }
}
