# Benessere e cibo

App Android per il cibo, la cucina e la salute, senza AI: i dati restano sul telefono.

- **Diario** del cibo (a mano, con codice a barre da Open Food Facts, o rimettendo un cibo mangiato di recente), calorie e macro del giorno
- **Acqua** con obiettivo e bicchiere a scelta
- **Fabbisogno** di calorie (Mifflin-St Jeor), BMI, obiettivo dell'acqua
- **Ricette**: scritte a mano, o salvate da Giallo Zafferano e da altri siti (la ricetta si legge dalla pagina, formato schema.org), con **modalità cucina** (passo per passo, schermo acceso, lettura a voce)
- **Menu della settimana** e **lista della spesa** (dalle ricette e dal menu; si condivide con altre app o con Web Launcher)
- **Peso e misure** (peso con BMI, pressione, glicemia, battito, temperatura, saturazione, vita, fianchi) con grafico; il peso alimenta il fabbisogno di calorie
- **Farmaci** con orari, promemoria, scorte che scendono quando segni la dose
- **Visite** con promemoria la sera prima e due ore prima
- **Scheda d'emergenza**, a scelta visibile anche a schermo bloccato
- **Referto per il medico** in PDF
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
- Niente promemoria per bere (per ora). Niente passi, sonno e allenamento (fasi successive).
- I prodotti di Open Food Facts li inseriscono gli utenti: controlla sempre l'etichetta.
