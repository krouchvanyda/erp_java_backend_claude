<?php

/*
| kreait/laravel-firebase config. Only the messaging side is used (FCM call
| push). The service stays a no-op unless FCM_ENABLED=true and a service
| account JSON is provided — see App\Features\Devices\Services\FcmService.
*/

return [
    'projects' => [
        'app' => [
            'credentials' => [
                'file' => env('FCM_SERVICE_ACCOUNT_JSON_PATH') ?: null,
            ],
        ],
    ],
    'default' => 'app',
];
