public class PairElement<K,T> {
    public K first;
    public T second;

    public PairElement(K first, T second){
        this.first = first;
        this.second = second;
    }

    @Override
    public String toString() {
        return "PairElement{" +
                "first=" + first +
                ", second=" + second +
                '}';
    }
}
