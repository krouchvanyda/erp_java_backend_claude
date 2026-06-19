# ===========================================================================
# ERP backend — Laravel 8 / PHP 7.4 (php-fpm)
# ===========================================================================
FROM php:7.4-fpm

# System deps + PHP extensions (pdo_pgsql for Postgres, pcntl for the
# websockets server, bcmath/zip for general use).
RUN apt-get update && apt-get install -y --no-install-recommends \
        git unzip libpq-dev libzip-dev libonig-dev \
    && docker-php-ext-install pdo pdo_pgsql pcntl bcmath zip \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Composer
COPY --from=composer:2 /usr/bin/composer /usr/bin/composer

WORKDIR /var/www/html

# Install PHP deps first (better layer caching).
COPY composer.json composer.lock* ./
RUN composer install --no-interaction --no-scripts --no-autoloader --prefer-dist || true

# App source
COPY . .

RUN composer install --no-interaction --optimize-autoloader --no-dev \
    && composer dump-autoload --optimize \
    && chown -R www-data:www-data storage bootstrap/cache \
    && chmod -R 775 storage bootstrap/cache

EXPOSE 9000
CMD ["php-fpm"]
