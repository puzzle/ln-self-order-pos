package ch.puzzle.ln.zeus.web.rest;

import ch.puzzle.ln.zeus.domain.Invoice;
import ch.puzzle.ln.zeus.domain.enums.InvoiceType;
import ch.puzzle.ln.zeus.security.AuthoritiesConstants;
import ch.puzzle.ln.zeus.service.InvoiceService;
import ch.puzzle.ln.zeus.service.dto.InvoiceDTO;
import ch.puzzle.ln.zeus.web.rest.errors.BadRequestAlertException;
import ch.puzzle.ln.zeus.web.rest.errors.InternalServerErrorException;
import ch.puzzle.ln.zeus.web.rest.util.HeaderUtil;
import ch.puzzle.ln.zeus.web.rest.vm.BeerTapVM;
import ch.puzzle.ln.zeus.web.rest.vm.DonationVM;
import ch.puzzle.ln.zeus.web.rest.vm.OrderVM;
import io.github.jhipster.web.util.ResponseUtil;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@Timed
public class InvoiceResource {

    private static final Logger LOG = LoggerFactory.getLogger(InvoiceResource.class);
    private static final String ENTITY_NAME = "invoice";

    private final InvoiceService invoiceService;

    public InvoiceResource(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping("/invoice/new")
    public ResponseEntity<InvoiceDTO> createInvoice(@Valid @RequestBody OrderVM order) {
        LOG.debug("REST request to save Order : {}", order);
        Invoice invoice = invoiceService.validateAndMapOrder(order);
        invoiceService.calculatePrice(invoice);
        invoiceService.generateLndInvoice(invoice);
        InvoiceDTO result = invoiceService.saveGenerated(invoice);

        try {
            return ResponseEntity.created(new URI("/api/invoices/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
                .body(result);
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @PostMapping("/invoice/donation")
    public ResponseEntity<InvoiceDTO> createDonation(@Valid @RequestBody DonationVM donation) {
        LOG.debug("REST request to save donation : {}", donation);
        Invoice invoice = invoiceService.validateAndMapDonation(donation);
        invoiceService.generateLndInvoice(invoice);
        InvoiceDTO result = invoiceService.saveGenerated(invoice);

        try {
            return ResponseEntity.created(new URI("/api/invoices/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
                .body(result);
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @PostMapping("/invoice/dirtyfiat")
    public ResponseEntity<InvoiceDTO> createDirtyFiatInvoice(@Valid @RequestBody OrderVM order) {
        LOG.debug("REST request to save dirty fiat Order : {}", order);
        Invoice invoice = invoiceService.validateAndMapOrder(order);
        invoiceService.calculatePrice(invoice);
        invoice.setAmount(0L);
        invoice.setExchangeRate(0.0d);
        invoice.setSettled(false);
        invoice.setHashHex("00000000");
        invoice.setPreimageHex("00000000");
        invoice.setInvoiceType(InvoiceType.DIRTY_FIAT);
        invoice.setCreationDate(Instant.now());
        invoice.setSettleDate(Instant.now());
        InvoiceDTO result = invoiceService.saveGenerated(invoice);
        invoiceService.sendEvent(result, true);

        try {
            return ResponseEntity.created(new URI("/api/invoices/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
                .body(result);
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @PostMapping("/invoice/beertap")
    public ResponseEntity<InvoiceDTO> createBeerInvoice(@Valid @RequestBody BeerTapVM beerTap) {
        LOG.debug("REST request to save beerTap : {}", beerTap);
        Invoice invoice = invoiceService.validateAndMapBeerTap(beerTap);
        invoiceService.generateLndInvoice(invoice);
        InvoiceDTO result = invoiceService.saveGenerated(invoice);

        try {
            return ResponseEntity.created(new URI("/api/invoices/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
                .body(result);
        } catch (Exception e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    /**
     * PUT  /invoices : Updates an existing invoice.
     *
     * @param invoiceDTO the invoiceDTO to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated invoiceDTO,
     * or with status 400 (Bad Request) if the invoiceDTO is not valid,
     * or with status 500 (Internal Server Error) if the invoiceDTO couldn't be updated
     */
    @PutMapping("/invoices")
    @Secured(AuthoritiesConstants.ADMIN)
    public ResponseEntity<InvoiceDTO> updateInvoice(@RequestBody InvoiceDTO invoiceDTO) {
        LOG.debug("REST request to update Invoice : {}", invoiceDTO);
        if (invoiceDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        InvoiceDTO result = invoiceService.save(invoiceDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, invoiceDTO.getId().toString()))
            .body(result);
    }

    /**
     * GET  /invoices : get all the invoices.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of invoices in body
     */
    @GetMapping("/invoices")
    @Secured(AuthoritiesConstants.ADMIN)
    public List<InvoiceDTO> getAllInvoices() {
        LOG.debug("REST request to get all Invoices");
        return invoiceService.findAll();
    }

    /**
     * GET  /invoices/:id : get the "id" invoice.
     *
     * @param id the id of the invoiceDTO to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the invoiceDTO, or with status 404 (Not Found)
     */
    @GetMapping("/invoices/{id}")
    @Secured(AuthoritiesConstants.ADMIN)
    public ResponseEntity<InvoiceDTO> getInvoice(@PathVariable Long id) {
        LOG.debug("REST request to get Invoice : {}", id);
        Optional<InvoiceDTO> invoiceDTO = invoiceService.findOne(id);
        return ResponseUtil.wrapOrNotFound(invoiceDTO);
    }

    /**
     * DELETE  /invoices/:id : delete the "id" invoice.
     *
     * @param id the id of the invoiceDTO to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/invoices/{id}")
    @Secured(AuthoritiesConstants.ADMIN)
    public ResponseEntity<Void> deleteInvoice(@PathVariable Long id) {
        LOG.debug("REST request to delete Invoice : {}", id);
        invoiceService.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
