package com.eatthepath.pushy.console;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.SSLException;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.ApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.TokenUtil;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

public class Demo {

    //TODO 測試
    public PushNotificationResponse test() throws InvalidKeyException, SSLException, NoSuchAlgorithmException, IOException, InterruptedException, URISyntaxException {
        
        //TLS authentication
        //System.out.println("[DEBUG]  TLS authentication");        
        File p12File=new File("D:/Senao/bitbucket.org/prod_appsvc.git/WebContent/apps_cert_files/SPLUS2R.p12");
        String p12Password="qwer1234";
        //System.out.println("[DEBUG]  p12File="+p12File.getAbsolutePath());
        
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup(10); //注意: EventLoopGroup的線程數不要配置超過ConcurrentConnections連接數
        
        final ApnsClient apnsClient = new ApnsClientBuilder().
            setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST). //api.sandbox.push.apple.com
            setClientCredentials(p12File, p12Password).
            
            //setProxyHandlerFactory(new Socks5ProxyHandlerFactory(new InetSocketAddress("PROXY主機",3128), "PROXY帳號", "PROXY密碼")).  //透過一般方式使用PROXY
            //setProxyHandlerFactory(HttpProxyHandlerFactory.fromSystemProxies(ApnsClientBuilder.DEVELOPMENT_APNS_HOST)). //透過JAVA系統設定使用PROXY https://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html
            
            //APNs服務器可以保證同時發送1500條消息，當超過這個限制時，Pushy會緩存消息，所以我們不必擔心異步操作發送的消息過多
            //setConcurrentConnections(10). //注意: EventLoopGroup的線程數不要配置超過ConcurrentConnections連接數
            //setEventLoopGroup(eventLoopGroup). //注意: EventLoopGroup的線程數不要配置超過ConcurrentConnections連接數
            
            build();
        
        //要發送的
        final SimpleApnsPushNotification pushNotification;
        final ApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder(); //ApnsPayloadBuilder
        payloadBuilder.setAlertBody("YenCheChang Body");
        payloadBuilder.setAlertTitle("YenCheChang Title");
        //
        final String payload = payloadBuilder.build(); //推播有效載荷JSON
        //
        final String deviceToken = TokenUtil.sanitizeTokenString("375c78f0411c3246ffcc85faae33438a080a854ad131ba55df9709fc259020ed");
        //final String deviceToken  = "375c78f0411c3246ffcc85faae33438a080a854ad131ba55df9709fc259020ed";
        //
        String topic="tw.com.senao.splus2rInhouse";
        pushNotification = new SimpleApnsPushNotification(deviceToken, topic, payload);
        //
        System.out.println("[DEBUG] pushNotification.getApnsId()="+pushNotification.getApnsId());
        System.out.println("[DEBUG] pushNotification.getCollapseId()="+pushNotification.getCollapseId());
        System.out.println("[DEBUG] pushNotification.getToken()="+pushNotification.getToken());
        System.out.println("[DEBUG] pushNotification.getTopic()="+pushNotification.getTopic());
        System.out.println("[DEBUG] pushNotification.getExpiration()="+pushNotification.getExpiration());
        System.out.println("[DEBUG] pushNotification.getPriority()="+pushNotification.getPriority());
        System.out.println("[DEBUG] pushNotification.getPushType()="+pushNotification.getPushType());
        System.out.println("[DEBUG] pushNotification.getClass()="+pushNotification.getClass());
        System.out.println("[DEBUG] pushNotification.getPayload()="+pushNotification.getPayload());
        
        final PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> sendNotificationFuture;        
        sendNotificationFuture=apnsClient.sendNotification(pushNotification);
                
        PushNotificationResponse<SimpleApnsPushNotification> pushNotificationResponse=null;
        try {
            //取得APNs回應
            pushNotificationResponse=sendNotificationFuture.get();
            
            System.out.println("[DEBUG]  isAccepted="+pushNotificationResponse.isAccepted());
            if(pushNotificationResponse.isAccepted()) {
                System.out.println("[DEBUG] 推播成功 Push notification accepted by APNs gateway.");
            } else {
                System.out.println("[DEBUG] 推播失敗 Notification rejected by the APNs gateway: "+pushNotificationResponse.getRejectionReason());
                java.time.Instant timestamp=pushNotificationResponse.getTokenInvalidationTimestamp();
                System.out.println("[DEBUG] timestamp="+timestamp);
            }
            
            /*
            sendNotificationFuture.whenComplete((response, cause) -> {
                if (response != null) {
                    //這裡可以 處理推送通知響應
                }
                else {
                    //這裡可以 嘗試將通知發送到APNs服務器時出了點問題。 請注意，這不同於來自服務器//的拒絕，並表示實際上發送通知或等待回复時出了點問題。
                }
            });
            */
            
        }catch(ExecutionException exe) {
            exe.printStackTrace();
        }
                
        //結束推播處理
        CompletableFuture<Void> closeFuture=apnsClient.close();
        
        return pushNotificationResponse;
    }
    
    //TODO 測試
    ApnsClient cerClient(Boolean isProduct,String p12Path, String p12Pwd) throws SSLException, IOException {
        File file = new File(p12Path);
        String host = isProduct ? ApnsClientBuilder.PRODUCTION_APNS_HOST : ApnsClientBuilder.DEVELOPMENT_APNS_HOST;
        ApnsClientBuilder apnsClientBuiler = new ApnsClientBuilder();
        apnsClientBuiler.setApnsServer(host);
        apnsClientBuiler.setClientCredentials(file, p12Pwd);
        ApnsClient client = apnsClientBuiler.build();
        return client;
    }
    
    public static void main(String args[]) {
        
    	Demo p=new Demo();
        
    	/*
    	//測試一
        try {
            p.test();
        } catch(InvalidKeyException|NoSuchAlgorithmException|IOException|InterruptedException|URISyntaxException e) {
            e.printStackTrace();
        }
        */
        
    	/*
    	//測試二
        try {
        	 File p12File=new File("D:/Senao/bitbucket.org/prod_appsvc.git/WebContent/apps_cert_files/SPLUS2R.p12");
        	 String p12Path=p12File.getAbsolutePath();
             String p12Password="qwer1234";             
             com.eatthepath.pushy.apns.ApnsClient c=p.cerClient(false, p12Path, p12Password);
             
             ApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder(); //ApnsPayloadBuilder
             payloadBuilder.setAlertBody("YenCheChang Body");
             payloadBuilder.setAlertTitle("YenCheChang Title");
             String payload = payloadBuilder.build(); //推播有效載荷JSON
             String deviceToken = TokenUtil.sanitizeTokenString("375c78f0411c3246ffcc85faae33438a080a854ad131ba55df9709fc259020ed");
             String topic="tw.com.senao.splus2rInhouse";
             SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(deviceToken, topic, payload);
                          
             PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> sendNotificationFuture=c.sendNotification(pushNotification);
             System.out.println(sendNotificationFuture.get().isAccepted());
        } catch(Exception e) {
        	e.printStackTrace();
        }
        */
        
        /*
        //測試三
        try {
            System.out.println("開始");
            File p12File=new File("D:/Senao/bitbucket.org/prod_appsvc.git/WebContent/apps_cert_files/SPLUS2R.p12");
            String p12Password="qwer1234";
            
            //An APNs client sends push notifications to the APNs gateway
            final ApnsClient apnsClient=new ApnsClientBuilder().
                setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST). //api.sandbox.push.apple.com
                setClientCredentials(p12File, p12Password).
                build();

            //要發送的 The push notification
            final SimpleApnsPushNotification pushNotification;
            
            //final ApnsPayloadBuilder payloadBuilder=new ApnsPayloadBuilder();
            final ApnsPayloadBuilder payloadBuilder=new SimpleApnsPayloadBuilder(); //ApnsPayloadBuilder
            
            payloadBuilder.setAlertBody("Example");
            payloadBuilder.setAlertTitle("Example Title");

            final String payload=payloadBuilder.build(); //推播有效載荷JSON
            final String token=TokenUtil.sanitizeTokenString("375c78f0411c3246ffcc85faae33438a080a854ad131ba55df9709fc259020ed"); //裝置TOKEN
            final String topic="tw.com.senao.splus2rInhouse";
            
            pushNotification=new SimpleApnsPushNotification(token, topic, payload);

            try {
                //推播的回應
                final PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> sendNotificationFuture=apnsClient.sendNotification(pushNotification);

                //取得APNs回應 
                final PushNotificationResponse<SimpleApnsPushNotification> pushNotificationResponse=sendNotificationFuture.get();
                if(pushNotificationResponse.isAccepted()) {
                    System.out.println("推播成功 Push notification accepted by APNs gateway.");
                } else {
                    
                    System.out.println("推播失敗 pushNotificationResponse.getRejectionReason()="+pushNotificationResponse.getRejectionReason());
                    System.out.println("pushNotificationResponse.getApnsId()="+pushNotificationResponse.getApnsId());
                    System.out.println("pushNotificationResponse.getTokenInvalidationTimestamp()="+pushNotificationResponse.getTokenInvalidationTimestamp());
                    
                    if(pushNotificationResponse.getTokenInvalidationTimestamp()!=null) {
                        System.out.println("and the token is invalid as of "+pushNotificationResponse.getTokenInvalidationTimestamp());
                    }
                    
                }
            } catch(final ExecutionException e) {
                System.err.println("Failed to send push notification.");
                e.printStackTrace();
            }
            
            apnsClient.close();
            System.out.println("結束");
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        */
    }
    

}
