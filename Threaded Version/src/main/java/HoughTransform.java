import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class HoughTransform {
    private Image image;
    private ExecutorService threadPool;
    private Integer[][] houghArray;
    private Integer[][] filteredImage;
    private Integer globalMaximum = -1;

    public HoughTransform(Image image, ExecutorService threadPool) throws ExecutionException, InterruptedException {
        this.image = image;
        this.threadPool = threadPool;
        Integer rValue = (int) Math.sqrt(image.getWidth()*image.getWidth()+image.getHeight()*image.getHeight());
        houghArray = new Integer[180]
                [2 * rValue];
        for(int i=0; i<180; i++)
            for(int j=0; j<2*rValue; j++)
                houghArray[i][j] = 0;
        filteredImage = image.applySobelFilter();
        executeHoughTransform();
    }

    private void executeHoughTransform() {
        Integer initialR = (int) Math.sqrt(image.getWidth() * image.getWidth() + image.getHeight() * image.getHeight());
        System.out.println(houghArray[0].length);
        for (int x = 0; x < image.getHeight(); x++)
            for (int y = 0; y < image.getWidth(); y++)
                if (filteredImage[x][y] != 0) {
                    for (int angle = 0; angle < 180; angle++) {
                        Integer r = (int) (x * Math.cos(Math.toRadians(angle)) + y * Math.sin(Math.toRadians(angle)));
                        houghArray[angle][r + initialR] += 1;
                        if (houghArray[angle][r + initialR] > globalMaximum) {
                            globalMaximum = houghArray[angle][r + initialR];
                        }

                    }
                }

//        if(globalMaximum > 260)
//            globalMaximum = 255;

        for (int x = 0; x < 180; x++) {
            for (int y = 0; y < 2 * initialR; y++) {
                if (houghArray[x][y] < 0.3 * globalMaximum)
                    houghArray[x][y] = 0;
//                if(houghArray[x][y] > 255)
//                    houghArray[x][y] = 255;
            }
        }

        int neighbourhoodSize = 10;
        // Search for local peaks above threshold to draw
        for (int t = 0; t < 180; t++) {
            loop:
            for (int r = neighbourhoodSize; r < 2 * initialR - neighbourhoodSize; r++) {

                // Only consider points above threshold
                if (houghArray[t][r] > 0.3 * globalMaximum) {

                    int peak = houghArray[t][r];

                    // Check that this peak is indeed the local maxima
                    for (int dx = -neighbourhoodSize; dx <= neighbourhoodSize; dx++) {
                        for (int dy = -neighbourhoodSize; dy <= neighbourhoodSize; dy++) {
                            int dt = t + dx;
                            int dr = r + dy;
                            if (dt < 0) dt = dt + 180;
                            else if (dt >= 180) dt = dt - 180;
                            if (houghArray[dt][dr] > peak) {
                                // found a bigger point nearby, skip
                                houghArray[t][r] = 0;
                                continue loop;
                            }
                        }
                    }

                }
            }
        }
    }

    public Integer[][] getHoughArray(){
        return this.houghArray;
    }

    public List<PairElement<Integer, Integer>> getEdgePoints(){
        List<PairElement<Integer, Integer>> points = new ArrayList<>();
        int initialR = (int) Math.sqrt(image.getWidth()*image.getWidth()+image.getHeight()*image.getHeight());
        for(int angle=0; angle<180; angle++)
            for(int r=0; r<houghArray[0].length; r++)
            if(houghArray[angle][r] != 0) {
                for (int i = 0; i < image.getHeight(); i++)
                    for (int j = 0; j < image.getWidth(); j++) {

                        if (j == (int)((-Math.cos(Math.toRadians(angle))/Math.sin(Math.toRadians(angle))) * i + (r - initialR)/Math.sin(Math.toRadians(angle))) ){

                            points.add(new PairElement<>(i, j));
                        }
                    }
            }
        return points;
    }
    public void putLinesOnImage(){
        List<PairElement<Integer, Integer>> points = getEdgePoints();
        for(PairElement<Integer, Integer> p1: points){
                    image.setPixel(p1,124,252,0);
                }
    }
}
