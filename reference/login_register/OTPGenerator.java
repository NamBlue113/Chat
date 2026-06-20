package login_register;

import java.util.Random;

public class OTPGenerator {

    public static String generateOTP(){

        Random random =
                new Random();

        return String.valueOf(
                100000 +
                        random.nextInt(900000)
        );
    }
}