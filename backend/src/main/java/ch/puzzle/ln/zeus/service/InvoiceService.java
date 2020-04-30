package ch.puzzle.ln.zeus.service;

import ch.puzzle.ln.zeus.config.ApplicationProperties;
import ch.puzzle.ln.zeus.config.ApplicationProperties.Product;
import ch.puzzle.ln.zeus.config.ApplicationProperties.Shop;
import ch.puzzle.ln.zeus.domain.Invoice;
import ch.puzzle.ln.zeus.domain.OrderItem;
import ch.puzzle.ln.zeus.domain.enums.InvoiceType;
import ch.puzzle.ln.zeus.repository.InvoiceRepository;
import ch.puzzle.ln.zeus.service.dto.InvoiceDTO;
import ch.puzzle.ln.zeus.service.mapper.InvoiceMapper;
import ch.puzzle.ln.zeus.service.util.ConvertUtil;
import ch.puzzle.ln.zeus.web.rest.errors.InternalServerErrorException;
import ch.puzzle.ln.zeus.web.rest.errors.InvalidOrderException;
import ch.puzzle.ln.zeus.web.rest.errors.OutsideOpeningHoursException;
import ch.puzzle.ln.zeus.web.rest.vm.BeerTapVM;
import ch.puzzle.ln.zeus.web.rest.vm.DonationVM;
import ch.puzzle.ln.zeus.web.rest.vm.OrderItemVM;
import ch.puzzle.ln.zeus.web.rest.vm.OrderVM;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.lightningj.lnd.wrapper.StatusException;
import org.lightningj.lnd.wrapper.ValidationException;
import org.lightningj.lnd.wrapper.message.AddInvoiceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static ch.puzzle.ln.zeus.service.util.ConvertUtil.bytesToHex;
import static ch.puzzle.ln.zeus.service.util.ConvertUtil.unixTimestampToInstant;

/**
 * Service Implementation for managing Invoice.
 */
@Service
@Transactional
public class InvoiceService implements InvoiceHandler {

    private static final Logger LOG = LoggerFactory.getLogger(InvoiceService.class);
    private static final Integer DAY_IN_MINUTES = 24 * 60;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceMapper invoiceMapper;
    private final ApplicationProperties applicationProperties;
    private final BitcoinService bitcoinService;
    private final LndService lndService;
    private final ApplicationEventPublisher eventPublisher;
    private final ShopService shopService;
    private final Set<String> pendingInvoices = new HashSet<>();
    private final Counter paidInvoices;
    private final Counter createdInvoices;

    public InvoiceService(InvoiceRepository invoiceRepository, InvoiceMapper invoiceMapper, ApplicationProperties applicationProperties,
                          BitcoinService bitcoinService, LndService lndService, ApplicationEventPublisher eventPublisher,
                          ShopService shopService, MeterRegistry meterRegistry) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceMapper = invoiceMapper;
        this.applicationProperties = applicationProperties;
        this.bitcoinService = bitcoinService;
        this.lndService = lndService;
        this.eventPublisher = eventPublisher;
        this.shopService = shopService;
        this.lndService.addInvoiceHandler(this);
        this.paidInvoices = meterRegistry.counter("zeus.invoices.paid");
        this.createdInvoices = meterRegistry.counter("zeus.invoices.created");
    }

    public InvoiceDTO saveGenerated(Invoice invoice) {
        LOG.debug("Request to save Invoice : {}", invoice);
        invoice = invoiceRepository.save(invoice);
        return invoiceMapper.toDto(invoice);
    }

    /**
     * Save a invoice.
     *
     * @param invoiceDTO the entity to save
     * @return the persisted entity
     */
    public InvoiceDTO save(InvoiceDTO invoiceDTO) {
        LOG.debug("Request to save Invoice : {}", invoiceDTO);
        Invoice invoice = invoiceMapper.toEntity(invoiceDTO);
        invoice = invoiceRepository.save(invoice);
        return invoiceMapper.toDto(invoice);
    }

    /**
     * Get all the invoices.
     *
     * @return the list of entities
     */
    @Transactional(readOnly = true)
    public List<InvoiceDTO> findAll() {
        LOG.debug("Request to get all Invoices");
        return invoiceRepository.findAll().stream()
            .map(invoiceMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }


    /**
     * Get one invoice by id.
     *
     * @param id the id of the entity
     * @return the entity
     */
    @Transactional(readOnly = true)
    public Optional<InvoiceDTO> findOne(Long id) {
        LOG.debug("Request to get Invoice : {}", id);
        return invoiceRepository.findById(id)
            .map(invoiceMapper::toDto);
    }

    /**
     * Delete the invoice by id.
     *
     * @param id the id of the entity
     */
    public void delete(Long id) {
        LOG.debug("Request to delete Invoice : {}", id);
        invoiceRepository.deleteById(id);
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000) // every 10 minutes
    public void removeOldSelfOrderUnsettledInvoices() {
        Instant tenMinutesAgo = Instant.now().minusSeconds(10 * 60);
        List<Invoice> oldInvoices = invoiceRepository.findAllByInvoiceTypeAndSettledFalseAndCreationDateLessThan(InvoiceType.SELF_ORDER, tenMinutesAgo);
        oldInvoices.forEach(i -> delete(i.getId()));
    }

    public Invoice validateAndMapOrder(OrderVM order) {
        Shop shop = applicationProperties.getShop();
        InvalidOrderException generalException = new InvalidOrderException();
        OutsideOpeningHoursException openingHoursException = new OutsideOpeningHoursException();
        if (order.getPickupDelayMinutes() < 0 || order.getPickupDelayMinutes() > DAY_IN_MINUTES) {
            throw generalException;
        }
        if (shopService.isClosed()) {
            throw openingHoursException;
        }
        if (!shopService.areOpeningHoursOverridden() && !shopService.isShopOpenInMinutes(order.getPickupDelayMinutes())) {
            throw openingHoursException;
        }
        if (order.getOrderName().trim().isEmpty()) {
            throw generalException;
        }
        List<OrderItemVM> items = order.getOrderItems();
        for (OrderItemVM item : items) {
            if (item.getCount() != item.getOptions().size()) {
                throw generalException;
            }
            if (!applicationProperties.getShop().findProductByKey(item.getProductKey()).isPresent()) {
                throw generalException;
            }

        }

        // everything ok so far, let's build the invoice
        Invoice invoice = new Invoice();
        invoice.setOrderName(order.getOrderName());
        invoice.setPickupLocation(order.getPickupLocation());
        invoice.setPickupDelayMinutes(order.getPickupDelayMinutes());
        invoice.setInvoiceType(order.getInvoiceType());
        order.getOrderItems().forEach(i -> {
            OrderItem item = new OrderItem();
            item.setProductKey(i.getProductKey());
            item.setCount(i.getCount());
            item.setOptions(i.getOptions());
            item.setInvoice(invoice);
            invoice.getOrderItems().add(item);
        });
        invoice.setReferenceId(UUID.randomUUID().toString().toLowerCase());
        String memoPrefix = applicationProperties.getMemoPrefix();
        if (order.getMemoPrefix() != null) {
            memoPrefix = order.getMemoPrefix().trim() + " ";
        }
        invoice.setMemo(memoPrefix + invoice.getReferenceIdShort() + " " + invoice.getOrderName() +
            " (" + ConvertUtil.formatCurrency(applicationProperties.getCurrencyTicker(), calculateTotalChf(invoice, shop)) + ")");
        return invoice;
    }

    public Invoice validateAndMapDonation(DonationVM donation) {
        Invoice invoice = new Invoice();
        invoice.setOrderName("Donation");
        invoice.setInvoiceType(InvoiceType.DONATION);
        invoice.setReferenceId(UUID.randomUUID().toString().toLowerCase());
        invoice.setAmountChf(0.0d);
        invoice.setExchangeRate(0.0d);
        invoice.setAmount(donation.getAmount().longValue());
        invoice.setMemo(invoice.getReferenceIdShort() + ": Donation to " + donation.getMemoPrefix());
        return invoice;
    }

    public Invoice validateAndMapBeerTap(BeerTapVM beerTap) {
        Invoice invoice = new Invoice();
        invoice.setOrderName(beerTap.getOrderName());
        invoice.setInvoiceType(InvoiceType.BEER_TAP);
        invoice.setReferenceId(UUID.randomUUID().toString().toLowerCase());
        if (beerTap.isAmountInSats()) {
            invoice.setAmountChf(0.0d);
            invoice.setExchangeRate(0.0d);
            invoice.setAmount(beerTap.getAmount().longValue());
        } else {
            calculatePrice(invoice, beerTap.getAmount());
        }
        beerTap.getOrderItems().forEach(i -> {
            OrderItem item = new OrderItem();
            item.setProductKey(i.getProductKey());
            item.setCount(i.getCount());
            item.setOptions(i.getOptions());
            item.setInvoice(invoice);
            invoice.getOrderItems().add(item);
        });
        invoice.setMemo(beerTap.getMemoPrefix() + " " + invoice.getReferenceIdShort() + ": " + beerTap.getOrderName());
        return invoice;
    }

    public void calculatePrice(Invoice invoice) {
        Double total = calculateTotalChf(invoice, applicationProperties.getShop());
        calculatePrice(invoice, total);
    }

    private void calculatePrice(Invoice invoice, Double total) {
        Double exchangeRate = bitcoinService.buyPricePerBitcoinIn(applicationProperties.getCurrencyTicker());
        invoice.setAmountChf(total);
        invoice.setExchangeRate(exchangeRate);
        invoice.setAmount(bitcoinService.satoshisForPrice(total, exchangeRate));
    }

    public void generateLndInvoice(Invoice invoice) {
        try {
            AddInvoiceResponse response = lndService.addInvoice(mapToLndInvoice(invoice));
            String hashHex = bytesToHex(response.getRHash());
            invoice.setHashHex(hashHex);
            invoice.setPaymentRequest(response.getPaymentRequest());
            invoice.setSettled(false);
            org.lightningj.lnd.wrapper.message.Invoice generated = lndService.lookupInvoice(hashHex);
            invoice.setCreationDate(unixTimestampToInstant(generated.getCreationDate()));
            pendingInvoices.add(hashHex);
            createdInvoices.increment();
        } catch (StatusException | ValidationException | IOException e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    public void handleInvoiceUpdated(String hashHex, org.lightningj.lnd.wrapper.message.Invoice invoice) {
        Invoice dbInvoice = invoiceRepository.findByHashHex(hashHex);
        if (dbInvoice != null) {
            boolean wasSettledPreviously = dbInvoice.isSettled();
            dbInvoice.setSettled(invoice.getSettled());
            dbInvoice.setPreimageHex(invoice.getRPreimage() != null ? bytesToHex(invoice.getRPreimage()) : null);
            dbInvoice.setSettleDate(unixTimestampToInstant(invoice.getSettleDate()));
            dbInvoice = invoiceRepository.save(dbInvoice);
            sendEvent(invoiceMapper.toDto(dbInvoice), !wasSettledPreviously);

            if (invoice.getSettled() && !wasSettledPreviously) {
                pendingInvoices.remove(hashHex);
                paidInvoices.increment();
            }
        }
    }

    public void sendEvent(InvoiceDTO invoice, boolean firstSettleEvent) {
        eventPublisher.publishEvent(new InvoiceEvent(this, invoice, firstSettleEvent));
    }

    public static Double calculateTotalChf(Invoice invoice, Shop shop) {
        return invoice.getOrderItems().stream()
            .mapToDouble(item -> item.getCount() * shop.findProductByKey(item.getProductKey()).map(Product::getPrice).orElse(0.0d))
            .sum();
    }

    public static Double calculateTotalChf(InvoiceDTO invoice, Shop shop) {
        return invoice.getOrderItems().stream()
            .mapToDouble(item -> item.getCount() * shop.findProductByKey(item.getProductKey()).map(Product::getPrice).orElse(0.0d))
            .sum();
    }

    private org.lightningj.lnd.wrapper.message.Invoice mapToLndInvoice(Invoice invoice) {
        org.lightningj.lnd.wrapper.message.Invoice lndInvoice = new org.lightningj.lnd.wrapper.message.Invoice();
        lndInvoice.setMemo(invoice.getMemo());
        lndInvoice.setValue(invoice.getAmount());
        lndInvoice.setExpiry(applicationProperties.getLnd().getInvoiceExpirySeconds());
        return lndInvoice;
    }
}
