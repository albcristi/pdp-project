import java.util.concurrent.ExecutorService;

public class HoughTransform {
    private Image image;
    private ExecutorService threadPool;
    private PairElement<Integer, Integer> currentPixel;
    private Integer[][] houghArray;
    private Integer degrees;
    private Double radians;

    public HoughTransform(Image image, ExecutorService threadPool){
        this.image = image;
        this.threadPool = threadPool;
        currentPixel = new PairElement<>(0,0);
        houghArray = new Integer[180][image.getWidth()];
        for(int i=0; i<180; i++)
            for(int j=0; j<image.getWidth(); j++)
                houghArray[i][j] = 0;
        degrees = 0;
        radians = 0.0;
    }

}
