package com.micmax.cibo

/**
 * La scheda completa di un esercizio: come si fa passo per passo, errori, respirazione, il consiglio detto a voce,
 * i muscoli, la variante più facile e più difficile, quando fare attenzione, lo spazio che serve, le parti del corpo
 * che carica (per chi deve evitarle) e la foto libera (free-exercise-db, pubblico dominio).
 * I testi sono scritti per questa app.
 */
data class MoveInfo(
    val en: String, val photo: String?, val primary: Set<String>, val secondary: Set<String>,
    /** 0 = basta un metro (in piedi, fermo); 1 = lo spazio di un tappetino; 2 = serve una stanza libera. */
    val space: Int,
    /** Parti del corpo caricate: ginocchia, schiena, spalle, polsi. */
    val stress: Set<String>,
    val easier: String?, val harder: String?,
    val steps: List<String>, val mistakes: List<String>, val breath: String, val cue: String, val caution: String,
)

object MoveInfos {
    /** Muscoli: nome da mostrare. */
    val MUSCLES = linkedMapOf(
        "petto" to "Petto", "spalle" to "Spalle", "bicipiti" to "Bicipiti", "tricipiti" to "Tricipiti", "avambracci" to "Avambracci", "addome" to "Addominali", "obliqui" to "Obliqui",
        "quadricipiti" to "Quadricipiti", "adduttori" to "Interno coscia", "flessori" to "Flessori dell'anca", "polpacci" to "Polpacci",
        "trapezi" to "Trapezi", "dorsali" to "Dorsali", "lombari" to "Lombari", "glutei" to "Glutei", "femorali" to "Femorali", "collo" to "Collo",
    )
    val BODY_PARTS = linkedMapOf("ginocchia" to "Ginocchia", "schiena" to "Schiena", "spalle" to "Spalle", "polsi" to "Polsi")
    val EQUIPMENT = linkedMapOf("manubri" to "Manubri (o bottiglie piene)", "kettlebell" to "Kettlebell", "elastico" to "Elastico", "corda" to "Corda per saltare", "sbarra" to "Sbarra per trazioni", "sedia" to "Sedia robusta o gradino")
    val SPACES = listOf("Una stanza libera", "Lo spazio di un tappetino", "Pochissimo (1 metro)")

    private fun parts(s: String) = s.mapNotNull { when (it) { 'g' -> "ginocchia"; 'b' -> "schiena"; 's' -> "spalle"; 'w' -> "polsi"; else -> null } }.toSet()
    private fun mus(s: String) = s.split('/').let { p -> p[0].trim().split(' ').filter { it.isNotBlank() }.toSet() to (p.getOrNull(1)?.trim()?.split(' ')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()) }

    private fun i(en: String, photo: String?, muscles: String, space: Int, stress: String, easier: String?, harder: String?,
                  steps: List<String>, mistakes: List<String>, breath: String, cue: String, caution: String = ""): MoveInfo {
        val (p, s) = mus(muscles)
        return MoveInfo(en, photo, p, s, space, parts(stress), easier, harder, steps, mistakes, breath, cue, caution)
    }

    private const val EXHALE_UP = "Espira mentre fai la fatica, inspira mentre torni."
    private const val HOLD_BREATH = "Respira con calma dal naso, senza trattenere il fiato."

    val ALL: Map<String, MoveInfo> = mapOf(
        // ----- stretching -----
        "polpacci" to i("Calf Stretch", "Calf_Stretch_Hands_Against_Wall", "polpacci / femorali", 0, "", null, null,
            listOf("Mani al muro all'altezza delle spalle.", "Una gamba avanti piegata, l'altra indietro tesa.", "Tallone dietro ben a terra e punta in avanti.", "Spingi piano i fianchi verso il muro e tieni."),
            listOf("Tallone dietro che si stacca", "Piede dietro girato in fuori"), HOLD_BREATH, "Tallone dietro a terra", ""),
        "quadricipite" to i("Standing Quad Stretch", "Quad_Stretch", "quadricipiti / flessori", 0, "g", null, null,
            listOf("In piedi, appoggiati a un muro o a una sedia se serve.", "Piega una gamba e prendi la caviglia con la mano.", "Porta il tallone verso il gluteo, ginocchia vicine.", "Spingi piano il bacino in avanti e tieni."),
            listOf("Ginocchio che va in fuori o in avanti", "Schiena inarcata"), HOLD_BREATH, "Ginocchia vicine, bacino avanti", "Con dolore al ginocchio non tirare forte il tallone."),
        "femorali" to i("Seated Hamstring Stretch", "Seated_Floor_Hamstring_Stretch", "femorali / lombari polpacci", 1, "b", null, null,
            listOf("Seduto con le gambe tese davanti.", "Allunga la schiena verso l'alto.", "Scendi in avanti piegandoti dalle anche.", "Mani verso le caviglie o i piedi, e tieni."),
            listOf("Schiena tutta arrotondata", "Ginocchia che si piegano molto"), "Espira mentre scendi, poi respira lento.", "Scendi dalle anche, schiena lunga", "Con mal di schiena piega un po' le ginocchia."),
        "flessori" to i("Kneeling Hip Flexor Stretch", "Kneeling_Hip_Flexor", "flessori quadricipiti", 1, "g", null, "mondo",
            listOf("Un ginocchio a terra (su un cuscino), l'altro piede avanti.", "Busto dritto, mani sul ginocchio davanti.", "Stringi il gluteo della gamba dietro.", "Spingi piano il bacino in avanti e tieni."),
            listOf("Schiena inarcata invece del bacino avanti", "Ginocchio davanti oltre la punta"), HOLD_BREATH, "Stringi il gluteo dietro", "Metti un cuscino sotto il ginocchio."),
        "bambino" to i("Child's Pose", "Childs_Pose", "lombari dorsali / glutei spalle", 1, "g", null, null,
            listOf("In ginocchio, alluci che si toccano.", "Siediti sui talloni.", "Porta il busto sulle cosce e le braccia lunghe avanti.", "Fronte a terra, lascia andare la schiena."),
            listOf("Spalle contratte verso le orecchie", "Fiato trattenuto"), "Respira lento e profondo nella schiena.", "Lascia andare le spalle", "Con le ginocchia doloranti metti un cuscino tra talloni e glutei."),
        "cobra" to i("Cobra Stretch", null, "addome lombari / flessori", 1, "bw", null, null,
            listOf("A pancia in giù, mani sotto le spalle.", "Gomiti vicini al corpo.", "Spingi piano e solleva il petto.", "Bacino a terra, spalle basse."),
            listOf("Spalle alle orecchie", "Spingere troppo: deve essere comodo"), "Inspira salendo, espira scendendo.", "Bacino a terra, spalle basse", "Con mal di schiena sali poco, sugli avambracci."),
        "cane" to i("Downward Dog", null, "femorali polpacci / spalle dorsali", 1, "sw", "bambino", "pike",
            listOf("A quattro zampe, mani un po' avanti.", "Stacca le ginocchia e alza il bacino.", "Spingi il pavimento con le mani.", "Porta i talloni verso terra, testa tra le braccia."),
            listOf("Schiena tonda: piega le ginocchia", "Peso tutto sulle mani"), HOLD_BREATH, "Bacino in alto, talloni giù", "Con dolore ai polsi appoggiati sui pugni."),
        "avanti" to i("Standing Toe Touch", "Standing_Toe_Touches", "femorali lombari / polpacci", 0, "b", "femorali", null,
            listOf("In piedi, piedi uniti, ginocchia morbide.", "Scendi in avanti lentamente.", "Lascia pendere testa e braccia.", "Risali srotolando la schiena piano."),
            listOf("Molleggiare", "Ginocchia bloccate"), "Espira mentre scendi.", "Lascia pendere la testa", "Con mal di schiena o pressione bassa risali molto piano."),
        "ginocchia-petto" to i("Knees to Chest", "Hug_Knees_To_Chest", "lombari glutei", 1, "", null, null,
            listOf("Sdraiato sulla schiena.", "Porta le ginocchia al petto.", "Abbracciale con le mani.", "Dondola piano da un lato all'altro."),
            listOf("Testa sollevata e collo teso"), HOLD_BREATH, "Collo rilassato a terra", ""),
        "laterale" to i("Standing Side Stretch", "Standing_Lateral_Stretch", "obliqui dorsali", 0, "", null, null,
            listOf("In piedi, gambe un po' aperte.", "Porta un braccio sopra la testa.", "Piegati dall'altra parte.", "Busto di fronte, senza ruotare."),
            listOf("Ruotare il busto", "Spingere il bacino troppo di lato"), "Espira mentre ti pieghi.", "Allunga verso l'alto, poi di lato", ""),
        "squat-profondo" to i("Deep Squat Hold", null, "adduttori glutei / polpacci", 0, "g", null, null,
            listOf("Piedi larghi, punte un po' in fuori.", "Scendi in squat più in basso che puoi.", "Talloni a terra, gomiti dentro le ginocchia.", "Petto alto, spingi le ginocchia in fuori."),
            listOf("Talloni che si staccano", "Schiena tutta curva"), HOLD_BREATH, "Talloni giù, petto alto", "Se i talloni si alzano mettici sotto un libro."),

        // ----- mobilità -----
        "spalle" to i("Arm Circles", "Arm_Circles", "spalle / trapezi", 0, "", null, null,
            listOf("In piedi, braccia tese.", "Disegna cerchi grandi e lenti in avanti.", "Poi fai lo stesso all'indietro."),
            listOf("Spalle alzate verso le orecchie", "Cerchi troppo veloci"), "Respira normalmente.", "Cerchi grandi e lenti", "Con dolore alla spalla fai cerchi piccoli."),
        "gatto" to i("Cat Cow", "Cat_Stretch", "lombari addome / collo", 1, "w", null, null,
            listOf("A quattro zampe: mani sotto le spalle, ginocchia sotto le anche.", "Espira: arrotonda la schiena e guarda l'ombelico.", "Inspira: inarca piano e guarda avanti.", "Vai lento, seguendo il respiro."),
            listOf("Muovere solo il collo", "Andare veloci"), "Espira arrotondando, inspira inarcando.", "Segui il respiro", ""),
        "oscillazioni" to i("Leg Swings", null, "flessori femorali / glutei", 0, "", null, null,
            listOf("In piedi, una mano su un muro.", "Lascia oscillare una gamba avanti e indietro.", "Sempre un po' più in alto, senza forzare.", "Poi cambia gamba."),
            listOf("Busto che si inclina", "Calci bruschi"), "Respira normalmente.", "Gamba morbida, busto fermo", ""),
        "mondo" to i("World's Greatest Stretch", "Worlds_Greatest_Stretch", "flessori dorsali / obliqui femorali", 1, "gw", "flessori", null,
            listOf("Fai un affondo lungo.", "Appoggia a terra la mano dal lato della gamba dietro.", "Apri l'altro braccio verso il soffitto.", "Seguilo con lo sguardo, poi torna."),
            listOf("Ginocchio davanti che cade dentro", "Ruotare solo il braccio"), "Inspira aprendo, espira tornando.", "Apri il petto verso il soffitto", ""),
        "collo" to i("Neck Mobility", null, "collo trapezi", 0, "", null, null,
            listOf("Seduto o in piedi, spalle basse.", "Porta il mento al petto.", "Poi guarda in alto, piano.", "Niente cerchi completi con la testa."),
            listOf("Movimenti veloci", "Spalle alzate"), "Respira lento.", "Piano, spalle basse", "Se gira la testa fermati."),
        "marcia" to i("March in Place", null, "flessori quadricipiti / polpacci", 0, "", null, "skip",
            listOf("In piedi, busto dritto.", "Alza un ginocchio, poi l'altro.", "Muovi le braccia come camminando.", "Ritmo tranquillo."),
            listOf("Busto all'indietro"), "Respira normalmente.", "Ginocchia su, busto dritto", ""),

        // ----- gambe e glutei -----
        "squat" to i("Bodyweight Squat", "Bodyweight_Squat", "quadricipiti glutei / femorali addome", 0, "g", "muro", "squat-salto",
            listOf("Piedi larghi come le spalle, punte appena in fuori.", "Porta il bacino indietro come per sederti.", "Scendi con il petto alto finché le cosce sono parallele.", "Risali spingendo con i talloni."),
            listOf("Ginocchia che vanno in dentro", "Talloni che si staccano", "Schiena curva"), EXHALE_UP, "Petto alto, peso sui talloni", "Con dolore alle ginocchia scendi meno, o fai lo squat al muro."),
        "sumo" to i("Sumo Squat", "Plie_Dumbbell_Squat", "adduttori glutei quadricipiti", 0, "g", "squat", null,
            listOf("Piedi molto larghi, punte in fuori.", "Mani unite al petto.", "Scendi dritto con la schiena.", "Spingi le ginocchia in fuori e risali."),
            listOf("Ginocchia che cadono in dentro", "Busto in avanti"), EXHALE_UP, "Ginocchia in fuori", ""),
        "affondi" to i("Lunge", "Bodyweight_Walking_Lunge", "quadricipiti glutei / femorali", 1, "g", "squat", "affondi-salto",
            listOf("In piedi, mani sui fianchi.", "Fai un passo lungo in avanti.", "Scendi finché il ginocchio dietro sfiora terra.", "Spingi con il piede davanti e torna su."),
            listOf("Ginocchio davanti oltre la punta", "Busto piegato in avanti", "Passo troppo corto"), EXHALE_UP, "Busto dritto, ginocchio sopra la caviglia", "Con le ginocchia doloranti fai l'affondo all'indietro, più corto."),
        "muro" to i("Wall Sit", null, "quadricipiti / glutei", 0, "g", null, "squat",
            listOf("Schiena contro il muro.", "Scendi finché le cosce sono parallele al pavimento.", "Ginocchia sopra le caviglie.", "Resta lì respirando."),
            listOf("Ginocchia oltre le punte", "Mani che spingono sulle cosce"), HOLD_BREATH, "Schiena al muro, respira", "Con dolore alle ginocchia scendi meno."),
        "ponte" to i("Glute Bridge", "Butt_Lift_Bridge", "glutei femorali / lombari addome", 1, "", null, "ponte-una-gamba",
            listOf("Sdraiato, ginocchia piegate, piedi a terra.", "Braccia lungo i fianchi.", "Stringi i glutei e solleva il bacino.", "Fai una linea spalle-ginocchia, poi scendi piano."),
            listOf("Inarcare la schiena invece di usare i glutei", "Spingere con le punte"), EXHALE_UP, "Stringi i glutei in alto", ""),
        "calci" to i("Glute Kickback", "Glute_Kickback", "glutei / femorali lombari", 1, "w", "ponte", null,
            listOf("A quattro zampe.", "Ginocchio piegato a 90°.", "Spingi il piede verso il soffitto.", "Stringi il gluteo, poi torna giù."),
            listOf("Schiena che si inarca", "Movimento con lo slancio"), EXHALE_UP, "Schiena ferma, spingi col tallone", ""),
        "buongiorno" to i("Good Morning", null, "femorali glutei / lombari", 0, "b", null, "stacco",
            listOf("In piedi, mani sul petto, ginocchia morbide.", "Porta il bacino indietro.", "Scendi col busto dritto fino a sentire i femorali.", "Torna su spingendo il bacino avanti."),
            listOf("Schiena curva", "Piegare troppo le ginocchia"), "Inspira scendendo, espira salendo.", "Bacino indietro, schiena dritta", "Con mal di schiena scendi poco."),
        "squat-salto" to i("Jump Squat", "Freehand_Jump_Squat", "quadricipiti glutei / polpacci", 1, "g", "squat", null,
            listOf("Piedi larghi come le spalle.", "Scendi in squat con le braccia indietro.", "Salta in alto slanciando le braccia.", "Atterra morbido e riscendi subito."),
            listOf("Atterrare a gambe tese", "Ginocchia in dentro all'atterraggio"), "Espira saltando.", "Atterra morbido", "Niente salti con dolori alle ginocchia o vicini di sotto: fai lo squat."),
        "affondi-salto" to i("Split Jump", "Split_Jump", "quadricipiti glutei / polpacci", 2, "g", "affondi", null,
            listOf("Parti da un affondo.", "Salta e cambia gamba in aria.", "Atterra nell'affondo con l'altra gamba avanti.", "Busto dritto, ritmo costante."),
            listOf("Atterraggio rigido", "Busto in avanti"), "Espira a ogni salto.", "Atterra morbido, busto dritto", "Esercizio duro per le ginocchia."),
        "pistol" to i("Pistol Squat", "Kettlebell_Pistol_Squat", "quadricipiti glutei / addome", 0, "g", "squat", null,
            listOf("In piedi su una gamba, l'altra tesa avanti.", "Braccia avanti per l'equilibrio.", "Scendi piano più che puoi, tallone a terra.", "Risali spingendo col tallone."),
            listOf("Ginocchio che cade dentro", "Scendere di colpo"), EXHALE_UP, "Tallone a terra, scendi piano", "All'inizio tieniti a una sedia o scendi su una panca."),
        "step-up" to i("Step-up", "Step-up_with_Knee_Raise", "quadricipiti glutei / polpacci", 1, "g", "marcia", null,
            listOf("Davanti a un gradino o a una sedia ferma.", "Metti tutto il piede sopra.", "Spingi con quella gamba e sali, porta su l'altro ginocchio.", "Scendi con calma."),
            listOf("Spingere col piede rimasto a terra", "Sedia che si muove"), EXHALE_UP, "Spingi col piede che è sopra", "Usa un gradino basso se hai dolore alle ginocchia."),
        "affondo-laterale" to i("Lateral Lunge", null, "adduttori quadricipiti glutei", 2, "g", "sumo", null,
            listOf("Gambe molto larghe.", "Piega un ginocchio portando il bacino indietro e di lato.", "L'altra gamba resta tesa.", "Torna al centro e cambia lato."),
            listOf("Ginocchio oltre la punta", "Busto piegato in avanti"), EXHALE_UP, "Bacino indietro, petto alto", ""),
        "ponte-una-gamba" to i("Single Leg Glute Bridge", "Single_Leg_Glute_Bridge", "glutei femorali / addome", 1, "", "ponte", null,
            listOf("Come il ponte, ma una gamba tesa in alto.", "Spingi con il tallone a terra.", "Solleva il bacino dritto.", "Scendi piano, poi cambia gamba."),
            listOf("Bacino che cade da un lato"), EXHALE_UP, "Bacino dritto", ""),
        "stacco" to i("Romanian Deadlift", "Stiff-Legged_Dumbbell_Deadlift", "femorali glutei / lombari", 0, "b", "buongiorno", null,
            listOf("Manubri davanti alle cosce, ginocchia morbide.", "Porta il bacino indietro.", "Scendi con la schiena dritta, pesi vicini alle gambe.", "Risali spingendo il bacino avanti."),
            listOf("Schiena curva", "Pesi lontani dal corpo"), "Inspira scendendo, espira salendo.", "Schiena dritta, pesi vicini alle gambe", "Con mal di schiena usa pesi leggeri e scendi poco."),
        "goblet" to i("Goblet Squat", "Goblet_Squat", "quadricipiti glutei / addome", 0, "g", "squat", null,
            listOf("Peso davanti al petto, gomiti in basso.", "Piedi un po' più larghi delle spalle.", "Scendi con il petto alto.", "Risali spingendo con i talloni."),
            listOf("Petto che cade in avanti", "Ginocchia in dentro"), EXHALE_UP, "Petto alto, peso vicino", ""),
        "swing" to i("Kettlebell Swing", "One-Arm_Kettlebell_Swings", "glutei femorali / lombari spalle", 1, "b", "stacco", null,
            listOf("Piedi larghi, kettlebell tra le gambe.", "Piegati dalle anche, schiena dritta.", "Spingi il bacino avanti con forza.", "Lascia salire il peso fino al petto, poi riscendi."),
            listOf("Sollevare con le braccia", "Fare uno squat invece di piegarsi dalle anche", "Schiena curva"), "Espira forte quando spingi il bacino.", "La forza viene dai fianchi", "Impara prima lo stacco; niente swing con mal di schiena."),

        // ----- parte alta -----
        "flessioni" to i("Push-up", "Pushups", "petto tricipiti / spalle addome", 1, "sw", "flessioni-ginocchia", "pike",
            listOf("Mani sotto le spalle, un po' più larghe.", "Corpo dritto dalla testa ai talloni.", "Scendi con il petto verso terra, gomiti a 45°.", "Spingi su fino a braccia tese."),
            listOf("Bacino che cade", "Gomiti aperti a T", "Testa che scende prima del petto"), "Inspira scendendo, espira spingendo.", "Corpo dritto come una tavola", "Con dolore ai polsi usa i pugni o due manubri come appoggio."),
        "flessioni-ginocchia" to i("Knee Push-up", "Incline_Push-Up", "petto tricipiti / spalle", 1, "sw", "flessioni-muro", "flessioni",
            listOf("Ginocchia a terra, mani sotto le spalle.", "Corpo dritto dalle ginocchia alla testa.", "Scendi con il petto.", "Spingi su."),
            listOf("Bacino in alto", "Scendere poco"), "Inspira scendendo, espira spingendo.", "Dritto dalle ginocchia alla testa", ""),
        "flessioni-muro" to i("Wall Push-up", "Incline_Push-Up", "petto tricipiti / spalle", 0, "s", null, "flessioni-ginocchia",
            listOf("In piedi davanti al muro.", "Mani all'altezza delle spalle.", "Piega i gomiti e porta il petto al muro.", "Spingi indietro."),
            listOf("Bacino all'indietro"), "Inspira avvicinandoti, espira spingendo.", "Corpo dritto", ""),
        "pugni" to i("Shadow Boxing", null, "spalle / tricipiti obliqui", 0, "", null, null,
            listOf("Gambe un po' piegate, pugni davanti al viso.", "Tira pugni dritti alternando le braccia.", "Ruota un po' il busto a ogni pugno.", "Veloce ma senza bloccare i gomiti."),
            listOf("Gomiti che si bloccano di colpo", "Guardia bassa"), "Espira a ogni pugno.", "Pugni veloci, gomiti morbidi", ""),
        "pike" to i("Pike Push-up", null, "spalle tricipiti / petto", 1, "sw", "cane", null,
            listOf("Bacino in alto: il corpo fa una V rovesciata.", "Mani larghe come le spalle.", "Piega i gomiti e porta la testa verso terra.", "Spingi su."),
            listOf("Gomiti troppo aperti", "Bacino che scende"), "Inspira scendendo, espira spingendo.", "Testa tra le mani", "Esercizio duro per le spalle."),
        "curl" to i("Dumbbell Bicep Curl", "Dumbbell_Bicep_Curl", "bicipiti / avambracci", 0, "", null, null,
            listOf("In piedi, manubri lungo i fianchi, palmi avanti.", "Gomiti fermi vicino al corpo.", "Piega i gomiti e porta i pesi alle spalle.", "Scendi piano."),
            listOf("Dondolare con la schiena", "Gomiti che vanno avanti"), EXHALE_UP, "Gomiti fermi", ""),
        "rematore" to i("Bent Over Dumbbell Row", "Bent_Over_Two-Dumbbell_Row", "dorsali trapezi / bicipiti lombari", 0, "b", "elastico-rematore", null,
            listOf("Busto inclinato, schiena dritta, ginocchia morbide.", "Manubri a braccia tese.", "Tira i gomiti indietro verso i fianchi.", "Stringi le scapole, poi scendi."),
            listOf("Schiena curva", "Tirare con le spalle alzate"), "Espira tirando, inspira scendendo.", "Gomiti indietro, scapole strette", "Con mal di schiena appoggia una mano a una sedia."),
        "spalle-manubri" to i("Dumbbell Shoulder Press", "Dumbbell_Shoulder_Press", "spalle tricipiti / trapezi", 0, "s", null, null,
            listOf("In piedi, manubri all'altezza delle spalle.", "Gomiti in fuori, addominali stretti.", "Spingi i pesi sopra la testa.", "Torna giù piano."),
            listOf("Inarcare la schiena", "Bloccare i gomiti di colpo"), EXHALE_UP, "Pancia stretta, spingi su", "Con dolore alla spalla usa pesi leggeri o salta l'esercizio."),
        "elastico-rematore" to i("Band Row", null, "dorsali trapezi / bicipiti", 0, "", null, "rematore",
            listOf("Fissa l'elastico davanti, all'altezza del petto.", "Braccia tese davanti.", "Tira i gomiti indietro stringendo le scapole.", "Lascia tornare lento."),
            listOf("Spalle che salgono", "Elastico che scatta indietro"), "Espira tirando.", "Scapole strette", "Controlla che l'elastico sia fissato bene."),
        "elastico-aperture" to i("Band Pull Apart", "Band_Pull_Apart", "trapezi spalle / dorsali", 0, "", null, null,
            listOf("Braccia tese davanti al petto, elastico tra le mani.", "Apri le braccia di lato.", "Porta l'elastico al petto stringendo le scapole.", "Richiudi piano."),
            listOf("Piegare i gomiti", "Inarcare la schiena"), "Espira aprendo.", "Apri e stringi le scapole", "Ottimo per chi sta tanto seduto."),
        "trazioni" to i("Pull-up", "Pullups", "dorsali bicipiti / trapezi addome", 0, "s", "elastico-rematore", null,
            listOf("Appeso alla sbarra, mani poco più larghe delle spalle.", "Abbassa le spalle.", "Tira portando il petto verso la sbarra.", "Scendi lento fino a braccia tese."),
            listOf("Dondolarsi", "Scendere solo a metà"), "Espira tirando su.", "Spalle giù, petto alla sbarra", "Se non ci riesci ancora, resta appeso o scendi lento da sopra."),
        "dip-sedia" to i("Bench Dip", "Bench_Dips", "tricipiti / petto spalle", 1, "sw", "flessioni-muro", null,
            listOf("Mani sul bordo di una sedia ferma dietro di te.", "Gambe avanti, bacino vicino alla sedia.", "Piega i gomiti indietro e scendi.", "Spingi su."),
            listOf("Gomiti aperti", "Scendere troppo"), "Inspira scendendo, espira spingendo.", "Gomiti indietro, bacino vicino alla sedia", "Con dolore alle spalle scendi poco o salta l'esercizio."),

        // ----- addominali e schiena -----
        "plank" to i("Plank", "Plank", "addome / spalle glutei lombari", 1, "s", null, "plank-su-giu",
            listOf("Avambracci a terra, gomiti sotto le spalle.", "Punte dei piedi a terra, gambe tese.", "Corpo dritto dalla testa ai talloni.", "Stringi pancia e glutei e tieni."),
            listOf("Bacino che cade", "Sedere troppo in alto", "Fiato trattenuto"), HOLD_BREATH, "Pancia e glutei stretti", "Più facile: con le ginocchia a terra."),
        "plank-alto" to i("High Plank", "Pushups", "addome spalle / tricipiti", 1, "sw", "plank", "tocchi-spalle",
            listOf("Mani sotto le spalle, braccia tese.", "Corpo dritto.", "Spingi il pavimento lontano.", "Tieni respirando."),
            listOf("Bacino che cade", "Spalle sopra le orecchie"), HOLD_BREATH, "Spingi il pavimento", ""),
        "tocchi-spalle" to i("Plank Shoulder Tap", null, "addome obliqui / spalle", 1, "sw", "plank-alto", null,
            listOf("Plank a braccia tese, piedi un po' larghi.", "Tocca con una mano la spalla opposta.", "Rimettila giù e cambia.", "Il bacino non deve ondeggiare."),
            listOf("Bacino che ruota", "Andare troppo veloci"), "Respira a ogni tocco.", "Bacino fermo", ""),
        "plank-su-giu" to i("Plank Up Down", null, "addome tricipiti / spalle petto", 1, "sw", "plank", null,
            listOf("Dal plank sugli avambracci.", "Sali su una mano, poi sull'altra.", "Arriva al plank a braccia tese.", "Riscendi un avambraccio alla volta."),
            listOf("Bacino che ondeggia"), "Respira regolare.", "Bacino fermo", ""),
        "plank-laterale" to i("Side Plank", "Side_Bridge", "obliqui / addome spalle glutei", 1, "s", "plank", null,
            listOf("Su un fianco, gomito sotto la spalla.", "Gambe tese, piedi uno sull'altro.", "Solleva il bacino finché il corpo è dritto.", "Tieni, poi cambia lato."),
            listOf("Bacino che scende", "Spalla schiacciata"), HOLD_BREATH, "Bacino alto", "Più facile: con il ginocchio di sotto a terra."),
        "crunch" to i("Crunch", "Cross-Body_Crunch", "addome / obliqui", 1, "", null, "situp",
            listOf("Sdraiato, ginocchia piegate.", "Mani verso le ginocchia.", "Stacca le spalle da terra.", "Torna giù piano."),
            listOf("Tirare il collo con le mani", "Salire con lo slancio"), EXHALE_UP, "Sali con la pancia, non col collo", ""),
        "situp" to i("Sit-Up", "Sit-Up", "addome flessori / obliqui", 1, "b", "crunch", null,
            listOf("Sdraiato, ginocchia piegate, piedi a terra.", "Braccia sopra la testa.", "Sali fino a sederti portando le braccia avanti.", "Torna giù vertebra dopo vertebra."),
            listOf("Piedi che si staccano", "Cadere giù di colpo"), EXHALE_UP, "Sali arrotolando la schiena", "Con mal di schiena fai il crunch."),
        "bicicletta" to i("Bicycle Crunch", "Air_Bike", "addome obliqui", 1, "", "crunch", null,
            listOf("Sdraiato, mani dietro la testa.", "Spalle sollevate.", "Porta un ginocchio al petto e allunga l'altra gamba.", "Ruota il busto verso il ginocchio e alterna."),
            listOf("Tirare il collo", "Andare troppo veloci"), "Espira a ogni torsione.", "Ruota con il busto", ""),
        "gambe-tese" to i("Lying Leg Raise", "Flat_Bench_Lying_Leg_Raise", "addome flessori", 1, "b", "dead-bug", "barchetta",
            listOf("Sdraiato, mani sotto i glutei.", "Gambe tese unite.", "Sollevale fino in verticale.", "Riscendi lento senza toccare terra."),
            listOf("Schiena che si inarca", "Scendere di colpo"), EXHALE_UP, "Schiena bassa a terra", "Con mal di schiena piega le ginocchia o fai il dead bug."),
        "dead-bug" to i("Dead Bug", "Dead_Bug", "addome / flessori", 1, "", null, "gambe-tese",
            listOf("Sdraiato, braccia verso il soffitto.", "Ginocchia piegate in alto a 90°.", "Allunga un braccio dietro e la gamba opposta.", "Torna e cambia lato."),
            listOf("Schiena che si stacca", "Andare veloci"), "Espira allungando.", "Schiena incollata a terra", "Ottimo anche con il mal di schiena."),
        "bird-dog" to i("Bird Dog", null, "lombari glutei / addome spalle", 1, "w", null, null,
            listOf("A quattro zampe.", "Allunga avanti un braccio e indietro la gamba opposta.", "In linea con la schiena.", "Tieni un attimo e cambia."),
            listOf("Bacino che ruota", "Gamba troppo alta"), "Espira allungando.", "Schiena piatta come un tavolo", "Ottimo anche con il mal di schiena."),
        "superman" to i("Superman", "Superman", "lombari glutei / trapezi", 1, "b", "bird-dog", null,
            listOf("A pancia in giù, braccia lunghe davanti.", "Solleva insieme braccia, petto e gambe.", "Pochi centimetri bastano.", "Tieni un attimo e scendi."),
            listOf("Collo piegato all'indietro", "Salire troppo"), EXHALE_UP, "Allungati, non salire troppo", "Con mal di schiena fai il bird dog."),
        "russian" to i("Russian Twist", "Russian_Twist", "obliqui addome", 1, "b", "crunch", null,
            listOf("Seduto, busto inclinato indietro.", "Piedi sollevati (o a terra, più facile).", "Mani unite davanti.", "Ruota il busto da un fianco all'altro."),
            listOf("Schiena curva", "Muovere solo le braccia"), "Espira a ogni rotazione.", "Ruota con il petto", "Con mal di schiena tieni i piedi a terra."),
        "barchetta" to i("Hollow Hold", null, "addome / flessori", 1, "b", "dead-bug", null,
            listOf("Sdraiato sulla schiena.", "Braccia lunghe sopra la testa.", "Stacca spalle e gambe tese da terra.", "Schiena bassa appoggiata, e tieni."),
            listOf("Schiena che si inarca", "Gambe troppo basse"), HOLD_BREATH, "Schiena bassa giù", "Più facile: ginocchia piegate."),

        // ----- cardio -----
        "scalatore" to i("Mountain Climber", "Mountain_Climbers", "addome flessori / spalle quadricipiti", 1, "sw", "plank-alto", null,
            listOf("In posizione di flessione.", "Porta un ginocchio verso il petto.", "Cambia gamba veloce, come se corressi.", "Bacino basso."),
            listOf("Bacino in alto", "Mani lontane dalle spalle"), "Respira veloce e regolare.", "Bacino basso, ginocchia al petto", ""),
        "calciata" to i("Butt Kicks", "Single_Leg_Butt_Kick", "femorali / polpacci", 0, "", "marcia", "corsa",
            listOf("Corri o cammina sul posto.", "Porta i talloni verso i glutei.", "Busto dritto.", "Braccia che accompagnano."),
            listOf("Busto in avanti"), "Respira regolare.", "Talloni ai glutei", ""),
        "ginocchio-gomito" to i("Standing Knee to Elbow", "Elbow_to_Knee", "obliqui addome / flessori", 0, "", "marcia", null,
            listOf("In piedi, mani dietro la testa.", "Porta un ginocchio verso il gomito opposto.", "Piega il busto di lato.", "Cambia lato."),
            listOf("Tirare il collo", "Busto troppo in avanti"), "Espira a ogni ginocchio.", "Ginocchio su, gomito giù", ""),
        "jumping-jack" to i("Jumping Jack", "Star_Jump", "polpacci spalle / adduttori glutei", 1, "g", "marcia", "star-jump",
            listOf("In piedi, braccia lungo i fianchi.", "Salta aprendo gambe e braccia.", "Salta e chiudi.", "Atterra sulle punte, morbido."),
            listOf("Atterrare sui talloni"), "Respira regolare.", "Atterra morbido", "Senza salti: apri una gamba alla volta."),
        "skip" to i("High Knees", "Fast_Skipping", "flessori quadricipiti / polpacci", 0, "g", "marcia", null,
            listOf("Corri sul posto.", "Ginocchia all'altezza dei fianchi.", "Braccia che spingono.", "Resta sulle punte."),
            listOf("Busto indietro", "Ginocchia basse"), "Respira veloce e regolare.", "Ginocchia alte", ""),
        "corsa" to i("Running in Place", null, "quadricipiti polpacci / flessori", 0, "g", "marcia", "skip",
            listOf("Corri sul posto, sulle punte.", "Braccia piegate.", "Spingi le braccia avanti e indietro.", "Ritmo veloce."),
            listOf("Atterrare sui talloni"), "Respira regolare.", "Sulle punte", ""),
        "burpee" to i("Burpee", null, "quadricipiti petto / addome spalle", 2, "gsbw", "sprawl", null,
            listOf("In piedi.", "Scendi in squat e appoggia le mani.", "Butta i piedi indietro in plank (con una flessione, se vuoi).", "Torna in squat e salta con le braccia in alto."),
            listOf("Schiena che cade nel plank", "Atterraggio rigido"), "Espira saltando.", "Plank dritto, salto morbido", "Senza salti: alzati senza saltare e fai i piedi indietro uno alla volta."),
        "sprawl" to i("Sprawl", null, "addome quadricipiti / spalle", 2, "gw", "scalatore", "burpee",
            listOf("In piedi.", "Mani a terra.", "Butta i piedi indietro fino a sdraiarti.", "Torna su veloce."),
            listOf("Schiena inarcata"), "Espira scendendo.", "Veloce giù, veloce su", ""),
        "star-jump" to i("Star Jump", "Star_Jump", "quadricipiti glutei / spalle polpacci", 2, "g", "jumping-jack", null,
            listOf("Accovacciati con le mani verso terra.", "Salta in alto.", "Apri braccia e gambe a stella.", "Atterra morbido a gambe unite."),
            listOf("Atterraggio rigido"), "Espira saltando.", "Esplodi e apri", ""),
        "corda" to i("Rope Jumping", "Rope_Jumping", "polpacci / spalle quadricipiti", 1, "g", "marcia", null,
            listOf("Gomiti vicini ai fianchi.", "La corda gira con i polsi.", "Salti piccoli sulle punte.", "Appena sopra la corda."),
            listOf("Salti troppo alti", "Braccia larghe"), "Respira regolare.", "Salti piccoli, polsi che girano", "Serve un po' di spazio sopra la testa."),

        // ----- altri esercizi con gli attrezzi -----
        "affondi-manubri" to i("Dumbbell Lunge", "Dumbbell_Lunges", "quadricipiti glutei / femorali avambracci", 1, "g", "affondi", "bulgari",
            listOf("Un manubrio per mano lungo i fianchi.", "Fai un passo lungo in avanti.", "Scendi finché il ginocchio dietro sfiora terra.", "Spingi col piede davanti e torna su."),
            listOf("Busto piegato in avanti", "Ginocchio davanti che cade dentro"), EXHALE_UP, "Busto dritto, pesi lungo i fianchi", "Con le ginocchia doloranti fai passi più corti."),
        "squat-manubri" to i("Dumbbell Squat", "Dumbbell_Squat", "quadricipiti glutei / femorali avambracci", 0, "g", "squat", "thruster",
            listOf("Manubri lungo i fianchi, piedi larghi come le spalle.", "Porta il bacino indietro e scendi.", "Petto alto, pesi che scendono dritti.", "Risali spingendo coi talloni."),
            listOf("Schiena curva", "Talloni che si staccano"), EXHALE_UP, "Petto alto, talloni giù", ""),
        "alzate-laterali" to i("Dumbbell Lateral Raise", "Side_Laterals_to_Front_Raise", "spalle / trapezi", 0, "s", null, null,
            listOf("In piedi, manubri lungo i fianchi.", "Gomiti appena piegati.", "Alza le braccia di lato fino alle spalle.", "Scendi piano."),
            listOf("Alzare oltre le spalle", "Dondolare con il busto", "Spalle che salgono alle orecchie"), EXHALE_UP, "Spalle basse, braccia di lato", "Usa pesi leggeri: bastano 1-3 kg."),
        "alzate-frontali" to i("Front Dumbbell Raise", "Front_Dumbbell_Raise", "spalle / petto", 0, "s", null, null,
            listOf("Manubri davanti alle cosce.", "Braccia quasi tese.", "Alzale davanti fino all'altezza delle spalle.", "Scendi senza dondolare."),
            listOf("Slancio con la schiena"), EXHALE_UP, "Su lento, giù lento", "Pesi leggeri."),
        "floor-press" to i("Dumbbell Floor Press", "Dumbbell_Floor_Press", "petto tricipiti / spalle", 1, "s", "flessioni-ginocchia", null,
            listOf("Sdraiato, ginocchia piegate.", "Gomiti a terra, manubri sopra il petto.", "Spingi i pesi in alto a braccia tese.", "Riscendi finché i gomiti toccano terra."),
            listOf("Gomiti aperti a 90°", "Pesi che sbattono"), "Inspira scendendo, espira spingendo.", "Gomiti a 45° dal corpo", "Il pavimento protegge le spalle: buono anche per chi ha poco equilibrio."),
        "tricipiti-testa" to i("Overhead Triceps Extension", "Dumbbell_One-Arm_Triceps_Extension", "tricipiti / spalle", 0, "s", "kickback", null,
            listOf("In piedi, un manubrio con due mani sopra la testa.", "Gomiti stretti e rivolti in alto.", "Piega i gomiti e porta il peso dietro la testa.", "Distendi le braccia."),
            listOf("Gomiti che si aprono", "Schiena inarcata"), EXHALE_UP, "Gomiti fermi e stretti", "Con dolore alle spalle fai gli slanci indietro."),
        "kickback" to i("Dumbbell Triceps Kickback", "Tricep_Dumbbell_Kickback", "tricipiti / spalle", 0, "b", null, "tricipiti-testa",
            listOf("Busto inclinato, schiena dritta.", "Gomiti alti vicino ai fianchi.", "Distendi le braccia indietro.", "Torna con calma."),
            listOf("Gomiti che scendono", "Slancio"), EXHALE_UP, "Gomiti fermi in alto", "Con mal di schiena appoggia una mano a una sedia."),
        "thruster" to i("Dumbbell Thruster", "Kettlebell_Thruster", "quadricipiti spalle / glutei tricipiti addome", 1, "gs", "squat-manubri", null,
            listOf("Manubri alle spalle.", "Scendi in squat.", "Risali con forza e spingi i pesi sopra la testa.", "Riporta i pesi alle spalle e riparti."),
            listOf("Spingere solo con le braccia", "Schiena inarcata in alto"), "Espira mentre spingi in alto.", "Spingi con le gambe", "Esercizio intenso: pesi leggeri all'inizio."),
        "renegade" to i("Renegade Row", "Alternating_Renegade_Row", "dorsali addome / spalle tricipiti", 1, "sw", "rematore", null,
            listOf("Plank a braccia tese, mani sui manubri.", "Piedi larghi.", "Tira un manubrio verso il fianco.", "Posalo e cambia braccio."),
            listOf("Bacino che ruota", "Piedi stretti"), "Espira tirando.", "Bacino fermo", "Usa manubri che non rotolano."),
        "stacco-kb" to i("Kettlebell Deadlift", "Kettlebell_Dead_Clean", "glutei femorali / lombari avambracci", 0, "b", "buongiorno", "swing",
            listOf("Kettlebell a terra tra i piedi.", "Piegati dalle anche, schiena dritta.", "Prendi il peso con due mani.", "Alzati spingendo il bacino avanti."),
            listOf("Schiena curva", "Tirare con le braccia"), "Inspira scendendo, espira salendo.", "Schiena dritta, spingi col bacino", "Con mal di schiena peso leggero."),
        "rematore-kb" to i("One-Arm Kettlebell Row", "One-Arm_Kettlebell_Row", "dorsali trapezi / bicipiti", 0, "", "elastico-rematore", null,
            listOf("Una mano su un ginocchio (o su una sedia).", "Schiena dritta, kettlebell nell'altra mano.", "Tira il gomito verso il fianco.", "Scendi piano."),
            listOf("Ruotare il busto", "Schiena curva"), "Espira tirando.", "Gomito verso il fianco", ""),
        "squat-elastico" to i("Band Squat", "Squats_-_With_Bands", "quadricipiti glutei", 0, "g", "squat", "squat-manubri",
            listOf("Elastico sotto i piedi.", "Estremità all'altezza delle spalle.", "Scendi in squat.", "Risali spingendo contro l'elastico."),
            listOf("Elastico che scivola dai piedi"), EXHALE_UP, "Petto alto", "Controlla che l'elastico sia ben fermo sotto i piedi."),
        "spalle-elastico" to i("Band Shoulder Press", "Shoulder_Press_-_With_Bands", "spalle tricipiti", 0, "s", null, "spalle-manubri",
            listOf("Elastico sotto i piedi, mani alle spalle.", "Pancia stretta.", "Spingi le braccia sopra la testa.", "Torna giù con controllo."),
            listOf("Schiena inarcata"), EXHALE_UP, "Pancia stretta, spingi su", ""),
        "curl-elastico" to i("Band Curl", null, "bicipiti / avambracci", 0, "", null, "curl",
            listOf("Elastico sotto i piedi, palmi avanti.", "Gomiti vicini ai fianchi.", "Porta le mani alle spalle.", "Scendi lento."),
            listOf("Gomiti che vanno avanti"), EXHALE_UP, "Gomiti fermi", ""),
        "passi-elastico" to i("Lateral Band Walk", "Monster_Walk", "glutei adduttori / quadricipiti", 1, "", null, null,
            listOf("Elastico sopra le ginocchia.", "Gambe un po' piegate.", "Passo di lato tenendo l'elastico teso.", "Avvicina l'altro piede senza farlo cedere."),
            listOf("Ginocchia che cadono in dentro", "Busto che dondola"), "Respira regolare.", "Ginocchia in fuori", "Ottimo per glutei e ginocchia stabili."),
        "ginocchia-appeso" to i("Hanging Knee Raise", "Hanging_Leg_Raise", "addome flessori / avambracci", 0, "s", "dead-bug", null,
            listOf("Appeso alla sbarra.", "Spalle attive, non a penzoloni.", "Porta le ginocchia al petto.", "Scendi lento senza dondolare."),
            listOf("Dondolare", "Scendere di colpo"), EXHALE_UP, "Ginocchia su, niente dondolio", ""),
        "appeso" to i("Dead Hang", null, "avambracci dorsali / spalle", 0, "s", null, "trazioni",
            listOf("Prendi la sbarra, mani larghe come le spalle.", "Stacca i piedi da terra.", "Lasciati pendere con le braccia tese.", "Respira e tieni."),
            listOf("Fiato trattenuto", "Spalle che fanno male: scendi"), HOLD_BREATH, "Rilassa e respira", "Con dolore alle spalle tieni i piedi appoggiati."),
        "bulgari" to i("Bulgarian Split Squat", "Split_Squat_with_Dumbbells", "quadricipiti glutei / femorali", 1, "g", "affondi", null,
            listOf("Piede dietro su una sedia.", "Piede davanti lontano.", "Scendi dritto finché la coscia davanti è parallela.", "Risali spingendo col tallone."),
            listOf("Piede davanti troppo vicino", "Busto che cade in avanti"), EXHALE_UP, "Scendi dritto, peso sul piede davanti", "All'inizio tieniti a un muro."),
        "flessioni-sedia" to i("Incline Push-Up", "Incline_Push-Up", "petto tricipiti / spalle", 1, "sw", "flessioni-muro", "flessioni",
            listOf("Mani sul bordo di una sedia ferma.", "Corpo dritto.", "Scendi col petto verso la sedia.", "Spingi su."),
            listOf("Bacino che cade", "Sedia che scivola"), "Inspira scendendo, espira spingendo.", "Corpo dritto", "Appoggia la sedia contro il muro."),
        "flessioni-rialzate" to i("Decline Push-Up", "Decline_Push-Up", "petto spalle / tricipiti addome", 1, "sw", "flessioni", null,
            listOf("Piedi su una sedia.", "Mani a terra sotto le spalle.", "Scendi col petto.", "Spingi su."),
            listOf("Bacino che cade", "Testa che scende prima"), "Inspira scendendo, espira spingendo.", "Corpo dritto", "Difficile: fai prima bene le flessioni a terra."),
    )

    /** La scheda dell'esercizio; per quelli che non hanno scheda propria, una scheda semplice dai dati del catalogo. */
    fun of(id: String): MoveInfo = ALL[id] ?: MoveInfo(id, null, emptySet(), emptySet(), 1, emptySet(), null, null, emptyList(), emptyList(), HOLD_BREATH, "", "")

    /** Indirizzo della foto (free-exercise-db, pubblico dominio): 0 = posizione di partenza, 1 = arrivo. */
    fun photoUrl(photo: String, n: Int) = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/$photo/$n.jpg"

    /** Ricerca dell'esercizio su MuscleWiki (si apre nel browser). */
    fun muscleWikiUrl(en: String) = "https://www.google.com/search?q=" + java.net.URLEncoder.encode("musclewiki $en", "UTF-8")
}

/** La sagoma del corpo, davanti e dietro (riquadro 100×132 ciascuna), divisa nelle zone dei muscoli. */
object MuscleMap {
    /** Una zona: muscolo (vuoto = solo sagoma), dietro o davanti, rettangolo arrotondato o ovale. */
    data class Zone(val key: String, val back: Boolean, val x: Float, val y: Float, val w: Float, val h: Float, val oval: Boolean = false, val r: Float = 4f)

    private fun both(key: String, back: Boolean, x: Float, y: Float, w: Float, h: Float, oval: Boolean = false, r: Float = 4f) =
        listOf(Zone(key, back, x, y, w, h, oval, r), Zone(key, back, 100f - x - w, y, w, h, oval, r))

    private fun common(back: Boolean) = listOf(Zone("", back, 41f, 1f, 18f, 19f, oval = true)) +   // testa
        both("", back, 17f, 76f, 8f, 9f, oval = true) + both("", back, 37f, 127f, 10f, 5f, oval = true)  // mani e piedi

    val ZONES: List<Zone> = common(false) + common(true) + listOf(
        // davanti
        Zone("collo", false, 45f, 18f, 10f, 8f, r = 3f),
        *both("spalle", false, 25f, 24f, 14f, 13f, oval = true).toTypedArray(),
        *both("petto", false, 37f, 26f, 13f, 14f, r = 5f).toTypedArray(),
        *both("bicipiti", false, 22f, 37f, 10f, 19f, oval = true).toTypedArray(),
        *both("avambracci", false, 18f, 56f, 9f, 21f, oval = true).toTypedArray(),
        Zone("addome", false, 42f, 41f, 16f, 25f, r = 5f),
        *both("obliqui", false, 35f, 41f, 7f, 23f, r = 3f).toTypedArray(),
        *both("flessori", false, 37f, 65f, 10f, 7f, r = 3f).toTypedArray(),
        *both("quadricipiti", false, 34f, 71f, 13f, 33f, oval = true).toTypedArray(),
        *both("adduttori", false, 46.5f, 74f, 3.5f, 22f, oval = true).toTypedArray(),
        *both("polpacci", false, 36f, 105f, 10f, 22f, oval = true).toTypedArray(),
        // dietro
        Zone("collo", true, 45f, 18f, 10f, 8f, r = 3f),
        Zone("trapezi", true, 38f, 22f, 24f, 13f, r = 6f),
        *both("spalle", true, 25f, 24f, 14f, 13f, oval = true).toTypedArray(),
        *both("tricipiti", true, 22f, 37f, 10f, 19f, oval = true).toTypedArray(),
        *both("avambracci", true, 18f, 56f, 9f, 21f, oval = true).toTypedArray(),
        *both("dorsali", true, 35f, 34f, 13f, 22f, r = 6f).toTypedArray(),
        Zone("lombari", true, 43f, 50f, 14f, 15f, r = 4f),
        *both("glutei", true, 36f, 63f, 14f, 14f, oval = true).toTypedArray(),
        *both("femorali", true, 34f, 77f, 13f, 27f, oval = true).toTypedArray(),
        *both("polpacci", true, 36f, 105f, 10f, 22f, oval = true).toTypedArray(),
    )
}

