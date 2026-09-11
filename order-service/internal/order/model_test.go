package order

import "testing"

func TestCreateRequest_Validate(t *testing.T) {
	tests := []struct {
		name    string
		req     CreateRequest
		wantErr bool
	}{
		{
			name: "valid request",
			req: CreateRequest{
				CustomerID: "cust-1",
				Items: []Item{
					{ProductID: "sku-1", Quantity: 2, UnitPrice: 9.99},
				},
			},
			wantErr: false,
		},
		{
			name:    "missing customer id",
			req:     CreateRequest{Items: []Item{{ProductID: "sku-1", Quantity: 1, UnitPrice: 1}}},
			wantErr: true,
		},
		{
			name:    "empty items",
			req:     CreateRequest{CustomerID: "cust-1", Items: []Item{}},
			wantErr: true,
		},
		{
			name: "negative quantity",
			req: CreateRequest{
				CustomerID: "cust-1",
				Items:      []Item{{ProductID: "sku-1", Quantity: -1, UnitPrice: 1}},
			},
			wantErr: true,
		},
		{
			name: "negative unit price",
			req: CreateRequest{
				CustomerID: "cust-1",
				Items:      []Item{{ProductID: "sku-1", Quantity: 1, UnitPrice: -1}},
			},
			wantErr: true,
		},
		{
			name: "missing product id",
			req: CreateRequest{
				CustomerID: "cust-1",
				Items:      []Item{{Quantity: 1, UnitPrice: 1}},
			},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.req.Validate()
			if (err != nil) != tt.wantErr {
				t.Fatalf("Validate() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestCreateRequest_ComputeTotal(t *testing.T) {
	req := CreateRequest{
		CustomerID: "cust-1",
		Items: []Item{
			{ProductID: "sku-1", Quantity: 2, UnitPrice: 10},
			{ProductID: "sku-2", Quantity: 1, UnitPrice: 5.5},
		},
	}

	want := 25.5
	if got := req.ComputeTotal(); got != want {
		t.Fatalf("ComputeTotal() = %v, want %v", got, want)
	}
}
