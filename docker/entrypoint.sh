#!/usr/bin/env bash
set -e

cd /var/www/html

# Ensure an app key exists (generate one if the .env placeholder is empty).
if ! grep -q "^APP_KEY=base64:" .env 2>/dev/null; then
    php artisan key:generate --force || true
fi

# Wait for Postgres, then migrate + seed (the Laravel analogue of Flyway-on-boot).
echo "Waiting for database ${DB_HOST:-db}:${DB_PORT:-5432}…"
until php -r "exit(@fsockopen(getenv('DB_HOST')?:'db', (int)(getenv('DB_PORT')?:5432)) ? 0 : 1);" 2>/dev/null; do
    sleep 2
done

php artisan migrate --force --seed || php artisan migrate --force
php artisan config:clear || true

exec php-fpm
