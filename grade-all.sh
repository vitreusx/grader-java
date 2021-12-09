out=$(mktemp)
err=$(mktemp)
pwd="$(pwd)"

rm -r to_check
mkdir -p to_check
touch to_check/name-errors.txt
touch to_check/unpacking-errors.txt
touch to_check/compile-errors.txt
touch to_check/validate-errors.txt
touch to_check/compiling-tests.txt

for solution in payload/*; do
    name="$(basename "$solution")"

    sol_dir="results/$name"
    mkdir -p "$sol_dir"
    rsync "$solution" "$sol_dir"

    log="$sol_dir/log.txt"
    touch "$log"
    cat /dev/null >"$log"

    echo "[$name]"
    if [[ ! -f "$sol_dir/.can_test" || ! -z "$CHECK_ALL" ]]; then
        if ! [[ "$name" =~ ^[a-z]{2}[0-9]{6}\.tar\.gz$ ]]; then
            echo "error: bad name and/or format" | tee -a "$log"
            echo "$name" >>to_check/name-errors.txt
        else
            if ! [[ -f "$sol_dir/renamed.tar.gz" ]]; then
                rsync "$sol_dir/$name" "$sol_dir/renamed.tar.gz"
            fi
            touch "$sol_dir/.can_unpack"
        fi
        
        if [[ -f "$sol_dir/.can_unpack" || ! -z "$CHECK_ALL" ]]; then
            unpack=$(mktemp -d)
            mkdir -p "$sol_dir/unpacked"
            if ! (tar xzf "$sol_dir/renamed.tar.gz" -C "$unpack" 1>"$out" 2>"$err"); then
                echo "error: unpacking" | tee -a "$log"
                echo "$name" >>to_check/unpacking-errors.txt
                rsync "$out" "$sol_dir/unpacking.out"
                rsync "$err" "$sol_dir/unpacking.err"
            else
                if ! [[ -d "$sol_dir/unpacked" ]]; then
                    rsync -r "$unpack/" "$sol_dir/unpacked"
                fi
                touch "$sol_dir/.can_compile"
            fi
        fi

        if [[ -f "$sol_dir/.can_compile" || ! -z "$CHECK_ALL" ]]; then
            if ! [[ -d "$sol_dir/compile" ]]; then
                rsync -r "$sol_dir/unpacked/" "$sol_dir/compile"
                rsync Validate.java "$sol_dir/compile"
            fi

            if ! (cd "$sol_dir/compile" && javac -Xlint -Werror Validate.java 1>"$out" 2>"$err"); then
                echo "error: compiling Validate" | tee -a "$log"
                echo "$name" >>to_check/compile-errors.txt
                rsync "$out" "$sol_dir/compile.out"
                rsync "$err" "$sol_dir/compile.err"
            else
                if ! [[ -d "$sol_dir/validate" ]]; then
                    rsync -r "$sol_dir/compile/" "$sol_dir/validate"
                fi
                touch "$sol_dir/.can_validate"
            fi
        fi

        if [[ -f "$sol_dir/.can_validate" || ! -z "$CHECK_ALL" ]]; then
            if ! [[ -d "$sol_dir/validate" ]]; then
                rsync -r "$sol_dir/compile/" "$sol_dir/validate"
            fi
            if ! [[ -f "$sol_dir/validate/Validate.class" ]]; then
                rsync Validate.java "$sol_dir/validate"
                (cd "$sol_dir/validate" && javac -Xlint -Werror Validate.java )
            fi

            if ! (cd "$sol_dir/validate" && java Validate 1>"$out" 2>"$err"); then
                echo "error: running Validate" | tee -a "$log"
                echo "$name" >>to_check/validate-errors.txt
                rsync "$out" "$sol_dir/validate.out"
                rsync "$err" "$sol_dir/validate.err"
            else
                if ! [[ -d "$sol_dir/test_compile" ]]; then
                    rsync -r "$sol_dir/validate/" "$sol_dir/test_compile"
                fi
                touch "$sol_dir/.can_compile_tests"
            fi
        fi

        if [[ -f "$sol_dir/.can_compile_tests" || ! -z "$CHECK_ALL" ]]; then
            rm -rf src/main/java/
            rsync -r "$sol_dir/test_compile/" src/main/java
            rm -f src/main/java/concurrentcube/CubeTest.java

            if ! (./gradlew clean assemble 1>"$out" 2>"$err"); then
                echo "error: compiling tests" | tee -a "$log"
                echo "$name" >>to_check/compiling-tests.txt
                rsync "$out" "$sol_dir/compile-tests.out"
                rsync "$err" "$sol_dir/compile-tests.err"
            else
                if ! [[ -d "$sol_dir/test" ]]; then
                    rsync -r "$sol_dir/test_compile/" "$sol_dir/test"
                fi
                touch "$sol_dir/.can_test"
            fi
        fi
    fi

    if [[ -f "$sol_dir/.can_test" && ! -f "$sol_dir/.tested" && -z "$ONLY_VALIDATE" ]]; then
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
    fi
done