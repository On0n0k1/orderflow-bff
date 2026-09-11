package order

import "testing"

func TestInMemoryStore_SaveAndGet(t *testing.T) {
	store := NewInMemoryStore()

	want := Order{ID: "order-1", CustomerID: "cust-1", Total: 10}
	if err := store.Save(want); err != nil {
		t.Fatalf("Save() error = %v", err)
	}

	got, ok := store.Get("order-1")
	if !ok {
		t.Fatal("Get() ok = false, want true")
	}
	if got.ID != want.ID || got.CustomerID != want.CustomerID || got.Total != want.Total {
		t.Fatalf("Get() = %+v, want %+v", got, want)
	}
}

func TestInMemoryStore_GetMissing(t *testing.T) {
	store := NewInMemoryStore()

	_, ok := store.Get("missing")
	if ok {
		t.Fatal("Get() ok = true, want false for missing order")
	}
}
