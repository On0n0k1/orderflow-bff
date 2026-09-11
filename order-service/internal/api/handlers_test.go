package api

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/go-chi/chi/v5"

	"github.com/On0n0k1/orderflow-bff/order-service/internal/events"
	"github.com/On0n0k1/orderflow-bff/order-service/internal/order"
)

// fakeProducer is a test double for events.Producer.
type fakeProducer struct {
	published []events.OrderCreated
	err       error
}

func (f *fakeProducer) PublishOrderCreated(_ context.Context, event events.OrderCreated) error {
	if f.err != nil {
		return f.err
	}
	f.published = append(f.published, event)
	return nil
}

func (f *fakeProducer) Close() error { return nil }

func newTestRouter(producer events.Producer) (*chi.Mux, order.Repository) {
	store := order.NewInMemoryStore()
	handler := NewHandler(store, producer)
	return NewRouter(handler), store
}

func TestCreateOrder_Success(t *testing.T) {
	producer := &fakeProducer{}
	router, store := newTestRouter(producer)

	body := `{"customerId":"cust-1","items":[{"productId":"sku-1","quantity":2,"unitPrice":10}]}`
	req := httptest.NewRequest(http.MethodPost, "/orders", bytes.NewBufferString(body))
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusCreated {
		t.Fatalf("status = %d, want %d, body = %s", rec.Code, http.StatusCreated, rec.Body.String())
	}

	var got order.Order
	if err := json.Unmarshal(rec.Body.Bytes(), &got); err != nil {
		t.Fatalf("unmarshal response: %v", err)
	}
	if got.Total != 20 {
		t.Errorf("Total = %v, want 20", got.Total)
	}
	if got.ID == "" {
		t.Error("ID is empty, want generated id")
	}

	if _, ok := store.Get(got.ID); !ok {
		t.Error("order was not persisted")
	}

	if len(producer.published) != 1 || producer.published[0].OrderID != got.ID {
		t.Errorf("published events = %+v, want one event for order %s", producer.published, got.ID)
	}
}

func TestCreateOrder_ValidationFailure(t *testing.T) {
	router, _ := newTestRouter(&fakeProducer{})

	body := `{"customerId":"","items":[]}`
	req := httptest.NewRequest(http.MethodPost, "/orders", bytes.NewBufferString(body))
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d, body = %s", rec.Code, http.StatusBadRequest, rec.Body.String())
	}
}

func TestCreateOrder_MalformedJSON(t *testing.T) {
	router, _ := newTestRouter(&fakeProducer{})

	req := httptest.NewRequest(http.MethodPost, "/orders", bytes.NewBufferString("{not json"))
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusBadRequest)
	}
}

func TestCreateOrder_PublishFailure(t *testing.T) {
	producer := &fakeProducer{err: errors.New("kafka unavailable")}
	router, _ := newTestRouter(producer)

	body := `{"customerId":"cust-1","items":[{"productId":"sku-1","quantity":1,"unitPrice":5}]}`
	req := httptest.NewRequest(http.MethodPost, "/orders", bytes.NewBufferString(body))
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadGateway {
		t.Fatalf("status = %d, want %d, body = %s", rec.Code, http.StatusBadGateway, rec.Body.String())
	}
}

func TestGetOrder_Found(t *testing.T) {
	router, store := newTestRouter(&fakeProducer{})
	_ = store.Save(order.Order{ID: "order-1", CustomerID: "cust-1", Total: 42})

	req := httptest.NewRequest(http.MethodGet, "/orders/order-1", nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d, body = %s", rec.Code, http.StatusOK, rec.Body.String())
	}

	var got order.Order
	if err := json.Unmarshal(rec.Body.Bytes(), &got); err != nil {
		t.Fatalf("unmarshal response: %v", err)
	}
	if got.ID != "order-1" {
		t.Errorf("ID = %q, want %q", got.ID, "order-1")
	}
}

func TestGetOrder_NotFound(t *testing.T) {
	router, _ := newTestRouter(&fakeProducer{})

	req := httptest.NewRequest(http.MethodGet, "/orders/missing", nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusNotFound)
	}
}
