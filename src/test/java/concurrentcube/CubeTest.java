package concurrentcube;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

class CubeTest {
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

    static boolean simulatedBehaviourIsEqual(int size, Cube cube, List<Object> operations)
            throws InterruptedException {
        operations.add(new ShowOp(cube.show()));

        Cube refCube = new Cube(
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
                if (!showOp.state.equals(state)) {
                    return false;
                }
            }
            ++counter;
        }

        return true;
    }

    @Test
    @DisplayName("Testing correctness of concurrent <rotate> operations.")
    void checkCorrectnessOfRotate() throws InterruptedException {
        int size = 3;

        // The way we will check the correctness shall be by registering the
        // operations we invoke, and then (sequentially) simulating them on the
        // reference solution and checking the results.
        Lock insertLock = new ReentrantLock();
        List<Object> operations = new ArrayList<>();

        BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
            insertLock.lock();
            operations.add(new RotateOp(side, layer));
            insertLock.unlock();
        };
        BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {};
        Runnable beforeShowing = () -> {};
        Runnable afterShowing = () -> {};

        Cube cube = new Cube(size,
                beforeRotation, afterRotation,
                beforeShowing, afterShowing);

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
        int numThreads = 2*Runtime.getRuntime().availableProcessors();
        for (int threadIdx = 0; threadIdx < numThreads; ++threadIdx) {
            threadList.add(new Thread(workerFn));
        }

        for (Thread thread: threadList) {
            thread.start();
        }

        Thread.sleep(1000);

        stillRunning.set(false);
        for (Thread thread: threadList) {
            thread.join();
        }

        assertFalse(hasThrown.get());
        assertTrue(simulatedBehaviourIsEqual(size, cube, operations));
    }

    @Test
    @DisplayName("Testing correctness of <rotate> and <show>")
    void checkCorrectnessOfBoth() throws InterruptedException {
        int size = 16;

        Lock insertLock = new ReentrantLock(true);
        List<Object> operations = new ArrayList<>();

        // In order to handle checking the results of <show>, we do a trick
        // wherein we add a ShowOp to the list, and update the result value within
        // after we get it later on.
        ThreadLocal<ShowOp> showOpRef = new ThreadLocal<>();
        AtomicBoolean stillRunning = new AtomicBoolean(true);

        Lock ensureSeq = new ReentrantLock(true);

        BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
            insertLock.lock();
            operations.add(new RotateOp(side, layer));
            insertLock.unlock();
        };
        BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {
        };
        Runnable beforeShowing = () -> {
            // This is here so that we don't add new elements while testing the
            // correctness in the <simulateBehaviourIsEqual> method.
            if (stillRunning.get()) {
                insertLock.lock();
                ShowOp showOp = new ShowOp("");
                operations.add(showOp);
                showOpRef.set(showOp);
                insertLock.unlock();
            }
        };
        Runnable afterShowing = () -> {
        };

        Cube cube = new Cube(size,
                beforeRotation, afterRotation,
                beforeShowing, afterShowing);

        AtomicBoolean hasThrown = new AtomicBoolean(false);
        double showProbability = 0.1;

        Runnable workerFn = () -> {
            try {
                Random random = new Random();

                while (stillRunning.get()) {
                    ensureSeq.lock();
                    if (random.nextDouble() > showProbability) {
                        int side = random.nextInt(6);
                        int layer = random.nextInt(size);
                        cube.rotate(side, layer);
                    }
                    else {
                        String state = cube.show();
                        showOpRef.get().state = new String(state);
                    }
                    ensureSeq.unlock();
                }
            }
            catch (InterruptedException e) {
                hasThrown.set(true);
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

        Thread.sleep(100);

        stillRunning.set(false);
        for (Thread thread: threadList) {
            thread.join();
        }

        assertFalse(hasThrown.get(),
                "The test has thrown an exception!");
        assertTrue(simulatedBehaviourIsEqual(size, cube, operations),
                "The transitions are incorrect!");
    }

    @Test
    @DisplayName("Testing correctness of <rotate> and <show> in the presence of interruptions")
    void testOperationsWithInterruptions() {

    }

    @Test
    @DisplayName("Checking correctness of <rotate> and <show> in the presence of interruptions")
    void verifyImmediate() throws InterruptedException {
        int size = 16;

        CyclicBarrier beforeRotationBarrier = new CyclicBarrier(2);
        CyclicBarrier interruptBarrier = new CyclicBarrier(2);

        BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {
            waitIgnoreExn(beforeRotationBarrier);
            try { Thread.sleep((long)1e6); }
            catch (InterruptedException ignored) {}
        };
        BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {};
        Runnable beforeShowing = () -> {};
        Runnable afterShowing = () -> {};

        Cube cube = new Cube(size,
                beforeRotation, afterRotation,
                beforeShowing, afterShowing);

        Runnable workerFn = () -> {
            try {
                cube.rotate(0, 0);
            }
            catch (InterruptedException e) {
                waitIgnoreExn(interruptBarrier);
            }
        };

        Thread blockingThread = new Thread(workerFn);
        blockingThread.start();
        waitIgnoreExn(beforeRotationBarrier);

        Thread waitingThread = new Thread(workerFn);
        waitingThread.start();

        waitingThread.interrupt();
        waitIgnoreExn(interruptBarrier);
    }

    @ParameterizedTest
    @CsvSource({"0,0,0,1", "0,0,5,0", "1,0,3,0", "2,0,4,0"})
    void verifyTrueParallelism(int side1, int layer1, int side2, int layer2) throws InterruptedException {
        CyclicBarrier workersBarrier = new CyclicBarrier(2);
        CyclicBarrier entireBarrier = new CyclicBarrier(3);

        BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {};
        BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {
            waitIgnoreExn(workersBarrier);
            waitIgnoreExn(entireBarrier);
        };
        Runnable beforeShowing = () -> {};
        Runnable afterShowing = () -> {};

        int size = 3;
        Cube cube = new Cube(size,
                beforeRotation, afterRotation,
                beforeShowing, afterShowing);

        Thread worker1 = new Thread(() -> {
            try { cube.rotate(side1, layer1); }
            catch (InterruptedException ignored) {}
        });
        worker1.start();

        Thread worker2 = new Thread(() -> {
            try { cube.rotate(side2, layer2); }
            catch (InterruptedException ignored) {}
        });
        worker2.start();

        waitIgnoreExn(entireBarrier);
    }

    @Test
    void liveliness() {
        ReentrantLock readersLock = new ReentrantLock();
        CyclicBarrier readersBarrier = new CyclicBarrier(2);
        AtomicInteger readerIdx = new AtomicInteger(0);
        ThreadLocal<Boolean> isReader = new ThreadLocal<>();
        AtomicBoolean readerInFunc = new AtomicBoolean(false);
        CyclicBarrier writerBarrier = new CyclicBarrier(2);

        BiConsumer<Integer, Integer> beforeRotation = (side, layer) -> {};
        BiConsumer<Integer, Integer> afterRotation = (side, layer) -> {
            if (isReader.get()) {
                waitIgnoreExn(readersBarrier);
            }
        };
        Runnable beforeShowing = () -> {};
        Runnable afterShowing = () -> {};

        int size = 16;
        Cube cube = new Cube(size,
                beforeRotation, afterRotation,
                beforeShowing, afterShowing);

        AtomicBoolean run = new AtomicBoolean(true);

        Runnable readerFn = () -> {
            isReader.set(true);
            int localReaderIdx = readerIdx.getAndAdd(1);

            while (run.get()) {
                try {
                    readersLock.lock();
                    if (readerInFunc.get()) {
                        waitIgnoreExn(readersBarrier);
                        readerInFunc.set(false);
                    }
                    else {
                        readerInFunc.set(true);
                    }
                    readersLock.unlock();

                    cube.rotate(0, localReaderIdx);
                }
                catch (InterruptedException ignored) {}
            }
        };

        Runnable writerFn = () -> {
            try { cube.show(); }
            catch (InterruptedException ignored) {}
            waitIgnoreExn(writerBarrier);
        };

        Thread reader1 = new Thread(readerFn);
        reader1.start();

        Thread reader2 = new Thread(readerFn);
        reader2.start();

        Thread writer = new Thread(writerFn);
        writer.start();

        waitIgnoreExn(writerBarrier);
    }
}