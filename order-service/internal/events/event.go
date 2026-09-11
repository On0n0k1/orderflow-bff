// Package events defines the domain events published by order-service and
// the producer that publishes them to Kafka.
package events

import (
	"time"

	"github.com/On0n0k1/orderflow-bff/order-service/internal/order"
)

// Topic is the Kafka topic OrderCreated events are published to.
const Topic = "orders.created"

// OrderCreated is the event published whenever a new order is persisted.
// This is the contract the BFF service depends on; changes here must stay
// backward compatible or be versioned.
type OrderCreated struct {
	OrderID    string       `json:"orderId"`
	CustomerID string       `json:"customerId"`
	Items      []order.Item `json:"items"`
	Total      float64      `json:"total"`
	Timestamp  time.Time    `json:"timestamp"`
}

// NewOrderCreated builds the event payload for a persisted order.
func NewOrderCreated(o order.Order) OrderCreated {
	return OrderCreated{
		OrderID:    o.ID,
		CustomerID: o.CustomerID,
		Items:      o.Items,
		Total:      o.Total,
		Timestamp:  o.CreatedAt,
	}
}
