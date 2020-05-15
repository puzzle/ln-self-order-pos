package ch.puzzle.ln.zeus.service;

import ch.puzzle.ln.zeus.config.ApplicationProperties;
import ch.puzzle.ln.zeus.config.ApplicationProperties.Lnd;
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import io.grpc.stub.StreamObserver;
import org.lightningj.lnd.proto.LightningApi;
import org.lightningj.lnd.wrapper.*;
import org.lightningj.lnd.wrapper.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static ch.puzzle.ln.zeus.service.util.ConvertUtil.bytesToHex;
import static ch.puzzle.ln.zeus.service.util.ConvertUtil.hexToBytes;

@Component
public class LndService implements StreamObserver<org.lightningj.lnd.wrapper.message.Invoice> {

    private static final Logger LOG = LoggerFactory.getLogger(LndService.class);
    private static final long CONNECTION_RETRY_TIMEOUT = 10000;
    private static final long NODE_LOCKED_RETRY_TIMEOUT = 30000;

    private final ResourceLoader resourceLoader;
    private final ApplicationProperties applicationProperties;
    private final Set<InvoiceHandler> invoiceHandlers = new HashSet<>();

    private SynchronousLndAPI syncReadOnlyAPI;
    private SynchronousLndAPI syncInvoiceAPI;
    private AsynchronousLndAPI asyncAPI;

    public LndService(ResourceLoader resourceLoader, ApplicationProperties applicationProperties) throws Exception {
        this.resourceLoader = resourceLoader;
        this.applicationProperties = applicationProperties;

        subscribeToInvoices();
    }

    private void subscribeToInvoices() throws IOException, StatusException, ValidationException {
        InvoiceSubscription invoiceSubscription = new InvoiceSubscription();
        getAsyncApi().subscribeInvoices(invoiceSubscription, this);
    }

    private AsynchronousLndAPI getAsyncApi() throws IOException {
        if (asyncAPI == null) {
            Lnd lnd = applicationProperties.getLnd();
            asyncAPI = new AsynchronousLndAPI(
                lnd.getHost(),
                lnd.getPort(),
                getSslContext(),
                lnd.getInvoiceMacaroonContext()
            );
        }
        return asyncAPI;
    }

    private SynchronousLndAPI getSyncInvoiceApi() throws IOException {
        if (syncInvoiceAPI == null) {
            Lnd lnd = applicationProperties.getLnd();
            syncInvoiceAPI = new SynchronousLndAPI(
                lnd.getHost(),
                lnd.getPort(),
                getSslContext(),
                lnd.getInvoiceMacaroonContext()
            );
        }
        return syncInvoiceAPI;
    }

    private SynchronousLndAPI getSyncReadonlyApi() throws IOException {
        if (syncReadOnlyAPI == null) {
            Lnd lnd = applicationProperties.getLnd();
            syncReadOnlyAPI = new SynchronousLndAPI(
                lnd.getHost(),
                lnd.getPort(),
                getSslContext(),
                lnd.getReadonlyMacaroonContext()
            );
        }
        return syncReadOnlyAPI;
    }

    void addInvoiceHandler(InvoiceHandler handler) {
        invoiceHandlers.add(handler);
    }

    public GetInfoResponse getInfo() throws IOException, StatusException, ValidationException {
        LOG.info("getInfo called");
        try {
            return getSyncReadonlyApi().getInfo();
        } catch (StatusException | ValidationException | IOException e) {
            LOG.warn("getInfo call failed, retrying with fresh api");
            resetSyncReadOnlyApi();
            return getSyncReadonlyApi().getInfo();
        }
    }

    public ListChannelsResponse getChannels() throws IOException, StatusException, ValidationException {
        LOG.info("getChannels called");
        ListChannelsRequest listChannelsRequest = new ListChannelsRequest();
        listChannelsRequest.setActiveOnly(true);
        listChannelsRequest.setInactiveOnly(false);
        listChannelsRequest.setPublicOnly(true);
        listChannelsRequest.setPrivateOnly(false);
        try {
            return getSyncReadonlyApi().listChannels(listChannelsRequest);
        } catch (StatusException | ValidationException | IOException e) {
            LOG.warn("getChannels call failed, retrying with fresh api");
            resetSyncReadOnlyApi();
            return getSyncReadonlyApi().listChannels(listChannelsRequest);
        }
    }

    public NodeInfo getNodeInfo(String nodeId) throws IOException, StatusException, ValidationException {
        LOG.info("getNodeInfo called with nodeId={}", nodeId);
        NodeInfoRequest nodeInfoRequest = new NodeInfoRequest();
        nodeInfoRequest.setPubKey(nodeId);
        try {
            return getSyncReadonlyApi().getNodeInfo(nodeInfoRequest);
        } catch (StatusException | ValidationException | IOException e) {
            LOG.warn("getNodeInfo call failed, retrying with fresh api");
            resetSyncReadOnlyApi();
            return getSyncReadonlyApi().getNodeInfo(nodeInfoRequest);
        }
    }

    AddInvoiceResponse addInvoice(Invoice invoice) throws IOException, StatusException, ValidationException {
        LOG.info("addInvoice called with memo={}, amount={}", invoice.getMemo(), invoice.getValue());
        try {
            return getSyncInvoiceApi().addInvoice(invoice);
        } catch (StatusException | ValidationException | IOException e) {
            LOG.warn("addInvoice call failed, retrying with fresh api");
            resetSyncInvoiceApi();
            return getSyncInvoiceApi().addInvoice(invoice);
        }
    }

    Invoice lookupInvoice(String hashHex) throws IOException, StatusException, ValidationException {
        LOG.info("lookupInvoice called with {}", hashHex);
        PaymentHash paymentHash = new PaymentHash();
        byte[] rHash = hexToBytes(hashHex);
        paymentHash.setRHash(rHash);
        try {
            return getSyncInvoiceApi().lookupInvoice(paymentHash);
        } catch (StatusException | ValidationException | IOException e) {
            LOG.warn("lookupInvoice call failed, retrying with fresh api");
            resetSyncInvoiceApi();
            return getSyncInvoiceApi().lookupInvoice(paymentHash);
        }

    }

    @Override
    public void onNext(org.lightningj.lnd.wrapper.message.Invoice invoice) {
        String invoiceHex = bytesToHex(invoice.getRHash());
        LOG.info("Received update on subscription for {}.", invoiceHex);
        invoiceHandlers.forEach(h -> h.handleInvoiceUpdated(invoiceHex, invoice));
    }

    @Override
    public void onError(Throwable t) {
        try {
            if (t instanceof ServerSideException && ((ServerSideException) t).getStatus().getCode() == Status.Code.UNIMPLEMENTED) {
                LOG.error("It seems the lightning node is locked! Please unlock it. Will try again in {} seconds.", NODE_LOCKED_RETRY_TIMEOUT / 1000);
                Thread.sleep(NODE_LOCKED_RETRY_TIMEOUT);
            } else {
                LOG.error("Subscription for listening to invoices failed with message '{}'! Will try again in {} seconds.",
                    t.getMessage(), CONNECTION_RETRY_TIMEOUT / 1000);
                Thread.sleep(CONNECTION_RETRY_TIMEOUT);
            }

            // after waiting an appropriate amount of time, we try again...
            try {
                resetAsyncApi();
                subscribeToInvoices();
            } catch (StatusException | ValidationException | IOException e) {
                LOG.error("Couldn't subscribe to invoices! sleeping for 5 seconds", e);
                Thread.sleep(CONNECTION_RETRY_TIMEOUT);
                onError(e);
            }
        } catch (InterruptedException e1) {
            LOG.error("woke up from sleep, exiting loop", e1);
        }
    }

    private void resetSyncReadOnlyApi() {
        if (syncReadOnlyAPI != null) {
            try {
                syncReadOnlyAPI.close();
            } catch (StatusException e) {
                LOG.error("Couldn't close sync readonly api", e);
            } finally {
                syncReadOnlyAPI = null;
            }
        }
    }

    private void resetSyncInvoiceApi() {
        if (syncInvoiceAPI != null) {
            try {
                syncInvoiceAPI.close();
            } catch (StatusException e) {
                LOG.error("Couldn't close sync invoice api", e);
            } finally {
                syncInvoiceAPI = null;
            }
        }
    }

    private void resetAsyncApi() {
        if (asyncAPI != null) {
            try {
                asyncAPI.close();
            } catch (StatusException e) {
                LOG.error("Couldn't close async api", e);
            } finally {
                asyncAPI = null;
            }
        }
    }

    @Override
    public void onCompleted() {
        LOG.info("Subscription for listening to invoices completed.");
    }

    private SslContext getSslContext() throws IOException {
        return GrpcSslContexts
            .configure(SslContextBuilder.forClient(), SslProvider.OPENSSL)
            .trustManager(resourceLoader.getResource(applicationProperties.getLnd().getCertPath()).getInputStream())
            .build();
    }
}
