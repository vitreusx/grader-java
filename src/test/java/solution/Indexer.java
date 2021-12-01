package solution;

public class Indexer {
    public Indexer(int size) {
        this.size = size;
        this.dataLength = 6*size*size;
    }

    public int index(Side facingSide, Side rotatedSide, int layer, int offset) {
        int rotatedSideIndex = Side.rotatedSideIndex(facingSide, rotatedSide);
        Coord coordMap = facingSide.coordMaps()[rotatedSideIndex];
        int row = coordMap.row(size, layer, offset);
        int column = coordMap.column(size, layer, offset);
        return column + size * (row + size * rotatedSide.ordinal());
    }

    public final int size, dataLength;
}
