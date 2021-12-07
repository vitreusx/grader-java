package solution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Cube {
    public Cube(int size,
                BiConsumer<Integer, Integer> beforeRotation,
                BiConsumer<Integer, Integer> afterRotation,
                Runnable beforeShowing,
                Runnable afterShowing) {
        this.size = size;
        this.indexer = new Indexer(size);

        StringBuilder stateBuilder = new StringBuilder();
        for (Side side: Side.values()) {
            char sideChar = (char)('0' + side.ordinal());
            stateBuilder.append(Character.toString(sideChar).repeat(size*size));
        }

        state = stateBuilder.toString().toCharArray();

        this.beforeRotation = beforeRotation;
        this.afterRotation = afterRotation;
        this.beforeShowing = beforeShowing;
        this.afterShowing = afterShowing;

        acquisitionLock = new ReentrantLock(true);
        showLock = new ReentrantReadWriteLock(true);

        axisLocks = new HashMap<>();
        layerLocks = new HashMap<>();
        for (Axis axis: Axis.values()) {
            axisLocks.put(axis, new ReentrantReadWriteLock(true));

            List<ReentrantLock> axisLayerLocks = new ArrayList<>();
            for (int layer = 0; layer < size; ++layer) {
                axisLayerLocks.add(new ReentrantLock(true));
            }
            layerLocks.put(axis, axisLayerLocks);
        }
    }

    public void rotate(int sideIndex, int layer) throws InterruptedException {
        Side side = Side.values()[sideIndex];

        Axis sideAxis = side.axis();
        List<Axis> otherAxes = new ArrayList<>();
        for (Axis axis: Axis.values()) {
            if (!axis.equals(sideAxis))
                otherAxes.add(axis);
        }

        ReentrantReadWriteLock.ReadLock sideAxisLock = axisLocks.get(sideAxis).readLock();
        boolean sideAxisLockHeld = false;

        List<ReentrantReadWriteLock.WriteLock> otherAxesLocks = new ArrayList<>();
        for (Axis otherAxis: otherAxes) {
            otherAxesLocks.add(axisLocks.get(otherAxis).writeLock());
        }

        int lockLayer = layer;
        if (side.parity() < 0) {
            lockLayer = (size - 1) - lockLayer;
        }
        ReentrantLock layerLock = layerLocks.get(side.axis()).get(lockLayer);

        ReentrantReadWriteLock.WriteLock showWriteLock = showLock.writeLock();

        try {
            acquisitionLock.lockInterruptibly();

            sideAxisLock.lockInterruptibly();
            sideAxisLockHeld = true;

            layerLock.lockInterruptibly();

            for (ReentrantReadWriteLock.WriteLock otherAxisLock: otherAxesLocks) {
                otherAxisLock.lock();
                otherAxisLock.unlock();
            }

            showWriteLock.lockInterruptibly();
            showWriteLock.unlock();

            acquisitionLock.unlock();

            beforeRotation.accept(sideIndex, layer);

            Side[] rotatedSides = side.rotatedSides();
            synchronized (state) {
                for (int offset = 0; offset < size; ++offset) {
                    Character[] savedChars = new Character[rotatedSides.length];
                    int[] indices = new int[rotatedSides.length];

                    for (int rotatedIdx = 0; rotatedIdx < rotatedSides.length; ++rotatedIdx) {
                        indices[rotatedIdx] = indexer.index(side, rotatedSides[rotatedIdx], layer, offset);
                        savedChars[rotatedIdx] = state[indices[rotatedIdx]];
                    }

                    for (int rotatedIdx = 0; rotatedIdx < rotatedSides.length; ++rotatedIdx) {
                        state[indices[(rotatedIdx+1)%rotatedSides.length]] = savedChars[rotatedIdx];
                    }
                }

                if (layer == 0 || layer == size-1) {
                    Consumer<Integer> rotateSide = (rotatedSide) -> {
                        char[] saved = Arrays.copyOfRange(state, rotatedSide*size*size, (rotatedSide+1)*size*size);
                        for (int x = 0; x < size; ++x) {
                            for (int y = 0; y < size; ++y) {
                                int u = y, v = size-1-x;
                                state[rotatedSide*size*size+u*size+v] = saved[x*size+y];
                            }
                        }
                    };

                    if (layer == 0) {
                        rotateSide.accept(sideIndex);
                    }
                    else {
                        for (int idx = 0; idx < 3; ++idx) {
                            rotateSide.accept(side.opposite().ordinal());
                        }
                    }
                }
            }

            afterRotation.accept(sideIndex, layer);
        }
        finally {
            if (showWriteLock.isHeldByCurrentThread())
                showWriteLock.unlock();

            for (ReentrantReadWriteLock.WriteLock otherAxisLock: otherAxesLocks) {
                if (otherAxisLock.isHeldByCurrentThread())
                    otherAxisLock.unlock();
            }

            if (layerLock.isHeldByCurrentThread())
                layerLock.unlock();

            if (sideAxisLockHeld)
                sideAxisLock.unlock();

            if (acquisitionLock.isHeldByCurrentThread())
                acquisitionLock.unlock();
        }
    }

    public String show() throws InterruptedException {
        List<ReentrantReadWriteLock.WriteLock> allAxesLocks = new ArrayList<>();
        for (Axis axis: Axis.values()) {
            allAxesLocks.add(axisLocks.get(axis).writeLock());
        }

        ReentrantReadWriteLock.ReadLock showReadLock = showLock.readLock();
        boolean showReadLockHeld = false;

        String stateStr;
        try {
            acquisitionLock.lockInterruptibly();

            showReadLock.lockInterruptibly();
            showReadLockHeld = true;

            for (ReentrantReadWriteLock.WriteLock axisLock: allAxesLocks) {
                axisLock.lockInterruptibly();
                axisLock.unlock();
            }

            acquisitionLock.unlock();

            beforeShowing.run();
            synchronized (state) {
                stateStr = new String(state);
            }
            afterShowing.run();

            return stateStr;
        }
        finally {
            for (ReentrantReadWriteLock.WriteLock axisLock: allAxesLocks) {
                if (axisLock.isHeldByCurrentThread())
                    axisLock.unlock();
            }

            if (showReadLockHeld)
                showReadLock.unlock();

            if (acquisitionLock.isHeldByCurrentThread())
                acquisitionLock.unlock();
        }
    }

    private final int size;
    private final Indexer indexer;
    private final char[] state;

    private final BiConsumer<Integer, Integer> beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;

    private final ReentrantLock acquisitionLock;
    private final ReentrantReadWriteLock showLock;
    private final HashMap<Axis, ReentrantReadWriteLock> axisLocks;
    private final HashMap<Axis, List<ReentrantLock>> layerLocks;
}
