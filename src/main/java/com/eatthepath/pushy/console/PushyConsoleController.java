/*
 * Copyright (c) 2020 Jon Chambers.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.eatthepath.pushy.console;

import com.eatthepath.pushy.apns.*;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.proxy.HttpProxyHandlerFactory;
import com.eatthepath.pushy.apns.proxy.Socks5ProxyHandlerFactory;
import com.eatthepath.pushy.apns.util.ApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.TokenUtil;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLException;

/**
 * A controller for the main pushy console window. The console controller delegates notification composition to a
 * {@link ComposeNotificationController} and manages the actual transmission of push notifications and reporting of
 * results.
 *
 * @author <a href="https://github.com/jchambers/">Jon Chambers</a>
 */
public class PushyConsoleController {

    @FXML private ResourceBundle resources;

    @FXML ComposeNotificationController composeNotificationController;

    @FXML TableView<PushNotificationResponse<ApnsPushNotification>> notificationResultTableView;

    @FXML private TableColumn<PushNotificationResponse<ApnsPushNotification>, String> notificationResultTopicColumn;
    @FXML private TableColumn<PushNotificationResponse<ApnsPushNotification>, String> notificationResultTokenColumn;
    @FXML private TableColumn<PushNotificationResponse<ApnsPushNotification>, String> notificationResultPayloadColumn;
    @FXML private TableColumn<PushNotificationResponse<ApnsPushNotification>, String> notificationResultCollapseIdColumn;
    @FXML private TableColumn<PushNotificationResponse<ApnsPushNotification>, String> notificationResultPriorityColumn;

    @FXML private TableColumn<PushNotificationResponse<ApnsPushNotification>, String> notificationResultStatusColumn;
    @FXML private TableColumn<PushNotificationResponse<ApnsPushNotification>, String> notificationResultDetailsColumn;
    @FXML private TableColumn<PushNotificationResponse<ApnsPushNotification>, String> notificationResultApnsIdColumn;

    private final BooleanProperty readyToSendProperty = new SimpleBooleanProperty();

    private final ExecutorService sendNotificationExecutorService = Executors.newSingleThreadExecutor();

    /**
     * Initializes the controller and its various controls and bindings.
     */
    public void initialize() {
        notificationResultTableView.setPlaceholder(new Label(resources.getString("notification-result.placeholder")));

        notificationResultTopicColumn.setCellValueFactory(cellDataFeatures ->
                new ReadOnlyStringWrapper(cellDataFeatures.getValue().getPushNotification().getTopic()));

        notificationResultTokenColumn.setCellValueFactory(cellDataFeatures ->
                new ReadOnlyStringWrapper(cellDataFeatures.getValue().getPushNotification().getToken()));

        notificationResultPayloadColumn.setCellValueFactory(cellDataFeatures ->
                new ReadOnlyStringWrapper(cellDataFeatures.getValue().getPushNotification().getPayload()
                        .replace('\n', ' ')
                        .replaceAll("\\s+", " ")));

        notificationResultCollapseIdColumn.setCellValueFactory(cellDataFeatures ->
                new ReadOnlyStringWrapper(cellDataFeatures.getValue().getPushNotification().getCollapseId()));

        notificationResultPriorityColumn.setCellValueFactory(cellDataFeatures -> new ReadOnlyStringWrapper(
                cellDataFeatures.getValue().getPushNotification().getPriority() == DeliveryPriority.IMMEDIATE ?
                        resources.getString("delivery-priority.immediate") :
                        resources.getString("delivery-priority.conserve-power")));

        notificationResultStatusColumn.setCellValueFactory(cellDataFeatures -> new ReadOnlyStringWrapper(
                cellDataFeatures.getValue().isAccepted() ?
                        resources.getString("notification-result.status.accepted") :
                        resources.getString("notification-result.status.rejected")));

        notificationResultDetailsColumn.setCellValueFactory(cellDataFeatures -> {
            final PushNotificationResponse<ApnsPushNotification> pushNotificationResponse = cellDataFeatures.getValue();

            final String details;

            if (pushNotificationResponse.isAccepted()) {
                details = resources.getString("notification-result.details.accepted");
            } else {
                if (pushNotificationResponse.getTokenInvalidationTimestamp() == null) {
                    details = pushNotificationResponse.getRejectionReason();
                } else {
                    details = new MessageFormat(resources.getString("notification-result.details.expiration")).format(
                            new Object[] {
                                    cellDataFeatures.getValue().getRejectionReason(),
                                    cellDataFeatures.getValue().getTokenInvalidationTimestamp() });
                }
            }

            return new ReadOnlyStringWrapper(details);
        });

        notificationResultApnsIdColumn.setCellValueFactory(cellDataFeatures ->
                new ReadOnlyStringWrapper(cellDataFeatures.getValue().getApnsId().toString()));

        readyToSendProperty.bind(new BooleanBinding() {
            {
                super.bind(composeNotificationController.apnsCredentialsProperty(),
                        composeNotificationController.pushNotificationProperty());
            }

            @Override
            protected boolean computeValue() {
                return composeNotificationController.apnsCredentialsProperty().get() != null &&
                        composeNotificationController.pushNotificationProperty().get() != null;
            }
        });
    }
    
    
    
    
    
    
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
    public static void main(String args[]) {
        
        //PushyConsoleController p=new PushyConsoleController();
        //try {
        //    p.test();
        //} catch(InvalidKeyException|NoSuchAlgorithmException|IOException|InterruptedException|URISyntaxException e) {
        //    e.printStackTrace();
        //}
        
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
    }
    
    
    

    @FXML
    void handleSendNotificationButtonAction(final ActionEvent event) {
        System.out.println("[DEBUG] handleSendNotificationButtonAction() readyToSendProperty.get()="+readyToSendProperty.get());
        if (readyToSendProperty.get()) {
            composeNotificationController.handleNotificationSent();

            final Task<PushNotificationResponse<ApnsPushNotification>> sendNotificationTask = new Task<PushNotificationResponse<ApnsPushNotification>>() {

                @Override
                protected PushNotificationResponse<ApnsPushNotification> call() throws Exception {
                    final String server = composeNotificationController.apnsServerProperty().get();
                    final int port = composeNotificationController.apnsPortProperty().get();
                    final ApnsCredentials credentials = composeNotificationController.apnsCredentialsProperty().get();

                    final ApnsClientBuilder apnsClientBuilder = new ApnsClientBuilder();
                    apnsClientBuilder.setApnsServer(server, port);
                    
                    
                    System.out.println("[DEBUG] server="+server+", port="+port);
                    System.out.println("[DEBUG] Key="+credentials.getCertificateAndPrivateKey().get().getValue());
                    System.out.println("[DEBUG] Value="+credentials.getCertificateAndPrivateKey().get().getValue());
                    
                    credentials.getCertificateAndPrivateKey().ifPresent(certificateAndPrivateKey ->
                            apnsClientBuilder.setClientCredentials(certificateAndPrivateKey.getKey(), certificateAndPrivateKey.getValue(), null));

                    credentials.getSigningKey().ifPresent(apnsClientBuilder::setSigningKey);

                    final ApnsClient apnsClient = apnsClientBuilder.build();

                    try {
                        PushNotificationResponse pnRes=null;
                        
                        com.eatthepath.pushy.apns.ApnsPushNotification apn=composeNotificationController.pushNotificationProperty().get();
                        System.out.println("[DEBUG] apn.getApnsId()="+apn.getApnsId());
                        System.out.println("[DEBUG] apn.getCollapseId()="+apn.getCollapseId());
                        System.out.println("[DEBUG] apn.getToken()="+apn.getToken());
                        System.out.println("[DEBUG] apn.getTopic()="+apn.getTopic());
                        System.out.println("[DEBUG] apn.getExpiration()="+apn.getExpiration());
                        System.out.println("[DEBUG] apn.getPriority()="+apn.getPriority());
                        System.out.println("[DEBUG] apn.getPushType()="+apn.getPushType());
                        System.out.println("[DEBUG] apn.getClass()="+apn.getClass());
                        System.out.println("[DEBUG] apn.getPayload()="+apn.getPayload());
                        
                        pnRes=apnsClient.sendNotification(composeNotificationController.pushNotificationProperty().get()).get();
                        
                        System.out.println("[DEBUG]  APNsID="+pnRes.getApnsId()+", Payload="+pnRes.getPushNotification().getPayload()+", Priority="+pnRes.getPushNotification().getPriority()+", Topic="+pnRes.getPushNotification().getTopic()+", Token="+pnRes.getPushNotification().getToken()+", PushTyp="+pnRes.getPushNotification().getPushType());
                        
                        return pnRes;
                    } finally {
                        apnsClient.close();
                    }
                }
            };

            sendNotificationTask.setOnSucceeded(workerStateEvent ->
                    handlePushNotificationResponse(sendNotificationTask.getValue()));

            sendNotificationTask.setOnFailed(workerStateEvent ->
                    reportPushNotificationError(sendNotificationTask.getException()));

            sendNotificationExecutorService.execute(sendNotificationTask);
        } else {
            composeNotificationController.setRequiredFieldGroupHighlighted(true);
        }
    }

    void handlePushNotificationResponse(final PushNotificationResponse<ApnsPushNotification> pushNotificationPushNotificationResponse) {
        notificationResultTableView.getItems().add(pushNotificationPushNotificationResponse);
    }

    private void reportPushNotificationError(final Throwable exception) {
        final Alert alert = new Alert(Alert.AlertType.WARNING);

        alert.setTitle(resources.getString("alert.notification-failed.title"));
        alert.setHeaderText(resources.getString("alert.notification-failed.header"));
        alert.setContentText(exception.getLocalizedMessage());

        final String stackTrace;
        {
            final StringWriter stringWriter = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(stringWriter);

            exception.printStackTrace(printWriter);

            stackTrace = stringWriter.toString();
        }

        final TextArea stackTraceTextArea = new TextArea(stackTrace);
        stackTraceTextArea.setEditable(false);
        stackTraceTextArea.setMaxWidth(Double.MAX_VALUE);
        stackTraceTextArea.setMaxHeight(Double.MAX_VALUE);

        alert.getDialogPane().setExpandableContent(stackTraceTextArea);

        alert.showAndWait();
    }

    void stop() {
        sendNotificationExecutorService.shutdown();
    }
}
