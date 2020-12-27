import jdk.internal.net.http.common.Pair;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class Image {
    private ExecutorService threadPool;
    private Integer height;
    private Integer width;
    private Integer[][] rValues;
    private Integer[][] gValues;
    private Integer[][] bValues;
    private Integer[][] grayScale;

    public Image(String path, ExecutorService threadPool) throws ExecutionException, InterruptedException {
        BufferedImage image = null;
        try {
            this.threadPool = threadPool;
            File file = new File(path);
            image = ImageIO.read(file);
            height = image.getHeight();
            width = image.getWidth();
        }
        catch (Exception e){
            System.out.println(e.getMessage());
            System.exit(-1);
        }
        toRgbAndGrayScale(image);

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
}
