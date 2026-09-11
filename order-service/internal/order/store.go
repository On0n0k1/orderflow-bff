package order

import "sync"

// Repository persists and retrieves orders.
type Repository interface {
	Save(o Order) error
	Get(id string) (Order, bool)
}

// InMemoryStore is a Repository backed by a mutex-guarded map. It is not
// durable across restarts; that trade-off is documented in the project README.
type InMemoryStore struct {
	mu     sync.RWMutex
	orders map[string]Order
}

// NewInMemoryStore returns an empty, ready-to-use InMemoryStore.
func NewInMemoryStore() *InMemoryStore {
	return &InMemoryStore{orders: make(map[string]Order)}
}

func (s *InMemoryStore) Save(o Order) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.orders[o.ID] = o
	return nil
}

func (s *InMemoryStore) Get(id string) (Order, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	o, ok := s.orders[id]
	return o, ok
}
