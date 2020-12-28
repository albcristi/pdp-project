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

    private void executeHoughTransform(){
        Integer initialR = (int) Math.sqrt(image.getWidth()*image.getWidth()+image.getHeight()*image.getHeight());
        System.out.println(houghArray[0].length);
        for(int x=0; x<image.getHeight(); x++)
            for(int y=0; y<image.getWidth(); y++)
                if(filteredImage[x][y] !=0){
                    for(int angle=0; angle<180; angle++){
                         Integer r = (int) (x* Math.cos(Math.toRadians(angle)) + y*Math.sin(Math.toRadians(angle)));
                         houghArray[angle][r+initialR] += 1;
                         if(houghArray[angle][r+initialR] > globalMaximum){
                             globalMaximum = houghArray[angle][r+initialR];
                         }

                    }
                }

//        if(globalMaximum > 260)
//            globalMaximum = 255;

        for(int x=0; x<180; x++) {
            for (int y = 0; y < 2 * initialR; y++) {
                houghArray[x][y] = houghArray[x][y]> 0.3*globalMaximum ? houghArray[x][y] : 0;
                if(houghArray[x][y] > 255)
                    houghArray[x][y] = 255;
            }
        }

    }

    public Integer[][] getHoughArray(){
        return this.houghArray;
    }

    public List<PairElement<Integer, Integer>> getEdgePoints(){
        List<PairElement<Integer, Integer>> points = new ArrayList<>();
        Integer initialR = (int) Math.sqrt(image.getWidth()*image.getWidth()+image.getHeight()*image.getHeight());
        for(int angle=0; angle<180; angle++)
            for(int r=0; r<houghArray[0].length; r++)
            if(houghArray[angle][r] != 0){
//                for(int i=0; i<image.getHeight(); i++)
//                    for(int j=0; j<image.getWidth(); j++) {
////                        System.out.println("-==-=-");
////                        System.out.println((int) (i * Math.cos(Math.toRadians(angle) + j * Math.sin(Math.toRadians(angle)))));
////                        System.out.println(r);
//                        if ((int) (i * Math.cos(Math.toRadians(angle) + j * Math.sin(Math.toRadians(angle)))) == r-initialR) {
//                            points.add(new PairElement<>(i, j));
//                        }
//                    }
                Integer x, y;
                x = (int) (r*Math.cos(Math.toRadians(angle)))-initialR;
                y = (int) (r*Math.sin(Math.toRadians(angle)))-initialR;
                PairElement<Integer, Integer> point = new PairElement<>(x,y);
                if(!image.containsPixel(point)){
                    Integer x1,y1,x2,y2, maxLength;
                    maxLength = (int) (Math.sqrt(image.getWidth()*image.getWidth()+image.getHeight()*image.getHeight()));
                    x1 = x + maxLength + (int) (r*Math.cos(Math.toRadians(angle))) -initialR;
                    y1 = y+ maxLength+ (int) (r*Math.sin(Math.toRadians(angle))) -initialR;
                    if(image.containsPixel(new PairElement<>(x1,y1))){
                        points.add(new PairElement<>(x1,y1));
                    }
                    else{
                        x2 = x - maxLength + (int) (r*Math.cos(Math.toRadians(angle))) -initialR;
                        y2 = y - maxLength+ (int) (r*Math.sin(Math.toRadians(angle))) -initialR;
                        if(image.containsPixel(new PairElement<>(x2,y2)))
                            points.add(new PairElement<>(x2,y2));
                    }
                }
                else {
                    points.add(new PairElement<>(x, y));
                }
            }
        return points;
    }
    public void putLinesOnImage(){
        List<PairElement<Integer, Integer>> points = getEdgePoints();
        for(PairElement<Integer, Integer> p1: points){
//            for(PairElement<Integer, Integer> p2: points)
//                if(!p1.first.equals(p2.first) && !p1.second.equals(p2.second)){
                    image.setPixel(p1,124,252,0);
                    //image.setPixel(p2,124,252,0);
                }
    }
}
