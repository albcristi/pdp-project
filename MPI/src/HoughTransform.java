import mpi.MPI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;


public class HoughTransform {
    // TAGS FOR BUILDING THE HOUGH ARRAY
    public static int BUILD_HOUGH_IN_PARAMS_TAG = 16;
    public static int BUILD_HOUGH_IN_IMG_TAG = 17;
    public static int BUILD_HOUGH_RES_ARR_TAG = 18;

    // TAGS FOR GET EDGE POINTS
    public static int EDGE_POINTS_IN_PARAMS_TAG = 19;
    public static int EDGE_POINTS_IN_HOUGH_TAG = 20;
    public static int EDGE_POINTS_RES_ARRAY_TAG = 22;
    public static int EDGE_POINTS_RES_SIZE_TAG = 21;

    private Image image;
    private int[][] houghArray;
    private int[][] houghWithoutThreshHold;
    private int[][] filteredImage;
    private Integer globalMaximum = -1;
    private static Double THRESHOLD = 0.3;

    public HoughTransform(Image image){
        this.image = image;
        int rValue = (int) Math.sqrt(image.getWidth()*image.getWidth()+image.getHeight()*image.getHeight());
        houghArray = new int[180]
                [2 * rValue];
        for(int i=0; i<180; i++)
            for(int j=0; j<2*rValue; j++) {
                houghArray[i][j] = 0;
            }
        image.applySobelMaster();
        filteredImage = image.getSobelImage();
        executeHoughTransform();
    }

    private static PairElement<Integer,Integer> getElementCoordinates(Integer noColumns, Integer orderNo){
        if(orderNo%noColumns==0)
            return new PairElement<>(orderNo/noColumns-1, noColumns-1);
        return new PairElement<>(orderNo/noColumns, orderNo%noColumns-1);
    }

    public static void buildHoughArrayWorker(){
        if(MPI.COMM_WORLD.Rank() == 0)
            return;
        System.out.println("Worker ID: "+MPI.COMM_WORLD.Rank()+
                " ---> Entered buildHoughArrayWorker()");
        int[] params = new int[4];
        MPI.COMM_WORLD.Recv(params, 0, 5, MPI.INT, 0,
                HoughTransform.BUILD_HOUGH_IN_PARAMS_TAG);
        int[][] image = new int[params[0]][params[1]];
        MPI.COMM_WORLD.Recv(image, 0, params[0], MPI.OBJECT, 0,
                HoughTransform.BUILD_HOUGH_IN_IMG_TAG);
        int initialR = (int) Math.sqrt(params[0] * params[0] + params[1] * params[1]);
        int[][] houghArray = new int[180][2*initialR];
        for(int i=0; i<180; i++)
            for(int j=0; j<2*initialR; j++)
                houghArray[i][j] = 0;
        Integer row = getElementCoordinates(params[1],params[3]).first;
        Integer column = getElementCoordinates(params[1], params[3]).second;
        int computed = 0;
        while (computed < params[2] && row < image.length){
            if(column.equals(image[0].length)){
                column = 0;
                row++;
                if(row.equals(image.length))
                    break;
            }
            if (image[row][column] != 0) {
                for (int angle = 0; angle < 180; angle++) {
                    int r = (int) (row * Math.cos(Math.toRadians(angle)) + column * Math.sin(Math.toRadians(angle)));
                    houghArray[angle][r + initialR] += 1;
                }
            }
            computed++;
            column++;
        }
        MPI.COMM_WORLD.Send(houghArray,0, 180, MPI.OBJECT, 0,
                HoughTransform.BUILD_HOUGH_RES_ARR_TAG);
        System.out.println("Worker ID: "+MPI.COMM_WORLD.Rank()+
                " ---> Finished buildHoughArrayWorker()");
    }

    private void buildHoughArrayMaster(){
        if(MPI.COMM_WORLD.Rank() != 0)
            return;
        Integer initialR = (int) Math.sqrt(image.getWidth() * image.getWidth() + image.getHeight() * image.getHeight());
        int noPerProcess = (image.getHeight()*image.getWidth())/Main.noOfProcesses;
        int order = 1;
        for(int i=0; i<Main.noOfProcesses; i++){
            if(i+1==Main.noOfProcesses){
                noPerProcess+= (image.getHeight()*image.getWidth())%Main.noOfProcesses;
            }
            else{
                // send and array with : [height, width, noElements, order]
                // send filterImage matrix
                int[] params = new int[4];
                params[0] = image.getHeight(); params[1]=image.getWidth(); params[2] = noPerProcess;
                params[3] = order;
                MPI.COMM_WORLD.Send(params, 0, 4, MPI.INT, i+1,
                        HoughTransform.BUILD_HOUGH_IN_PARAMS_TAG);
                MPI.COMM_WORLD.Send(filteredImage, 0, image.getHeight(), MPI.OBJECT, i+1,
                        HoughTransform.BUILD_HOUGH_IN_IMG_TAG);
                order += noPerProcess;
            }
        }
        // master will process its part of data
        Integer row = getElementCoordinates(image.getWidth(), order).first;
        Integer column = getElementCoordinates(image.getWidth(), order).second;
        int computed = 0;
        while (computed <noPerProcess && row <image.getHeight()){
            if(column.equals(image.getWidth())){
                column = 0;
                row++;
                if(row.equals(image.getHeight()))
                    break;
            }
            if (filteredImage[row][column] != 0) {
                for (int angle = 0; angle < 180; angle++) {
                    int r = (int) (row * Math.cos(Math.toRadians(angle)) + column * Math.sin(Math.toRadians(angle)));
                    houghArray[angle][r + initialR] += 1;
                }
            }
            computed++;
            column++;
        }
        // time to gather partial results from workers
        for(int i=0; i<Main.noOfProcesses-1; i++){
            int[][] partialHough = new int[180][2*initialR];
            MPI.COMM_WORLD.Recv(partialHough, 0, 180, MPI.OBJECT,i+1,
                    HoughTransform.BUILD_HOUGH_RES_ARR_TAG);
            for(int x=0; x<180; x++)
                for(int y=0; y<2*initialR; y++) {
                    houghArray[x][y] += partialHough[x][y];
                    if(i+1==Main.noOfProcesses-1 && houghArray[x][y]>globalMaximum)
                        globalMaximum = houghArray[x][y];
                }
        }
    }

    private void executeHoughTransform(){
        buildHoughArrayMaster();
        int rValue = (int) Math.sqrt(image.getWidth()*image.getWidth()+image.getHeight()*image.getHeight());
        int neighbourhoodSize = 4;
        houghWithoutThreshHold = houghArray;
        //apply threshold
        // TODO: PARALLELIZE
        for (int x = 0; x < 180; x++) {
            for (int y = 0; y < 2 * rValue; y++) {
                System.out.println(houghArray[x][y]);
                if (houghArray[x][y] < THRESHOLD * globalMaximum)
                    houghArray[x][y] = 0;
            }
        }

        // Search for local peaks above threshold to draw
        for (int t = 0; t < 180; t++) {
            loop:
            for (int r = neighbourhoodSize; r < 2 * rValue - neighbourhoodSize; r++) {
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

    public static void findEdgePointsWorker(){
        if(MPI.COMM_WORLD.Rank() == 0)
            return;
        System.out.println("Worker ID: "+MPI.COMM_WORLD.Rank()+
                " ---> findEdgeWorker started");
        List<PairElement<Integer, Integer>> points =  new ArrayList<>();
        int[] params =  new int[4];
        MPI.COMM_WORLD.Recv(params, 0, 4, MPI.INT, 0,
                HoughTransform.EDGE_POINTS_IN_PARAMS_TAG);
        int initialR = (int) Math.sqrt(params[0]*params[0]+params[1]*params[1]);
        int[][] houghArray = new int[180][2*initialR];
        MPI.COMM_WORLD.Recv(houghArray, 0, 180, MPI.OBJECT, 0,
                HoughTransform.EDGE_POINTS_IN_HOUGH_TAG);
        Integer row = getElementCoordinates(params[1], params[3]).first;
        Integer column = getElementCoordinates(params[1], params[3]).second;
        int computed = 0;
        while (computed < params[2] && row < 180){
            if(column.equals(2*initialR)){
                column = 0;
                row++;
                if(row.equals(180))
                    break;
            }
            if (houghArray[row][column] != 0) {
                for (int i = 0; i < params[0]; i++)
                    for (int j = 0; j < params[1]; j++) {
                        if (j == (int)((-Math.cos(Math.toRadians(row))/Math.sin(Math.toRadians(row))) * i
                                + (column - initialR)/Math.sin(Math.toRadians(row))) ){
                            points.add(new PairElement<>(i, j));
                        }
                    }
            }
            computed++;
            column++;
        }
        int[] elems = new int[points.size()*2];
        for(int i=0; i<points.size(); i++){
            elems[i] = points.get(i).first;
            elems[points.size()+i] = points.get(i).second;
        }

        int sz[] = new int[1];
        sz[0]=points.size();
        MPI.COMM_WORLD.Send(sz, 0, 1, MPI.INT, 0,
                HoughTransform.EDGE_POINTS_RES_SIZE_TAG);
        MPI.COMM_WORLD.Send(elems, 0, points.size()*2, MPI.INT, 0,
                HoughTransform.EDGE_POINTS_RES_ARRAY_TAG);
        System.out.println("Worker ID: "+MPI.COMM_WORLD.Rank()+
                " ---> findEdgeWorker finished");
    }

    private List<PairElement<Integer, Integer>> findEdgePointsMaster(){
        if(MPI.COMM_WORLD.Rank() != 0)
            return  new ArrayList<>();
        List<PairElement<Integer, Integer>> points = new ArrayList<>();
        //split work between processes
        int order = 1;
        int noPerProcess = (180*houghArray[0].length)/Main.noOfProcesses;
        for(int i=0; i<Main.noOfProcesses; i++){
            if(i+1==Main.noOfProcesses){
                noPerProcess += (180*houghArray[0].length)%Main.noOfProcesses;
            }
            else{
                int[] params = new int[4];
                params[0] = image.getHeight(); params[1] = image.getWidth(); params[2] = noPerProcess;
                params[3] = order;
                MPI.COMM_WORLD.Send(params,0,4,MPI.INT, i+1,
                        HoughTransform.EDGE_POINTS_IN_PARAMS_TAG);
                MPI.COMM_WORLD.Send(houghArray, 0,180, MPI.OBJECT, i+1,
                        HoughTransform.EDGE_POINTS_IN_HOUGH_TAG);
            }
        }
        //master processing its part of the job
        int initialR = (int) Math.sqrt(image.getWidth()*image.getWidth()+
                image.getHeight()*image.getHeight());
        Integer row = getElementCoordinates(image.getWidth(), order).first;
        Integer column = getElementCoordinates(image.getWidth(), order).second;
        int computed = 0;
        while (computed <noPerProcess && row < 180){
            if(column.equals(2*initialR)){
                column = 0;
                row++;
                if(row.equals(180))
                    break;
            }
            if (houghArray[row][column] != 0) {
                for (int i = 0; i < image.getHeight(); i++)
                    for (int j = 0; j <image.getWidth(); j++) {
                        if (j == (int)((-Math.cos(Math.toRadians(row))/Math.sin(Math.toRadians(row))) * i
                                + (column - initialR)/Math.sin(Math.toRadians(row))) ){
                            points.add(new PairElement<>(i, j));
                        }
                    }
            }
            computed++;
            column++;
        }
        // gather results from worker processes
        for(int i=0; i<Main.noOfProcesses-1; i++){
            int[] size = new int[1];
            MPI.COMM_WORLD.Recv(size, 0, 1 , MPI.INT, i+1,
                    HoughTransform.EDGE_POINTS_RES_SIZE_TAG);
            int[] elems = new int[size[0]*2];
            MPI.COMM_WORLD.Recv(elems, 0, size[0]*2, MPI.INT, i+1,
                    HoughTransform.EDGE_POINTS_RES_ARRAY_TAG);
            for(int j=0; j<size[0]; j++)
                points.add(new PairElement<>(elems[j],elems[j+size[0]]));
        }
        return points;
    }

    public Image putLinesOnImage(){
        List<PairElement<Integer, Integer>> points = findEdgePointsMaster();
        for(PairElement<Integer, Integer> point: points){
            image.setPixel(point, 124,252,0);
        }
        return image;
    }
}
