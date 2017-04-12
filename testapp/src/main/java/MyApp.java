import org.apache.log4j.Logger;

/**
 * Created by vazvadsk on 2017-04-12.
 */
public class MyApp {
    final static Logger logger = Logger.getLogger(MyApp.class);

    public static void main(String[] args) throws InterruptedException {

        while(true){
            logger.trace("My TRACE message ...");
            logger.debug("My DEBUG message ...");
            logger.info("My INFO message ...");
            logger.warn("My WARN message ...");
            logger.error("My ERROR message ...");
            logger.fatal("My FATAL message ...");

            try{
                int i = 0;
                i = i / i;
            }catch (ArithmeticException ex){
                logger.error("My arithmetic error ...", ex);
            }

            Thread.sleep(1000);
        }

    }
}
