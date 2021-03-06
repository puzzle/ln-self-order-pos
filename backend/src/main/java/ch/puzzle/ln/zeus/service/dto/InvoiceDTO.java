package ch.puzzle.ln.zeus.service.dto;

import ch.puzzle.ln.zeus.domain.enums.InvoiceType;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class InvoiceDTO implements Serializable {

    private Long id;
    private String referenceId;
    private String referenceIdShort;
    private String memo;
    private String hashHex;
    private String preimageHex;
    private Long amount;
    private Double amountChf;
    private Double exchangeRate;
    private String orderName;
    private Boolean settled;
    private Instant creationDate;
    private Instant settleDate;
    private String paymentRequest;
    private List<OrderItemDTO> orderItems = new ArrayList<>();
    private InvoiceType invoiceType;
    private String pickupLocation;
    private Integer pickupDelayMinutes;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getReferenceIdShort() {
        return referenceIdShort;
    }

    public void setReferenceIdShort(String referenceIdShort) {
        this.referenceIdShort = referenceIdShort;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public String getHashHex() {
        return hashHex;
    }

    public void setHashHex(String hashHex) {
        this.hashHex = hashHex;
    }

    public String getPreimageHex() {
        return preimageHex;
    }

    public void setPreimageHex(String preimageHex) {
        this.preimageHex = preimageHex;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public Double getAmountChf() {
        return amountChf;
    }

    public void setAmountChf(Double amountChf) {
        this.amountChf = amountChf;
    }

    public Double getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(Double exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public String getOrderName() {
        return orderName;
    }

    public void setOrderName(String orderName) {
        this.orderName = orderName;
    }

    public Boolean isSettled() {
        return settled;
    }

    public void setSettled(Boolean settled) {
        this.settled = settled;
    }

    public Instant getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Instant creationDate) {
        this.creationDate = creationDate;
    }

    public Instant getSettleDate() {
        return settleDate;
    }

    public void setSettleDate(Instant settleDate) {
        this.settleDate = settleDate;
    }

    public String getPaymentRequest() {
        return paymentRequest;
    }

    public void setPaymentRequest(String paymentRequest) {
        this.paymentRequest = paymentRequest;
    }

    public List<OrderItemDTO> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<OrderItemDTO> orderItems) {
        this.orderItems = orderItems;
    }

    public InvoiceType getInvoiceType() {
        return invoiceType;
    }

    public void setInvoiceType(InvoiceType invoiceType) {
        this.invoiceType = invoiceType;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public void setPickupLocation(String pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public Integer getPickupDelayMinutes() {
        return pickupDelayMinutes;
    }

    public void setPickupDelayMinutes(Integer pickupDelayMinutes) {
        this.pickupDelayMinutes = pickupDelayMinutes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        InvoiceDTO invoiceDTO = (InvoiceDTO) o;
        if (invoiceDTO.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), invoiceDTO.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "InvoiceDTO{" +
            "id=" + id +
            ", referenceId='" + referenceId + '\'' +
            ", memo='" + memo + '\'' +
            ", hashHex='" + hashHex + '\'' +
            ", preimageHex='" + preimageHex + '\'' +
            ", amount=" + amount +
            ", amountChf=" + amountChf +
            ", exchangeRate=" + exchangeRate +
            ", orderName='" + orderName + '\'' +
            ", settled=" + settled +
            ", creationDate=" + creationDate +
            ", settleDate=" + settleDate +
            ", paymentRequest='" + paymentRequest + '\'' +
            ", orderItems=" + orderItems +
            '}';
    }
}
