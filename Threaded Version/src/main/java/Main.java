import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static AtomicInteger NO_THREADS = new AtomicInteger(1);

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(NO_THREADS.get());
        Image img = new Image("./data/img3.jpg", service);
        HoughTransform houghTransform = new HoughTransform(img, service);
        img.writeToFileImage(img.applySobelFilter(),"./output/sobel-filtered-img.png","png");
        houghTransform.putLinesOnImage();
        img.writeImageToFile("./output/final.png","png");
        service.shutdownNow();
    }
}
