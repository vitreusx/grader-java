package concurrentcube;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class CubeTest {
    private static final double showProbability = 0.2;
    private static final long multiplier = 10;
    private static final long taskEntryLag = 50*multiplier;
    private static final long taskExecTime = 250*multiplier;
    private static final long interruptLag = 50*multiplier;
    private static final long sampleTime = 250;
    
    static class RotateOp {
        public int side;
        public int layer;

        public RotateOp(int side, int layer) {
            this.side = side;
            this.layer = layer;
        }
    }

    static class ShowOp {
        public String state;

        public ShowOp(String state) {
            this.state = state;
        }
    }

    static void waitForThreadJoin(Thread t, String message) throws InterruptedException {
        t.join(taskExecTime);
        assertFalse(t.isAlive(), message);
    }

    // A class for storing the sequence of actions over the cube, as well
    // as verifying whether the execution matches that of the reference
    // implementation executed sequentially.
    static class History {
        public List<Object> operations;

        public History() {
            operations = new ArrayList<>();
        }

        public RotateOp addRotateOp(int side, int layer) {
            RotateOp rotateOp = new RotateOp(side, layer);
            operations.add(rotateOp);
            return rotateOp;
        }

        public ShowOp addShowOp() {
            // We use a null, because we will fill it in later on.
            ShowOp showOp = new ShowOp(null);
            operations.add(showOp);
            return showOp;
        }

        public boolean validate(int size, Cube cube) {
            try {
                AtomicReference<String> finalState = new AtomicReference<>("");
                Thread t = new Thread(() -> {
                    try {
                        finalState.set(cube.show());
                    }
                    catch (InterruptedException ignored) {}
                });

                t.start();
                waitForThreadJoin(t, "Could not get the final state in the validation procedure.");

                solution.Cube refCube = new solution.Cube(
                        size,
                        (side, layer) -> {},
                        (side, layer) -> {},
                        () -> {},
                        () -> {});

                int counter = 0;
                for (Object op: operations) {
                    if (op instanceof RotateOp) {
                        RotateOp rotateOp = (RotateOp) op;
                        refCube.rotate(rotateOp.side, rotateOp.layer);
                    }
                    else if (op instanceof ShowOp) {
                        ShowOp showOp = (ShowOp) op;
                        String state = refCube.show();
                        if (showOp.state != null && !showOp.state.equals(state)) {
                            return false;
                        }
                    }
                    ++counter;
                }

                String finalRefState = refCube.show();
                return finalState.get().equals(finalRefState);
            }
            catch (InterruptedException e) {
                // This code is never reached, but we need to shut up the compiler.
                return false;
            }
        }
    }

    static void waitForThreadAtABarrier(CyclicBarrier barrier, long timeout) {
        try {
            barrier.await(timeout, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException | BrokenBarrierException ignored) {
            // Never happens (?)
        }
        catch (TimeoutException ignored) {
            fail("Waited too long for a thread at a barrier");
        }
    }

    static void stallOnABarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
        }
        catch (InterruptedException | BrokenBarrierException ignored) {}
    }

    @Nested
    @DisplayName("Tests for concurrent rotate operations.")
    class RotateTests {
        void testTemplate(int numThreads) throws InterruptedException {
            // We will simply launch a bunch of threads, let them do random rotates,
            // wait a while to accumulate them, and in the end validate the state.
            int size = 3;

            History history = new History();

            // We use a flag here, so that in case we call cube methods after the experiment
            // (say, in the validate function above), we don't do anything.
            AtomicBoolean testMode = new AtomicBoolean(true);

            BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
                if (testMode.get()) {
                    // Synchronized is here, because (1) we use lists, (2) just in case memory consistency breaks down.
                    synchronized (history) {
                        history.addRotateOp(side, layer);
                    }
                }
            };
            BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {};
            Runnable beforeShowing = () -> {};
            Runnable afterShowing = () -> {};

            Cube cube = new Cube(size,
                    beforeRotation, afterRotation,
                    beforeShowing, afterShowing);

            // We use this flag to gracefully stop the threads later on.
            AtomicBoolean stillRunning = new AtomicBoolean(true);

            AtomicBoolean hasThrown = new AtomicBoolean(false);

            Runnable workerFn = () -> {
                try {
                    long threadId = Thread.currentThread().getId();
                    Random random = new Random(threadId);

                    while (stillRunning.get()) {
                        int side = random.nextInt(6);
                        int layer = random.nextInt(size);
                        cube.rotate(side, layer);
                    }
                }
                catch (InterruptedException e) {
                    hasThrown.set(true);
                }
            };

            List<Thread> threadList = new ArrayList<>();
            for (int threadIdx = 0; threadIdx < numThreads; ++threadIdx) {
                threadList.add(new Thread(workerFn));
            }

            for (Thread thread: threadList) {
                thread.start();
            }

            Thread.sleep(sampleTime);

            stillRunning.set(false);
            for (Thread thread: threadList) {
                waitForThreadJoin(thread, "The thread got stuck.");
            }

            testMode.set(false);
            assertFalse(hasThrown.get(),
                    "Methods returned InterruptedException spuriously.");
            assertTrue(history.validate(size, cube),
                    "The state doesn't match the reference implementation.");
        }

        @Test
        @DisplayName("Testing correctness of sequential rotate operations.")
        void testCorrectnessOfSequentialRotate() throws InterruptedException {
            testTemplate(1);
        }

        @Test
        @DisplayName("Testing correctness of concurrent rotate operations.")
        void testCorrectnessOfRunningRotate() throws InterruptedException {
            testTemplate(2*Runtime.getRuntime().availableProcessors());
        }
    }

    @Nested
    @DisplayName("Tests for correctness of rotate and show at the same time.")
    class CorrectnessOfBoth {
        void testTemplate(int numThreads) throws InterruptedException {
            // This is pretty much the same test, but now we also do show calls.
            int size = 3;

            History history = new History();

            // In order to handle recording the show operations, we need to do something as follows:
            // 1. Add the rotate op with null state within "beforeRotation".
            // 2. Once we exit the show call and get the state, we fill it in.
            // We use showOpRef for holding the incomplete rotate op added to the history.
            ThreadLocal<ShowOp> showOpRef = new ThreadLocal<>();
            AtomicBoolean stillRunning = new AtomicBoolean(true);
            AtomicBoolean testMode = new AtomicBoolean(true);

            BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
                if (testMode.get()) {
                    synchronized (history) {
                        history.addRotateOp(side, layer);
                    }
                }
            };
            BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {
            };
            Runnable beforeShowing = () -> {
                if (testMode.get()) {
                    synchronized (history) {
                        showOpRef.set(history.addShowOp());
                    }
                }
            };
            Runnable afterShowing = () -> {
            };

            Cube cube = new Cube(size,
                    beforeRotation, afterRotation,
                    beforeShowing, afterShowing);

            AtomicBoolean hasThrown = new AtomicBoolean(false);

            Runnable workerFn = () -> {
                try {
                    Random random = new Random();

                    while (stillRunning.get()) {
                        if (random.nextDouble() > showProbability) {
                            int side = random.nextInt(6);
                            int layer = random.nextInt(size);
                            cube.rotate(side, layer);
                        }
                        else {
                            String state = cube.show();
                            // Fill the incomplete rotate op.
                            showOpRef.get().state = state;
                        }
                    }
                }
                catch (InterruptedException e) {
                    hasThrown.set(true);
                }
            };

            List<Thread> threadList = new ArrayList<>();
            for (int threadIdx = 0; threadIdx < numThreads; ++threadIdx) {
                threadList.add(new Thread(workerFn));
            }

            for (Thread thread: threadList) {
                thread.start();
            }

            Thread.sleep(sampleTime);

            stillRunning.set(false);
            for (Thread thread: threadList) {
                waitForThreadJoin(thread, "The thread got stuck.");
            }

            testMode.set(false);
            assertFalse(hasThrown.get(),
                    "Methods returned InterruptedException spuriously.");
            assertTrue(history.validate(size, cube),
                    "The state doesn't match the reference implementation.");
        }

        @Test
        @DisplayName("Testing correctness of rotate and show when sequential.")
        void testCorrectnessWhenRunningBothSequentially() throws InterruptedException {
            testTemplate(1);
        }

        @Test
        @DisplayName("Testing correctness of rotate and show for multiple threads.")
        void testCorrectnessWhenRunningBoth() throws InterruptedException {
            testTemplate(2*Runtime.getRuntime().availableProcessors());
        }
    }

    @Nested
    @DisplayName("Tests for the correctness of the operations in the presence of interruptions.")
    class InterruptionCorrectnessTests {
        @Test
        @DisplayName("Simply testing integrity of the cube state.")
        void testIntegrity() throws InterruptedException {
            // The scenario here is fairly simple: we do stuff as in the previous test, but the main thread
            // will randomly interrupt threads for some time. The interrupted threads will continue working looping
            // so that we interrupt enough of them.
            int size = 8;

            History history = new History();

            ThreadLocal<ShowOp> showOpRef = new ThreadLocal<>();
            AtomicBoolean stillRunning = new AtomicBoolean(true);
            AtomicBoolean testMode = new AtomicBoolean(true);

            BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
                if (testMode.get()) {
                    synchronized (history) {
                        history.addRotateOp(side, layer);
                    }
                }
            };
            BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {
            };
            Runnable beforeShowing = () -> {
                if (testMode.get()) {
                    synchronized (history) {
                        showOpRef.set(history.addShowOp());
                    }
                }
            };
            Runnable afterShowing = () -> {
            };

            Cube cube = new Cube(size,
                    beforeRotation, afterRotation,
                    beforeShowing, afterShowing);

            Runnable workerFn = () -> {
                Random random = new Random();

                while (stillRunning.get()) {
                    try {
                        if (random.nextDouble() > showProbability) {
                            int side = random.nextInt(6);
                            int layer = random.nextInt(size);
                            cube.rotate(side, layer);
                        }
                        else {
                            String state = cube.show();
                            showOpRef.get().state = state;
                        }
                    }
                    catch (InterruptedException ignored) {}
                }
            };

            List<Thread> threadList = new ArrayList<>();
            int numThreads = 2*Runtime.getRuntime().availableProcessors();
            for (int threadIdx = 0; threadIdx < numThreads; ++threadIdx) {
                threadList.add(new Thread(workerFn));
            }

            for (Thread thread: threadList) {
                thread.start();
            }

            long testDurationNs = (long)(sampleTime * 1e6);
            Random random = new Random();
            long start = System.nanoTime();
            while (System.nanoTime() - start < testDurationNs) {
                int threadIndex = random.nextInt(numThreads);
                threadList.get(threadIndex).interrupt();
                Thread.sleep(1);
            }

            stillRunning.set(false);
            for (Thread thread: threadList) {
                waitForThreadJoin(thread, "The thread got stuck.");
            }

            testMode.set(false);
            assertTrue(history.validate(size, cube),
                    "The state doesn't match the reference implementation.");
        }

        @Test
        @DisplayName("Testing whether the interruptions actually end the threads.")
        void testWhetherInterruptionsEndThreads() throws InterruptedException {
            // The way we test this is as follows: the worker threads run an infinite loop, and
            // the only way out is via the InterruptedException catch. Then, we interrupt every thread and check
            // if, after a while, all of them are not alive.
            int size = 8;

            History history = new History();

            ThreadLocal<ShowOp> showOpRef = new ThreadLocal<>();
            AtomicBoolean testMode = new AtomicBoolean(true);

            BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
                if (testMode.get()) {
                    synchronized (history) {
                        history.addRotateOp(side, layer);
                    }
                }
            };
            BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {
            };
            Runnable beforeShowing = () -> {
                if (testMode.get()) {
                    synchronized (history) {
                        showOpRef.set(history.addShowOp());
                    }
                }
            };
            Runnable afterShowing = () -> {
            };

            Cube cube = new Cube(size,
                    beforeRotation, afterRotation,
                    beforeShowing, afterShowing);

            Runnable workerFn = () -> {
                Random random = new Random();

                try {
                    while (true) {
                        if (random.nextDouble() > showProbability) {
                            int side = random.nextInt(6);
                            int layer = random.nextInt(size);
                            cube.rotate(side, layer);
                        }
                        else {
                            String state = cube.show();
                            showOpRef.get().state = state;
                        }
                    }
                }
                catch (InterruptedException ignored) {}
            };

            List<Thread> threadList = new ArrayList<>();
            int numThreads = 2*Runtime.getRuntime().availableProcessors();
            for (int threadIdx = 0; threadIdx < numThreads; ++threadIdx) {
                threadList.add(new Thread(workerFn));
            }

            for (Thread thread: threadList) {
                thread.start();
            }

            Thread.sleep(sampleTime);
            for (Thread thread: threadList) {
                thread.interrupt();
            }

            for (Thread thread: threadList) {
                waitForThreadJoin(thread, "Thread, which was interrupted, has not stopped execution.");
                if (thread.isAlive()) break;
            }

            testMode.set(false);

            assertTrue(history.validate(size, cube),
                    "The state doesn't match the reference implementation.");
        }
    }

    @Nested
    @DisplayName("Tests for the behaviour of interruptions and waiting at locks.")
    class InterruptingWaitingTests {
        @Test
        @DisplayName("Testing whether interruptions interrupt threads waiting at the locks.")
        void testWhetherInterruptionsInterruptWaiting() throws InterruptedException {
            // We test it in a following fashion: we have two threads ("active" and "waiting"), we start the "active" one
            // and ensure it's at the afterRotation/afterShowing call. Then, we have it wait at a second barrier, at which
            // it will wait until we're done testing. We then launch the "waiting" thread, wait a bit (50ms here) until
            // we're reasonably sure they're waiting at the locks, and interrupt it. The catch block contains a barrier,
            // and we wait at it in the main thread for a reasonable time (100ms) to check whether it has arrived there.
            // We repeat this for both show task and the rotate task waiting.
            int size = 8;

            History history = new History();

            ThreadLocal<ShowOp> showOpRef = new ThreadLocal<>();
            AtomicBoolean testMode = new AtomicBoolean(true);

            CyclicBarrier activeThreadPresent = new CyclicBarrier(2);
            CyclicBarrier activeThreadExit = new CyclicBarrier(2);

            BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
                if (testMode.get()) {
                    synchronized (history) {
                        history.addRotateOp(side, layer);
                    }
                }
            };
            BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {
                if (testMode.get()) {
                    stallOnABarrier(activeThreadPresent);
                    stallOnABarrier(activeThreadExit);
                }
            };
            Runnable beforeShowing = () -> {
                if (testMode.get()) {
                    synchronized (history) {
                        showOpRef.set(history.addShowOp());
                    }
                }
            };
            Runnable afterShowing = () -> {
                if (testMode.get()) {
                    stallOnABarrier(activeThreadPresent);
                    stallOnABarrier(activeThreadExit);
                }
            };

            Cube cube = new Cube(size,
                    beforeRotation, afterRotation,
                    beforeShowing, afterShowing);

            CyclicBarrier waitingRotateThreadInterrupted = new CyclicBarrier(2);
            Runnable rotateTask = () -> {
                try {
                    cube.rotate(0, 0);
                }
                catch (InterruptedException ignored) {
                    stallOnABarrier(waitingRotateThreadInterrupted);
                }
            };

            CyclicBarrier waitingShowThreadInterrupted = new CyclicBarrier(2);
            Runnable showTask = () -> {
                try {
                    String state = cube.show();
                    showOpRef.get().state = state;
                }
                catch (InterruptedException ignored) {
                    stallOnABarrier(waitingShowThreadInterrupted);
                }
            };

            Thread activeRotateThread = new Thread(rotateTask);
            Thread waitingRotateThread = new Thread(rotateTask);
            Thread waitingShowThread = new Thread(showTask);

            activeRotateThread.start();
            waitForThreadAtABarrier(activeThreadPresent, taskEntryLag);

            waitingRotateThread.start();
            Thread.sleep(taskEntryLag);
            assertDoesNotThrow(() -> {
                waitingRotateThread.interrupt();
                waitingRotateThreadInterrupted.await(interruptLag, TimeUnit.MILLISECONDS);
            }, "Interrupted rotate task still (seemingly) waits at a lock.");

            waitingShowThread.start();
            Thread.sleep(taskEntryLag);
            assertDoesNotThrow(() -> {
                waitingShowThread.interrupt();
                waitingShowThreadInterrupted.await(interruptLag, TimeUnit.MILLISECONDS);
            }, "Interrupted show task still (seemingly) waits at a lock.");

            waitForThreadAtABarrier(activeThreadExit, taskEntryLag);

            waitForThreadJoin(activeRotateThread, "The active thread got stuck.");
            waitForThreadJoin(waitingRotateThread, "The waiting rotate thread got stuck.");
            waitForThreadJoin(waitingShowThread, "The waiting show thread got stuck.");

            testMode.set(false);
            assertTrue(history.validate(size, cube),
                    "The state doesn't match the reference implementation.");
        }
    }

    @Nested
    @DisplayName("Tests of the parallel execution of non-conflicting operations.")
    class ParallelExecutionTests {
        // This is an unified template for running the tasks. The "Function<Cube, String>" is sort-of a fused
        // version of both show and rotate: the rotate task will return null, which we will detect so that we can
        // set the state string to the appropriate value in showOp in the history validation part of the code.
        void testTemplate(int size, Function<Cube, String> task1, Function<Cube, String> task2)
                throws InterruptedException {
            // In order to check whether the execution is parallel, we set up a barrier with threshold of 3,
            // and start two threads with non-conflicting actions. We let threads wait at the barrier in both beforeX
            // and afterX callbacks, and after starting the threads wait at the barrier in the main threads for some
            // reasonable time. If it times out, not all the threads have arrived at the barriers, so they weren't
            // executing in parallel. We do this for both the beforeX barrier and the afterX barrier.
            History history = new History();

            ThreadLocal<ShowOp> showOpRef = new ThreadLocal<>();
            AtomicBoolean testMode = new AtomicBoolean(true);

            CyclicBarrier parallelThreadsBarrier = new CyclicBarrier(3);

            BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
                if (testMode.get()) {
                    synchronized (history) {
                        history.addRotateOp(side, layer);
                    }
                    stallOnABarrier(parallelThreadsBarrier);
                }
            };
            BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {
                if (testMode.get()) {
                    stallOnABarrier(parallelThreadsBarrier);
                }
            };
            Runnable beforeShowing = () -> {
                if (testMode.get()) {
                    synchronized (history) {
                        showOpRef.set(history.addShowOp());
                    }
                    stallOnABarrier(parallelThreadsBarrier);
                }
            };
            Runnable afterShowing = () -> {
                if (testMode.get()) {
                    stallOnABarrier(parallelThreadsBarrier);
                }
            };

            Cube cube = new Cube(size,
                    beforeRotation, afterRotation,
                    beforeShowing, afterShowing);

            Thread thread1 = new Thread(() -> {
                String result = task1.apply(cube);
                if (result != null) {
                    // task1 is a show(), so we proceed as usual with show calls
                    showOpRef.get().state = result;
                }
            });

            Thread thread2 = new Thread(() -> {
                String result = task2.apply(cube);
                if (result != null) {
                    // task2 is a show(), so we proceed as usual with show calls
                    showOpRef.get().state = result;
                }
            });

            thread1.start();
            thread2.start();

            assertDoesNotThrow(() -> {
                parallelThreadsBarrier.await(2*taskExecTime, TimeUnit.MILLISECONDS);
            }, "Both of the threads have not reached the beforeX callback.");

            assertDoesNotThrow(() -> {
                parallelThreadsBarrier.await(2*taskExecTime, TimeUnit.MILLISECONDS);
            }, "Both of the threads have not reached the afterX callback.");

            waitForThreadJoin(thread1, "The thread got stuck.");
            waitForThreadJoin(thread2, "The thread got stuck.");

            testMode.set(false);
            assertTrue(history.validate(size, cube),
                    "The state doesn't match the reference implementation.");
        }

        Function<Cube, String> showTask() {
            return (cube) -> {
                try {
                    return cube.show();
                }
                catch (InterruptedException e) {
                    // This code shouldn't be ever reached.
                    return "";
                }
            };
        }

        Function<Cube, String> rotateTask(int side, int layer) {
            return (cube) -> {
                try {
                    cube.rotate(side, layer);
                    return null;
                }
                catch (InterruptedException e) {
                    // This code shouldn't be ever reached.
                    return null;
                }
            };
        }

        @Test
        @DisplayName("Testing whether rotate operations are parallel.")
        void testParallelShowOperations() throws InterruptedException {
            testTemplate(8, showTask(), showTask());
        }

        @Test
        @DisplayName("Testing whether rotate operations on the same side/different layers are parallel.")
        void testParallelRotateSameSide() throws InterruptedException {
            testTemplate(8, rotateTask(0, 0), rotateTask(0, 1));
        }

        @Test
        @DisplayName("Testing whether rotate operations on the same axis/different layers are parallel.")
        void testParallelRotateSameAxis() throws InterruptedException {
            testTemplate(8, rotateTask(0, 0), rotateTask(5, 0));
            testTemplate(8, rotateTask(1, 0), rotateTask(3, 0));
            testTemplate(8, rotateTask(2, 0), rotateTask(4, 0));
        }
    }

    @Nested
    @DisplayName("Tests of the sequentiality of conflicting operations.")
    class SequentialityTests {
        void testTemplate(int size, Function<Cube, String> task1, Function<Cube, String> task2)
                throws InterruptedException {
            // We ensure that the conflicting operations are executed sequentially by running two threads
            // ("active" and "waiting"), starting the active one and ensuring that it has reached the beforeX callback,
            // and then starting the waiting one and ensuring that it has *not* reached the afterX callback (by waiting
            // at a barrier for 100ms).
            History history = new History();

            ThreadLocal<ShowOp> showOpRef = new ThreadLocal<>();
            AtomicBoolean testMode = new AtomicBoolean(true);

            AtomicBoolean activeThread = new AtomicBoolean(true);
            ThreadLocal<Boolean> isActive = new ThreadLocal<>();
            CyclicBarrier activeThreadPreExit = new CyclicBarrier(2);
            CyclicBarrier waitingThreadEntry = new CyclicBarrier(2);
            CyclicBarrier activeThreadExit = new CyclicBarrier(2);

            BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
                if (testMode.get()) {
                    synchronized (history) {
                        history.addRotateOp(side, layer);
                    }

                    // The first thread entering will have isActive set to true, the other to false.
                    if (activeThread.get()) {
                        activeThread.set(false);
                        isActive.set(true);
                    }
                    else {
                        isActive.set(false);
                        stallOnABarrier(waitingThreadEntry);
                    }
                }
            };
            BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {
                if (testMode.get()) {
                    if (isActive.get()) {
                        stallOnABarrier(activeThreadPreExit);
                        stallOnABarrier(activeThreadExit);
                    }
                }
            };
            Runnable beforeShowing = () -> {
                if (testMode.get()) {
                    synchronized (history) {
                        showOpRef.set(history.addShowOp());
                    }

                    if (activeThread.get()) {
                        activeThread.set(false);
                        isActive.set(true);
                    }
                    else {
                        isActive.set(false);
                        stallOnABarrier(waitingThreadEntry);
                    }
                }
            };
            Runnable afterShowing = () -> {
                if (testMode.get()) {
                    if (isActive.get()) {
                        stallOnABarrier(activeThreadPreExit);
                        stallOnABarrier(activeThreadExit);
                    }
                }
            };

            Cube cube = new Cube(size,
                    beforeRotation, afterRotation,
                    beforeShowing, afterShowing);

            Thread thread1 = new Thread(() -> {
                String result = task1.apply(cube);
                if (result != null) {
                    showOpRef.get().state = result;
                }
            });

            Thread thread2 = new Thread(() -> {
                String result = task2.apply(cube);
                if (result != null) {
                    showOpRef.get().state = result;
                }
            });

            thread1.start();
            waitForThreadAtABarrier(activeThreadPreExit, taskExecTime + taskEntryLag);

            thread2.start();
            assertThrows(TimeoutException.class, () -> {
                waitingThreadEntry.await(taskEntryLag, TimeUnit.MILLISECONDS);
            }, "The second thread has reached the callback, and so the execution was non-exclusive.");

            waitForThreadAtABarrier(activeThreadExit, taskEntryLag);
            waitForThreadAtABarrier(waitingThreadEntry, taskEntryLag);

            waitForThreadJoin(thread1, "The thread got stuck.");
            waitForThreadJoin(thread2, "The thread got stuck.");

            testMode.set(false);
            assertTrue(history.validate(size, cube),
                    "The state doesn't match the reference implementation.");
        }

        Function<Cube, String> showTask() {
            return (cube) -> {
                try {
                    return cube.show();
                }
                catch (InterruptedException e) {
                    return "";
                }
            };
        }

        Function<Cube, String> rotateTask(int side, int layer) {
            return (cube) -> {
                try {
                    cube.rotate(side, layer);
                    return null;
                }
                catch (InterruptedException e) {
                    return null;
                }
            };
        }

        @Test
        @DisplayName("Test whether rotate and rotate are exclusive.")
        void testSequentialRotateAndShow() throws InterruptedException {
            testTemplate(8, showTask(), rotateTask(0, 0));
        }

        @Test
        @DisplayName("Test whether rotate tasks are exclusive on different axes.")
        void testSequentialRotateDifferentAxes() throws InterruptedException {
            testTemplate(8, rotateTask(0, 0), rotateTask(1, 1));
            testTemplate(8, rotateTask(0, 0), rotateTask(2, 1));
        }

        @Test
        @DisplayName("Test whether rotate tasks are exclusive on the same side and the same layer.")
        void testSequentialRotateSameSideSameLayer() throws InterruptedException {
            for (int side = 0; side < 6; ++side) {
                testTemplate(8, rotateTask(side, 0), rotateTask(side, 0));
            }
        }

        @Test
        @DisplayName("Test whether rotate tasks are exclusive on the same axis and the same layer.")
        void testSequentialRotateSameAxisSameLayer() throws InterruptedException {
            testTemplate(3, rotateTask(0, 0), rotateTask(5, 2));
            testTemplate(3, rotateTask(1, 0), rotateTask(3, 2));
            testTemplate(3, rotateTask(2, 0), rotateTask(4, 2));
        }
    }

    @Nested
    @DisplayName("Tests for the liveliness of the implementation.")
    class LivelinessTests {
        @Test
        @DisplayName("Testing the liveliness of the implementation.")
        void testLiveliness() throws InterruptedException {
            // It's impossible to be sure whether a solution satisfied liveliness, but we do something as follows:
            // first, we launch a "filler" set of threads, which contains multiples of threads running every possible task.
            // We wait a bit so that the cube is (presumably) always occupied, and then, for each task we launch a new
            // thread and check if it can complete it in reasonable time (100ms here).
            int size = 3;

            History history = new History();
            ThreadLocal<ShowOp> showOpRef = new ThreadLocal<>();
            AtomicBoolean stillRunning = new AtomicBoolean(true);
            AtomicBoolean testMode = new AtomicBoolean(true);

            BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
                if (testMode.get()) {
                    synchronized (history) {
                        history.addRotateOp(side, layer);
                    }
                }
            };
            BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {
            };
            Runnable beforeShowing = () -> {
                if (testMode.get()) {
                    synchronized (history) {
                        showOpRef.set(history.addShowOp());
                    }
                }
            };
            Runnable afterShowing = () -> {
            };

            Cube cube = new Cube(size,
                    beforeRotation, afterRotation,
                    beforeShowing, afterShowing);

            AtomicBoolean hasThrown = new AtomicBoolean(false);
            

            Runnable workerFn = () -> {
                try {
                    Random random = new Random();

                    while (stillRunning.get()) {
                        if (random.nextDouble() > showProbability) {
                            int side = random.nextInt(6);
                            int layer = random.nextInt(size);
                            cube.rotate(side, layer);
                        }
                        else {
                            String state = cube.show();
                            showOpRef.get().state = state;
                        }
                    }
                }
                catch (InterruptedException e) {
                    hasThrown.set(true);
                }
            };

            List<Thread> fillerThreads = new ArrayList<>();
            for (int side = 0; side < 6; ++side) {
                for (int layer = 0; layer < size; ++layer) {
                    int finalSide = side;
                    int finalLayer = layer;
                    fillerThreads.add(new Thread(() -> {
                        try {
                            while (stillRunning.get()) {
                                cube.rotate(finalSide, finalLayer);
                            }
                        }
                        catch (InterruptedException e) {
                            hasThrown.set(true);
                        }
                    }));
                }
            }
            fillerThreads.add(new Thread(() -> {
                try {
                    while (stillRunning.get()) {
                        String state = cube.show();
                        showOpRef.get().state = state;
                    }
                }
                catch (InterruptedException e) {
                    hasThrown.set(true);
                }
            }));

            for (Thread thread: fillerThreads) {
                thread.start();
            }

            Thread.sleep(sampleTime);

            CyclicBarrier barrier = new CyclicBarrier(6*size+2);

            List<Thread> lateThreads = new ArrayList<>();
            for (int side = 0; side < 6; ++side) {
                for (int layer = 0; layer < size; ++layer) {
                    int finalSide = side;
                    int finalLayer = layer;

                    lateThreads.add(new Thread(() -> {
                        try {
                            cube.rotate(finalSide, finalLayer);
                            barrier.await();
                        }
                        catch (InterruptedException e) {
                            hasThrown.set(true);
                        }
                        catch (BrokenBarrierException ignored) {}
                    }));
                }
            }
            lateThreads.add(new Thread(() -> {
                try {
                    String state = cube.show();
                    showOpRef.get().state = state;
                    barrier.await();
                }
                catch (InterruptedException e) {
                    hasThrown.set(true);
                }
                catch (BrokenBarrierException ignored) {}
            }));

            for (Thread t: lateThreads) {
                t.start();
            }


            waitForThreadAtABarrier(barrier, 2 * taskExecTime * lateThreads.size());

            stillRunning.set(false);
            for (Thread thread: fillerThreads) {
                waitForThreadJoin(thread, "The filler thread stuck at execution");
            }

            testMode.set(false);
            assertFalse(hasThrown.get(),
                    "Methods returned InterruptedException spuriously.");
            assertTrue(history.validate(size, cube),
                    "The state doesn't match the reference implementation.");
        }
    }
}
