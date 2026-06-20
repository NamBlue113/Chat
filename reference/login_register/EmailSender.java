package login_register;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

public class EmailSender {

    public static void sendOTP(
            String toEmail,
            String otp
    ){

        String fromEmail =
                "thuannd.25ceb@vku.udn.vn";

        String password =
                "wahm oull zjmo bpog".replace(" ","");

        Properties props =
                new Properties();

        props.put(
                "mail.smtp.auth",
                "true"
        );

        props.put(
                "mail.smtp.starttls.enable",
                "true"
        );

        props.put(
                "mail.smtp.host",
                "smtp.gmail.com"
        );

        props.put(
                "mail.smtp.port",
                "587"
        );

        Session session =
                Session.getInstance(
                        props,
                        new Authenticator() {

                            protected PasswordAuthentication
                            getPasswordAuthentication(){

                                return new PasswordAuthentication(
                                        fromEmail,
                                        password
                                );
                            }
                        }
                );

        try{

            Message message =
                    new MimeMessage(
                            session
                    );

            message.setFrom(
                    new InternetAddress(
                            fromEmail
                    )
            );

            message.setRecipients(
                    Message.RecipientType.TO,
                    InternetAddress.parse(
                            toEmail
                    )
            );

            message.setSubject(
                    "OTP Reset Password"
            );

            message.setText(
                    "Mã OTP của bạn là: "
                            + otp
            );

            Transport.send(message);

        }catch(Exception e){

            e.printStackTrace();
        }
    }
}