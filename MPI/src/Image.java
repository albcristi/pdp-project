import mpi.MPI;
import mpi.Min;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.Serializable;


public class Image implements Serializable {
    // TAGS FOR RGB AND GRAY IMG PROC
    public static int RGB_AND_GRAY_INPUT_ARRAY_SIZE_TAG = 1;
    public static int RGB_AND_GRAY_INPUT_ARRAY_TAG = 2;
    public static int RGB_AND_GRAY_RESULT_R_TAG = 3;
    public static int RGB_AND_GRAY_RESULT_G_TAG = 4;
    public static int RGB_AND_GRAY_RESULT_B_TAG = 5;
    public static int RGB_AND_GRAY_RESULT_GS_TAG = 6;

    // TAGS FOR WRITE TO FILE IMG  -- Image Construction
    public static int WRITE_FILE_IN_SIZE_TAG = 7;
    public static int WRITE_FILE_IN_R_TAG = 8;
    public static int WRITE_FILE_IN_G_TAG = 9;
    public static int WRITE_FILE_IN_B_TAG = 10;
    public static int WRITE_FILE_RESULT_IMG_TAG = 11;
    
    // SOBEL
    public static int SOBEL_IN_PARAMS_TAG = 12;
    public static int SOBEL_IN_GRAY_SCALE_TAG = 13;
    public static int SOBEL_RES_ARRAY = 15;

    

    private int[][] rValues;
    private int[][] gValues;
    private Integer height;
    private Integer width;
    private int[][] bValues;
    private int[][] grayScale;
    private int[][] sobelFilterApplied;
    private int[][] imgForFile;

    public Image(String pathToImage){
        BufferedImage image = null;
        try {
            File file = new File(pathToImage);
            image = ImageIO.read(file);
            height = image.getWidth();
            width = image.getHeight();
        }

        catch (Exception e){
            System.out.println(e.getMessage());
            System.exit(-1);
        }
        toRgbAndGrayScaleMaster(image);
    }

    public int getWidth(){
        return this.width;
    }

    public int getHeight(){
        return this.height;
    }

    public int[][] getSobelImage(){
        return this.sobelFilterApplied;
    }

    private static PairElement<Integer,Integer> getElementCoordinates(Integer noColumns, Integer orderNo){
        if(orderNo%noColumns==0)
            return new PairElement<>(orderNo/noColumns-1, noColumns-1);
        return new PairElement<>(orderNo/noColumns, orderNo%noColumns-1);
    }

    public static void toRgbAndGrayScaleWorker(){
        int[] chunkSize = new int[1];
        MPI.COMM_WORLD.Recv(chunkSize, 0, 1,MPI.INT, 0,
                Image.RGB_AND_GRAY_INPUT_ARRAY_SIZE_TAG);
        System.out.println("Worker ID: "+MPI.COMM_WORLD.Rank()+" entered toRGBandGrayScale");
        int[] chunkData = new int[chunkSize[0]];
        System.out.println("Worker ID: "+MPI.COMM_WORLD.Rank()+" chunk data size: "+chunkSize[0]);
        MPI.COMM_WORLD.Recv(chunkData, 0, chunkSize[0], MPI.INT, 0,
                Image.RGB_AND_GRAY_INPUT_ARRAY_TAG);
        System.out.println("Worker ID: "+MPI.COMM_WORLD.Rank()+" finished toRGBandGrayScale");
        int size = chunkSize[0];
        int[] rValuesChunk = new int[size];
        int[] gValuesChunk = new int[size];
        int[] bValuesChunk = new int[size];
        int[] gsChunk = new int[size];
        for(int i=0; i<size; i++){
            Color color = new Color(chunkData[i]);
            rValuesChunk[i] = color.getRed();
            gValuesChunk[i] = color.getGreen();
            bValuesChunk[i] = color.getBlue();
            gsChunk[i] = (int) (rValuesChunk[i] * 0.299 + gValuesChunk[i] * 0.587 +
                    bValuesChunk[i] * 0.114);
        }
        MPI.COMM_WORLD.Send(rValuesChunk,0, size, MPI.INT, 0,
                Image.RGB_AND_GRAY_RESULT_R_TAG);
        MPI.COMM_WORLD.Send(gValuesChunk, 0, size, MPI.INT, 0,
                Image.RGB_AND_GRAY_RESULT_G_TAG);
        MPI.COMM_WORLD.Send(bValuesChunk, 0, size, MPI.INT, 0,
                Image.RGB_AND_GRAY_RESULT_B_TAG);
        MPI.COMM_WORLD.Send(gsChunk, 0, size, MPI.INT, 0,
                Image.RGB_AND_GRAY_RESULT_GS_TAG);
    }

    private void toRgbAndGrayScaleMaster(BufferedImage image){
        int noElementsPerNode = (width*height)/Main.noOfProcesses;
        rValues = new int[height][width];
        gValues = new int[height][width];
        bValues = new int[height][width];
        grayScale = new int[height][width];
        int order = 1;
        int[] masterValues = new int[noElementsPerNode+(width * height) % Main.noOfProcesses];
        for(int i=0; i<Main.noOfProcesses; ++i){
            if(i+1==Main.noOfProcesses) {
                noElementsPerNode += (width * height) % Main.noOfProcesses;
                for(int j=0; j<noElementsPerNode; j++){
                    PairElement<Integer, Integer> coordinates = getElementCoordinates(width, order+j);
                    masterValues[j] = image.getRGB(coordinates.first, coordinates.second);
                }
            }
            else {
                // Step1 - build array of rgb values from start to end
                int[] values = new int[noElementsPerNode];
                for(int j=0; j<noElementsPerNode; j++){
                    PairElement<Integer, Integer> coordinates = getElementCoordinates(width, order+j);
                    values[j] = image.getRGB(coordinates.first, coordinates.second);
                }
                // Step2 - send array
                //   \__ in another node: build array of rgb and gray scale values
                // Send Array Size
                int[] size = new int[1];
                size[0] =  noElementsPerNode;
                MPI.COMM_WORLD.Ssend(size,0,1,MPI.INT, i+1,
                        Image.RGB_AND_GRAY_INPUT_ARRAY_SIZE_TAG);
                MPI.COMM_WORLD.Ssend(values, 0, size[0], MPI.INT, i+1,
                        Image.RGB_AND_GRAY_INPUT_ARRAY_TAG);
                order += noElementsPerNode;
            }
        }

        // master will also process data (compute its own chunk of data)
        int size = noElementsPerNode;
        int[] rValuesChunk = new int[size];
        int[] gValuesChunk = new int[size];
        int[] bValuesChunk = new int[size];
        int[] gsChunk = new int[size];
        for(int i=0; i<size; i++){
            Color color = new Color(masterValues[i]);
            rValuesChunk[i] = color.getRed();
            gValuesChunk[i] = color.getGreen();
            bValuesChunk[i] = color.getBlue();
            gsChunk[i] = (int) (rValuesChunk[i] * 0.299 + gValuesChunk[i] * 0.587 +
                    bValuesChunk[i] * 0.114);
        }
        // gather back data from workers and process results
        order = 1;
        // for ....
        //    \__ > retrieve rValues, gValues, bValues and grayScaleValues in range [start, end)
        noElementsPerNode = (width*height)/Main.noOfProcesses;
        for(int i=0; i<Main.noOfProcesses; ++i){
           if(i+1==Main.noOfProcesses){
               noElementsPerNode += (width * height) % Main.noOfProcesses;
           }
            int[] rv = new int[noElementsPerNode];
            int[] gv = new int[noElementsPerNode];
            int[] bv = new int[noElementsPerNode];
            int[] gs = new int[noElementsPerNode];
            if(i+1==Main.noOfProcesses){
                rv=rValuesChunk;
                gv=gValuesChunk;
                bv=bValuesChunk;
                gs=gsChunk;
            }
            else {
                MPI.COMM_WORLD.Recv(rv, 0, noElementsPerNode, MPI.INT, i + 1,
                        Image.RGB_AND_GRAY_RESULT_R_TAG);
                MPI.COMM_WORLD.Recv(gv, 0, noElementsPerNode, MPI.INT, i + 1,
                        Image.RGB_AND_GRAY_RESULT_G_TAG);
                MPI.COMM_WORLD.Recv(bv, 0, noElementsPerNode, MPI.INT, i + 1,
                        Image.RGB_AND_GRAY_RESULT_B_TAG);
                MPI.COMM_WORLD.Recv(gs, 0, noElementsPerNode, MPI.INT, i + 1,
                        Image.RGB_AND_GRAY_RESULT_GS_TAG);
            }
            for(int j=0; j<noElementsPerNode; j++){
                PairElement<Integer, Integer> coordinates = getElementCoordinates(width, order);
                order++;
                rValues[coordinates.first][coordinates.second] = rv[j];
                gValues[coordinates.first][coordinates.second] = gv[j];
                bValues[coordinates.first][coordinates.second] = bv[j];
                grayScale[coordinates.first][coordinates.second] = gs[j];
            }
        }

    }

    public static void writeToFileWorker(){
        int[] size = new int[1];
        MPI.COMM_WORLD.Recv(size,0,1, MPI.INT, 0,
                Image.WRITE_FILE_IN_SIZE_TAG);
        int s = size[0];
        System.out.println("Worker ID: "+MPI.COMM_WORLD.Rank()+" entered write to file -prepare image");
        int[] rm, gm, bm;
        rm = new int[s]; gm = new int[s]; bm = new int[s];
        int[] rs = new int[s];
        MPI.COMM_WORLD.Recv(rm, 0, s, MPI.INT, 0,
                Image.WRITE_FILE_IN_R_TAG);
        MPI.COMM_WORLD.Recv(gm, 0, s, MPI.INT, 0,
                Image.WRITE_FILE_IN_G_TAG);
        MPI.COMM_WORLD.Recv(bm, 0, s, MPI.INT, 0,
                Image.WRITE_FILE_IN_B_TAG);
        for(int i=0; i<s; i++){
            rs[i] = new Color(rm[i], gm[i], bm[i]).getRGB();
        }
        MPI.COMM_WORLD.Ssend(rs, 0, s, MPI.INT, 0,
                Image.WRITE_FILE_RESULT_IMG_TAG);
        System.out.println("Worker ID: "+MPI.COMM_WORLD.Rank()+" finished write to file -prepare image");
    }

    public void writeToFIle(){
        BufferedImage image = new BufferedImage(height, width, BufferedImage.TYPE_INT_RGB);
        for(int i=0; i<height; i++)
            for(int j=0; j<width; j++){
                image.setRGB(i,j,
                        new Color(sobelFilterApplied[i][j],sobelFilterApplied[i][j],sobelFilterApplied[i][j]).getRGB());
            }
        try{
            File outputFile = new File("./output/sobel.png");

            ImageIO.write(image, "PNG", outputFile);
        }
        catch (Exception e){
            System.exit(-1);
        }
    }

    public void writeImageToFileMaster(String pathToImage, String format){
//        BufferedImage image = new BufferedImage(height, width, BufferedImage.TYPE_INT_RGB);
//        for(int i=0; i<height; i++)
//            for(int j=0; j<width; j++){
//                image.setRGB(i,j,
//                        new Color(rValues[i][j], gValues[i][j], bValues[i][j]).getRGB());
//            }
//        try{
//            File outputFile = new File(pathToImage);
//
//            ImageIO.write(image, format, outputFile);
//        }
//        catch (Exception e){
//            System.exit(-1);
//        }
        if(MPI.COMM_WORLD.Rank() != 0)
            return;
        int noElementsPerNode = (width*height)/Main.noOfProcesses;
        int order = 1;
        int[] rm = new int[noElementsPerNode+(width * height) % Main.noOfProcesses];
        int[] gm = new int[noElementsPerNode+(width * height) % Main.noOfProcesses];
        int[] bm = new int[noElementsPerNode+(width * height) % Main.noOfProcesses];
        for(int i=0; i<Main.noOfProcesses; ++i){
            if(i+1==Main.noOfProcesses) {
                noElementsPerNode += (width * height) % Main.noOfProcesses;
                for(int j=0; j<noElementsPerNode; j++){
                    PairElement<Integer, Integer> coordinates = getElementCoordinates(width, order+j);
                    rm[j] = rValues[coordinates.first][coordinates.second];
                    gm[j] = gValues[coordinates.first][coordinates.second];
                    bm[j] = bValues[coordinates.first][coordinates.second];
                }
            }
            else {
                // Step1 - build array of rgb values from start to end
                int[] r = new int[noElementsPerNode];
                int[] g = new int[noElementsPerNode];
                int[] b = new int[noElementsPerNode];
                for(int j=0; j<noElementsPerNode; j++){
                    PairElement<Integer, Integer> coordinates = getElementCoordinates(width, order+j);
                    r[j] = rValues[coordinates.first][coordinates.second];
                    g[j] = gValues[coordinates.first][coordinates.second];
                    b[j] = bValues[coordinates.first][coordinates.second];
                }
                // Step2 - send array
                //   \__ in another node: build array of rgb and gray scale values
                // Send Array Size
                int[] size = new int[1];
                size[0] =  noElementsPerNode;
                MPI.COMM_WORLD.Ssend(size,0,1,MPI.INT, i+1,
                        Image.WRITE_FILE_IN_SIZE_TAG);
                MPI.COMM_WORLD.Ssend(r,0,size[0], MPI.INT, i+1,
                        Image.WRITE_FILE_IN_R_TAG);
                MPI.COMM_WORLD.Ssend(g,0,size[0], MPI.INT, i+1,
                        Image.WRITE_FILE_IN_G_TAG);
                MPI.COMM_WORLD.Ssend(b,0,size[0], MPI.INT, i+1,
                        Image.WRITE_FILE_IN_B_TAG);
                order += noElementsPerNode;
            }
        }
        // master will also process data (compute its own chunk of data)
        int[] resMaster = new int[noElementsPerNode];
        for(int i=0; i<noElementsPerNode; i++){
            resMaster[i] = new Color(rm[i], gm[i], bm[i]).getRGB();
        }
        // gather back data from workers and process results
        // for ....
        //    \__ > retrieve rValues, gValues, bValues and grayScaleValues in range [start, end)
        noElementsPerNode = (width*height)/Main.noOfProcesses;
        order = 1;
        BufferedImage image = new BufferedImage(height, width, BufferedImage.TYPE_INT_RGB);
        for(int i=0; i<Main.noOfProcesses; ++i){
            if(i+1==Main.noOfProcesses){
                noElementsPerNode += (width * height) % Main.noOfProcesses;
            }
            int[] imgChunk = new int[noElementsPerNode];
            if(i+1==Main.noOfProcesses){
                imgChunk = resMaster;
            }
            else {
                MPI.COMM_WORLD.Recv(imgChunk, 0, noElementsPerNode, MPI.INT, i + 1,
                        Image.WRITE_FILE_RESULT_IMG_TAG);
            }
            for(int j=0; j<noElementsPerNode; j++){
                PairElement<Integer, Integer> coordinates = getElementCoordinates(width, order);
                order++;
                image.setRGB(coordinates.first, coordinates.second, imgChunk[j]);
            }
        }
        try{
            File outputFile = new File(pathToImage);

            ImageIO.write(image, format, outputFile);
        }
        catch (Exception e){
            System.out.println(e.getMessage());
            System.exit(-1);
        }
    }

    public static void applySobelWorker(){
        int[] dim = new int[5];
        MPI.COMM_WORLD.Recv(dim, 0, 5, MPI.INT, 0,
                Image.SOBEL_IN_PARAMS_TAG);
        System.out.println("Worker ID:"+MPI.COMM_WORLD.Rank()+" --> started applySobelWorker");
        int[][] gray = new int[dim[0]][dim[1]];
        MPI.COMM_WORLD.Recv(gray, 0, dim[0], MPI.OBJECT, 0,
                Image.SOBEL_IN_GRAY_SCALE_TAG);
        Integer i = dim[3];
        Integer j = dim[4];
        Integer computed = 0;
        int[] sobelValues = new int[dim[2]];
        int idx = 0;
        while (computed < dim[2] && i < dim[0]) {
            if (j==dim[1]) {
                j = 0;
                i++;
                if (i == dim[0])
                    break;
            }
            if (i != 0 && i != dim[0] - 1 && j != 0 && j != dim[1] - 1) {
                int val00 = gray[i - 1][j - 1];
                int val01 = gray[i - 1][j];
                int val02 = gray[i - 1][j + 1];
                int val10 = gray[i][j - 1];
                int val11 = gray[i][j];
                int val12 = gray[i][j + 1];
                int val20 = gray[i + 1][j - 1];
                int val21 = gray[i + 1][j];
                int val22 = gray[i + 1][j + 1];

                int gx = ((-1 * val00) + (0 * val01) + (1 * val02))
                        + ((-2 * val10) + (0 * val11) + (2 * val12))
                        + ((-1 * val20) + (0 * val21) + (1 * val22));

                int gy = ((-1 * val00) + (-2 * val01) + (-1 * val02))
                        + ((0 * val10) + (0 * val11) + (0 * val12))
                        + ((1 * val20) + (2 * val21) + (1 * val22));
                double gval = Math.sqrt((gx * gx) + (gy * gy));
                int g = (int) gval;

                sobelValues[idx] = Math.min(g, 255);
                sobelValues[idx] = Math.max(sobelValues[idx], 0);
                if(sobelValues[idx] >= 128)
                    sobelValues[idx] = 255;
                else
                    sobelValues[idx] = 0;
                idx++;
            }
            else {
                sobelValues[idx] = 0;
                idx++;
            }
            j++;
            computed++;
        }
        MPI.COMM_WORLD.Send(sobelValues, 0, dim[2], MPI.INT, 0,
                Image.SOBEL_RES_ARRAY);
        System.out.println("Worker ID:"+MPI.COMM_WORLD.Rank()+" --> finished applySobelWorker");
    }

    public void applySobelMaster(){
        sobelFilterApplied = new int [height][width];

        for(int i=0; i<height; i++){
            sobelFilterApplied[i][0] = grayScale[i][0];
            sobelFilterApplied[i][width-1] =  grayScale[i][width-1];
        }
        for(int i=0; i<width; i++){
            sobelFilterApplied[0][i] = grayScale[0][i];
            sobelFilterApplied[height-1][i] =  grayScale[height-1][i];
        }
        int noElementsPerNode = (width*height)/Main.noOfProcesses;
        int order = 1;
        int[] values = new int[2];
        for(int i =0; i<Main.noOfProcesses; i++){
            if(Main.noOfProcesses == i+1){
                noElementsPerNode += (width*height)%Main.noOfProcesses;
                values = new int[noElementsPerNode];
            }
            else{
                int[] dimensions = new int[5];
                dimensions[0] = height; dimensions[1] = width; dimensions[2] = noElementsPerNode;
                dimensions[3] = getElementCoordinates(width, order).first;
                dimensions[4] = getElementCoordinates(width, order).second;
                MPI.COMM_WORLD.Ssend(dimensions, 0, 5, MPI.INT, i+1,
                        Image.SOBEL_IN_PARAMS_TAG);
                MPI.COMM_WORLD.Ssend(grayScale,0 , height, MPI.OBJECT, i+1,
                        Image.SOBEL_IN_GRAY_SCALE_TAG);
                order+= noElementsPerNode;
            }
        }
        // master does its part
        Integer i = getElementCoordinates(width, order).first;
        Integer j = getElementCoordinates(width, order).second;
        Integer computed = 0;
        int idx = 0;
        while (computed < noElementsPerNode && i < height) {
            if (j.equals(width)) {
                j = 0;
                i++;
                if (i.equals(height))
                    break;
            }

            if (i != 0 && i != height - 1 && j != 0 && j !=width - 1) {
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

                values[idx] = Math.min(g, 255);
                values[idx] = Math.max(values[idx], 0);
                if(values[idx] >= 128)
                    values[idx] = 255;
                else
                    values[idx] = 0;
                idx++;
            }
            else {
                values[idx] = 0;
                idx++;
            }
            j++;
            computed++;
        }

        order = 1;
        // retrieve data
        noElementsPerNode = (width*height)/Main.noOfProcesses;
        for(int ii=0; ii<Main.noOfProcesses; ii++){
            int[] res;
            if(Main.noOfProcesses == ii+1){
               res = values;
               noElementsPerNode += (width*height)%Main.noOfProcesses;
            }
            else{
                res = new int[noElementsPerNode];
                MPI.COMM_WORLD.Recv(res, 0, noElementsPerNode, MPI.INT, ii+1,
                        Image.SOBEL_RES_ARRAY);
            }
            for(int jj=0; jj<noElementsPerNode; jj++){
                if(getElementCoordinates(width, order).first == 0 ||
                    getElementCoordinates(width, order).first+1 == height ||
                    getElementCoordinates(width, order).second == 0 ||
                    getElementCoordinates(width, order).second +1 == width) {
                    order++;
                    continue;
                }
                sobelFilterApplied[getElementCoordinates(width,order).first][getElementCoordinates(width, order).second]
                        = res[jj];
                order++;
            }

        }
    }

    public void setPixel(PairElement<Integer, Integer> coordinates, Integer r, Integer g, Integer b){
        if(coordinates.first < 0 || coordinates.first>height || coordinates.second<0 || coordinates.second>width)
            return;
        this.rValues[coordinates.first][coordinates.second] = r;
        this.gValues[coordinates.first][coordinates.second] = g;
        this.bValues[coordinates.first][coordinates.second] = b;
    }
}
