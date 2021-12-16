stdout=$(mktemp)
stderr=$(mktemp)

mkdir -p errors

name_check() {
    if ! [[ "$name" =~ ^[a-z]{2}[0-9]{6}\.tar\.gz$ ]]; then
        return 1
    else
        mkdir -p "$output_dir"
        rsync "$input_dir/$name" "$output_dir/renamed.tar.gz"
        return 0
    fi
}

unpacking() {
    work_dir=$(mktemp -d)
    if [[ -f "$input_dir/renamed.tar.gz" ]]; then
        if ! (tar xzf "$input_dir/renamed.tar.gz" -C $work_dir 1>$stdout 2>$stderr); then
            rm -rf $work_dir
            return 1
        else
            mkdir -p "$output_dir"
            rsync -r "$work_dir/" "$output_dir"
            rm -rf $work_dir
            return 0
        fi
    else
        rm -rf $work_dir
        return 1
    fi
}

val_compile() {
    work_input=$(mktemp -d)
    rsync -r "$input_dir/" "$work_input"
    rsync Validate.java "$work_input"

    if ! (cd "$work_input" && javac -Xlint -Werror Validate.java 1>$stdout 2>$stderr); then
        rm -rf $work_input
        return 1
    else
        mkdir -p "$output_dir"
        rsync -r "$work_input/" "$output_dir"
        rm -rf $work_input
        return 0
    fi
}

validate() {
    if ! (cd "$input_dir" && java Validate 1>$stdout 2>$stderr); then
        return 1
    else
        mkdir -p "$output_dir"
        rsync -r "$input_dir/" "$output_dir"
        return 0
    fi
}

assemble() {
    rm -rf build
    rm -rf src/main/java
    mkdir -p src/main
    rsync -r "$input_dir/" src/main/java
    rm -rf src/main/java/concurrentcube/CubeTest.java

    if ! (./gradlew clean assemble 1>$stdout 2>$stderr); then
        return 1
    else
        mkdir -p "$output_dir"
        rsync -r "$input_dir/" "$output_dir"
        return 0
    fi
}

perform_tests() {
    rm -rf build
    rm -rf src/main/java
    mkdir -p src/main
    rsync -r "$input_dir/" src/main/java
    rm -rf src/main/java/concurrentcube/CubeTest.java
    
    timeout --foreground --verbose --signal=SIGINT 60 ./gradlew clean test 1>$stdout 2>$stderr
    STATUS=$?
    if [[ "$STATUS" -eq "124" ]]; then
        return 1
    else
        mkdir -p $output_dir
        rsync $stdout "$output_dir/results.out"
        rsync $stderr "$output_dir/results.err"
        rsync -r build/reports/tests/test/ "$output_dir/html"
        rsync -r build/test-results/test/ "$output_dir/xml"
        python3 compose_report.py "$output_dir/xml" >"$output_dir/report.md"
        return 0
    fi
}

exec_pass() {
    pass_name="$1"
    error_msg="$2"
    pass_dir="$sol_dir/$pass_name"

    input_dir="$pass_dir/input"
    output_dir="$pass_dir/output"

    if ! ($pass_name); then
        touch "$sol_dir/errors.txt"
        echo "error: $error_msg" | tee -a "$sol_dir/errors.txt"
        rsync "$stdout" "$pass_dir/stdout"
        rsync "$stderr" "$pass_dir/stderr"
        
        touch errors/${pass_name}.txt
        echo "$name" >>errors/${pass_name}.txt

        if [[ -d "$pass_dir/fixed-input" ]]; then
            input_dir="$pass_dir/fixed-input"
            output_dir="$pass_dir/fixed-output"

            if ! ($pass_name); then
                echo "[admin] \"fixed\" solution for [$name], pass [$pass_name] not fixed!"
                rsync "$stdout" "$pass_dir/fixed.stdout"
                rsync "$stderr" "$pass_dir/fixed.stderr"
                return 1
            else
                touch "$pass_dir/.allowed"
                return 0
            fi
        elif [[ -f "$pass_dir/.allowed" ]]; then
            return 0
        else
            return 1
        fi
    else
        touch "$pass_dir/.passed"
        return 0
    fi
}

copy_from() {
    from_pass="$1"
    to_pass="$2"
    if [[ -d "$sol_dir/$from_pass/output" ]]; then
        mkdir -p "$sol_dir/$to_pass/input"
        rsync -r "$sol_dir/$from_pass/output/" "$sol_dir/$to_pass/input"
    elif [[ -d "$sol_dir/$from_pass/fixed-output" ]]; then
        mkdir -p "$sol_dir/$to_pass/input"
        rsync -r "$sol_dir/$from_pass/fixed-output/" "$sol_dir/$to_pass/input"
    fi
}

completed() {
    pass="$1"
    if [[ -f "$sol_dir/$pass/.passed" || -f "$sol_dir/$pass/.allowed" ]]; then
        return 0
    else
        return 1
    fi
}

for solution in payload/*; do
    name="$(basename "$solution")"
    sol_dir="results/$name"
    echo "[$name]"

    mkdir -p "$sol_dir"

    if [[ (! $(completed "assemble"; echo $?) -eq 0 || ! -z "$RECHECK") && -z "$ONLY_TEST" ]]; then
        mkdir -p "$sol_dir/name_check/input"
        if [[ ! -f "$sol_dir/name_check/input/$name" ]]; then
            rsync -r "$solution" "$sol_dir/name_check/input"
        fi

        rm -f "$sol_dir/errors.txt"
        if exec_pass "name_check" "invalid name"; then
            copy_from "name_check" "unpacking"
            if exec_pass "unpacking" "unpacking error"; then
                copy_from "unpacking" "val_compile"
                if exec_pass "val_compile" "compiling Validate"; then
                    copy_from "val_compile" "validate"
                    if exec_pass "validate" "running Validate"; then
                        copy_from "validate" "assemble"
                        exec_pass "assemble" "assembling tests"
                    fi
                fi
            fi
        fi
    fi

    if ! completed "assemble"; then
        touch errors/not_to_test.txt
        echo "$name" >>errors/not_to_test.txt
    elif [[ (! -f "$sol_dir/perform_tests/.passed" || ! -z "$RETEST") && -z "$ONLY_VALIDATE" ]]; then
        copy_from "assemble" "perform_tests"
        exec_pass "perform_tests" "performing tests"
    fi
done
