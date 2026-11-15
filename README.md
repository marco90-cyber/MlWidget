# MlWidget

Widget per la Home divisi per stile (Material You, iOS, One UI, HyperOS, Nothing, Sense, Retrò, Minimal), che si adattano alla grandezza e si personalizzano al massimo.

## Cosa si può fare
- Aprire l'app e vedere tutti i widget divisi per stile
- Toccare un widget per modificarlo:
  - scegliere quali parti mostrare (data, località, ore, giorni, appuntamenti, copertina...)
  - grandezza del testo
  - **quadrante e stile delle lancette** degli orologi analogici, lancetta dei secondi
  - carattere dei numeri
  - colori dal **sistema (Material You)**, dallo **sfondo del telefono** o **personali**
  - **chiaro/scuro automatico** (segue la modalità scura del telefono) o fisso
  - sfondo: tonale, neutro, pieno, vetro, nessuno; opacità e angoli
- **Aggiungi alla Home** direttamente dall'editor, oppure dalla Home: *Widget → MlWidget* e si sceglie quale mostrare
- Le modifiche si vedono subito sui widget già messi sulla Home

## I widget
| Stile | Widget |
|---|---|
| Material You | Orologio, Orologio analogico, In breve, Meteo, Batteria, Calendario, Musica, Dispositivo, Nota, Ricerca |
| iOS | Orologio, Meteo, Batterie, Calendario, Musica, Note |
| One UI | Orologio, Meteo, Batteria, Calendario, Musica |
| HyperOS | Orologio, Orologio analogico, Meteo, Batteria (a riempimento), Calendario, Note |
| Nothing | Orologio, Orologio analogico, Meteo, Batteria, Cuffie e accessori, Passi, Citazione del giorno, Calendario, Ricerca |
| Sense | Orologio Sensation, Cartellini con meteo, Cartellini |
| Retrò | Orologio a cartellini, Sveglia LCD, iPod, Calendario a strappo |
| Minimal | Ora, Lancette, Meteo in una riga, Batteria a linea, Musica in una riga |

Ogni widget **si adatta da solo** alla grandezza (2×1, 2×2, 4×1, 4×2, 4×3...): nell'editor si sceglie quali parti mostrare e si prova l'anteprima in tutte le grandezze.

## Come funziona
- Ore e lancette sui widget sono **orologi di sistema**: si muovono da soli senza consumare batteria.
- Batteria: si ridisegna quando cambia (e comunque ogni 10 minuti). Meteo: ogni 15/30/60 minuti, o toccando il widget. Calendario: quando cambia un appuntamento. Musica: a ogni cambio di brano (serve l'accesso alle notifiche).
- Chiaro e scuro: Android sceglie da solo la versione giusta del widget quando cambia la modalità scura.
- Meteo da Open-Meteo; la posizione si aggiorna quando l'app è aperta (niente posizione in background).
- Android 12 o successivo. Tema grafico: [MLTheme](https://github.com/marco90-cyber/MLTheme).

## Build
Ogni push su `main` crea l'APK firmato con GitHub Actions e lo pubblica nelle **Release**.
Per firmarlo con la propria chiave servono i Secrets `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (come nelle altre MLapps); senza, l'APK usa la firma di debug.

## Crediti
- Meteo: [Open-Meteo](https://open-meteo.com) (CC BY 4.0)
- Carattere a puntini: [Doto](https://github.com/oliverlalan/Doto), SIL Open Font License (vedi `LICENSE-Doto-OFL.txt`)
