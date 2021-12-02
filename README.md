# Java assignment testing framework

## Usage

Entering a following command:
```shell
./grade.sh xy123456
```
in the project root directory, given the presence of `xy123456.tar.gz` in it as well, 
will perform validation, compile and run tests and create reports.

## Output

The output consists of a directory `xy123456/`, in which one can find:

- directory `clean_copy`, with just the contents of the archive;
- directory `validation`, used for running `Validate.java`;
- directory `html` with an HTML report of the test results;
- text file `test_output.txt` with JUnit output, just in case;
- text file `report.txt` generated by `compose_report.py`, at the moment it contains
    the number of points and list of which test suites have passed and which have failed.