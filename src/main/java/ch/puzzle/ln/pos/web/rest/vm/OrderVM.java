package ch.puzzle.ln.pos.web.rest.vm;

import ch.puzzle.ln.pos.domain.enums.OrderItemType;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class OrderVM {

    @NotNull
    private String orderName;

    private Boolean autoGenerated;

    @NotEmpty
    private List<OrderItemVM> orderItems = new ArrayList<>();

    public String getOrderName() {
        return orderName;
    }

    public void setOrderName(String orderName) {
        this.orderName = orderName;
    }

    public List<OrderItemVM> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<OrderItemVM> orderItems) {
        this.orderItems = orderItems;
    }

    public Boolean getAutoGenerated() {
        return autoGenerated;
    }

    public void setAutoGenerated(Boolean autoGenerated) {
        this.autoGenerated = autoGenerated;
    }

    public static class OrderItemVM {

        private OrderItemType itemType;
        private Integer count;
        private List<String> options = new ArrayList<>();

        public OrderItemType getItemType() {
            return itemType;
        }

        public void setItemType(OrderItemType itemType) {
            this.itemType = itemType;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public List<String> getOptions() {
            return options;
        }

        public void setOptions(List<String> options) {
            this.options = options;
        }
    }
}