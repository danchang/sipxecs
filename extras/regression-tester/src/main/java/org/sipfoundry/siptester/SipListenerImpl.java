package org.sipfoundry.siptester;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

import gov.nist.javax.sip.DialogExt;
import gov.nist.javax.sip.DialogTimeoutEvent;
import gov.nist.javax.sip.ListeningPointExt;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.message.RequestExt;
import gov.nist.javax.sip.message.ResponseExt;
import gov.nist.javax.sip.message.SIPRequest;

import javax.sip.ClientTransaction;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.header.ContactHeader;
import javax.sip.header.RequireHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;

public class SipListenerImpl implements SipListenerExt {

    private static Logger logger = Logger.getLogger(SipListenerImpl.class);

    private Endpoint endpoint;

    long wallClockTime;

    public SipListenerImpl(Endpoint endpoint) {
        this.endpoint = endpoint;

    }

    public void sendRegistration() throws Exception {
        if (!endpoint.getSutUA().getRegistrations().isEmpty()) {
            /*
             * For each registration.
             */
            for (String registration : endpoint.getSutUA().getRegistrations()) {
                String testUser = SipTester.getTestUser(registration);
                new RegistrationManager(testUser, endpoint).sendRegistrationRequest();
            }

        }
    }

    public void processRequest(RequestEvent requestEvent) {
        try {
            RequestExt request = (RequestExt) requestEvent.getRequest();
          
            ServerTransaction serverTransaction = requestEvent.getServerTransaction();
            SipProvider sipProvider = (SipProvider) requestEvent.getSource();
            if (serverTransaction == null) {
                serverTransaction = sipProvider.getNewServerTransaction(request);
            }

            ListeningPointExt listeningPoint = (ListeningPointExt) sipProvider
                    .getListeningPoint(request.getTopmostViaHeader().getTransport());

            Endpoint endpoint = SipTester.getEndpoint(listeningPoint);

            SipServerTransaction sst = endpoint.findSipServerTransaction(request);

            if (sst == null) {
                System.out.println("Unexpected request " + request.getFirstLine());
                if (request.getMethod().equals(Request.NOTIFY)) {
                    Response response = SipTester.getMessageFactory().createResponse(Response.OK,
                            request);
                    ContactHeader contact = SipUtilities.createContactHeader(listeningPoint);
                    response.addHeader(contact);
                    serverTransaction.sendResponse(response);
                    return;
                }
            }

            serverTransaction.setApplicationData(sst);
            sst.setServerTransaction(serverTransaction);
            SipDialog sipDialogWrapper = sst.getSipDialog();
            System.out.println("processRequest " + request.getFirstLine().trim() + " sipDialog = " + sipDialogWrapper);

          
            sst.sendResponses();
            
          
        } catch (Exception ex) {
            SipTester.fail("unexpected error processing request", ex);
        }

    }

    public void processResponse(ResponseEvent responseEvent) {
        try {
            Response response = responseEvent.getResponse();
            String method = SipUtilities.getCSeqMethod(response);
            if (method.equals(Request.REGISTER)) {
                ClientTransaction ctx = responseEvent.getClientTransaction();
                RegistrationManager registrationManager = (RegistrationManager) ctx
                        .getApplicationData();
                registrationManager.processResponse(responseEvent);
            } else if (method.equals(Request.INVITE) || method.equals(Request.SUBSCRIBE)
                    || method.equals(Request.NOTIFY) || method.equals(Request.PRACK)
                    || method.equals(Request.BYE)) {
                ClientTransaction ctx = responseEvent.getClientTransaction();
                SipClientTransaction sipCtx = (SipClientTransaction) ctx.getApplicationData();
                sipCtx.processResponse(responseEvent);
            }
        } catch (Exception ex) {
            logger.error("Unexpected exception ", ex);
            SipTester.fail("Unexpected exception", ex);
        }

    }

    public void processTimeout(TimeoutEvent timeoutEvent) {
        if (timeoutEvent.getClientTransaction() != null) {
            System.err.println("Test Failed! "
                    + timeoutEvent.getClientTransaction().getRequest().getMethod() + " Time out");
        } else {
            System.err.println("Test Failed! "
                    + timeoutEvent.getServerTransaction().getRequest().getMethod() + " Time out");

        }

    }

    @Override
    public void processDialogTimeout(DialogTimeoutEvent timeoutEvent) {
        // TODO Auto-generated method stub

    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        // TODO Auto-generated method stub

    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        // TODO Auto-generated method stub

    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        // TODO Auto-generated method stub

    }

}