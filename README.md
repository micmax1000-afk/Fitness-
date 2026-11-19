# Benessere e cibo

App Android per il cibo, la cucina e la salute, senza AI: i dati restano sul telefono.

- **Diario** del cibo (a mano, con codice a barre da Open Food Facts, o rimettendo un cibo mangiato di recente), calorie e macro del giorno
- **Acqua** con obiettivo, bicchiere a scelta e promemoria per bere (5 al giorno)
- **Fabbisogno** di calorie (Mifflin-St Jeor), BMI, obiettivo dell'acqua
- **Ricette**: scritte a mano, o salvate da Giallo Zafferano e da altri siti (la ricetta si legge dalla pagina, formato schema.org), con **modalità cucina** (passo per passo, schermo acceso, lettura a voce)
- **Menu della settimana** e **lista della spesa** (dalle ricette e dal menu; si condivide con altre app o con Web Launcher)
- **Peso e misure** (peso con BMI, pressione, glicemia, battito, temperatura, saturazione, vita, fianchi) con grafico; il peso alimenta il fabbisogno di calorie
- **Farmaci** con orari, promemoria, scorte che scendono quando segni la dose
- **Visite** con promemoria la sera prima e due ore prima
- **Scheda d'emergenza**, a scelta visibile anche a schermo bloccato
- **Referto per il medico** in PDF
- **Passi e sonno** da Health Connect (l'app dell'orologio li condivide col telefono): obiettivo dei passi, sonno con fasi, promemoria per andare a letto, sonno inserito a mano
- **Allenamento**: schede (serie, ripetizioni contate in automatico a ritmo regolabile o toccando, riposo con cronometro, bip e voce), Tabata e intervalli, allenamenti inseriti a mano, obiettivo settimanale con serie di settimane
- **Crea una scheda**: scegli stretching, mobilità, tutto il corpo, gambe, parte alta, addominali, cardio o schiena; durata e livello. L'app compone la scheda dal suo catalogo (riscaldamento, esercizi, allungamenti finali), sul telefono e senza internet; «Un'altra» la rifà diversa
- **Esercizi con il disegno**: oltre 40 esercizi con l'omino animato che mostra il movimento e la spiegazione; il disegno compare anche durante la scheda
- **Home «Oggi»** con tre anelli (calorie, acqua, passi), sonno, allenamenti della settimana e farmaci da prendere
- **Tema** chiaro, scuro o come il telefono; icone disegnate dall'app, uguali su tutti i telefoni
- **Esporta / Importa**: un file con tutto. «Importa» legge anche i dati di Web Launcher (stessi nomi dei campi)

## Costruzione

```
gradle testDebugUnitTest assembleDebug
```

Su GitHub la costruzione parte a ogni modifica; sul ramo `main` crea la release con l'APK.
Per aggiornamenti installabili sopra la versione precedente servono i secret
`CIBO_KEYSTORE_BASE64` e `CIBO_KEYSTORE_PASSWORD` (alias `weblauncher`).

## Limiti

- Niente foto del piatto né del frigo (servono l'AI): restano in Web Launcher.
Niente GPS né fascia cardio nell'allenamento. I passi vengono da Health Connect (serve un orologio o un'app che li conta).
- I prodotti di Open Food Facts li inseriscono gli utenti: controlla sempre l'etichetta.
