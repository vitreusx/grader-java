find results -name perform_tests | xargs rm -rf
rm -rf errors/perform_tests.txt errors/not_to_test.txt
ONLY_TEST=1 ./grade-all.sh