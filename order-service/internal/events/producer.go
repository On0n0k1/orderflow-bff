package events

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/segmentio/kafka-go"
)

// Producer publishes domain events.
type Producer interface {
	PublishOrderCreated(ctx context.Context, event OrderCreated) error
	Close() error
}

// KafkaProducer publishes events to Kafka using a single-topic writer.
type KafkaProducer struct {
	writer *kafka.Writer
}

// NewKafkaProducer returns a Producer that writes to topic on the given brokers.
func NewKafkaProducer(brokers []string, topic string) *KafkaProducer {
	return &KafkaProducer{
		writer: &kafka.Writer{
			Addr:                   kafka.TCP(brokers...),
			Topic:                  topic,
			Balancer:               &kafka.LeastBytes{},
			AllowAutoTopicCreation: true,
		},
	}
}

func (p *KafkaProducer) PublishOrderCreated(ctx context.Context, event OrderCreated) error {
	payload, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("marshal OrderCreated event: %w", err)
	}

	msg := kafka.Message{
		Key:   []byte(event.OrderID),
		Value: payload,
	}

	if err := p.writer.WriteMessages(ctx, msg); err != nil {
		return fmt.Errorf("publish OrderCreated event: %w", err)
	}
	return nil
}

func (p *KafkaProducer) Close() error {
	return p.writer.Close()
}
