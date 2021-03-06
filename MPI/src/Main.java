import mpi.*;

public class Main {
    public static Integer noOfProcesses;

    public static void main(String[] args) {
        MPI.Init(args);
        int me = MPI.COMM_WORLD.Rank();
        Main.noOfProcesses = MPI.COMM_WORLD.Size();
        if(me == 0){
            Image im = new Image("./data/img3.PNG");
            HoughTransform houghTransform = new HoughTransform(im);
            im = houghTransform.putLinesOnImage();
            im.writeImageToFileMaster("./output/result.PNG", "PNG");
        }
        else{
            // all events will 'happen' sequentially
            // firstly we will construct the RGB and GrayScale 2D arrays
            Image.toRgbAndGrayScaleWorker();
            Image.applySobelWorker();
            HoughTransform.buildHoughArrayWorker();
            HoughTransform.findEdgePointsWorker();
            Image.writeToFileWorker();

        }
        MPI.Finalize();
    }
}
