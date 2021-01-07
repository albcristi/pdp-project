import mpi.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class Main {
    public static Integer noOfProcesses;



    public static void main(String[] args) {
        // write your code here
        MPI.Init(args);
        int me = MPI.COMM_WORLD.Rank();
        Main.noOfProcesses = MPI.COMM_WORLD.Size();
        if(me == 0){
            Image im = new Image("./data/img2.png");
            im.writeImageToFileMaster("./output/result.png", "png");
        }
        else{
            // all events will 'happen' sequentially
            // firstly we will construct the RGB and GrayScale 2D arrays
            Image.toRgbAndGrayScaleWorker();
            Image.writeToFileWorker();
        }
        MPI.Finalize();
    }
}
