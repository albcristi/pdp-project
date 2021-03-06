import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static AtomicInteger NO_THREADS = new AtomicInteger(8);

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(NO_THREADS.get());
        Image img = new Image("./data/img.png", service);
        HoughTransform houghTransform = new HoughTransform(img, service);
        houghTransform.putLinesOnImage();
        img.writeImageToFile("./output/final.png","png");
        service.shutdownNow();
    }
}
