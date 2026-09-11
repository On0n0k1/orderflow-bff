FROM golang:1.26-alpine AS builder
WORKDIR /src

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 go build -o /out/order-service .

FROM alpine:3.20
RUN apk add --no-cache ca-certificates
COPY --from=builder /out/order-service /usr/local/bin/order-service

EXPOSE 8080
ENTRYPOINT ["order-service"]
