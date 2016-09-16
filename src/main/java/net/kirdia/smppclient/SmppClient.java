/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.kirdia.smppclient;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.*;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.jsmpp.util.AbsoluteTimeFormatter;
import org.jsmpp.util.InvalidDeliveryReceiptException;
import org.jsmpp.util.TimeFormatter;

/**
 *
 * @author Kiriakos Diamantis <kd@amdtelecom.net>
 */
public class SmppClient {
    
    private SMPPSession session;
    private Map params;
    
    public SmppClient(Map params) {
        session = new SMPPSession();
        this.params = params;
    }
    
    public boolean connect() throws IOException {
        String host = (String) params.get("host");
        Integer port = (Integer) params.get("port");
        String user = (String) params.get("username");
        String pass = (String) params.get("password");
        
        session = new SMPPSession();
        session.connectAndBind(host, port, BindType.BIND_TRX, user, pass, null, TypeOfNumber.UNKNOWN, NumberingPlanIndicator.ISDN, null);
        session.setEnquireLinkTimer(5000);
        return true;
    }
    
    public boolean disconnect() {
        session.unbindAndClose();
        session = null;
        return true;
    }
    
//    public boolean sendMessage() {
//    }
    
    public boolean getStatus() {
        if (session != null) {
            return session.getSessionState().isBound();
        }
        return false;
    }
    
    public String getStatusText() {
        if (session != null) {
            return session.getSessionState().toString();
        }
        return "No active session";

    }
    
    public String sendSingleMessage(byte[] text, String sender, String receiver, int dcs) {
        
        String status;
        TimeFormatter timeFormatter = new AbsoluteTimeFormatter();
        DataCoding dt = new GeneralDataCoding(Alphabet.ALPHA_DEFAULT);
//        DataCoding dt = new GeneralDataCoding(dcs);
        try {
            String messageId = session.submitShortMessage("VMA", TypeOfNumber.INTERNATIONAL, 
                    NumberingPlanIndicator.UNKNOWN, sender, TypeOfNumber.INTERNATIONAL, 
                    NumberingPlanIndicator.UNKNOWN, receiver, new ESMClass(), 
                    (byte)0, (byte)1,  null, null, 
                    new RegisteredDelivery(SMSCDeliveryReceipt.DEFAULT), (byte)0, 
                    dt, (byte)0, text);

            status = "Message submitted, message_id is " + messageId;
        } catch (PDUException e) {
            // Invalid PDU parameter
            status = "Invalid PDU parameter";
            status += e.getMessage();
        } catch (ResponseTimeoutException e) {
            // Response timeout
            status = "Response timeout";
            status += e.getMessage();
        } catch (InvalidResponseException e) {
            // Invalid response
            status = "Receive invalid response";
            status += e.getMessage();
        } catch (NegativeResponseException e) {
            // Receiving negative response (non-zero command_status)
            status = "Receive negative response\n";
            status += e.getMessage();
        } catch (IOException e) {
            status = "IO error occur";
            status += e.getMessage();
        }
        

        // receive Message

        // Set listener to receive deliver_sm
        session.setMessageReceiverListener(new MessageReceiverListener() {

            public void onAcceptDeliverSm(DeliverSm deliverSm) throws ProcessRequestException {
                if (MessageType.SMSC_DEL_RECEIPT.containedIn(deliverSm.getEsmClass())) {
                    // delivery receipt
                    try {
                        DeliveryReceipt delReceipt = deliverSm.getShortMessageAsDeliveryReceipt();
                        long id = Long.parseLong(delReceipt.getId()) & 0xffffffff;
                        String messageId = Long.toString(id, 16).toUpperCase();
                        System.out.println("received '" + messageId + "' : " + delReceipt);
                    } catch (InvalidDeliveryReceiptException e) {
                        System.err.println("receive faild");
                        e.printStackTrace();
                    }
                } else {
                    // regular short message
                    System.out.println("Receiving message : " + new String(deliverSm.getShortMessage()));
                }
            }

            public void onAcceptAlertNotification(AlertNotification alertNotification) {
                System.out.println("onAcceptAlertNotification");
            }

            public DataSmResult onAcceptDataSm(DataSm dataSm, Session source) throws ProcessRequestException {
                System.out.println("onAcceptDataSm");
                return null;
            }

        });       
        
        // wait 3 second
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }        
        return status;
    
    }

    public void setParams(Map params) {
        this.params = params;
    }
    
    
}
