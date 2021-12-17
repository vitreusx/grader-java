# Java Assignment Testing

## Tests

There are 7 test suites, each worth 1 point if all of the constituent tests pass:

- `RotateTests`: we run `rotate` and check if the final state matches the reference implementation;
- `CorrectnessOfBoth`: we run `rotate` and `show` and check if the intermediate states, as well as the final one 
  match the reference implementation;
- `InterruptionCorrectnessTests`: here we do a version of `CorrectnessOfBoth` where the main thread continuously
  interrupts the worker threads;
- `InterruptingWaitingThreads`: here we check if the interrupted threads are *actually interrupted*. This we do by 
  simply interrupting a thread and checking if it has reached the exit barrier.
- `ParallelExecutionTests`: here we check whether nonconflicting operations (say, `rotate` on different layers of the 
  same axis or multiple `show` operations) can be performed in parallel, by running two threads and checking if both 
  can reach `beforeX`/`afterX` callbacks;
- `SequentialityTests`: here we check whether conflicting operations are indeed mutually exclusive, by running one 
  thread, having it reach the callback (and stop at it with a barrier), and running another one and ensuring that it
  has *not* reached the callback;
- `LivelinessTests`: here we check whether threads can or cannot be starved. We launch two sets of threads: "filler" 
  threads which are supposed to prevent execution, and "late" threads for which we test if they can run. The filler
  threads are first all started, then we launch the late threads and see if any of them can run the operation.

## Running

In order to validate your solution, you can run `./grade-some.sh (path to package with the solution)`. The result shall 
be stored in the `results/(basename)`, which shall contain data for each of the pass:

- `name_check`: checking whether the archive has a correct name;
- `unpacking`: checking whether we can unpack the archive with `tar xzf`;
- `val_compile`: running `javac -Xlint -Werror Validate.java`;
- `validate`: running `java Validate`;
- `assemble`: running `./gradlew clean assemble`;
- `perform_tests`: running the tests.

Each of the pass directories contains `input/` and `output/` directories, as well as possibly the `stdout` and
`stderr` files. If the solution is incorrect at some stage but one wants to check the next ones, there are two options:

- create a `fixed_input/` directory alongside `input/`, if it can be fixed by changing the input and running the
  same procedure;
- create a `fixed_output/` directory and add `.allowed` file, if one wants to create the output of the next stage 
  manually.

Default input for each pass is the output of the previous one.

Insofar as `perform_tests` pass is concerned, the relevant variables to tweak are:

- `TIMEOUT` variable in `grade_some.sh` script, which is the number of seconds that each test suite is allowed to run 
  for;
- static final variables at the top of the `src/test/java/concurrentcube/CubeTest.java` file, which control timeouts,
  number of repeats of various tests etc.

All of these can be increased so as to check whether the default values were too stringent and so the points for the
relevant tests were improperly not given (although the starting values are fairly generous).