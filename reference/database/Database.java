package database;

import java.sql.Connection;
import java.sql.DriverManager;


public class Database {

    static Connection conn;

    public static Connection connect(){

        try{

            if(conn == null || conn.isClosed()){

                Class.forName(
                        "com.mysql.cj.jdbc.Driver"
                );

                conn =
                        DriverManager.getConnection(
                                "jdbc:mysql://172.26.51.126:3306/chatapptest_java",
                                "chatuser",
                                "123456"
                        );
            }

        }catch(Exception e){

            e.printStackTrace();
        }

        return conn;
    }
}