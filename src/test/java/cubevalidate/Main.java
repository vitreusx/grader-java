package cubevalidate;

import solution.ICube;

import java.util.function.Function;

public class Main {
    private static final String EXPECTED1 =
            "0000"
                    + "0000"
                    + "0000"
                    + "1111"

                    + "1115"
                    + "1115"
                    + "4444"
                    + "1115"

                    + "2222"
                    + "2222"
                    + "1115"
                    + "2222"

                    + "0333"
                    + "0333"
                    + "2222"
                    + "0333"

                    + "4444"
                    + "4444"
                    + "0333"
                    + "4444"

                    + "3333"
                    + "5555"
                    + "5555"
                    + "5555";

    static String prettyPrintCube(String state) {
        Function<Integer, String> s = (n) -> state.substring(3*n, 3*n+3);
        String sp = " ".repeat(3);

        String flat = sp + s.apply(0) + sp + sp + "\n"
                + sp + s.apply(1) + sp + sp + "\n"
                + sp + s.apply(2) + sp + sp + "\n"
                + s.apply(3) + s.apply(6) + s.apply(9) + s.apply(12) + "\n"
                + s.apply(4) + s.apply(7) + s.apply(10) + s.apply(13) + "\n"
                + s.apply(5) + s.apply(8) + s.apply(11) + s.apply(14) + "\n"
                + sp + s.apply(15) + sp + sp + "\n"
                + sp + s.apply(16) + sp + sp + "\n"
                + sp + s.apply(17) + sp + sp + "\n";

        StringBuilder builder = new StringBuilder();
        for (Character c: flat.toCharArray()) {
            builder.append("  ");
            builder.append(c);
        }

        return builder.toString();
    }

    static String validateSeq1(ICube cube) throws InterruptedException {
        // This is the sequence fetched from the Validate.java file
        cube.rotate(2, 0);
        cube.rotate(5, 1);
        return cube.show();
    }

    static String validateSeq2(ICube cube) throws InterruptedException {
        // This is a sequence that I found
        cube.rotate(1, 1);
        cube.rotate(0, 2);
        cube.rotate(3, 2);
        return cube.show();
    }

    public static void main(String[] args) throws InterruptedException {
        solution.Cube cube1 = new solution.Cube(4, (x, y) -> {}, (x, y) -> {}, () -> {}, () -> {});
        othersol.Cube cube2 = new othersol.Cube(4, (x, y) -> {}, (x, y) -> {}, () -> {}, () -> {});

        System.out.println("The original validate procedure:");
        System.out.printf("Reference cube: %b%n", validateSeq1(cube1).equals(EXPECTED1));
        System.out.printf("Other solution: %b%n", validateSeq1(cube2).equals(EXPECTED1));

        solution.Cube cube3 = new solution.Cube(3, (x, y) -> {}, (x, y) -> {}, () -> {}, () -> {});
        othersol.Cube cube4 = new othersol.Cube(3, (x, y) -> {}, (x, y) -> {}, () -> {}, () -> {});
        boolean agree = validateSeq2(cube3).equals(validateSeq2(cube4));
        System.out.printf("The solutions agree on another sequence: %b%n", agree);

        System.out.println("Reference solution state:");
        System.out.println(prettyPrintCube(cube3.show()));

        System.out.println("Other solution state:");
        System.out.println(prettyPrintCube(cube4.show()));
    }
}
