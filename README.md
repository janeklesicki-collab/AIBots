# AI Boty (krok 1)

Mod Fabric 1.21.4 (tylko klient). Klawisz **G** (do zmiany w Sterowaniu, kategoria "Boty AI") otwiera menu:
- wpisz nick (puste = losowy) i ewentualnie komendy po wejściu, rozdzielone średnikiem, np. `/register haslo haslo;/login haslo`
- **Dodaj bota** wysyła bota na serwer, na którym aktualnie jesteś
- przy każdym bocie: przełącznik chodzenia i usuwanie

Boty działają w samym modzie (bez dodatkowych programów), tylko na serwerach non-premium (offline-mode).

## Jak zbudować
1. Wrzuć zawartość tego folderu na GitHub (razem z folderem `.github`).
2. Zakładka **Actions** -> przebieg "build" -> na dole **Artifacts** -> `aibots-mod` (w środku jest plik `.jar`).
3. Plik `.jar` do folderu `mods` razem z **Fabric API 0.119.2+1.21.4**.

## Co w kroku 1 jeszcze nie działa
Bot nie widzi świata (nie czyta chunków), więc tylko stoi albo chodzi na ślepo po prostej. Kopanie, craft, nawigacja, cele i AI to kolejne kroki.
Numery pakietów są w `core/Ids.java`, gdyby serwer wyrzucał bota z błędem protokołu.
