# MlWidget

Tutti i widget delle **MLapps** in un'app sola, divisi per categorie e personalizzabili al massimo.

## Cosa si può fare
- Aprire l'app e vedere tutti i widget: **Orologi, Meteo, Sistema, Note e strumenti**
- Toccare un widget per modificarlo:
  - **spostare gli elementi col dito** nell'anteprima (si agganciano al centro)
  - grandezza, larghezza, allineamento, colore e visibilità di ogni elemento
  - **quadrante e stile delle lancette** degli orologi analogici, lancetta dei secondi
  - carattere dei numeri
  - colori dal **sistema (Material You)**, dallo **sfondo del telefono** o **personali**
  - **chiaro/scuro automatico** (segue la modalità scura del telefono) o fisso
  - sfondo: tonale, neutro, pieno, vetro, nessuno; opacità e angoli
- **Aggiungi alla Home** direttamente dall'editor, oppure dalla Home: *Widget → MlWidget* e si sceglie quale mostrare
- Le modifiche si vedono subito sui widget già messi sulla Home

## Da dove arrivano i widget
| Widget | App |
|---|---|
| Orologio, Orologio verticale, Solo testo, Analogico, Analogico con data | MLauncher |
| Orologio Sensation, Orologio a cartellini | MLauncher (tema Sensation) |
| Meteo, Meteo quadrato | MLauncher |
| Meteo mini, Meteo completo, Meteo con dettagli, Pioggia in arrivo | MLMeteo |
| Batteria, Batteria larga, Batteria a barra | MLauncher |
| Nota veloce, Barra di ricerca | MLauncher |

## Come funziona
- Ore e lancette sui widget sono **orologi di sistema**: si muovono da soli senza consumare batteria.
- Il resto è un'immagine ridisegnata ogni 15 minuti (meteo, batteria), a mezzanotte (data) e a ogni modifica.
- Chiaro e scuro: Android sceglie da solo la versione giusta del widget quando cambia la modalità scura.
- Meteo da Open-Meteo; la posizione si aggiorna quando l'app è aperta (niente posizione in background).
- Android 12 o successivo. Tema grafico: [MLTheme](https://github.com/marco90-cyber/MLTheme).

## Build
Ogni push su `main` crea l'APK firmato con GitHub Actions e lo pubblica nelle **Release**.
Per firmarlo con la propria chiave servono i Secrets `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (come nelle altre MLapps); senza, l'APK usa la firma di debug.
