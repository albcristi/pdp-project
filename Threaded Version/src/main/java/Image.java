import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

public class Image {
    private ExecutorService threadPool;
    private Integer height;
    private Integer width;
    private Integer[][] rValues;
    private Integer[][] gValues;
    private Integer[][] bValues;
    private Integer[][] grayScale;
    private Integer[][] sobelFilterApplied;
    private Integer[][] imgForFile;

    ReentrantLock sobelLock = new ReentrantLock();
    Integer maxGradient = -1;


    public Image(String path, ExecutorService threadPool) throws ExecutionException, InterruptedException {
        BufferedImage image = null;
        try {
            this.threadPool = threadPool;
            File file = new File(path);
            image = ImageIO.read(file);
            height = image.getWidth();
            width = image.getHeight();
        }

        catch (Exception e){
            System.out.println(e.getMessage());
            System.exit(-1);
        }
        toRgbAndGrayScale(image);
    }


    public Integer[][] getGrayScale(){
        return grayScale;
    }

    public Integer getHeight(){
        return height;
    }

    public Integer getWidth(){
        return width;
    }

    private void toRgbAndGrayScale(BufferedImage image) throws ExecutionException, InterruptedException {
        Integer noElementsPerThread = (width*height)/Main.NO_THREADS.get();
        rValues = new Integer[height][width];
        gValues = new Integer[height][width];
        bValues = new Integer[height][width];
        grayScale = new Integer[height][width];
        Integer order = 1;
        List<Future<Boolean>> tasks = new ArrayList<>();
        for(int i=0; i<Main.NO_THREADS.get(); ++i){
            if(i+1==Main.NO_THREADS.get())
                noElementsPerThread += (width*height)%Main.NO_THREADS.get();
            PairElement<Integer, Integer> startCoordinates = getElementCoordinates(width, order);
            Integer finalNoElementsPerThread = noElementsPerThread;
            Future<Boolean> task = threadPool.submit(()->{
                processRGBMatrixFormation(startCoordinates, finalNoElementsPerThread, image);
                return true;
            });
            tasks.add(task);
            order += noElementsPerThread;
        }

            for (Future<Boolean> task : tasks) {
                task.get();
            }

    }

    private void processRGBMatrixFormation(PairElement<Integer,Integer> startCoordinates,
                                           Integer numberOfElements, BufferedImage image){
        Integer row = startCoordinates.first;
        Integer column = startCoordinates.second;
        Integer computed = 0;
        while (computed < numberOfElements && row < height){
            if(column.equals(width)){
                column = 0;
                row++;
                if(row.equals(height))
                    break;
            }
            Color rgb = new Color(image.getRGB(row, column));
            rValues[row][column] = rgb.getRed();
            gValues[row][column] = rgb.getGreen();
            bValues[row][column] = rgb.getBlue();
            grayScale[row][column] =  (int) (rValues[row][column] * 0.299 + gValues[row][column] * 0.587 +
                    bValues[row][column] * 0.114);
            grayScale[row][column] = grayScale[row][column] > 255 ? 255 : grayScale[row][column];
            computed++;
            column++;
        }

    }

    private static PairElement<Integer,Integer> getElementCoordinates(Integer noColumns, Integer orderNo){
        /*
        9*9
                  c0 c1  c2  c3  c4  c5  c6  c7  c8
             l1    1   2   3   4   5   6   7   8   9
             l2   10  11  12  13  14  15  16  17  18
             l3   19  20  21  22  23  24  25  26  27
          if(n%nrCols==0)
                i = n/nrCols-1
                j=nrCols-1
                return
           i = n/nrCols
           j = n%nrCols-1
         */
        if(orderNo%noColumns==0)
            return new PairElement<>(orderNo/noColumns-1, noColumns-1);
        return new PairElement<>(orderNo/noColumns, orderNo%noColumns-1);
    }


    private void writeImageToFileTask(PairElement<Integer,Integer> startCoordinates,
                                      Integer numberOfElements, BufferedImage image){
        Integer row = startCoordinates.first;
        Integer column = startCoordinates.second;
        Integer height, width;
        height = imgForFile.length;
        width = imgForFile[0].length;
        Integer computed = 0;
        while (computed < numberOfElements && row < height){
            if(column.equals(width)){
                column = 0;
                row++;
                if(row.equals(height))
                    break;
            }
            Color rgb = new Color(imgForFile[row][column], imgForFile[row][column], imgForFile[row][column]);
            image.setRGB(row, column, rgb.getRGB());
            computed++;
            column++;
        }

    }

    public void writeToFileImage(Integer[][] img, String location, String format){
        try {
            Integer height, width;
            height = img.length;
            width = img[0].length;;
            imgForFile = img;
            Integer noElementsPerThread = (width * height) / Main.NO_THREADS.get();
            Integer order = 1;
            List<Future<Boolean>> tasks = new ArrayList<>();
            BufferedImage image = new BufferedImage(height, width, BufferedImage.TYPE_INT_RGB);
            for (int i = 0; i < Main.NO_THREADS.get(); ++i) {
                if (i + 1 == Main.NO_THREADS.get())
                    noElementsPerThread += (width * height) % Main.NO_THREADS.get();
                PairElement<Integer, Integer> startCoordinates = getElementCoordinates(width, order);
                Integer finalNoElementsPerThread = noElementsPerThread;
                Future<Boolean> task = threadPool.submit(() -> {
                    writeImageToFileTask(startCoordinates, finalNoElementsPerThread, image);
                    return true;
                });
                tasks.add(task);
                order += noElementsPerThread;
            }

            for (Future<Boolean> task : tasks) {
                task.get();
            }

            File outputFile = new File(location);

            ImageIO.write(image, format, outputFile);

        } catch (InterruptedException | ExecutionException | IOException e) {
            e.printStackTrace();
        }
    }

    public Integer[][] applySobelFilter() throws ExecutionException, InterruptedException {
        sobelFilterApplied = new Integer[height][width];

        for(int i=0; i<height; i++){
            sobelFilterApplied[i][0] = grayScale[i][0];
            sobelFilterApplied[i][width-1] =  grayScale[i][width-1];
        }
        for(int i=0; i<width; i++){
            sobelFilterApplied[0][i] = grayScale[0][i];
            sobelFilterApplied[height-1][i] =  grayScale[height-1][i];
        }
        List<Future<Boolean>> tasks = new ArrayList<>();
        Integer noElemsPerTask = (width*height)/Main.NO_THREADS.get();
        Integer order = 1;
        for(int i=0; i<Main.NO_THREADS.get(); i++){
            if(i+1==Main.NO_THREADS.get())
                noElemsPerTask+= (width*height)%Main.NO_THREADS.get();
            Integer finalNoElemsPerTask = noElemsPerTask;
            Integer finalOrder = order;
            tasks.add(threadPool.submit(()->{
                sobelTask(getElementCoordinates(width, finalOrder), finalNoElemsPerTask);
                return true;
            }));
            order+=noElemsPerTask;
        }
        for(Future<Boolean> task: tasks)
            task.get();
        grayScale = sobelFilterApplied;
        return sobelFilterApplied;
    }

    public void sobelTask(PairElement<Integer, Integer> startCoordinates, Integer noElements){
        Integer i = startCoordinates.first;
        Integer j = startCoordinates.second;
        Integer computed = 0;
        while (computed < noElements && i < height) {
            if (j.equals(width)) {
                j = 0;
                i++;
                if (i.equals(height))
                    break;
            }
            if (i != 0 && i != height - 1 && j != 0 && j != width - 1) {
                int val00 = grayScale[i - 1][j - 1];
                int val01 = grayScale[i - 1][j];
                int val02 = grayScale[i - 1][j + 1];
                int val10 = grayScale[i][j - 1];
                int val11 = grayScale[i][j];
                int val12 = grayScale[i][j + 1];
                int val20 = grayScale[i + 1][j - 1];
                int val21 = grayScale[i + 1][j];
                int val22 = grayScale[i + 1][j + 1];

                int gx = ((-1 * val00) + (0 * val01) + (1 * val02))
                        + ((-2 * val10) + (0 * val11) + (2 * val12))
                        + ((-1 * val20) + (0 * val21) + (1 * val22));

                int gy = ((-1 * val00) + (-2 * val01) + (-1 * val02))
                        + ((0 * val10) + (0 * val11) + (0 * val12))
                        + ((1 * val20) + (2 * val21) + (1 * val22));
                double gval = Math.sqrt((gx * gx) + (gy * gy));
                int g = (int) gval;
                sobelLock.lock();
                if (maxGradient < g) {
                    maxGradient = g;
                }
                sobelLock.unlock();
                sobelFilterApplied[i][j] = g;
                if (g > 255)
                    sobelFilterApplied[i][j] = 255;
                if (g < 0)
                    sobelFilterApplied[i][j] = 0;

                if(sobelFilterApplied[i][j] < 128)
                    sobelFilterApplied[i][j] = 0;
                else
                    sobelFilterApplied[i][j] = 255;
            }
            j++;
            computed++;
        }

    }


    public void setPixel(PairElement<Integer, Integer> coordinates, Integer r, Integer g, Integer b){
        System.out.println("-s-fjisduhsd");
        System.out.println(coordinates);
        System.out.println(height);
        System.out.println(width);
        if(coordinates.first < 0 || coordinates.first>height || coordinates.second<0 || coordinates.second>width)
            return;
        System.out.println(r);
        System.out.println(g);
        this.rValues[coordinates.first][coordinates.second] = r;
        this.gValues[coordinates.first][coordinates.second] = g;
        this.bValues[coordinates.first][coordinates.second] = b;
    }

    public Boolean containsPixel(PairElement<Integer, Integer> pixel){
        return pixel.first>=0&&pixel.second>=0&&pixel.first<height&&pixel.second<width;
    }
    public void writeImageToFile(String location, String format){
        try {
            Integer noElementsPerThread = (width * height) / Main.NO_THREADS.get();
            Integer order = 1;
            List<Future<Boolean>> tasks = new ArrayList<>();
            BufferedImage image = new BufferedImage(height, width, BufferedImage.TYPE_INT_RGB);
            for (int i = 0; i < Main.NO_THREADS.get(); ++i) {
                if (i + 1 == Main.NO_THREADS.get())
                    noElementsPerThread += (width * height) % Main.NO_THREADS.get();
                PairElement<Integer, Integer> startCoordinates = getElementCoordinates(width, order);
                Integer finalNoElementsPerThread = noElementsPerThread;
                Future<Boolean> task = threadPool.submit(() -> {
                    writeColoredImageToFile(startCoordinates, finalNoElementsPerThread, image);
                    return true;
                });
                tasks.add(task);
                order += noElementsPerThread;
            }

            for (Future<Boolean> task : tasks) {
                task.get();
            }

            File outputFile = new File(location);

            ImageIO.write(image, format, outputFile);

        } catch (InterruptedException | ExecutionException | IOException e) {
            e.printStackTrace();
        }
    }

    private void  writeColoredImageToFile(PairElement<Integer,Integer> startCoordinates,
                                          Integer numberOfElements, BufferedImage image){
        Integer row = startCoordinates.first;
        Integer column = startCoordinates.second;
        Integer computed = 0;
        while (computed < numberOfElements && row < height){
            if(column.equals(width)){
                column = 0;
                row++;
                if(row.equals(height))
                    break;
            }
            Color rgb = new Color(rValues[row][column], gValues[row][column], bValues[row][column]);
            image.setRGB(row, column, rgb.getRGB());
            computed++;
            column++;
        }

    }
}
