import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

public class HoughTransform {
    private Image image;
    private ExecutorService threadPool;
    private Integer[][] houghArray;
    private ReentrantLock[][] houghElemsLock;
    private Integer[][] houghWithoutThreshHold;
    private Integer[][] filteredImage;
    private Integer globalMaximum = -1;
    private static Double THRESHOLD = 0.3;

    public HoughTransform(Image image, ExecutorService threadPool) throws ExecutionException, InterruptedException {
        this.image = image;
        this.threadPool = threadPool;
        Integer rValue = (int) Math.sqrt(image.getWidth()*image.getWidth()+image.getHeight()*image.getHeight());
        houghArray = new Integer[180]
                [2 * rValue];
        houghElemsLock = new ReentrantLock[180][2*rValue];
        for(int i=0; i<180; i++)
            for(int j=0; j<2*rValue; j++) {
                houghArray[i][j] = 0;
                houghElemsLock[i][j] = new ReentrantLock();
            }
        filteredImage = image.applySobelFilter();
        executeHoughTransform();
    }

    private PairElement<Integer, Integer> getCoordinates(Integer orderNo, Integer noColumns){
        if(orderNo%noColumns==0)
            return new PairElement<>(orderNo/noColumns-1, noColumns-1);
        return new PairElement<>(orderNo/noColumns, orderNo%noColumns-1);
    }

    private void executeHoughTransformTask(PairElement<Integer, Integer> startCoordinates,
                                           Integer noElems, Integer[][] image){
        Integer row = startCoordinates.first;
        Integer column = startCoordinates.second;
        Integer computed = 0;
        Integer initialR = (int) Math.sqrt(this.image.getWidth() * this.image.getWidth()
                + this.image.getHeight() * this.image.getHeight());
        while (computed < noElems && row < image.length){
            if(column.equals(image[0].length)){
                column = 0;
                row++;
                if(row.equals(image.length))
                    break;
            }
            if (image[row][column] != 0) {
                for (int angle = 0; angle < 180; angle++) {
                    Integer r = (int) (row * Math.cos(Math.toRadians(angle)) + column * Math.sin(Math.toRadians(angle)));
                    houghElemsLock[angle][r+initialR].lock();
                    houghArray[angle][r + initialR] += 1;
                    if (houghArray[angle][r + initialR] > globalMaximum) {
                        globalMaximum = houghArray[angle][r + initialR];
                    }
                    houghElemsLock[angle][r+initialR].unlock();

                }
            }
            computed++;
            column++;
        }
    }

    private void executeHoughTransform() throws ExecutionException, InterruptedException {
        Integer initialR = (int) Math.sqrt(image.getWidth() * image.getWidth() + image.getHeight() * image.getHeight());
        Integer elemsPerTask = (image.getHeight()*image.getWidth())/Main.NO_THREADS.get();
        Integer order = 1;
        List<Future<Boolean>> tasks = new ArrayList<>();
        for(int task=0; task<Main.NO_THREADS.get(); ++task){
            if(task+1==Main.NO_THREADS.get()){
                elemsPerTask += (image.getHeight()*image.getWidth())%Main.NO_THREADS.get();
                PairElement<Integer, Integer> start = getCoordinates(order, image.getWidth());
                Integer finalElemsPerTask = elemsPerTask;
                tasks.add(threadPool.submit(()->{
                    executeHoughTransformTask(start, finalElemsPerTask, filteredImage);
                    return true;
                }));
            }
        }
        for(Future<Boolean> task: tasks)
            task.get();

        houghWithoutThreshHold = houghArray;
        for (int x = 0; x < 180; x++) {
            for (int y = 0; y < 2 * initialR; y++) {
                if (houghArray[x][y] < THRESHOLD * globalMaximum)
                    houghArray[x][y] = 0;
            }
        }

        int neighbourhoodSize = 4;
        // Search for local peaks above threshold to draw
        for (int t = 0; t < 180; t++) {
            loop:
            for (int r = neighbourhoodSize; r < 2 * initialR - neighbourhoodSize; r++) {
                // Only consider points above threshold
                if (houghArray[t][r] > THRESHOLD* globalMaximum) {
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

    public Integer[][] getHoughWithoutThreshHold(){
        return this.houghWithoutThreshHold;
    }

    public List<PairElement<Integer, Integer>> getEdgePoints() throws ExecutionException, InterruptedException {
        List<PairElement<Integer, Integer>> points = new ArrayList<>();
        Integer order = 1;
        Integer noPerThread = (180*houghArray[0].length)/Main.NO_THREADS.get();
        List<Future<List<PairElement<Integer, Integer>>>> tasks = new ArrayList<>();
        for(int threadNo=0; threadNo<Main.NO_THREADS.get(); threadNo++){
            if(threadNo+1==Main.NO_THREADS.get()){
                noPerThread += (180*houghArray[0].length)%Main.NO_THREADS.get();
            }
            PairElement<Integer, Integer> start = getCoordinates(order, houghArray[0].length);
            Integer finalNoPerThread = noPerThread;
            tasks.add(threadPool.submit(()->{
                return createEdgePointsTask(start, finalNoPerThread, houghArray);
            }));
            order += noPerThread;
        }

        for(Future<List<PairElement<Integer, Integer>>> task: tasks)
            points.
                    addAll(task.get());
        return points;
    }

    public List<PairElement<Integer, Integer>> createEdgePointsTask(PairElement<Integer, Integer> startCoordinates,
                                     Integer noElems, Integer[][] image){
        List<PairElement<Integer, Integer>> points =  new ArrayList<>();
        Integer row = startCoordinates.first;
        Integer column = startCoordinates.second;
        Integer computed = 0;
        Integer initialR = (int) Math.sqrt(this.image.getWidth() * this.image.getWidth()
                + this.image.getHeight() * this.image.getHeight());
        while (computed < noElems && row < image.length){
            if(column.equals(image[0].length)){
                column = 0;
                row++;
                if(row.equals(image.length))
                    break;
            }
            if (image[row][column] != 0) {
                for (int i = 0; i < filteredImage.length; i++)
                    for (int j = 0; j < filteredImage[0].length; j++) {

                        if (j == (int)((-Math.cos(Math.toRadians(row))/Math.sin(Math.toRadians(row))) * i
                                + (column - initialR)/Math.sin(Math.toRadians(row))) ){

                            points.add(new PairElement<>(i, j));
                        }
                    }
            }
            computed++;
            column++;
        }
        return points;
    }

    public void putLinesOnImage() throws ExecutionException, InterruptedException {
        List<PairElement<Integer, Integer>> points = getEdgePoints();
        for(PairElement<Integer, Integer> p1: points){
                    image.setPixel(p1,124,252,0);
                }
    }
}
