package events

import (
	"testing"
	"time"

	"github.com/On0n0k1/orderflow-bff/order-service/internal/order"
)

func TestNewOrderCreated(t *testing.T) {
	createdAt := time.Date(2026, 1, 1, 12, 0, 0, 0, time.UTC)
	o := order.Order{
		ID:         "order-1",
		CustomerID: "cust-1",
		Items:      []order.Item{{ProductID: "sku-1", Quantity: 2, UnitPrice: 5}},
		Total:      10,
		CreatedAt:  createdAt,
	}

	event := NewOrderCreated(o)

	if event.OrderID != o.ID {
		t.Errorf("OrderID = %q, want %q", event.OrderID, o.ID)
	}
	if event.CustomerID != o.CustomerID {
		t.Errorf("CustomerID = %q, want %q", event.CustomerID, o.CustomerID)
	}
	if event.Total != o.Total {
		t.Errorf("Total = %v, want %v", event.Total, o.Total)
	}
	if !event.Timestamp.Equal(createdAt) {
		t.Errorf("Timestamp = %v, want %v", event.Timestamp, createdAt)
	}
	if len(event.Items) != 1 || event.Items[0].ProductID != "sku-1" {
		t.Errorf("Items = %+v, want one item with ProductID sku-1", event.Items)
	}
}
