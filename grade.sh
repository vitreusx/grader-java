echo Validation stage

rm -rf $1

if ! (mkdir -p $1/clean_copy && tar xzf $1.tar.gz -C $1/clean_copy)
then
    echo ERROR: unpacking $1
    exit 1
fi

mkdir -p $1/validation
cp -r $1/clean_copy/* $1/validation

if ! (cp Validate.java $1/validation && cd $1/validation && javac -Xlint -Werror Validate.java)
then
    echo ERROR: compiling Validate
    exit 1
fi

if ! (cd $1/validation && java Validate)
then
    echo ERROR: running Validate
    exit 1
fi

echo Grading stage
rm -rf src/main/java/*
mkdir -p src/main/java
cp -r $1/clean_copy/* src/main/java
rm -f src/main/java/concurrentcube/CubeTest.java

if ! (./gradlew clean assemble)
then
    echo ERROR: compiling tests
    exit 1
fi

./gradlew test | tee $1/test_output.txt
cp -r build/reports/tests/test $1/html
python3 compose_report.py | tee $1/report.txt