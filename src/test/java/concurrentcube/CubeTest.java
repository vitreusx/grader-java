package concurrentcube;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class CubeTest {
    private static final double showProbability = 0.2;
    private static final long multiplier = 2;
    private static final long taskEntryLag = 125 * multiplier;
    private static final long taskExecTime = 250 * multiplier;
    private static final long interruptLag = 125 * multiplier;
    private static final long noTimeout = 9999999;
    private static final long sampleTime = 250;
    private static final int numRepeats = 8;
    private static final int maxThreads = 16;

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
        waitForThreadJoinEx(t, taskExecTime, message);
    }

    static void waitForThreadJoinEx(Thread t, long time, String message) throws InterruptedException {
        t.join(time);
        assertFalse(t.isAlive(), message);
    }

    static void waitForAnyThreadExit(Semaphore s, String message) throws InterruptedException {
        waitForAnyThreadExitEx(s, taskExecTime, message);
    }

    static void waitForAnyThreadExitEx(Semaphore s, long time, String message) throws InterruptedException {
        assertTrue(s.tryAcquire(time, TimeUnit.MILLISECONDS), message);
    }

    static void waitForThreadAtABarrier(CyclicBarrier barrier, long timeout) {
        try {
            barrier.await(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | BrokenBarrierException ignored) {
            // Never happens (?)
        } catch (TimeoutException ignored) {
            fail("Waited too long for a thread at a barrier");
        }
    }

    static void stallOnABarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException | BrokenBarrierException ignored) {
        }
    }

    static void rotateTestTemplate(int numThreads) throws InterruptedException {
        // We will simply launch a bunch of threads, let them do random rotates,
        // wait a while to accumulate them, and in the end validate the state.
        int size = 3;

        solution.Cube ref = new solution.Cube(size, (x, y) -> {
        }, (x, y) -> {
        }, () -> {
        }, () -> {
        });

        // We use a flag here, so that in case we call cube methods after the experiment
        // (say, in the validate function above), we don't do anything.
        AtomicBoolean testMode = new AtomicBoolean(true);

        BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
            if (testMode.get()) {
                // Synchronized is here, because (1) we use lists, (2) just in case memory
                // consistency breaks down.
                synchronized (ref) {
                    ref.rotate(side, layer);
                }
            }
        };
        BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {
        };
        Runnable beforeShowing = () -> {
        };
        Runnable afterShowing = () -> {
        };

        Cube cube = new Cube(size,
                beforeRotation, afterRotation,
                beforeShowing, afterShowing);

        // We use this flag to gracefully stop the threads later on.
        AtomicBoolean stillRunning = new AtomicBoolean(true);

        AtomicBoolean hasThrown = new AtomicBoolean(false);

        Semaphore workerSem = new Semaphore(numThreads);

        Runnable workerFn = () -> {
            try {
                workerSem.acquire();
                long threadId = Thread.currentThread().getId();
                Random random = new Random(threadId);

                while (stillRunning.get()) {
                    int side = random.nextInt(6);
                    int layer = random.nextInt(size);
                    cube.rotate(side, layer);
                }

                workerSem.release();
            } catch (InterruptedException e) {
                hasThrown.set(true);
            }
        };

        List<Thread> threadList = new ArrayList<>();
        for (int threadIdx = 0; threadIdx < numThreads; ++threadIdx) {
            threadList.add(new Thread(workerFn));
        }

        for (Thread thread : threadList) {
            thread.start();
        }

        Thread.sleep(sampleTime);

        stillRunning.set(false);
        for (int threadIdx = 0; threadIdx < numThreads; ++threadIdx) {
            waitForAnyThreadExit(workerSem, "Worker thread got stuck");
        }

        testMode.set(false);
        assertFalse(hasThrown.get(),
                "Methods returned InterruptedException spuriously.");
        assertEquals(ref.show(), cube.show(), "The state doesn't match the reference implementation.");
    }

    @Nested
    @DisplayName("Tests for concurrent rotate operations (Lite)")
    class RotateTestsLite {
        @Test
        @DisplayName("Testing correctness with 1 thread")
        void testCorrectness1() throws InterruptedException {
            rotateTestTemplate(1);
        }

        @RepeatedTest(numRepeats)
        @DisplayName("Testing correctness with 2 threads")
        void testCorrectness2() throws InterruptedException {
            rotateTestTemplate(2);
        }
    }

    @Nested
    @DisplayName("Tests for concurrent rotate operations (Full).")
    class RotateTestsFull {
        @RepeatedTest(numRepeats)
        @DisplayName("Testing correctness with max # of threads")
        void testCorrectnessMax() throws InterruptedException {
            rotateTestTemplate(maxThreads);
        }
    }

    static void bothTestTemplate(int numThreads) throws InterruptedException {
        // This is pretty much the same test, but now we also do show calls.
        int size = 3;

        // In order to validate the solution, we have the reference Cube implementation
        // with which we duplicate the moves (in beforeRotation etc.)
        solution.Cube ref = new solution.Cube(size, (x, y) -> {
        }, (x, y) -> {
        }, () -> {
        }, () -> {
        });
        ThreadLocal<String> refShow = new ThreadLocal<>();
        AtomicBoolean statesEqual = new AtomicBoolean(true);

        AtomicBoolean stillRunning = new AtomicBoolean(true);
        AtomicBoolean testMode = new AtomicBoolean(true);

        BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
            if (testMode.get()) {
                synchronized (ref) {
                    ref.rotate(side, layer);
                }
            }
        };
        BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {
        };
        Runnable beforeShowing = () -> {
            if (testMode.get()) {
                synchronized (ref) {
                    refShow.set(ref.show());
                }
            }
        };
        Runnable afterShowing = () -> {
        };

        Cube cube = new Cube(size,
                beforeRotation, afterRotation,
                beforeShowing, afterShowing);

        AtomicBoolean hasThrown = new AtomicBoolean(false);

        Semaphore workerSem = new Semaphore(numThreads);

        Runnable workerFn = () -> {
            try {
                workerSem.acquire();
                long threadId = Thread.currentThread().getId();
                Random random = new Random(threadId);

                while (stillRunning.get()) {
                    if (random.nextDouble() > showProbability) {
                        int side = random.nextInt(6);
                        int layer = random.nextInt(size);
                        cube.rotate(side, layer);
                    } else {
                        String state = cube.show();
                        if (!refShow.get().equals(state))
                            statesEqual.set(false);
                    }
                }

                workerSem.release();
            } catch (InterruptedException e) {
                hasThrown.set(true);
            }
        };

        List<Thread> threadList = new ArrayList<>();
        for (int threadIdx = 0; threadIdx < numThreads; ++threadIdx) {
            threadList.add(new Thread(workerFn));
        }

        for (Thread thread : threadList) {
            thread.start();
        }

        Thread.sleep(sampleTime);

        stillRunning.set(false);

        for (int threadIdx = 0; threadIdx < numThreads; ++threadIdx) {
            waitForAnyThreadExit(workerSem, "Worker thread got stuck");
        }

        testMode.set(false);
        assertFalse(hasThrown.get(),
                "Methods returned InterruptedException spuriously.");
        assertTrue(statesEqual.get() && ref.show().equals(cube.show()),
                "The state doesn't match the reference implementation.");
    }

    @Nested
    @DisplayName("Tests for concurrent rotate and show operations (Lite)")
    class BothOpsTestsLite {
        @Test
        @DisplayName("Testing correctness with 1 thread")
        void testCorrectness1() throws InterruptedException {
            bothTestTemplate(1);
        }

        @RepeatedTest(numRepeats)
        @DisplayName("Testing correctness with 2 threads")
        void testCorrectness2() throws InterruptedException {
            bothTestTemplate(2);
        }
    }

    @Nested
    @DisplayName("Tests for concurrent rotate and show operations (Full).")
    class BothOpsTestsFull {
        @RepeatedTest(numRepeats)
        @DisplayName("Testing correctness with max # of threads")
        void testCorrectnessMax() throws InterruptedException {
            bothTestTemplate(maxThreads);
        }
    }

    @Nested
    @DisplayName("Tests for the handling of interruptions (Lite).")
    class InterruptionTestsLite {
        @RepeatedTest(numRepeats)
        @DisplayName("Testing whether the interruptions actually end the threads.")
        void interruptionsEndThreads() throws InterruptedException {
            // The way we test this is as follows: the worker threads run an infinite loop,
            // and the only way out is via the InterruptedException catch. Then, we interrupt
            // every thread and check if, after a while, all of them are not alive.
            int size = 3;

            CyclicBarrier firstThreadEnter = new CyclicBarrier(2);
            CyclicBarrier firstThreadExit = new CyclicBarrier(2);

            BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
                stallOnABarrier(firstThreadEnter);
                stallOnABarrier(firstThreadExit);
            };
            BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {
            };
            Runnable beforeShowing = () -> {
            };
            Runnable afterShowing = () -> {
            };

            Cube cube = new Cube(size,
                    beforeRotation, afterRotation,
                    beforeShowing, afterShowing);

            CyclicBarrier interruptBarrier = new CyclicBarrier(2);
            Runnable rotateFn = () -> {
                try {
                    cube.rotate(0, 0);
                }
                catch (InterruptedException ignored) {
                    stallOnABarrier(interruptBarrier);
                }
            };

            Runnable showFn = () -> {
                try {
                    cube.show();
                }
                catch (InterruptedException ignored) {
                    stallOnABarrier(interruptBarrier);
                }
            };

            Thread thr1 = new Thread(rotateFn), thr2 = new Thread(rotateFn), thr3 = new Thread(showFn);

            thr1.start();
            waitForThreadAtABarrier(firstThreadEnter, taskEntryLag);

            thr2.start();
            Thread.sleep(taskEntryLag);
            assertTrue(thr2.isAlive(), "Thread #2 has for some reason finished execution??");
            thr2.interrupt();
            assertDoesNotThrow(() -> {
                interruptBarrier.await(interruptLag, TimeUnit.MILLISECONDS);
            }, "Thread #2 should have left the waiting.");

            thr3.start();
            Thread.sleep(taskEntryLag);
            assertTrue(thr3.isAlive(), "Thread #3 has for some reason finished execution??");
            thr3.interrupt();
            assertDoesNotThrow(() -> {
                interruptBarrier.await(interruptLag, TimeUnit.MILLISECONDS);
            }, "Thread #3 should have left the waiting.");

            waitForThreadAtABarrier(firstThreadExit, taskEntryLag);
            waitForThreadJoin(thr1, "Somehow thread #1 hasn't finished");
        }
    }

    @Nested
    @DisplayName("Tests for the full correctness of the operations in the presence of interruptions.")
    class InterruptionCorrectnessFull {
        @RepeatedTest(numRepeats)
        @DisplayName("Simply testing integrity of the cube state.")
        void fullInterruptTest() throws InterruptedException {
            // The scenario here is fairly simple: we do stuff as in the previous test, but
            // the main thread will continuously interrupt a random thread for some time.
            // The interrupted threads will continue working looping so that we interrupt
            // enough of them.
            int size = 3;

            solution.Cube ref = new solution.Cube(size, (x, y) -> {
            }, (x, y) -> {
            }, () -> {
            }, () -> {
            });
            ThreadLocal<String> refShow = new ThreadLocal<>();
            AtomicBoolean statesEqual = new AtomicBoolean(true);

            AtomicBoolean stillRunning = new AtomicBoolean(true);
            AtomicBoolean testMode = new AtomicBoolean(true);

            BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
                if (testMode.get()) {
                    ref.rotate(side, layer);
                }
            };
            BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {
            };
            Runnable beforeShowing = () -> {
                if (testMode.get()) {
                    refShow.set(ref.show());
                }
            };
            Runnable afterShowing = () -> {
            };

            Cube cube = new Cube(size,
                    beforeRotation, afterRotation,
                    beforeShowing, afterShowing);

            Semaphore workerSem = new Semaphore(maxThreads);
            CyclicBarrier allEntered = new CyclicBarrier(maxThreads+1);

            Runnable workerFn = () -> {
                try { workerSem.acquire(); allEntered.await(); }
                catch (InterruptedException | BrokenBarrierException ignored) {}

                long threadId = Thread.currentThread().getId();
                Random random = new Random(threadId);

                while (stillRunning.get()) {
                    try {
                        if (random.nextDouble() > showProbability) {
                            int side = random.nextInt(6);
                            int layer = random.nextInt(size);
                            cube.rotate(side, layer);
                        } else {
                            String state = cube.show();
                            if (!refShow.get().equals(state))
                                statesEqual.set(false);
                        }
                    } catch (InterruptedException ignored) {
                    }
                }

                workerSem.release();
            };

            List<Thread> threadList = new ArrayList<>();
            for (int threadIdx = 0; threadIdx < maxThreads; ++threadIdx) {
                threadList.add(new Thread(workerFn));
            }

            for (Thread thread : threadList) {
                thread.start();
            }
            stallOnABarrier(allEntered);

            long testDurationNs = (long) (sampleTime * 1e6);
            Random random = new Random();
            long start = System.nanoTime();
            while (System.nanoTime() - start < testDurationNs) {
                int threadIndex = random.nextInt(maxThreads);
                threadList.get(threadIndex).interrupt();
            }

            stillRunning.set(false);
            for (int threadIdx = 0; threadIdx < maxThreads; ++threadIdx) {
                waitForAnyThreadExit(workerSem, "Worker thread got stuck");
            }

            testMode.set(false);
            assertTrue(statesEqual.get() && ref.show().equals(cube.show()),
                    "The state doesn't match the reference implementation.");
        }
    }

    static void parallelExecTestTemplate(Function<Cube, String> task1, Function<Cube, String> task2)
        throws InterruptedException {

        // In order to check whether the execution is parallel, we set up a barrier with
        // threshold of 3,
        // and start two threads with non-conflicting actions. We let threads wait at
        // the barrier in both beforeX
        // and afterX callbacks, and after starting the threads wait at the barrier in
        // the main threads for some
        // reasonable time. If it times out, not all the threads have arrived at the
        // barriers, so they weren't
        // executing in parallel. We do this for both the beforeX barrier and the afterX
        // barrier.

        solution.Cube ref = new solution.Cube(8, (x, y) -> {
        }, (x, y) -> {
        }, () -> {
        }, () -> {
        });
        ThreadLocal<String> refShow = new ThreadLocal<>();
        AtomicBoolean statesEqual = new AtomicBoolean(true);

        ThreadLocal<ShowOp> showOpRef = new ThreadLocal<>();
        AtomicBoolean testMode = new AtomicBoolean(true);

        CyclicBarrier parallelThreadsBarrier = new CyclicBarrier(3);

        BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
            if (testMode.get()) {
                synchronized (ref) {
                    ref.rotate(side, layer);
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
                synchronized (ref) {
                    refShow.set(ref.show());
                }
                stallOnABarrier(parallelThreadsBarrier);
            }
        };
        Runnable afterShowing = () -> {
            if (testMode.get()) {
                stallOnABarrier(parallelThreadsBarrier);
            }
        };

        Cube cube = new Cube(8,
                beforeRotation, afterRotation,
                beforeShowing, afterShowing);

        Thread thread1 = new Thread(() -> {
            String result = task1.apply(cube);
            if (result != null) {
                // task1 is a show(), so we proceed as usual with show calls
                if (!refShow.get().equals(result))
                    statesEqual.set(false);
            }
        });

        Thread thread2 = new Thread(() -> {
            String result = task2.apply(cube);
            if (result != null) {
                // task2 is a show(), so we proceed as usual with show calls
                if (!refShow.get().equals(result))
                    statesEqual.set(false);
            }
        });

        thread1.start();
        thread2.start();

        assertDoesNotThrow(() -> {
            parallelThreadsBarrier.await(2 * taskExecTime, TimeUnit.MILLISECONDS);
        }, "Both of the threads have not reached the beforeX callback.");

        assertDoesNotThrow(() -> {
            parallelThreadsBarrier.await(2 * taskExecTime, TimeUnit.MILLISECONDS);
        }, "Both of the threads have not reached the afterX callback.");

        waitForThreadJoin(thread1, "The thread got stuck.");
        waitForThreadJoin(thread2, "The thread got stuck.");
    }

    static Function<Cube, String> showTask() {
        return (cube) -> {
            try {
                return cube.show();
            } catch (InterruptedException e) {
                // This code shouldn't be ever reached.
                return "";
            }
        };
    }

    static Function<Cube, String> rotateTask(int side, int layer) {
        return (cube) -> {
            try {
                cube.rotate(side, layer);
                return null;
            } catch (InterruptedException e) {
                // This code shouldn't be ever reached.
                return null;
            }
        };
    }

    @Nested
    @DisplayName("Tests of the actual parallel exec of operations (show/show)")
    class ParallelExec1 {
        @RepeatedTest(numRepeats)
        @DisplayName("Testing whether rotate operations are parallel.")
        void testParallelShowOperations() throws InterruptedException {
            parallelExecTestTemplate(showTask(), showTask());
        }
    }

    @Nested
    @DisplayName("Tests of the actual parallel exec of operations (rotate, same side/different layers)")
    class ParallelExec2 {
        @RepeatedTest(numRepeats)
        @DisplayName("Testing whether rotate operations on the same side/different layers are parallel.")
        void testParallelRotateSameSide() throws InterruptedException {
            parallelExecTestTemplate(rotateTask(0, 0), rotateTask(0, 1));
        }
    }

    @Nested
    @DisplayName("Tests of the actual parallel exec of operations (rotate, same axis/different layers)")
    class ParallelExec3 {
        @RepeatedTest(numRepeats)
        @DisplayName("Testing whether rotate operations on the same axis/different layers are parallel.")
        void testParallelRotateSameAxis() throws InterruptedException {
            parallelExecTestTemplate(rotateTask(0, 0), rotateTask(5, 0));
            parallelExecTestTemplate(rotateTask(1, 0), rotateTask(3, 0));
            parallelExecTestTemplate(rotateTask(2, 0), rotateTask(4, 0));
        }
    }

    static void seqExecTestTemplate(Function<Cube, String> task1, Function<Cube, String> task2)
            throws InterruptedException {
        // We ensure that the conflicting operations are executed sequentially by
        // running two threads
        // ("active" and "waiting"), starting the active one and ensuring that it has
        // reached the beforeX callback,
        // and then starting the waiting one and ensuring that it has *not* reached the
        // afterX callback (by waiting
        // at a barrier for 100ms).

        solution.Cube ref = new solution.Cube(3, (x, y) -> {
        }, (x, y) -> {
        }, () -> {
        }, () -> {
        });
        ThreadLocal<String> refShow = new ThreadLocal<>();
        AtomicBoolean statesEqual = new AtomicBoolean(true);

        ThreadLocal<ShowOp> showOpRef = new ThreadLocal<>();
        AtomicBoolean testMode = new AtomicBoolean(true);

        AtomicBoolean activeThread = new AtomicBoolean(true);
        ThreadLocal<Boolean> isActive = new ThreadLocal<>();
        CyclicBarrier activeThreadPreExit = new CyclicBarrier(2);
        CyclicBarrier waitingThreadEntry = new CyclicBarrier(2);
        CyclicBarrier activeThreadExit = new CyclicBarrier(2);

        BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
            if (testMode.get()) {
                synchronized (ref) {
                    ref.rotate(side, layer);
                }

                // The first thread entering will have isActive set to true, the other to false.
                if (activeThread.get()) {
                    activeThread.set(false);
                    isActive.set(true);
                } else {
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
                synchronized (ref) {
                    refShow.set(ref.show());
                }

                if (activeThread.get()) {
                    activeThread.set(false);
                    isActive.set(true);
                } else {
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

        Cube cube = new Cube(3,
                beforeRotation, afterRotation,
                beforeShowing, afterShowing);

        Thread thread1 = new Thread(() -> {
            String result = task1.apply(cube);
            if (result != null) {
                if (!refShow.get().equals(result))
                    statesEqual.set(false);
            }
        });

        Thread thread2 = new Thread(() -> {
            String result = task2.apply(cube);
            if (result != null) {
                if (!refShow.get().equals(result))
                    statesEqual.set(false);
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
    }

    @Nested
    @DisplayName("Tests of the sequentiality of conflicting operations (same rotate/rotate)")
    class SeqExec1 {
        @Test
        @DisplayName("Test whether the same rotate and rotate are exclusive.")
        void testSequentialRotateAndShow() throws InterruptedException {
            seqExecTestTemplate(showTask(), rotateTask(0, 0));
        }
    }

    @Nested
    @DisplayName("Tests of the sequentiality of conflicting operations (rotate, different axes)")
    class SeqExec2 {
        @Test
        @DisplayName("Test whether rotate tasks are exclusive on different axes.")
        void testSequentialRotateDifferentAxes() throws InterruptedException {
            seqExecTestTemplate(rotateTask(0, 0), rotateTask(1, 1));
            seqExecTestTemplate(rotateTask(0, 0), rotateTask(2, 1));
        }
    }

    @Nested
    @DisplayName("Tests of the sequentiality of conflicting operations (rotate, same side, same layer)")
    class SeqExec3 {
        @Test
        @DisplayName("Test whether rotate tasks are exclusive on the same side and the same layer.")
        void testSequentialRotateSameSideSameLayer() throws InterruptedException {
            for (int side = 0; side < 6; ++side) {
                seqExecTestTemplate(rotateTask(side, 0), rotateTask(side, 0));
            }
        }
    }

    @Nested
    @DisplayName("Tests of the sequentiality of conflicting operations (rotate, same axis, same layer)")
    class SeqExec4 {
        @Test
        @DisplayName("Test whether rotate tasks are exclusive on the same axis and the same layer.")
        void testSequentialRotateSameAxisSameLayer() throws InterruptedException {
            seqExecTestTemplate(rotateTask(0, 0), rotateTask(5, 2));
            seqExecTestTemplate(rotateTask(1, 0), rotateTask(3, 2));
            seqExecTestTemplate(rotateTask(2, 0), rotateTask(4, 2));
        }
    }

    @Nested
    @DisplayName("Tests for the liveliness of the implementation.")
    class LivelinessTests {
        @RepeatedTest(numRepeats)
        @DisplayName("Testing the liveliness of the implementation.")
        void testLiveliness() throws InterruptedException {
            // It's impossible to be sure whether a solution satisfied liveliness, but we do
            // something as follows:
            // first, we launch a "filler" set of threads, which contains multiples of
            // threads running every possible task.
            // We wait a bit so that the cube is (presumably) always occupied, and then, for
            // each task we launch a new
            // thread and check if it can complete it in reasonable time (100ms here).
            int size = 3;

            solution.Cube ref = new solution.Cube(size, (x, y) -> {
            }, (x, y) -> {
            }, () -> {
            }, () -> {
            });
            ThreadLocal<String> refShow = new ThreadLocal<>();
            AtomicBoolean statesEqual = new AtomicBoolean(true);

            ThreadLocal<ShowOp> showOpRef = new ThreadLocal<>();
            AtomicBoolean stillRunning = new AtomicBoolean(true);
            AtomicBoolean testMode = new AtomicBoolean(true);

            BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
                if (testMode.get()) {
                    synchronized (ref) {
                        ref.rotate(side, layer);
                    }
                }
            };
            BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {
            };
            Runnable beforeShowing = () -> {
                synchronized (ref) {
                    refShow.set(ref.show());
                }
            };
            Runnable afterShowing = () -> {
            };

            Cube cube = new Cube(size,
                    beforeRotation, afterRotation,
                    beforeShowing, afterShowing);

            AtomicBoolean hasThrown = new AtomicBoolean(false);

            List<Thread> fillerThreads = new ArrayList<>();
            for (int side = 0; side < 6; ++side) {
                for (int layer = 0; layer < size; ++layer) {
                    int finalSide = side;
                    int finalLayer = layer;
                    for (int dup = 0; dup < 4; ++dup) {
                        fillerThreads.add(new Thread(() -> {
                            try {
                                while (stillRunning.get()) {
                                    cube.rotate(finalSide, finalLayer);
                                }
                            } catch (InterruptedException e) {
                                hasThrown.set(true);
                            }
                        }));
                    }
                }
            }
            fillerThreads.add(new Thread(() -> {
                try {
                    while (stillRunning.get()) {
                        String state = cube.show();
                        if (!refShow.get().equals(state))
                            statesEqual.set(false);
                    }
                } catch (InterruptedException e) {
                    hasThrown.set(true);
                }
            }));

            for (Thread thread : fillerThreads) {
                thread.start();
            }

            Thread.sleep(sampleTime);

            int numLateThreads = 6 * size + 1;
            CyclicBarrier semaphoresAcquiredBarrier = new CyclicBarrier(numLateThreads + 1);
            Semaphore lateThreadSem = new Semaphore(numLateThreads);

            List<Thread> lateThreads = new ArrayList<>();
            for (int side = 0; side < 6; ++side) {
                for (int layer = 0; layer < size; ++layer) {
                    int finalSide = side;
                    int finalLayer = layer;

                    lateThreads.add(new Thread(() -> {
                        try {
                            lateThreadSem.acquire();
                            semaphoresAcquiredBarrier.await();
                            cube.rotate(finalSide, finalLayer);
                            lateThreadSem.release();
                        } catch (InterruptedException | BrokenBarrierException e) {
                            hasThrown.set(true);
                        }
                    }));
                }
            }
            lateThreads.add(new Thread(() -> {
                try {
                    lateThreadSem.acquire();
                    semaphoresAcquiredBarrier.await();
                    String state = cube.show();
                    if (!refShow.get().equals(state))
                        statesEqual.set(false);
                    lateThreadSem.release();
                } catch (InterruptedException | BrokenBarrierException e) {
                    hasThrown.set(true);
                }
            }));

            for (Thread t : lateThreads) {
                t.start();
            }
            waitForThreadAtABarrier(semaphoresAcquiredBarrier, noTimeout);

            long timeout = 2 * taskExecTime * fillerThreads.size();
            for (int lateIdx = 0; lateIdx < numLateThreads; ++lateIdx) {
                waitForAnyThreadExitEx(lateThreadSem, timeout, "No late thread has reached the end.");
            }

            stillRunning.set(false);
            for (Thread thread : fillerThreads) {
                waitForThreadJoin(thread, "A filler thread got stuck.");
            }

            testMode.set(false);
            assertFalse(hasThrown.get(),
                    "Methods returned InterruptedException spuriously.");
        }
    }
}
