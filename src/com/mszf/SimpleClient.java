package com.mszf;

import com.mszf.handler.ResponseHandler;
import com.mszf.nio.NioClient;
import com.mszf.util.Message;

import static com.mszf.util.Constant.HOST;
import static com.mszf.util.Constant.PORT;


public class SimpleClient {

    public static void main(String[] args) throws Exception {
        // connect
        NioClient client = new NioClient(HOST, PORT);
        Thread t = new Thread(client);
        t.setDaemon(true);
        t.start();

        // request
        ResponseHandler responseHandler = new ResponseHandler();
        client.send(new Message("Hello!"), responseHandler);

        // get response
        System.out.println(new String(responseHandler.waitAndGet()));

    }

}
