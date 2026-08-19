import json

from confluent_kafka import Consumer, KafkaException, Producer
from django.conf import settings

_producer = None


class KafkaPublishError(Exception):
    pass


def get_kafka_producer():
    global _producer
    if _producer is None:
        _producer = Producer(
            {
                "bootstrap.servers": settings.KAFKA_BOOTSTRAP_SERVERS,
                "acks": "all",
                "socket.timeout.ms": 10000,
                "socket.connection.setup.timeout.ms": 10000,
                "message.timeout.ms": 15000,
            }
        )
    return _producer


def get_kafka_consumer(group_id):
    return Consumer(
        {
            "bootstrap.servers": settings.KAFKA_BOOTSTRAP_SERVERS,
            "group.id": group_id,
            "enable.auto.commit": False,
            "enable.auto.offset.store": False,
            "auto.offset.reset": "earliest",
            "socket.timeout.ms": 10000,
            "socket.connection.setup.timeout.ms": 10000,
        }
    )


def publish_record(topic, key, value, producer=None):
    if producer is None:
        producer = get_kafka_producer()
    encoded_key = key.encode("utf-8") if isinstance(key, str) else key
    encoded_value = json.dumps(value, separators=(",", ":"), default=str).encode(
        "utf-8"
    )
    delivery_error = []

    def on_delivery(err, msg):
        if err is not None:
            delivery_error.append(err)

    try:
        producer.produce(
            topic,
            key=encoded_key,
            value=encoded_value,
            on_delivery=on_delivery,
        )
        remaining = producer.flush(10)
    except KafkaException:
        raise KafkaPublishError("Kafka publish failed.") from None
    except Exception:
        raise KafkaPublishError("Kafka publish failed.") from None

    if remaining > 0:
        raise KafkaPublishError("Kafka publish failed.")
    if delivery_error:
        raise KafkaPublishError("Kafka publish failed.")
