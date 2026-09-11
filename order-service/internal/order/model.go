package order

import (
	"fmt"
	"strings"
	"time"
)

// Item is a single line item within an order.
type Item struct {
	ProductID string  `json:"productId"`
	Quantity  int     `json:"quantity"`
	UnitPrice float64 `json:"unitPrice"`
}

// Order is a persisted order, including its server-computed total.
type Order struct {
	ID         string    `json:"id"`
	CustomerID string    `json:"customerId"`
	Items      []Item    `json:"items"`
	Total      float64   `json:"total"`
	CreatedAt  time.Time `json:"createdAt"`
}

// CreateRequest is the client-supplied payload for creating an order.
// Total is deliberately not accepted from the client; it is computed
// server-side from the line items so it can't be spoofed or drift from them.
type CreateRequest struct {
	CustomerID string `json:"customerId"`
	Items      []Item `json:"items"`
}

// ValidationError reports one or more problems with a CreateRequest.
type ValidationError struct {
	Issues []string
}

func (e *ValidationError) Error() string {
	return fmt.Sprintf("invalid order: %s", strings.Join(e.Issues, "; "))
}

// Validate checks a CreateRequest for well-formedness, collecting every
// issue found rather than failing on the first one.
func (r CreateRequest) Validate() error {
	var issues []string

	if strings.TrimSpace(r.CustomerID) == "" {
		issues = append(issues, "customerId is required")
	}

	if len(r.Items) == 0 {
		issues = append(issues, "items must not be empty")
	}

	for i, item := range r.Items {
		if strings.TrimSpace(item.ProductID) == "" {
			issues = append(issues, fmt.Sprintf("items[%d].productId is required", i))
		}
		if item.Quantity <= 0 {
			issues = append(issues, fmt.Sprintf("items[%d].quantity must be positive", i))
		}
		if item.UnitPrice < 0 {
			issues = append(issues, fmt.Sprintf("items[%d].unitPrice must not be negative", i))
		}
	}

	if len(issues) > 0 {
		return &ValidationError{Issues: issues}
	}
	return nil
}

// ComputeTotal sums quantity*unitPrice across all items.
func (r CreateRequest) ComputeTotal() float64 {
	var total float64
	for _, item := range r.Items {
		total += float64(item.Quantity) * item.UnitPrice
	}
	return total
}
