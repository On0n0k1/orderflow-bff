package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/On0n0k1/orderflow-bff/order-service/internal/api"
	"github.com/On0n0k1/orderflow-bff/order-service/internal/events"
	"github.com/On0n0k1/orderflow-bff/order-service/internal/order"
)

func main() {
	port := getEnv("PORT", "8080")
	brokers := strings.Split(getEnv("KAFKA_BROKERS", "localhost:9092"), ",")

	store := order.NewInMemoryStore()
	producer := events.NewKafkaProducer(brokers, events.Topic)
	defer producer.Close()

	handler := api.NewHandler(store, producer)
	router := api.NewRouter(handler)

	srv := &http.Server{
		Addr:    ":" + port,
		Handler: router,
	}

	go func() {
		log.Printf("order-service listening on :%s", port)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("server error: %v", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		log.Printf("graceful shutdown failed: %v", err)
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
