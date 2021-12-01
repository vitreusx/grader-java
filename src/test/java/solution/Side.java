package solution;

import java.util.Arrays;

import static solution.Affine.*;
import static solution.Axis.*;

public enum Side {
    TOP, LEFT, FRONT, RIGHT, BACK, BOTTOM;

    final static Axis[] axisArray = {
            TOP_BOTTOM, LEFT_RIGHT, FRONT_BACK,
            LEFT_RIGHT, FRONT_BACK, TOP_BOTTOM
    };

    public Axis axis() {
        return axisArray[ordinal()];
    }

    final static int[] parityArray = {
            1, 1, 1, -1, -1, -1
    };

    public int parity() {
        return parityArray[ordinal()];
    }

    static Side[][] rotatedSidesArray = {
            { BACK, RIGHT, FRONT, LEFT },
            { TOP, FRONT, BOTTOM, BACK },
            { LEFT, TOP, RIGHT, BOTTOM },
            { BACK, BOTTOM, FRONT, TOP },
            { BOTTOM, RIGHT, TOP, LEFT },
            { LEFT, FRONT, RIGHT, BACK }
    };

    public Side[] rotatedSides() {
        return rotatedSidesArray[ordinal()];
    }

    static int[][] rotatedSidesIndices;
    static {
        rotatedSidesIndices = new int[6][6];
        for (Side facingSide: Side.values()) {
            int[] curRotatedSidesIndices = rotatedSidesIndices[facingSide.ordinal()];
            Arrays.fill(curRotatedSidesIndices, -1);

            Side[] curRotatedSides = facingSide.rotatedSides();
            for (int rotatedIdx = 0; rotatedIdx < curRotatedSides.length; ++rotatedIdx) {
                Side rotatedSide = curRotatedSides[rotatedIdx];
                curRotatedSidesIndices[rotatedSide.ordinal()] = rotatedIdx;
            }
        }
    }

    public static int rotatedSideIndex(Side facingSide, Side targetSide) {
        return rotatedSidesIndices[facingSide.ordinal()][targetSide.ordinal()];
    }

    static Affine[][] coordMapsArray = {
            { UL, UL, UL, UL },
            { LD, LD, LD, RU },
            { RU, DR, LD, UL },
            { LD, RU, RU, RU },
            { DR, RU, UL, LD },
            { DR, DR, DR, DR }
    };

    public Affine[] coordMaps() {
        return coordMapsArray[ordinal()];
    }
}
