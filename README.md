# Zadanie I z Javy - Testy

## Repo
Testy znajdują się na repo `https://github.com/vitreusx/grader-java`. Suita testów JUnit znajduje się w pliku 
`src/test/java/concurrentcube/CubeTest.java`. Używany jest także package `src/test/java/solution`, będący 
rozwiązaniem zadania (zwany dalej "referencyjnym") używanym w ramach testów. **Uwaga: nie jest to rozwiązanie 
wzorcowe, tylko część implementacji testów.**

## Opis testów

Jest 14 testów, razem wartych 7 punktów.

- Proste testy obsługi rotate (`RotateTestsLite`, 0.5 pkt): jeden lub dwa wątki wykonują operację `rotate` przez 
  jakiś czas, na koniec testujemy czy stan jest taki sam jak dla referencyjnego rozwiązania symulującego te same 
  operacje.
- Pełne testy obsługi rotate (`RotateTestsFull`, 0.5 pkt): wariant powyższych testów z większą ilością wątków.
- Proste testy obsługi rotate i show (`BothOpsTestsLite`, 0.5 pkt): jeden lub dwa wątki wykonują albo `rotate` albo 
  `show` przez jakiś czas, na koniec testujemy czy stan jest taki sam jak dla referencyjnego rozwiązania.
- Pełne testy obsługi rotate i show (`BothOpsTestsFull`, 0.5 pkt): wariant powyższych testów z większą ilością wątków.
- Podstawowa obsługa przerwań (`InterruptionTestsLite`, 1 pkt): tworzymy trzy wątki, z czego dwa wykonują `rotate(0, 
  0)`, a trzeci `show()`. Uruchamiamy pierwszy i blokujemy go na `beforeRotation`, po czym uruchamiamy drugi wątek. 
  Sprawdzamy czy jest on zablokowany (i.e. czeka na swoją kolej), po czym przerywamy go. Następnie sprawdzamy, czy 
  został on faktycznie przerwany - dokładniej, czy czeka na barierze ustawionej w bloku `catch 
  (InterruptedException`). Podobnie postępujemy z trzecim wątkiem.
- Pełna obsługa przerwań (`InterruptionCorrectnessFull`, 1 pkt): tworzymy wiele wątków, i z głównego wątku 
  przerywamy je. Na koniec sprawdzamy, czy stan kostki jest poprawny.
- Równoległe wykonywanie dozwolonych operacji (I) (`ParallelExec1`, 0.3 pkt): sprawdzamy, czy operacje `show` dają 
  się uruchamiać współbieżnie, co sprawdzamy w następujący sposób: tworzymy dwa wątki, uruchamiamy je i 
  sprawdzamy czy oba czekają na barierze ustawionej w `beforeShow`. Ponawiamy podobną procedurę z `afterShow`.
- Równoległe wykonywanie dozwolonych operacji (II) (`ParallelExec2`, 0.3 pkt): podobnie jak wyżej, tylko z 
  operacjami `rotate` dla tej samej strony i różnych warstw.
- Równoległe wykonywanie dozwolonych operacji (III) (`ParallelExec3`, 0.4 pkt): podobnie jak wyżej, tylko z 
  operacjami `rotate` dla tej samej osi i różnych warstw (np. `rotate(0, 0)` i `rotate(5, 0)`).
- Sekwencyjne wykonywanie wykluczających się operacji (I) (`SeqExec1`, 0.25 pkt): sprawdzamy, czy operacje `rotate(0,
  0)` i `show()` nie mogą być wykonane współbieżnie. W tym celu tworzymy dwa wątki, uruchamiamy 
  jeden z nich, blokujemy go na `beforeRotate`, uruchamiamy drugi i sprawdzamy czy nie może on dojść do tej samej 
  bariery.
- Sekwencyjne wykonywanie wykluczających się operacji (II) (`SeqExec2`, 0.25 pkt): podobnie jak wyżej, tylko oba 
  wątki wykonują `rotate` dla różnych osi.
- Sekwencyjne wykonywanie wykluczających się operacji (III) (`SeqExec3`, 0.25 pkt): podobnie jak wyżej, tylko z 
  `rotate` dla tej samej strony i warstwy.
- Sekwencyjne wykonywanie wykluczających się operacji (IV) (`SeqExec4`, 0.25 pkt): podobnie jak wyżej, tylko z 
  `rotate` dla ten samej *osi* i warstwy (np. dla kostki z `size=3`, `rotate(0, 0)` i `rotate(5, 2)` operują na tej 
  samej warstwie).
- Żywotność rozwiązania (`LivelinessTests`, 1 pkt): sprawdzamy czy wątki nie mogą być zagłodzone. W tym celu 
  tworzymy dwie grupy wątków: "zapełniacze" i "opóźnione". Tworzymy i startujemy te pierwsze (wykonują one operacje 
  w pętli nieskończonej), czekamy chwilę aż dostęp do kostki będzie nasycony, po czym tworzymy i startujemy te 
  drugie (one owej pętli nie mają), i sprawdzamy czy są one w stanie zakończyć pracę (czyli uzyskać dostęp do kostki)
  w "sensownym czasie".

## Reklamacje

Swoje rozwiązanie można sprawdzić tworząc folder `payload/`, wrzucając tam swoje rozwiązanie i wykonując `./grade-all.sh`.
Wyniki znajdą się w folderze `results/`. Skrypt testujący działa następująco - dla każdego rozwiązania w `payload/`, 
jest wykonywana procedura testowania składająca się z 5 faz (*passes*):

- `name_check`: sprawdzanie nazwy;
- `unpacking`: wypakowywanie za pomocą `tar xzf`;
- `val_compile`: wykonanie `javac -Xlint -Werror Validate.java`;
- `validate`: wykonanie `java Validate`;
- `assemble`: wykonanie `./gradlew clean assemble`;
- `perform_tests`: wykonanie `./gradlew :test --tests "concurrentcube.CubeTest\$${test}"` z timeoutem dla każdego z 14 
  testów.

Dla każdego z nich jest tworzony odpowiedni folder `results/${solution}/${pass_name}` (np. `results/sol.tar.
  gz/perform_tests`). W owym folderze znajdują się foldery `input/` i `output/`. Jeżeli wykonanie fazy się nie 
  powiodło, `stderr` i `stdout` mogą zawierać odpowiednie komunikaty. Aby manualnie naprawić rozwiązanie w zakresie 
  danej fazy, są dostępne dwie opcje:

- można zmienić zawartość `input/` i wrzucić poprawioną wersję do `fixed_input/`, po czym skrypt automatycznie 
  sprawdzi owe poprawione rozwiązanie;
- można stworzyć folder `fixed_output/` i stworzyć w folderze fazy plik `.allowed`.

Końcowym wynikiem całego skryptu jest folder `results/(xy123456)/perform_tests/output`, w którym znajdują się:

- plik `report.txt` ze skompilowanym raportem;
- folder dla każdego z 14 testów, w którym znajdują się:
  - pliki `out` i `err` z (odpowiednio) standardowym wyjściem i strumieniem błędów;
  - folder `html` z raportem dla danego testu w formie HTML.

Skrypt można kontrolować następującymi zmiennymi środowiskowymi:

- `RECHECK`: standardowo rozwiązania, które przejdą fazę przed testowaniem (dokładniej, fazę `assemble`), nie są pod 
  tym kątem znowu sprawdzane - ustawienie tej zmiennej na coś niepustego zmieni to zachowanie;
- `ONLY_TEST`: jeżeli niepuste, skrypt nie przeprowadzi walidacji rozwiązań;
- `RETEST`: standardowo rozwiązania, które przejdą fazę `perform_tests`, nie są znowu testowane - ustawienie tej 
  zmiennej na coś niepustego to zmieni;
- `ONLY_VALIDATE`: jeżeli niepuste, skrypt nie przeprowadzi testów rozwiązań.

Zmienne, które można tweakować, aby sprawdzić czy nie doszło do pomyłki w testowaniu to:

- timeout `TIMEOUT` w skrypcie `./grade-all.sh` - i.e. timeout w sekundach dla wykonania każdego z 14 testów;
- timeouty i inne zmienne na górze klasy `CubeTest` w `src/test/java/concurrentcube/CubeTest.java`, w szczególności 
  `multiplier`. 