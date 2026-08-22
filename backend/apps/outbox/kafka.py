import json
import logging

from confluent_kafka import Consumer, KafkaException, Producer
from django.conf import settings

logger = logging.getLogger("promise")

_producer = None


class KafkaPublishError(Exception):
    pass


def _build_kafka_config(base_conf: dict) -> dict:
    conf = dict(base_conf)
    security_protocol = getattr(settings, "KAFKA_SECURITY_PROTOCOL", "PLAINTEXT")
    if security_protocol and security_protocol.upper() != "PLAINTEXT":
        conf["security.protocol"] = security_protocol
        sasl_mechanism = getattr(settings, "KAFKA_SASL_MECHANISM", "")
        if sasl_mechanism:
            conf["sasl.mechanism"] = sasl_mechanism
        sasl_username = getattr(settings, "KAFKA_SASL_USERNAME", "")
        if sasl_username:
            conf["sasl.username"] = sasl_username
        sasl_password = getattr(settings, "KAFKA_SASL_PASSWORD", "")
        if sasl_password:
            conf["sasl.password"] = sasl_password
        # Prefer in-memory PEM string (KAFKA_SSL_CA_CERT) — works in Railway
        # containers where no local CA file exists.  Falls back to a filesystem
        # path (KAFKA_SSL_CA_LOCATION) for local development.
        # librdkafka >= 1.7 / confluent_kafka >= 2.x supports ssl.ca.pem.
        ssl_ca_cert = getattr(settings, "KAFKA_SSL_CA_CERT", "")
        ssl_ca_location = getattr(settings, "KAFKA_SSL_CA_LOCATION", "")
        if ssl_ca_cert:
            conf["ssl.ca.pem"] = ssl_ca_cert
            logger.debug("Kafka: using ssl.ca.pem from KAFKA_SSL_CA_CERT")
        elif ssl_ca_location:
            conf["ssl.ca.location"] = ssl_ca_location
            logger.debug("Kafka: using ssl.ca.location from KAFKA_SSL_CA_LOCATION")
    return conf


def get_kafka_producer():
    global _producer
    if _producer is None:
        producer_conf = _build_kafka_config(
            {
                "bootstrap.servers": settings.KAFKA_BOOTSTRAP_SERVERS,
                "acks": "all",
                "socket.timeout.ms": 10000,
                "socket.connection.setup.timeout.ms": 10000,
                "message.timeout.ms": 15000,
            }
        )
        _producer = Producer(producer_conf)
    return _producer


def get_kafka_consumer(group_id):
    consumer_conf = _build_kafka_config(
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
    return Consumer(consumer_conf)


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
