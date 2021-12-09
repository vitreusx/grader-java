out=$(mktemp)
err=$(mktemp)
pwd="$(pwd)"

name="$(basename "$solution")"
sol_dir="$1"

rm -rf "$sol_dir/html"

rm -rf src/main/java/
rsync -r "$sol_dir/test/" src/main/java
rm -f src/main/java/concurrentcube/CubeTest.java

./gradlew clean assemble 1>"$out" 2>"$err"
./gradlew test 1>"$out" 2>"$err"
rsync "$out" "$sol_dir/test-results.out"
rsync "$err" "$sol_dir/test-results.err"
rsync -r build/reports/tests/test/ "$sol_dir/html"
rsync -r build/test-results/test/ "$sol_dir/xml"
python3 compose_report.py "$name" >"$sol_dir/report.md"
touch "$sol_dir/.tested"