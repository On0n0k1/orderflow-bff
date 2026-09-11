// Package api holds the HTTP transport layer for order-service.
package api

import (
	"encoding/json"
	"errors"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

	"github.com/On0n0k1/orderflow-bff/order-service/internal/events"
	"github.com/On0n0k1/orderflow-bff/order-service/internal/order"
)

// Handler holds the dependencies needed to serve order-service's HTTP API.
type Handler struct {
	repo     order.Repository
	producer events.Producer
}

// NewHandler wires a Handler to its repository and event producer.
func NewHandler(repo order.Repository, producer events.Producer) *Handler {
	return &Handler{repo: repo, producer: producer}
}

type errorResponse struct {
	Error  string   `json:"error"`
	Issues []string `json:"issues,omitempty"`
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func writeError(w http.ResponseWriter, status int, message string, issues ...string) {
	writeJSON(w, status, errorResponse{Error: message, Issues: issues})
}

// CreateOrder handles POST /orders: validates the payload, persists the
// order, and publishes an OrderCreated event before responding.
func (h *Handler) CreateOrder(w http.ResponseWriter, r *http.Request) {
	var req order.CreateRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}

	if err := req.Validate(); err != nil {
		var vErr *order.ValidationError
		if errors.As(err, &vErr) {
			writeError(w, http.StatusBadRequest, "validation failed", vErr.Issues...)
			return
		}
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	o := order.Order{
		ID:         uuid.NewString(),
		CustomerID: req.CustomerID,
		Items:      req.Items,
		Total:      req.ComputeTotal(),
		CreatedAt:  time.Now().UTC(),
	}

	if err := h.repo.Save(o); err != nil {
		writeError(w, http.StatusInternalServerError, "failed to persist order")
		return
	}

	event := events.NewOrderCreated(o)
	if err := h.producer.PublishOrderCreated(r.Context(), event); err != nil {
		// The order is already persisted; the event stream is the only thing
		// that failed. Surfaced as 502 so a client/operator can tell this
		// apart from a validation or storage failure and retry/investigate.
		writeError(w, http.StatusBadGateway, "order created but failed to publish event")
		return
	}

	writeJSON(w, http.StatusCreated, o)
}

// GetOrder handles GET /orders/{id}.
func (h *Handler) GetOrder(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")

	o, ok := h.repo.Get(id)
	if !ok {
		writeError(w, http.StatusNotFound, "order not found")
		return
	}

	writeJSON(w, http.StatusOK, o)
}
