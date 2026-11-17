package com.micmax.cibo

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Mostrata da Health Connect («Perché questa app legge i miei dati?»). */
class HealthRationaleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = TextView(this).apply {
            text = "Benessere e cibo legge da Health Connect passi, battito, sonno, peso e allenamenti condivisi dalla tua app di fitness " +
                "(Samsung Health, Fitbit, Garmin, Zepp, Mi Fitness, Withings… o l'app del tuo orologio).\n\n" +
                "Li usa solo per mostrarti passi, sonno e allenamenti della settimana.\n\n" +
                "I dati restano sul telefono: non vengono inviati a nessun server, non vengono scritti in Health Connect e puoi revocare " +
                "l'accesso quando vuoi dalle impostazioni di Health Connect."
            textSize = 17f; setTextColor(col(R.color.text)); setLineSpacing(0f, 1.15f)
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(col(R.color.bg)); addView(t) })
        title = "Dati di salute"
    }
}
