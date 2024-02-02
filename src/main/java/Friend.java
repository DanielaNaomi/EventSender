public class Friend {
    private final String name;
    private final int id;
    private final String figure;

    public Friend(int id, String name, String figure) {
        this.id = id;
        this.name = name;
        this.figure = figure;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public String getFigure() {
        return figure;
    }

    @Override
    public String toString() {
        return name;
    }
}
