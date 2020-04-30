package ch.puzzle.ln.zeus.service.processors;

import ch.puzzle.ln.zeus.config.ApplicationProperties;
import ch.puzzle.ln.zeus.service.InvoiceEvent;
import ch.puzzle.ln.zeus.service.MailService;
import ch.puzzle.ln.zeus.service.dto.InvoiceDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import static ch.puzzle.ln.zeus.domain.enums.InvoiceType.BEER_TAP;
import static ch.puzzle.ln.zeus.domain.enums.InvoiceType.DIRTY_FIAT;

@Service
public class MailSendOrderProcessor implements ApplicationListener<InvoiceEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(MailSendOrderProcessor.class);

    private final ApplicationProperties properties;
    private final MailService mailService;

    public MailSendOrderProcessor(ApplicationProperties properties, MailService mailService) {
        this.properties = properties;
        this.mailService = mailService;
    }

    @Override
    public void onApplicationEvent(InvoiceEvent event) {
        if (!properties.getMail().isProcessorEnabled()) {
            LOG.info("Sending mails is disabled by configuration.");
            return;
        }

        InvoiceDTO invoice = event.getInvoice();

        // Make sure we only send an e-mail if the invoice has been settled
        // for the first time.
        if (invoice.isSettled() && event.isFirstSettleEvent() && invoice.getInvoiceType() != BEER_TAP) {
            mailService.sendOrderConfirmation(invoice);
        }

        if (!invoice.isSettled() && invoice.getInvoiceType() == DIRTY_FIAT) {
            mailService.sendOrderConfirmation(invoice);
        }
    }
}
