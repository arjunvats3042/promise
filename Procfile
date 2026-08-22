web: sh -c 'daphne -b 0.0.0.0 -p "${PORT:-8000}" config.asgi:application'
outbox_worker: python manage.py publish_outbox --continuous
goal_worker: python manage.py consume_goal_events
notification_worker: python manage.py run_notification_dispatcher --continuous
