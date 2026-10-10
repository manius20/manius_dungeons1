# manius_dungeons — ETAP 26 (0.26.0-SNAPSHOT)

**Wygodne planowanie na dowolna godzine oraz codziennie przez TAB i GUI.**

- `/dungeon admin zaplanuj <id> <mechowy|siarkowy|pustynny|mesa> <data|dzis|jutro|codziennie|dni:MONDAY,FRIDAY> <HH:mm> <minuty> [losowo|15-100]`
- W TAB na godzinie mozna wybrac **00:00...23:00** (wszystkie 24 godziny). Reczenie mozna podac tez minuty, np. `14:30`.
- TAB terminu proponuje `dzis`, `jutro`, `codziennie`, siedem dni tygodnia oraz 14 kolejnych dat.
- `/dungeon admin` -> **Harmonogram** -> **ZAPLANUJ DUNGEON** otwiera kreator: daty w stronach po 35 dni (przewijanie bez ograniczenia), cykl codzienny, dni robocze/weekend/pojedynczy dzien tygodnia, motyw, godzina (0-23), czas trwania (30-240 min), pokoje (15-100/losowo), osobne **ZATWIERDZ I ZAPISZ**. W GUI nazwa wydarzenia tworzy sie automatycznie; komenda przyjmuje wlasne ID.
- Harmonogramy sa nadal zatwierdzane przez administratora; `codziennie` oznacza powtarzanie juz zatwierdzonego wydarzenia, nie samowolne generowanie.

**Przyklady**:

```text
/dungeon admin zaplanuj mesa_6 mesa 2026-10-12 06:00 120 30
/dungeon admin zaplanuj mech_dzien mechowy codziennie 14:00 60 losowo
/dungeon admin zaplanuj piatki siarkowy dni:FRIDAY 23:00 120 50
/dungeon admin zaplanuj jutro_pust pustynny jutro 03:30 90 20
```

Testy: `bash scripts/test-offline.sh` (38 testow) i `python3 scripts/check-project.py`.

**UWAGA:** nie zmienia to blokady generatora do osobnego swiata testowego (20 pokoi i maks. 3 wejscia); nie instalowac na produkcyjnym SMP bez zweryfikowania kompilacji z Paper 26.2 i testow na serwerze.

---

# manius_dungeons — ETAP 25 (0.25.0-SNAPSHOT)

**Nowy priorytet: naprawa realnych usterek i zielona kompilacja zamiast małych, kosmetycznych etapów.**

Najważniejsze zmiany: odporne na przepełnienia wyliczenia granic, równomierna kontrola próbek podziemia oraz log błędów Mavena w GitHub Actions.

Instrukcja: [`docs/ETAP25-BUILD.md`](docs/ETAP25-BUILD.md).

**Generowanie na produkcyjnym SMP nadal jest niedozwolone; nie ukończono testu z rzeczywistym Paper 26.2.**

---

# manius_dungeons 0.24.0 — etap 24 (wersja rozwojowa)

**Nie jest to wersja do głównego świata S16 SMP 2.** Fizyczne generowanie pozostaje domyślnie wyłączone, do 20 pokoi i maksymalnie 3 wejść w oddzielnym świecie testowym. Nowy audyt nie daje pozwolenia na budowanie w SMP.

## Nowości

1. Komenda `/dungeon admin ocenpodziemie <id> <RRRR-MM-DD>`: wyłącznie odczytowe próbkowanie faktycznych bloków pod graczem dla dungeona z harmonogramu. Sprawdza podstawową geologię, jaskinie, wodę/lawę, bloki nienaturalne, zakres Y i załadowanie chunków. Nie ładuje chunków i nie zmienia świata.
2. Skan podziemia w **preflight** przed testowym generowaniem: jeśli zbadane próbki nie przejdą kontroli, budowanie zostaje odrzucone przed utworzeniem pracy generatora. **To wstępny filtr:** dalsza walidacja każdego rzeczywiście zmienianego bloku, zapis pełnego snapshotu oraz odzyskiwanie są nadal wymagane.
3. `DungeonPerformanceEstimate` — dolne oszacowanie liczby ticków potrzebnych do jednej fazy zmian, bez obiecywania konkretnego TPS. Przeszło 35 zestawów offline, dodatkowe testy skanera na atrapach API; pełny build Java 25/Paper 26.2 wymaga GitHub Actions.

**Ważne:** Skan próbkuje domyślnie maksymalnie 1024 współrzędne projektu, a nie wszystkie bloki. Nie wykrywa gwarantowanie wszystkich podziemnych budowli, NBT, WorldGuard ani claimów; wymagane są osobne kontrole. Przy prawdziwym generowaniu obowiązują istniejące blokady świata testowego.

Szczegóły: [`docs/ETAP24-TESTY.md`](docs/ETAP24-TESTY.md).

---

# manius_dungeons — ETAP 23 (0.23.0-SNAPSHOT)

**Rzeczywisty audyt 1–3 wejść + wspólna kontrola przed fizycznym testem + dodatkowe testy API-stub.**

- `/dungeon admin ocenwejsc <id> <RRRR-MM-DD>` odczytuje wysokość i teren **każdego** wejścia w załadowanych chunkach, bez zmieniania świata. Po poprawnym audycie pokazuje liczbę planowanych bloków/chunków według faktycznych wysokości i test granic z ochroną 50 bloków.
- Generator testowy używa tego samego skanera 7×7 dla wszystkich szybów; brak naturalnego gruntu, woda, lawa, zbyt strome podejście, przeszkody nad głową lub niewczytane chunki blokują start przed zapisem snapshotu i zmianami bloków.
- **33/33 testy offline** i oddzielny test prawdziwej klasy skanera na atrapach API (w tym dwa różne poziomy terenu). Workflow CI uruchamia oba rodzaje testów.

**Nadal NIE jest to wersja produkcyjna:** 20 pokoi / maks. 3 fizyczne wejścia w *dedykowanym świecie testowym*, domyślnie wyłączone. Pełna kompilacja Paper 26.2 i test odtwarzania terenu w działającej grze nadal do potwierdzenia.

[Dokumentacja etapu 23](docs/ETAP23-TESTY.md)

---

# manius_dungeons — ETAP 22 (0.22.0-SNAPSHOT)

**Trzy zmiany w jednej paczce (tylko dedykowany świat testowy):**

1. **Pełny koszt 1–3 szybów** w `/dungeon admin ocenplan <id> <RRRR-MM-DD>`: symulacja dodaje wszystkie wejścia do zbioru bloków, chunków i oszacowania dysku. Wysokość gruntu każdego szybu jest **symulowana jako jednakowa**; faktyczne powierzchnie trzeba sprawdzić na uruchomionym Paper.
2. **Wspólny audyt wszystkich szybów przed generowaniem**: odrzuca brakujące lub zduplikowane wejścia, nakładające się 7×7 strefy badania, zbyt płytkie/głębokie szyby i lokalizacje poza promieniem 3500 od (0,0). To dodatkowa kontrola po skanowaniu prawdziwego terenu, nadal bez zmian przed zatwierdzeniem planu.
3. **32/32 zestawy testów offline**: nowe testy porównują 1/2/3 szybiki nawet dla planu 100 pokoi oraz osiem przypadków poprawnej/niebezpiecznej lokalizacji.

**Status:** faktyczne budowanie wciąż ograniczone do 20 pokoi i 1–3 wejść, domyślnie wyłączone, WYŁĄCZNIE w osobnym świecie testowym. Brak potwierdzenia pełnej kompilacji Paper 26.2 i prób na serwerze. To nie jest wydanie produkcyjne ani pełna gwarancja odtworzenia danych świata.

[Instrukcja prób i ograniczenia](docs/ETAP22-TESTY.md)

---

# manius_dungeons — ETAP 21 (0.21.0-SNAPSHOT)

**Dopisana rzeczywista geometria 1–3 wejść** w oddzielnym świecie testowym (`safety.test-entrance-count`, domyślnie 1). Każdy szyb ma kontrolę gruntu, promienia 3500, wysokości i wczytanych chunków; wszystkie jego bloki trafiają do jednej kopii terenu. Po restarcie manifest zachowuje współrzędne wszystkich wejść. Markery BlueMap nie nadpisują się wzajemnie, a moby, pułapki i skrzynie omijają pokoje wejściowe.

**30/30 zestawów testów offline przeszło**, w tym nowe próby wielowejściowych planów i snapshotu. **Brak potwierdzenia pełnej kompilacji Paper 26.2 i testów na działającym serwerze**. Nie używaj na głównym SMP; limit 20 pokoi i osobnego świata testowego pozostają w mocy.

[Dokumentacja i scenariusz prób](docs/ETAP21-TESTY.md)

---

# manius_dungeons — ETAP 20 (0.20.0-SNAPSHOT)

**Trzy nowe mechaniki plus kontrola wydania:**

- `/dungeon admin testkomunikat <id> <RRRR-MM-DD> <30|10>` — prywatna kontrola czatu/Title, tylko dla faktycznie przygotowanego dungeona testowego. Nie emituje publicznych wiadomosci i nie zmienia swiata.
- `/dungeon admin podgladwejsc <id> <RRRR-MM-DD> [1-10]` — podglad polaczen i LOKALNYCH wspolrzednych proponowanych wejsc. Glowne wejscie w pokoju 0; 1–10 wejsc **nie jest jeszcze fizycznie budowane** (generator testowy nadal ma jeden szyb).
- Limit spoznionych powiadomien `entrances.test-notice-grace-seconds: 90` (0–300): pominie nieaktualne informacje po wiekszym lagu.
- `python3 scripts/check-project.py` — kontrola zgodnosci `pom.xml`, `plugin.yml` i wpisow testow przed publikacja ZIP/GitHub Actions.

**28/28 zestawow testow offline OK**, w tym 860 wariantow planowania wejsc i 10 scenariuszy opoznien. **Nie potwierdzono jeszcze kompilacji z prawdziwym Paper 26.2** ani testow na uruchomionym serwerze.

**NIE INSTALUJ jako gotowego generatora na glownym SMP.** Budowanie nadal tylko w osobnym swiecie TESTOWYM, domyslnie wylaczone, z limitem 20 pokoi. Szczegoly: [docs/ETAP20-TESTY.md](docs/ETAP20-TESTY.md).

---

# manius_dungeons — ETAP 19 (0.19.0-SNAPSHOT)

**Nowe:** 30 minut przed startem rzeczywiscie przygotowanego dungeona testowego wiadomosc na czacie oraz Title; 10 minut przed startem chat podaje **faktyczna** liczbe wejsc i czas trwania wydarzenia. Oba komunikaty wymagaja `entrances.test-public-announcements: true` (domyslnie false); dodatkowe przelaczniki `test-title-30-minutes` i `test-details-10-minutes` domyslnie true. Komunikaty nie sa wysylane dla samego terminu w kalendarzu. Przy lagach harmonogram nie dubluje przeterminowanych ogloszen.

**Wazne:** obecny generator testowy tworzy tylko **jedno rzeczywiste wejscie**, choc planer moze planowac do dziesieciu. Komunikat nie zawyza tej liczby. Wielowejściowe fizyczne dungeony wymagaja dalszej implementacji.

**Nie uruchamiac na glownym SMP.** Budowanie pozostaje domyslnie wylaczone i ograniczone do dedykowanego swiata testowego; wciaz wymaga zielonej kompilacji Paper 26.2 i testu na kopii swiata.

# manius_dungeons — ETAP 18 (0.18.0-SNAPSHOT)

**Wersja programistyczna; NIE uruchamiać na głównym świecie S16 SMP 2.**

Nowe funkcje tego etapu:

- **Audyt `recoveries/` przy starcie** wykrywający osierocone snapshoty, niedokończone pliki `.tmp`, nieprawidłowe nazwy i dowiązania symboliczne. Każda niezgodność blokuje generowanie na świecie testowym. Wyniki: `/dungeon admin audyt wszystkie`.
- **Bezpieczna polityka restartu:** niekompletne zdarzenia przywracane z kopii, `RESTORED` wymaga dowodu weryfikacji, a `FAILED` czeka na świadomą decyzję administratora zamiast samoczynnie ponawiać nieudaną operację.
- **Ochrona całego dedykowanego świata testowego** przy nierozpoznanym, uszkodzonym recovery (nie obejmuje świata produkcyjnego).
- **Trwalszy zapis manifestów YAML:** plik tymczasowy, synchronizacja na dysk i atomowa podmiana, jeśli system plików ją obsługuje.
- **25/25 testów offline**, w tym 20 scenariuszy cyklu snapshot -> przerwane budowanie -> restart -> rollback -> weryfikacja.

[Instrukcja i ograniczenia etapu 18](docs/ETAP18-TESTY.md).

**Pełna kompilacja Java 25 / Paper 26.2 i test w działającym Minecraft nie zostały wykonane** — wymagają GitHub Actions i osobnego serwera testowego. Limit testowy 20 pokoi pozostaje.

---

# manius_dungeons — ETAP 17 (0.17.0-SNAPSHOT)

**NADAL WERSJA TESTOWA. Nie instaluj jako generatora na głównym świecie S16 SMP 2.**

W tej wersji wykonano trzy prace w jednej paczce:

1. **Weryfikacja po przywróceniu świata:** po fazie przywrócenia BlockData i obsługiwanych kontenerów recovery przechodzi do fazy `VERIFYING`. Sprawdza każdy zapisany blok w świecie oraz — dla obsługiwanych kontenerów — zawartość slotów, blokadę i nazwę. Dopiero pełna zgodność pozwala zapisać `RESTORED`. Przy niezgodności ponawia przywracanie z oryginalnego snapshotu maksymalnie `safety.max-restore-verification-retries` razy (domyślnie 2). Po wyczerpaniu prób zgłasza **FAILED** i zachowuje snapshot i ochronę strefy. Nie oznacza uszkodzonego odtworzenia jako sukces.
2. **Trwalsza diagnostyka po restarcie:** manifest zapisuje liczbę ponowień, ostatnią liczbę niezgodności i `restoration-verified`. Restart podczas przywracania/weryfikacji rozpoczyna odtwarzanie od początku z niezmienionej kopii (licznik prób pozostaje), zamiast bezpodstawnie twierdzić, że świat jest naprawiony. `/dungeon admin audyt <id>` pokazuje te informacje. Stare manifesty bez pola weryfikacji **nie będą deklarować potwierdzonej kontroli**.
3. **Ocena obciążenia 15–100 pokoi:** `/dungeon admin ocenplan <id> <RRRR-MM-DD>` pokazuje limit testowych pokoi/bloków, minimalną liczbę ticków potrzebnych na pojedynczą fazę zmian oraz ostrzeżenia o braku gwarancji bezpieczeństwa. To wyłącznie symulacja; **nadal nie zezwala na fizyczne budowanie powyżej 20 pokoi**.

## Testy

```bash
bash scripts/test-offline.sh
```

Lokalnie przeszło **21/21 zestawów offline** (dwa nowe: `RestoreVerificationPolicySelfTest`, `DungeonTestReadinessSelfTest`). GitHub Actions uruchamia teraz testy offline osobno i następnie `mvn clean verify` na Java 25.

**Nie udało się potwierdzić kompilacji z prawdziwym Paper 26.2 ani uruchomić serwera testowego.** Testy offline nie obejmują interakcji Bukkit, pełnego NBT, fizyki Minecrafta, innych pluginów ani rzeczywistych lagów.

Dokładna instrukcja: [docs/ETAP17-TESTY.md](docs/ETAP17-TESTY.md).

---

# manius_dungeons — ETAP 16 (0.16.0-SNAPSHOT)

Kontynuacja projektu etapu 14. **Nadal wersja rozwojowa i tylko odrębny świat testowy**, nie gotowy plugin na główne SMP.

### Co nowego w etapie 15

- Wymóg potwierdzonej kontroli WorldGuard i S16Countries (fail-closed) przed testowym budowaniem. W razie kolizji lub braku możliwości weryfikacji generator odmawia; jawne wyjątki są możliwe wyłącznie w świadomie konfigurowanym środowisku testowym.
- Ewakuacja poza poziomą strefę ochrony (domyślnie 50 bloków) i limit czasu pracy na tick do 1–25 ms, oprócz istniejącego limitu liczby bloków.
- Test awarii: 252 symulowane przerwania podczas budowania/rollbacku oraz testy naruszenia kopii. Skrypt `scripts/test-offline.sh` uruchamia wszystkie **17** zestawów testów algorytmów bez Paper.

**Ważne:** testy offline przeszły, ale pełnej kompilacji z prawdziwym Paper 26.2 i testów na serwerze NIE wykonano. Nie używaj na głównym SMP. Przeczytaj [docs/ETAP15-TESTY.md](docs/ETAP15-TESTY.md) przed próbami.

---

# manius_dungeons — ETAP 14 (0.14.0-SNAPSHOT)

**WERSJA ROZWOJOWA. NIE INSTALUJ JAKO GOTOWEGO GENERATORA NA GŁÓWNYM ŚWIECIE S16 SMP 2.**

Plugin Java 25 / Paper 26.2, zbudowany na źródłach etapu 10. Etap 11 rozwija **trzy obszary równocześnie**:

## 1. Pułapki — tylko odrębny świat testowy

- Deterministyczne rozmieszczenie w regulowanym procencie pokoi (domyślnie 20%), z pominięciem pokoju wejściowego.
- Dwa warianty: **COBWEB** (utrudnienie ruchu) oraz **MAGMA_BLOCK** (obrażenia przy kontakcie; bez gwarancji śmiertelności).
- Miejsca nie blokują środkowej osi przejścia; unika się sąsiedztwa zaplanowanych skrzyń.
- Zmiany pułapek są częścią tego samego zestawu bloków, z którego **przed budowaniem** sporządzana jest kopia terenu. Odzyskiwanie obejmuje więc również usuwanie pułapek.
- Pułapki fizyczne są **domyślnie wyłączone** i wymagają zgody `traps.enable-test-traps: true` oraz nadal `safety.enable-test-world-mutations: true` w osobnym świecie testowym. Nadal obowiązuje limit 20 pokoi przy budowaniu testowym.

Komenda bez zmian świata:

```text
/dungeon admin podgladpulapki <id> <RRRR-MM-DD>
```

## 2. Kalendarz z wyjątkami

Dodana obsługa anulowania i ponownego przywracania **konkretnej daty** przy wydarzeniu jednorazowym lub tygodniowym, bez usuwania całego cyklu:

```text
/dungeon admin wyjatek piatkowa_mesa 2026-10-16 odwolaj
/dungeon admin wyjatek piatkowa_mesa 2026-10-16 przywroc
```

Wyjątki zapisują się w `schedules.yml` pod `cancelled-dates`; `kalendarz`, `on(day)` i wyszukiwanie terminów pomijają odwołane wystąpienia. Jeśli anulowana data dotyczy **właśnie aktywnego testu**, komenda najpierw żąda bezpiecznego rollbacku, a dopiero potem zapisuje anulowanie. W przypadku błędu rollbacku odmówi odwołania aktywnego wystąpienia.

**Cykliczne wydarzenia nadal oznaczają zapisane terminy, a nie gotową automatyczną generację SMP.**

## 3. Ostrzeżenia o czasie i autozamknięcie *testów*

- Oddzielna, testowalna logika okien czasowych; prawidłowo obsługuje przejście wydarzenia przez północ oraz sekundy przed przypomnieniem.
- Administratorzy otrzymują informację o zbliżającym się terminie startu (domyślnie 30 minut wcześniej), **bez fałszywego ogłaszania otwarcia czy nieistniejących współrzędnych**.
- Gdy test jest fizycznie **ACTIVE** i powiązany z konkretną datą, gracze w jego obszarze oraz administratorzy otrzymują przypomnienia przed końcem z `settings.close-warnings-minutes` (30/15/10/5/1).
- Po upływie czasu aktywny test może automatycznie uruchomić `/dungeon admin anuluj` **w zakresie samego rollbacku**: bez kasowania cyklicznego harmonogramu. Zabezpieczenie `settings.auto-close-active-tests: true` jest domyślnie włączone. Jeśli snapshot/ewakuacja się nie powiodą, plugin zgłosi błąd w logach i nie obiecuje przywrócenia.
- Informacje o braku zaplanowanego wydarzenia (godzinę przed slotem i raz dziennie) działają jak w etapie 10.

**Na głównym SMP nie są generowane, otwierane ani zamykane żadne dungeony.** Nowe bariery, współrzędne i znaczniki BlueMap działają tylko w świecie testowym i tylko dla rzeczywiście wygenerowanych, aktywnych dungeonów. Domyślnie publiczne komunikaty współrzędnych i BlueMap są wyłączone.

## Przydatne komendy

```text
/dungeon admin                        # GUI
/dungeon admin pomoc                 # spis komend; działa TAB
/dungeon admin zaplanuj test mesa 2026-10-17 17:00 60 15
/dungeon admin wyjatek test 2026-10-17 odwolaj
/dungeon admin wyjatek test 2026-10-17 przywroc
/dungeon admin podgladpulapki test 2026-10-17
/dungeon admin podgladmoby test 2026-10-17
/dungeon admin testbuduj test 2026-10-17 potwierdz
/dungeon admin sprawdzkopie test
/dungeon admin anuluj test potwierdz
/dungeon admin wznow test potwierdz
/dungeon admin status
```

`testbuduj` wymaga ręcznej komendy na serwerze **testowym**, załadowanych chunków, przejścia walidacji bezpieczeństwa i osobnych zgód w konfiguracji. Nie używaj powyższego przykładu `testbuduj` na świecie produkcyjnym.

## Testy i granice bezpieczeństwa

- Uruchomiono lokalnie `javac`/`java` **11 samodzielnych testów** (logika niezależna od API Paper): 86 rozmiarów geometrycznych, snapshoty v1/v2/v3, loot, moby/bossowie, zdarzenia czasowe oraz 3576 rozmieszczeń pułapek.
- Poprawność konfiguracji YAML można zweryfikować niezależnie.
- **Nie wykonano jeszcze pełnej kompilacji projektu przeciwko Paper 26.2 ani testów działania na prawdziwym serwerze.** GitHub Actions używa Java 25 i `mvn clean verify`; najpierw uzyskaj zielony build.
- Rollback nie stanowi gwarancji pełnej zgodności wszystkich typów NBT/PDC, danych bytów i zmian innych pluginów. Przed jakimikolwiek testami zrób niezależną kopię całego świata. Nie usuwaj folderu `recoveries/` w razie awarii.

Dalsze braki: bezpieczny produkcyjny generator SMP, fizyczne otwarcia z barierą i znaczniki BlueMap **dla normalnego SMP**, WorldEdit templates, pełny rollback danych świata, pełne GUI kalendarza, integracyjne testy claimów/WorldGuard oraz budowanie dla 100 pokoi w normalnym świecie.

Więcej szczegółów: `docs/ETAP11-TESTY.md`, `docs/ICEHOST-GITHUB.md`, `docs/PLAN-IMPLEMENTACJI.md`.

## Nowości ETAPU 12

- Zabezpieczenie wchodzenia do **aktywnego testu** przed otwarciem i po zamknięciu (przez ruch i teleportację), z uprawnieniem `maniusdungeons.barrier.bypass` dla operatorów.
- `/dungeon admin wejscia` — rzeczywiste współrzędne wejścia dla administratora; przycisk w GUI.
- `/dungeon wejscia` — tylko współrzędne już ujawnionych aktywnych testów (domyślnie od 30 minut przed startem).
- Opcjonalny komunikat publiczny testu `entrances.test-public-announcements` (domyślnie **false**).
- Opcjonalny most BlueMap `entrances.enable-test-bluemap-markers` (domyślnie **false**), znacznik aktualizowany co minutę. Nie tworzy wpisu w BlueMap dla samego harmonogramu.
- Test czystej logiki czasowej `EntranceRevealPolicySelfTest` (13 przypadków).

Instrukcja i ograniczenia: [docs/ETAP12-TESTY.md](docs/ETAP12-TESTY.md).

## Nowości ETAPU 13 (0.13.0-SNAPSHOT)

Wprowadzono **trzy powiązane poprawki bezpieczeństwa testów**:

1. Przypomnienia czasowe uwzględniają opóźnione wywołanie harmonogramu i nie giną przy niewielkim lagu; wcześniejszych wiadomości nie odtwarzamy po starcie serwera.
2. W manifestach recovery przechowywana jest godzina **ostatecznego zakończenia testu**, niezależna od późniejszych zmian `schedules.yml`. Jeśli termin minie przed końcem przygotowania, test nie będzie otwierany; rozpoczęte budowanie przejdzie do przywracania.
3. Podczas zamykania i przywracania obszar wraz z marginesem (domyślnie **50 bloków**) jest chroniony przed typowymi zmianami. Nowa komenda `/dungeon admin audyt <id|wszystkie>` pozwala bez zmian w świecie zbadać fazę, dostępność chunków i kopię terenu. Komenda ma podpowiedzi TAB.

**Nie jest to jeszcze produkcyjny plugin SMP**: aktywne testy nadal pozostają tylko do odczytu (nie zezwalamy graczom na kopanie w testowym obszarze), automatyczne generowanie na świecie SMP nadal nie działa, testów pełnej kompilacji z Paper i restartów rzeczywistego serwera nadal brakuje. Szczegóły i instrukcja: [docs/ETAP13-TESTY.md](docs/ETAP13-TESTY.md).


## Nowości ETAPU 14 (0.14.0-SNAPSHOT)

- Automatyczne przygotowanie **tylko w osobnym świecie testowym**, wyłącznie po włączeniu `safety.test-auto-prepare.enabled` i podaniu współrzędnych punktu testowego w konfiguracji. Nie ładuje nowych chunków.
- Cykl `PREPARED -> ACTIVE -> RESTORING`: konstrukcja może być gotowa wcześniej, ale moby i możliwość wejścia uruchamiają się dopiero o godzinie wydarzenia; wygasły test uruchamia rollback.
- Odrębne ID odzyskiwania dla każdej daty wydarzenia cyklicznego; poprawki anulowania konkretnego wystąpienia i anulowania całego harmonogramu.
- `/dungeon admin przygotowanie <id> <RRRR-MM-DD>` — podgląd terminu, stanu i ID kopii (bez zmian bloków), z podpowiedziami TAB.
- **Limit testu nadal 20 pokoi**, a produkcyjne generowanie SMP wyłączone. Bez potwierdzonej kompilacji Paper 26.2 i testów integracyjnych NIE instaluj na żywym SMP.

Instrukcja etapu, konfiguracja i ograniczenia: [docs/ETAP14-TESTY.md](docs/ETAP14-TESTY.md).


## ETAP 16 — 3 zmiany bezpieczeństwa i wydajności (0.16.0-SNAPSHOT)

1. **Pieczęć współrzędnych snapshotu.** Przed przechwyceniem oryginalnych bloków manifest zapisuje `planned-cell-count` i `planned-coordinates-sha256`. Po zapisie, przed budowaniem, przed awaryjnym wznowieniem i podczas odczytu po restarcie plugin sprawdza, czy snapshot zawiera **dokładnie** planowany zbiór współrzędnych. Nie wystarcza już poprawny SHA-256 samego pliku. Stare manifesty można nadal czytać ratunkowo, lecz mają ostrzeżenie o braku nowej pieczęci.
2. **Podgląd dużych planów bez modyfikacji świata:** `/dungeon admin ocenplan <id> <RRRR-MM-DD>` szacuje liczbę unikalnych bloków wraz z szybem, liczbę chunków, geometrię i konserwatywną rezerwę dysku. Działa także dla 100 pokoi i nie przełącza flag bezpieczeństwa. Położenie referencyjne to miejsce administratora lub 0,0 dla konsoli — to symulacja, a nie certyfikat lokalizacji.
3. **Kontrola ilości wolnego dysku** przed utworzeniem nowego testu: parametr `safety.minimum-free-recovery-bytes` (domyślnie 512 MiB) plus 2048 bajtów na każdy planowany blok. Przy niedoborze miejsca generowanie jest odrzucane przed pierwszą modyfikacją terenu. To nie jest gwarancja, że inny proces nie zapełni dysku później.

19 zestawów testów offline (w tym nowe testy poprawnych, lecz niepełnych snapshotów i geometrii 15–100 pokoi). Uruchamianie: `bash scripts/test-offline.sh`. GitHub Actions automatycznie uruchomi też dwa nowe zestawy przez Maven, jeśli prawdziwa kompilacja Paper się powiedzie.

**Limit fizycznego testu pozostał 20 pokoi**, z możliwością budowania wyłącznie w oddzielnym, nazwanym świecie TEST. Duże konstrukcje do 100 pokoi mają tylko suchy podgląd. Brak potwierdzenia kompilacji z Paper 26.2 i testu na uruchomionym serwerze. Przed wdrożeniem konieczna jest zewnętrzna kopia całego świata. Nie uruchamiać na produkcyjnym SMP.

Instrukcja i checklist: `docs/ETAP16-TESTY.md`.
