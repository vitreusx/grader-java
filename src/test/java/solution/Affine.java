package solution;

public enum Affine {
    UL, LD, DR, RU;

    public int row(int size, int layer, int offset) {
        switch (this) {
            case UL: return layer;
            case LD: return offset;
            case DR: return size-1-layer;
            case RU: return size-1-offset;
            default: return -1;
        }
    }

    public int column(int size, int layer, int offset) {
        switch (this) {
            case UL: return size-1-offset;
            case LD: return layer;
            case DR: return offset;
            case RU: return size-1-layer;
            default: return -1;
        }
    }
}
