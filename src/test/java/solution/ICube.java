package solution;

public interface ICube {
    void rotate(int side, int layer) throws InterruptedException;
    String show() throws InterruptedException;
}
